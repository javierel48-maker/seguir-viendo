package com.seguirviendo
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import org.json.JSONObject

// Detecta lo que reproducen otras apps (Netflix, Flow, Spotify, YouTube...) y lo guarda en el televisor.
class MediaListener : NotificationListenerService() {
    private var msm: MediaSessionManager? = null
    private val cbs = ArrayList<Pair<MediaController, Cb>>()
    private val listener = MediaSessionManager.OnActiveSessionsChangedListener { list -> attach(list ?: emptyList()) }

    private inner class Cb(val c: MediaController) : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = save(c)
        override fun onPlaybackStateChanged(state: PlaybackState?) = save(c)
    }

    override fun onListenerConnected() {
        try {
            val m = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            msm = m
            val cn = ComponentName(this, MediaListener::class.java)
            m.addOnActiveSessionsChangedListener(listener, cn)
            attach(m.getActiveSessions(cn))
        } catch (e: Exception) { }
    }

    override fun onListenerDisconnected() {
        try { msm?.removeOnActiveSessionsChangedListener(listener) } catch (e: Exception) { }
        detach()
    }

    private fun detach() {
        for ((c, cb) in cbs) try { c.unregisterCallback(cb) } catch (e: Exception) { }
        cbs.clear()
    }

    private fun attach(list: List<MediaController>) {
        detach()
        for (c in list) {
            if (c.packageName == packageName) continue
            val cb = Cb(c)
            c.registerCallback(cb)
            cbs.add(c to cb)
            save(c)
        }
    }

    private fun save(c: MediaController) {
        try {
            val m = c.metadata ?: return
            val st = c.playbackState
            var pos = st?.position ?: -1L
            if (st != null && st.state == PlaybackState.STATE_PLAYING && st.lastPositionUpdateTime > 0)
                pos += ((SystemClock.elapsedRealtime() - st.lastPositionUpdateTime) * st.playbackSpeed).toLong()
            val label = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(c.packageName, 0)).toString() } catch (e: Exception) { c.packageName }
            val o = JSONObject().put("pkg", c.packageName).put("service", label)
                .put("title", m.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "")
                .put("artist", m.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "")
                .put("album", m.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: "")
                .put("dTitle", m.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: "")
                .put("dSub", m.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: "")
                .put("duration", m.getLong(MediaMetadata.METADATA_KEY_DURATION))
                .put("position", pos).put("state", st?.state ?: -1).put("time", System.currentTimeMillis())
            Store(this).saveDetection(c.packageName, o)
        } catch (e: Exception) { }
    }
}
