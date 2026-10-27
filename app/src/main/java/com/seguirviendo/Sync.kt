package com.seguirviendo
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram

object Sync {
    fun run(c: Context) {
        val s = Store(c)
        val key = s.authKey ?: return
        val hid = s.hidden()
        val items = Stremio.inProgress(key).filter { it.last > hid.optLong(it.id, -1) }
        c.contentResolver.delete(TvContractCompat.WatchNextPrograms.CONTENT_URI, null, null)
        for (it in items) {
            val link = "stremio:///detail/${it.type}/${it.id}" + (it.videoId?.let { v -> "/$v" } ?: "")
            val parts = it.videoId?.split(":")
            val b = WatchNextProgram.Builder()
                .setType(if (it.type == "movie") TvContractCompat.WatchNextPrograms.TYPE_MOVIE else TvContractCompat.WatchNextPrograms.TYPE_CLIP)
                .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
                .setLastEngagementTimeUtcMillis(it.last)
                .setTitle(it.name)
                .setInternalProviderId(it.id)
                .setLastPlaybackPositionMillis(it.offset.toInt())
                .setPosterArtAspectRatio(TvContractCompat.WatchNextPrograms.ASPECT_RATIO_2_3)
                .setIntent(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
            if (it.duration > 0) b.setDurationMillis(it.duration.toInt())
            if (parts != null && parts.size >= 3) b.setDescription("Temporada ${parts[1]} · Episodio ${parts[2]}")
            it.poster?.let { p -> b.setPosterArtUri(Uri.parse(p)) }
            c.contentResolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, b.build().toContentValues())
        }
    }
}
