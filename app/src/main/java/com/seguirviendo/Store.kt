package com.seguirviendo
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class Store(c: Context) {
    private val p = c.getSharedPreferences("s", 0)
    var authKey: String?
        get() = p.getString("k", null)
        set(v) { p.edit().putString("k", v).apply() }
    var channelId: Long
        get() = p.getLong("c", -1)
        set(v) { p.edit().putLong("c", v).apply() }
    var asked: Boolean
        get() = p.getBoolean("a", false)
        set(v) { p.edit().putBoolean("a", v).apply() }
    fun opened(): JSONObject = JSONObject(p.getString("o", "{}")!!)
    fun markOpened(id: String) { p.edit().putString("o", opened().put(id, System.currentTimeMillis()).toString()).apply() }
    var tmdbKey: String?
        get() = p.getString("tk", null)
        set(v) { p.edit().putString("tk", v).apply() }
    fun manual(): JSONArray = JSONArray(p.getString("m", "[]")!!)
    private fun saveManual(a: JSONArray) { p.edit().putString("m", a.toString()).apply() }
    fun addManual(o: JSONObject) {
        val a = manual(); val n = JSONArray()
        for (i in 0 until a.length()) if (a.getJSONObject(i).getString("id") != o.getString("id")) n.put(a.getJSONObject(i))
        n.put(o); saveManual(n)
    }
    fun removeManual(id: String) {
        val a = manual(); val n = JSONArray()
        for (i in 0 until a.length()) if (a.getJSONObject(i).getString("id") != id) n.put(a.getJSONObject(i))
        saveManual(n)
    }
    fun updateEpisode(id: String, season: Int, episode: Int) {
        val a = manual()
        for (i in 0 until a.length()) { val o = a.getJSONObject(i); if (o.getString("id") == id) { o.put("s", season).put("e", episode).put("last", System.currentTimeMillis()) } }
        saveManual(a)
    }
    fun hidden(): JSONObject = JSONObject(p.getString("h", "{}")!!)
    fun hide(id: String) { p.edit().putString("h", hidden().put(id, System.currentTimeMillis()).toString()).apply() }
}
