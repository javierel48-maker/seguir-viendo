package com.seguirviendo
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.BaseColumns
import androidx.tvprovider.media.tv.*

class Entry(val item: Item, val variant: String, val label: String, val rowTitle: String)

object Sync {
    @Volatile var entries: List<Entry> = emptyList()

    // Enlace de Stremio con arranque automático (datos del episodio codificados)
    fun url(t: String, i: String, v: String?, variant: String): String {
        val base = "stremio:///detail/$t/$i"
        val vid = v ?: if (t == "movie") i else ""
        return if (vid.isEmpty()) base else "$base/${vid.replace(":", "%3A")}?autoPlay=true"
    }

    fun cardIntent(c: Context, e: Entry) = Intent(c, OpenActivity::class.java)
        .putExtra("t", e.item.type).putExtra("i", e.item.id).putExtra("v", e.item.videoId).putExtra("x", e.variant)

    fun open(c: Context, e: Entry) = openLink(c, e.item.type, e.item.id, e.item.videoId, e.variant)

    fun openLink(c: Context, t: String, i: String, v: String?, variant: String) {
        try { c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url(t, i, v, variant))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (e: Exception) { c.packageManager.getLeanbackLaunchIntentForPackage("com.stremio.one")?.let { c.startActivity(it) } }
    }

    fun askRow(a: Activity) {
        val id = Store(a).channelId
        if (id > 0) try { TvContractCompat.requestChannelBrowsable(a, id) } catch (e: Exception) {}
    }
    fun askRowOnce(a: Activity) {
        val s = Store(a)
        if (!s.asked && s.channelId > 0) { s.asked = true; askRow(a) }
    }

    private fun logo(c: Context, id: Long) {
        try {
            val bmp = BitmapFactory.decodeResource(c.resources, R.drawable.icon)
            ChannelLogoUtils.storeChannelLogo(c, id, Bitmap.createScaledBitmap(bmp, 256, 256, true))
        } catch (e: Exception) { }
    }

    private fun channel(c: Context, s: Store): Long {
        if (s.channelId > 0) return s.channelId
        val bmp = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF45B0E6.toInt()) }
        val ch = PreviewChannel.Builder().setDisplayName("Seguir viendo para Javi")
            .setAppLinkIntent(Intent(c, MainActivity::class.java)).setLogo(bmp).build()
        val id = PreviewChannelHelper(c).publishDefaultChannel(ch)
        s.channelId = id
        return id
    }

    fun run(c: Context): String {
        val s = Store(c)
        val key = s.authKey ?: return "Falta iniciar sesión"
        val lib = Stremio.inProgress(key)
        val hid = s.hidden()
        val list = lib.items.filter { it.last > hid.optLong(it.id, -1) }

        val es = list.map { x ->
            val p = x.videoId?.split(":")
            val ep = if (x.type != "movie" && p != null && p.size >= 3) " · T${p[1]} E${p[2]}" else ""
            Entry(x, "C", "${x.name} (Stremio)", "${x.name}$ep (Stremio)")
        }
        entries = es
        var msg = "Biblioteca: ${lib.total}. A medias: ${list.size}."

        try {
            val chId = channel(c, s)
            logo(c, chId)
            c.contentResolver.query(TvContractCompat.buildPreviewProgramsUriForChannel(chId), arrayOf(BaseColumns._ID), null, null, null)?.use {
                while (it.moveToNext()) c.contentResolver.delete(TvContractCompat.buildPreviewProgramUri(it.getLong(0)), null, null)
            }
            var n = 0
            es.forEachIndexed { i, e ->
                val x = e.item
                val parts = x.videoId?.split(":")
                val b = PreviewProgram.Builder().setChannelId(chId)
                    .setType(if (x.type == "movie") TvContractCompat.PreviewPrograms.TYPE_MOVIE else TvContractCompat.PreviewPrograms.TYPE_CLIP)
                    .setTitle(e.rowTitle).setInternalProviderId(x.id).setWeight(1000 - i)
                    .setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_2_3)
                    .setIntent(cardIntent(c, e))
                if (parts != null && parts.size >= 3) b.setDescription("Temporada ${parts[1]} · Episodio ${parts[2]}")
                x.poster?.let { p -> b.setPosterArtUri(Uri.parse(p)) }
                if (c.contentResolver.insert(TvContractCompat.PreviewPrograms.CONTENT_URI, b.build().toContentValues()) != null) n++
            }
            msg += " Fila propia: $n."
        } catch (e: Exception) { msg += " Fila propia con error: ${e.message}." }

        try {
            c.contentResolver.delete(TvContractCompat.WatchNextPrograms.CONTENT_URI, null, null)
            for (x in list) {
                val b = WatchNextProgram.Builder()
                    .setType(if (x.type == "movie") TvContractCompat.WatchNextPrograms.TYPE_MOVIE else TvContractCompat.WatchNextPrograms.TYPE_CLIP)
                    .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
                    .setLastEngagementTimeUtcMillis(x.last).setTitle(x.name).setInternalProviderId(x.id)
                    .setLastPlaybackPositionMillis(x.offset.toInt())
                    .setPosterArtAspectRatio(TvContractCompat.WatchNextPrograms.ASPECT_RATIO_2_3)
                    .setIntent(cardIntent(c, Entry(x, "C", x.name, x.name)))
                if (x.duration > 0) b.setDurationMillis(x.duration.toInt())
                x.poster?.let { p -> b.setPosterArtUri(Uri.parse(p)) }
                c.contentResolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, b.build().toContentValues())
            }
        } catch (e: Exception) { }
        return msg
    }
}
