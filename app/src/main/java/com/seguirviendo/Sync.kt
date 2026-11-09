package com.seguirviendo
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.BaseColumns
import androidx.tvprovider.media.tv.*

class Entry(val item: Item, val variant: String, val label: String, val rowTitle: String, val pkg: String? = null)

object Sync {
    @Volatile var entries: List<Entry> = emptyList()
    @Volatile var lastItems: List<Item> = emptyList()   // lo que viene de Stremio

    // "(1976)" o "(1959-1964)": sin guiones sueltos al principio ni al final.
    fun fmtYear(y: String): String {
        val t = y.replace('–', '-').replace('—', '-').trim().trim('-').trim()
        return if (t.isEmpty()) "" else "($t)"
    }

    fun url(t: String, i: String, v: String?, variant: String): String {
        val base = "stremio:///detail/$t/$i"
        val vid = v ?: if (t == "movie") i else ""
        return if (vid.isEmpty()) base else "$base/${vid.replace(":", "%3A")}?autoPlay=true"
    }

    fun cardIntent(c: Context, e: Entry) = Intent(c, OpenActivity::class.java)
        .putExtra("t", e.item.type).putExtra("i", e.item.id).putExtra("v", e.item.videoId).putExtra("x", e.variant).putExtra("p", e.pkg)

    fun open(c: Context, e: Entry) = openLink(c, e.item.type, e.item.id, e.item.videoId, e.variant, e.pkg)

