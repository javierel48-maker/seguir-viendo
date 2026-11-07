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
}
