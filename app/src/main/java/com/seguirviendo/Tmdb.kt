package com.seguirviendo
import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Tmdb {
    class Hit(val id: Int, val title: String, val english: String, val year: String, val tv: Boolean, val poster: String?)

    fun search(key: String, q: String): List<Hit> {
        val bearer = key.length > 60
        val u = "https://api.themoviedb.org/3/search/multi?language=en-US&include_adult=false&query=" + Uri.encode(q) +
            (if (bearer) "" else "&api_key=" + Uri.encode(key))
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 10000; c.readTimeout = 15000
        if (bearer) c.setRequestProperty("Authorization", "Bearer $key")
        val txt = (if (c.responseCode < 400) c.inputStream else c.errorStream).bufferedReader().readText()
        val j = JSONObject(txt)
        val arr = j.optJSONArray("results") ?: throw Exception(j.optString("status_message", "Error de TMDB"))
        val out = ArrayList<Hit>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val mt = o.optString("media_type")
            if (mt != "movie" && mt != "tv") continue
            val eng = o.optString(if (mt == "movie") "title" else "name")
            val orig = o.optString(if (mt == "movie") "original_title" else "original_name")
            val title = if (orig.isBlank() || orig.any { it.isLetter() && it.code > 0x24F }) eng else orig
            val date = o.optString(if (mt == "movie") "release_date" else "first_air_date")
            val pp = o.optString("poster_path").takeIf { it.isNotBlank() && it != "null" }
            out.add(Hit(o.getInt("id"), title, eng, date.take(4), mt == "tv", pp?.let { "https://image.tmdb.org/t/p/w342$it" }))
        }
        return out.take(12)
    }

    private fun getJson(key: String, path: String): JSONObject {
        val bearer = key.length > 60
        val u = "https://api.themoviedb.org/3$path" + (if (path.contains("?")) "&" else "?") + "language=en-US" + (if (bearer) "" else "&api_key=" + Uri.encode(key))
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 10000; c.readTimeout = 15000
        if (bearer) c.setRequestProperty("Authorization", "Bearer $key")
        val txt = (if (c.responseCode < 400) c.inputStream else c.errorStream).bufferedReader().readText()
        return JSONObject(txt)
    }

    private fun str(j: JSONObject, k: String): String = j.optString(k).let { if (it == "null") "" else it }

    class Info(val runtime: Int, val year: String)

    // Película: minutos y año. Serie: minutos de los capítulos que más se repiten (sin contar especiales largos) y período de años.
    fun info(key: String, id: Int, tv: Boolean): Info {
        val j = getJson(key, "/" + (if (tv) "tv" else "movie") + "/" + id)
        if (!tv) return Info(j.optInt("runtime", 0), str(j, "release_date").take(4))
        val first = str(j, "first_air_date").take(4)
        val last = str(j, "last_air_date").take(4)
        val going = str(j, "status") == "Returning Series" || j.optBoolean("in_production")
        val yr = if (first.isBlank()) "" else if (going) "$first-" else if (last.isBlank() || last == first) first else "$first-$last"
        val vals = ArrayList<Int>()
        j.optJSONArray("episode_run_time")?.let { a -> for (i in 0 until a.length()) if (a.getInt(i) > 0) vals.add(a.getInt(i)) }
        if (vals.isEmpty()) {
            try {
                val eps = getJson(key, "/tv/$id/season/1").optJSONArray("episodes")
                if (eps != null) for (i in 0 until eps.length()) { val r = eps.getJSONObject(i).optInt("runtime", 0); if (r > 0) vals.add(r) }
            } catch (e: Exception) { }
        }
        if (vals.isEmpty()) { val r = j.optJSONObject("last_episode_to_air")?.optInt("runtime", 0) ?: 0; if (r > 0) vals.add(r) }
        val rt = vals.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: 0
        return Info(rt, yr)
    }
}
