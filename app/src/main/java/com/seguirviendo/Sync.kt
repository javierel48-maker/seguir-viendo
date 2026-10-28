package com.seguirviendo
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram

object Sync {
    fun run(c: Context): String {
        val s = Store(c)
        val key = s.authKey ?: return "Falta iniciar sesión"
        val lib = Stremio.inProgress(key)
        val hid = s.hidden()
        val items = lib.items.filter { it.last > hid.optLong(it.id, -1) }
        c.contentResolver.delete(TvContractCompat.WatchNextPrograms.CONTENT_URI, null, null)
        var ok = 0
        var fail = ""
        for (x in items) {
            val link = "stremio:///detail/${x.type}/${x.id}" + (x.videoId?.let { v -> "/$v" } ?: "")
            val parts = x.videoId?.split(":")
            val b = WatchNextProgram.Builder()
                .setType(if (x.type == "movie") TvContractCompat.WatchNextPrograms.TYPE_MOVIE else TvContractCompat.WatchNextPrograms.TYPE_CLIP)
                .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
                .setLastEngagementTimeUtcMillis(x.last)
                .setTitle(x.name)
                .setInternalProviderId(x.id)
                .setLastPlaybackPositionMillis(x.offset.toInt())
                .setPosterArtAspectRatio(TvContractCompat.WatchNextPrograms.ASPECT_RATIO_2_3)
                .setIntent(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
            if (x.duration > 0) b.setDurationMillis(x.duration.toInt())
            if (parts != null && parts.size >= 3) b.setDescription("Temporada ${parts[1]} · Episodio ${parts[2]}")
            x.poster?.let { p -> b.setPosterArtUri(Uri.parse(p)) }
            try {
                if (c.contentResolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, b.build().toContentValues()) != null) ok++
            } catch (e: Exception) { fail = e.message ?: e.javaClass.simpleName }
        }
        return "Biblioteca: ${lib.total}. A medias: ${lib.items.size}. Publicadas: $ok." +
            (if (fail.isNotEmpty()) "\nError al publicar: $fail" else "") +
            "\n" + items.joinToString("\n") { "• " + it.name }
    }
}
