package com.seguirviendo
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

class Item(val id: String, val name: String, val type: String, val poster: String?,
           val videoId: String?, val offset: Long, val duration: Long, val last: Long)

class Lib(val total: Int, val items: List<Item>)

object Stremio {
    private fun post(path: String, body: JSONObject): JSONObject {
        val c = URL("https://api.strem.io/api/$path").openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val txt = (if (c.responseCode < 400) c.inputStream else c.errorStream).bufferedReader().readText()
        return JSONObject(txt)
    }

    fun login(email: String, pass: String): String {
        val r = post("login", JSONObject().put("type", "Login").put("email", email).put("password", pass).put("facebook", false))
        return r.optJSONObject("result")?.getString("authKey") ?: throw Exception("Email o contraseña incorrectos")
    }

    fun inProgress(key: String): Lib {
        val r = post("datastoreGet", JSONObject().put("authKey", key).put("collection", "libraryItem")
            .put("ids", JSONArray()).put("all", true))
        val arr = r.optJSONArray("result") ?: throw Exception("No pude leer la biblioteca")
        val out = ArrayList<Item>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val st = o.optJSONObject("state") ?: continue
            if (o.optBoolean("removed") && !o.optBoolean("temp")) continue
            val off = st.optLong("timeOffset"); if (off <= 0) continue
            val last = runCatching { Instant.parse(st.optString("lastWatched")).toEpochMilli() }.getOrDefault(0L)
            out.add(Item(o.getString("_id"), o.optString("name"), o.optString("type"),
                o.optString("poster").takeIf { it.isNotBlank() },
                st.optString("video_id").takeIf { it.isNotBlank() },
                off, st.optLong("duration"), last))
        }
        return Lib(arr.length(), out.sortedByDescending { it.last }.take(10))
    }
}
