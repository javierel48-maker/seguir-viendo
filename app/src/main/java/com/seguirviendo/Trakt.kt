package com.seguirviendo
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Trakt {
    private const val API = "https://api.trakt.tv"
    class Res(val code: Int, val body: String)
    class Dev(val deviceCode: String, val userCode: String, val url: String, val expires: Int, val interval: Int)
    class Pending(val season: Int, val number: Int, val title: String)

    fun hasKeys(s: Store) = !s.trClient.isNullOrBlank()   // el Secret es opcional (apps PKCE no lo tienen)
    fun connected(s: Store) = hasKeys(s) && !s.trAccess.isNullOrBlank()

    private fun raw(method: String, path: String, body: JSONObject?, clientId: String, token: String?): Res {
        val c = URL(API + path).openConnection() as HttpURLConnection
        c.requestMethod = method; c.connectTimeout = 10000; c.readTimeout = 20000
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("trakt-api-version", "2")
        c.setRequestProperty("trakt-api-key", clientId)
        c.setRequestProperty("User-Agent", "JaviVideoclub/22")
        if (token != null) c.setRequestProperty("Authorization", "Bearer $token")
        if (body != null) { c.doOutput = true; c.outputStream.use { it.write(body.toString().toByteArray()) } }
        val code = c.responseCode
        val txt = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
        return Res(code, txt)
    }

    private fun saveToken(s: Store, j: JSONObject) {
        s.trAccess = j.getString("access_token")
        s.trRefresh = j.optString("refresh_token").takeIf { it.isNotBlank() } ?: s.trRefresh
        val created = j.optLong("created_at", System.currentTimeMillis() / 1000)
        s.trExpires = (created + j.optLong("expires_in", 86400L)) * 1000
    }

    private fun refresh(s: Store) {
        val body = JSONObject().put("refresh_token", s.trRefresh).put("client_id", s.trClient)
            .put("redirect_uri", "urn:ietf:wg:oauth:2.0:oob").put("grant_type", "refresh_token")
        if (!s.trSecret.isNullOrBlank()) body.put("client_secret", s.trSecret)
        val r = raw("POST", "/oauth/token", body, s.trClient!!, null)
        if (r.code != 200) throw Exception("Trakt pide conectar de nuevo (Más… → Conectar Trakt)")
        saveToken(s, JSONObject(r.body))
    }

    private fun call(s: Store, method: String, path: String, body: JSONObject? = null): Res {
        if (!connected(s)) throw Exception("Trakt no está conectado")
        if (System.currentTimeMillis() > s.trExpires - 60000) refresh(s)
        var r = raw(method, path, body, s.trClient!!, s.trAccess)
        if (r.code == 401) { refresh(s); r = raw(method, path, body, s.trClient!!, s.trAccess) }
        if (r.code >= 400) throw Exception("Trakt respondió ${r.code}")
        return r
    }

    // ---- Conexión con la cuenta (código en pantalla, sin escribir contraseñas en la tele) ----
    fun deviceCode(s: Store): Dev {
        val r = raw("POST", "/oauth/device/code", JSONObject().put("client_id", s.trClient), s.trClient!!, null)
        if (r.code != 200) throw Exception("Trakt no aceptó el Client ID (${r.code})")
        val j = JSONObject(r.body)
        return Dev(j.getString("device_code"), j.getString("user_code"), j.optString("verification_url", "https://trakt.tv/activate"),
            j.optInt("expires_in", 600), j.optInt("interval", 5))
    }

    // Devuelve null si quedó conectado, o el motivo si no.
    fun waitForApproval(s: Store, d: Dev, cancelled: () -> Boolean): String? {
        var wait = d.interval.coerceAtLeast(1) * 1000L
        val end = System.currentTimeMillis() + d.expires * 1000L
        while (System.currentTimeMillis() < end) {
            Thread.sleep(wait)
            if (cancelled()) return "Cancelado"
            val body = JSONObject().put("code", d.deviceCode).put("client_id", s.trClient)
            if (!s.trSecret.isNullOrBlank()) body.put("client_secret", s.trSecret)
            val r = raw("POST", "/oauth/device/token", body, s.trClient!!, null)
            when (r.code) {
                200 -> { saveToken(s, JSONObject(r.body)); return null }
                400 -> { }                                   // todavía no lo aprobaste
                429 -> wait += 1000L                          // muy seguido
                404 -> return "Código inválido"
                409 -> return "Ese código ya se usó"
                410 -> return "El código venció"
                418 -> return "Lo rechazaste en Trakt"
                401 -> return "Client ID o Secret incorrectos"
                else -> return "Trakt respondió ${r.code}"
            }
        }
        return "El código venció"
    }

    // ---- Películas ----
    // true = quedó registrada como vista; false = Trakt no la tiene.
    fun markMovie(s: Store, tmdb: Int): Boolean {
        val body = JSONObject().put("movies", JSONArray().put(JSONObject().put("ids", JSONObject().put("tmdb", tmdb))))
        val j = JSONObject(call(s, "POST", "/sync/history", body).body)
        val nf = j.optJSONObject("not_found")?.optJSONArray("movies")?.length() ?: 0
        val added = j.optJSONObject("added")?.optInt("movies", 0) ?: 0
        return nf == 0 && added > 0
    }

    // ---- Series ----
    fun showId(s: Store, tmdb: Int): Int? {
        val a = JSONArray(call(s, "GET", "/search/tmdb/$tmdb?type=show").body)
        for (i in 0 until a.length()) {
            val id = a.getJSONObject(i).optJSONObject("show")?.optJSONObject("ids")?.optInt("trakt", 0) ?: 0
            if (id > 0) return id
        }
        return null
    }

    // Capítulo siguiente que te toca ver, según Trakt (null si no hay o ya viste todo).
    fun next(s: Store, traktId: Int): Pair<Int, Int>? {
        val prog = JSONObject(call(s, "GET", "/shows/$traktId/progress/watched?hidden=false&specials=false").body)
        val n = prog.optJSONObject("next_episode") ?: return null
        val sn = n.optInt("season"); val en = n.optInt("number")
        return if (sn > 0 && en > 0) Pair(sn, en) else null
    }

    // Capítulos emitidos que NO figuran como vistos en Trakt (con la numeración y nombres de Trakt).
    fun pending(s: Store, traktId: Int): List<Pending> {
        val prog = JSONObject(call(s, "GET", "/shows/$traktId/progress/watched?hidden=false&specials=false").body)
        val titles = HashMap<String, String>()
        try {
            val ss = JSONArray(call(s, "GET", "/shows/$traktId/seasons?extended=episodes").body)
            for (i in 0 until ss.length()) {
                val so = ss.getJSONObject(i); val sn = so.optInt("number")
                val eps = so.optJSONArray("episodes") ?: continue
                for (k in 0 until eps.length()) { val eo = eps.getJSONObject(k); titles["$sn:${eo.optInt("number")}"] = eo.optString("title").let { if (it == "null") "" else it } }
            }
        } catch (e: Exception) { }
        val out = ArrayList<Pending>()
        val seasons = prog.optJSONArray("seasons") ?: return out
        for (i in 0 until seasons.length()) {
            val so = seasons.getJSONObject(i); val sn = so.optInt("number")
            if (sn == 0) continue
            val eps = so.optJSONArray("episodes") ?: continue
            for (k in 0 until eps.length()) {
                val eo = eps.getJSONObject(k)
                if (!eo.optBoolean("completed")) out.add(Pending(sn, eo.optInt("number"), titles["$sn:${eo.optInt("number")}"] ?: ""))
            }
        }
        return out.sortedWith(compareBy({ it.season }, { it.number }))
    }

    // Registra como vistos los capítulos elegidos. Devuelve cuántos quedaron registrados.
    fun markEpisodes(s: Store, traktId: Int, eps: List<Pending>): Int {
        val seasons = JSONArray()
        for ((sn, list) in eps.groupBy { it.season }) {
            val arr = JSONArray(); for (e in list) arr.put(JSONObject().put("number", e.number))
            seasons.put(JSONObject().put("number", sn).put("episodes", arr))
        }
        val body = JSONObject().put("shows", JSONArray().put(JSONObject().put("ids", JSONObject().put("trakt", traktId)).put("seasons", seasons)))
        val j = JSONObject(call(s, "POST", "/sync/history", body).body)
        return j.optJSONObject("added")?.optInt("episodes", 0) ?: 0
    }
}
