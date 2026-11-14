package com.seguirviendo
import android.content.Context

// Consulta a Trakt: capítulo siguiente y cuántos faltan (series manuales y de Stremio) y cuándo viste algo por última vez.
// Lo usan la app al abrirse y la tarea en segundo plano (también al encender el aparato).
object TraktSync {
    private const val MANUAL_FRESH = 3 * 60 * 1000L        // no repite una serie manual antes de 3 minutos
    private const val STREMIO_FRESH = 20 * 60 * 1000L      // ni una de Stremio antes de 20 minutos

    // Devuelve true si algo cambió (hay que reordenar / republicar).
    fun refresh(c: Context): Boolean {
        val s = Store(c)
        if (!Trakt.connected(s)) return false
        var changed = false
        var calls = 0
        // series manuales
        val a = s.manual()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            if (o.optString("type") == "movie") continue
            val id = o.getString("id")
            val tmdb = id.drop(2).toIntOrNull() ?: continue
            if (s.leftAge(id) < MANUAL_FRESH) continue
            if (calls++ >= 30) break
            try {
                var tid = o.optInt("tid", 0)
                if (tid <= 0) tid = Trakt.showId(s, tmdb) ?: continue
                val pr = Trakt.progress(s, tid.toString())
                val before = s.leftWatched(id)
                if (s.setNext(id, tid, pr.next?.first ?: 0, pr.next?.second ?: 0, pr.left)) changed = true
                s.setLeft(id, pr.left, pr.lastWatched)
                if (pr.lastWatched != before) changed = true
            } catch (e: Exception) { }
        }
        // series de Stremio (por el código de IMDb)
        var n = 0
        for (x in Sync.lastItems) {
            if (x.type != "series" || !x.id.startsWith("tt")) continue
            if (s.leftAge(x.id) < STREMIO_FRESH) continue
            if (n++ >= 25) break
            try {
                val pr = Trakt.progress(s, x.id)
                if (s.leftFor(x.id) != pr.left || s.leftWatched(x.id) != pr.lastWatched) changed = true
                s.setLeft(x.id, pr.left, pr.lastWatched)
            } catch (e: Trakt.TraktHttp) { if (e.code == 404) s.setLeft(x.id, -1, 0L) }   // no está en Trakt: pantalla vacía
            catch (e: Exception) { }
        }
        return changed
    }
}