    fun openLink(c: Context, t: String, i: String, v: String?, variant: String, pkg: String? = null, done: (() -> Unit)? = null) {
        if (pkg != null && pkg.isBlank()) { done?.invoke(); return }   // "Otro": no abre nada
        val appCtx = c.applicationContext
        Thread { try { touch(appCtx, i) } catch (e: Exception) { } }.start()
        if (pkg != null) {   // lo agregado a mano: abre la app de la plataforma elegida
            c.packageManager.getLeanbackLaunchIntentForPackage(pkg)?.let { try { c.startActivity(it) } catch (e: Exception) { } }
            done?.invoke(); return
        }
        try { (c.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager).killBackgroundProcesses("com.stremio.one") } catch (e: Exception) { }
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try { c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url(t, i, v, variant))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) }
            catch (e: Exception) { c.packageManager.getLeanbackLaunchIntentForPackage("com.stremio.one")?.let { c.startActivity(it) } }
            done?.invoke()
        }, 450)
    }

    // Abre el buscador de Stremio (cierra Stremio y lo abre de cero, como con los títulos).
    fun openStremioSearch(c: Context) {
        try { (c.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager).killBackgroundProcesses("com.stremio.one") } catch (e: Exception) { }
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try { c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("stremio:///search?search=")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) }
            catch (e: Exception) { c.packageManager.getLeanbackLaunchIntentForPackage("com.stremio.one")?.let { c.startActivity(it) } }
        }, 450)
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
        val ch = PreviewChannel.Builder().setDisplayName("Seguir viendo (Javi Videoclub)")
            .setAppLinkIntent(Intent(c, MainActivity::class.java)).setLogo(bmp).build()
        val id = PreviewChannelHelper(c).publishDefaultChannel(ch)
        s.channelId = id
        return id
    }

    private fun effective(s: Store, x: Item): Long = maxOf(x.last, s.opened().optLong(x.id, 0L))

    private fun manualItems(s: Store): List<Item> {
        val a = s.manual(); val out = ArrayList<Item>()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            out.add(Item(o.getString("id"), o.optString("name"), o.optString("type"), o.optString("poster").takeIf { it.isNotBlank() },
                null, 0L, o.optInt("rt", 0).coerceAtLeast(0).toLong(), o.optLong("last"), o.optString("yr")))   // en lo manual, "duration" guarda los minutos
        }
        return out
    }

    private fun buildEntries(s: Store): List<Entry> {
        val meta = HashMap<String, Pair<String, String>>()
        val a = s.manual()
        for (i in 0 until a.length()) { val o = a.getJSONObject(i); meta[o.getString("id")] = o.optString("pkg") to o.optString("label") }
        if (lastItems.isEmpty()) lastItems = s.loadItems()
        val all = (lastItems + manualItems(s)).sortedByDescending { effective(s, it) }
        return all.map { x ->
            val p = x.videoId?.split(":")
            val ep = if (x.type != "movie" && p != null && p.size >= 3) " · T${p[1]} E${p[2]}" else ""
            val m = meta[x.id]
            val svc = m?.second ?: "Stremio"
            val yr = fmtYear(x.year).let { if (it.isEmpty()) "" else " $it" }
            Entry(x, "C", "${x.name} ($svc)", "${x.name}$yr$ep ($svc)", m?.first)
        }
    }

    private fun publishRow(c: Context, s: Store, es: List<Entry>): String {
        return try {
            val chId = channel(c, s)
            logo(c, chId)
            c.contentResolver.query(TvContractCompat.buildPreviewProgramsUriForChannel(chId), arrayOf(BaseColumns._ID), null, null, null)?.use {
                while (it.moveToNext()) c.contentResolver.delete(TvContractCompat.buildPreviewProgramUri(it.getLong(0)), null, null)
            }
            var n = 0
            es.take(50).forEachIndexed { i, e ->
                val x = e.item
                val parts = x.videoId?.split(":")
                val b = PreviewProgram.Builder().setChannelId(chId)
                    .setType(if (x.type == "movie") TvContractCompat.PreviewPrograms.TYPE_MOVIE else TvContractCompat.PreviewPrograms.TYPE_CLIP)
                    .setTitle(e.rowTitle).setInternalProviderId(x.id).setWeight(1000 - i)
                    .setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9)
                    .setIntent(cardIntent(c, e))
                if (parts != null && parts.size >= 3) b.setDescription("Temporada ${parts[1]} · Episodio ${parts[2]}")
                x.poster?.let { p -> b.setPosterArtUri(Uri.parse("content://com.seguirviendo.posters/p.png?v=12&u=" + Uri.encode(p))) }
                if (c.contentResolver.insert(TvContractCompat.PreviewPrograms.CONTENT_URI, b.build().toContentValues()) != null) n++
            }
            " Fila propia: $n."
        } catch (e: Exception) { " Fila propia con error: ${e.message}." }
    }

    // Reordena y republica la fila sin tocar la red (cambios locales: abrir, agregar, borrar, marcar vista).
    fun rebuild(c: Context) {
        val s = Store(c)
        val es = buildEntries(s)
        entries = es
        publishRow(c, s, es)
    }

    // Al abrir un título pasa al principio al instante (sin esperar a Stremio) y se actualiza la fila.
    fun touch(c: Context, id: String) {
        val s = Store(c)
        s.markOpened(id)
        if (lastItems.isEmpty()) lastItems = s.loadItems()
        if (lastItems.isEmpty() && s.authKey != null) { try { run(c) } catch (e: Exception) { rebuild(c) }; return }
        rebuild(c)
    }

    fun run(c: Context): String {
        val s = Store(c)
        val key = s.authKey ?: return "Falta iniciar sesión"
        val lib = Stremio.inProgress(key)
        val hid = s.hidden()
        val tk = s.tmdbKey
        var pedidos = 0
        val list = lib.items.filter { it.last > hid.optLong(it.id, -1) }.map { x ->
            if (x.year.isNotBlank() || !x.id.startsWith("tt")) x
            else {   // sin año en Stremio: se completa desde TMDB (y se recuerda)
                var y = s.yearCache(x.id)
                if (y == null && !tk.isNullOrBlank() && pedidos < 25) {
                    pedidos++
                    y = try { Tmdb.yearForImdb(tk, x.id, x.type != "movie") } catch (e: Exception) { null }
                    if (y != null) s.setYearCache(x.id, y)
                }
                if (y.isNullOrBlank()) x else Item(x.id, x.name, x.type, x.poster, x.videoId, x.offset, x.duration, x.last, y)
            }
        }
        lastItems = list
        s.saveItems(list)
        val es = buildEntries(s)
        entries = es
        val msg = "Biblioteca: ${lib.total}. A medias: ${list.size}." + publishRow(c, s, es)
        try {
            c.contentResolver.delete(TvContractCompat.WatchNextPrograms.CONTENT_URI, null, null)
            for (x in list.take(20)) {
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
