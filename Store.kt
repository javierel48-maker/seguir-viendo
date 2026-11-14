package com.seguirviendo
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class Store(private val c: Context) {
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
    fun saveItems(list: List<Item>) {
        val a = JSONArray()
        for (x in list) a.put(JSONObject().put("id", x.id).put("name", x.name).put("type", x.type).put("poster", x.poster ?: "")
            .put("video", x.videoId ?: "").put("off", x.offset).put("dur", x.duration).put("last", x.last).put("yr", x.year))
        p.edit().putString("li", a.toString()).apply()
    }
    fun loadItems(): List<Item> {
        val out = ArrayList<Item>()
        try {
            val a = JSONArray(p.getString("li", "[]")!!)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out.add(Item(o.getString("id"), o.optString("name"), o.optString("type"), o.optString("poster").takeIf { it.isNotBlank() },
                    o.optString("video").takeIf { it.isNotBlank() }, o.optLong("off"), o.optLong("dur"), o.optLong("last"), o.optString("yr")))
            }
        } catch (e: Exception) { }
        return out
    }
    fun yearCache(id: String): String? = JSONObject(p.getString("ym", "{}")!!).let { if (it.has(id)) it.getString(id) else null }
    fun setYearCache(id: String, y: String) { p.edit().putString("ym", JSONObject(p.getString("ym", "{}")!!).put(id, y).toString()).apply() }
    fun manual(): JSONArray = JSONArray(p.getString("m", "[]")!!)
    private fun saveManual(a: JSONArray, allowEmpty: Boolean = false) {
        p.edit().putString("m", a.toString()).apply()
        Backup.saveAsync(c.applicationContext, allowEmpty)   // backup automático de lo agregado a mano
    }
    var backupSig: String?
        get() = p.getString("bs", null)
        set(v) { p.edit().putString("bs", v).apply() }
    var pubSig: String
        get() = p.getString("ps", "")!!
        set(v) { p.edit().putString("ps", v).apply() }
    var pubTime: Long
        get() = p.getLong("pt", 0L)
        set(v) { p.edit().putLong("pt", v).apply() }
    // Trakt
    var trClient: String?
        get() = p.getString("trc", null)
        set(v) { p.edit().putString("trc", v).apply() }
    var trSecret: String?
        get() = p.getString("trs", null)
        set(v) { p.edit().putString("trs", v).apply() }
    var trAccess: String?
        get() = p.getString("tra", null)
        set(v) { p.edit().putString("tra", v).apply() }
    var trRefresh: String?
        get() = p.getString("trr", null)
        set(v) { p.edit().putString("trr", v).apply() }
    var trExpires: Long
        get() = p.getLong("tre", 0L)
        set(v) { p.edit().putLong("tre", v).apply() }
    fun addManual(o: JSONObject) {
        val a = manual(); val n = JSONArray()
        for (i in 0 until a.length()) if (a.getJSONObject(i).getString("id") != o.getString("id")) n.put(a.getJSONObject(i))
        n.put(o); saveManual(n)
    }
    fun removeManual(id: String) {
        val a = manual(); val n = JSONArray()
        for (i in 0 until a.length()) if (a.getJSONObject(i).getString("id") != id) n.put(a.getJSONObject(i))
        saveManual(n, true)
    }
    fun setInfo(id: String, rt: Int, yr: String) {
        val a = manual()
        for (i in 0 until a.length()) { val o = a.getJSONObject(i); if (o.getString("id") == id) o.put("rt", rt).put("yr", yr) }
        saveManual(a)
    }
    fun updateEpisode(id: String, season: Int, episode: Int) {
        val a = manual()
        for (i in 0 until a.length()) { val o = a.getJSONObject(i); if (o.getString("id") == id) { o.put("s", season).put("e", episode).put("last", System.currentTimeMillis()) } }
        saveManual(a)
    }
    // Capítulo siguiente (según Trakt) de una serie manual; s=0 lo borra. Devuelve true si algo cambió.
    fun setNext(id: String, tid: Int, s: Int, e: Int, left: Int = -1): Boolean {
        val a = manual(); var changed = false
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            if (o.getString("id") != id) continue
            if (tid > 0 && o.optInt("tid", 0) != tid) { o.put("tid", tid); changed = true }
            if (s > 0 && e > 0) { if (o.optInt("s", 0) != s || o.optInt("e", 0) != e) { o.put("s", s).put("e", e); changed = true } }
            else if (o.has("s") || o.has("e")) { o.remove("s"); o.remove("e"); changed = true }
            if (left >= 0) { if (o.optInt("left", -1) != left) { o.put("left", left); changed = true } }
            else if (o.has("left")) { o.remove("left"); changed = true }
        }
        if (changed) saveManual(a)
        return changed
    }
    // Capítulos que faltan de las series de Stremio (según Trakt), con la hora de la consulta.
    fun leftJson(): JSONObject = try { JSONObject(p.getString("lc", "{}")!!) } catch (e: Exception) { JSONObject() }
    fun leftWatched(id: String): Long = leftJson().optJSONObject(id)?.optLong("w", 0L) ?: 0L
    // Posiciones movidas a mano: id -> "fecha de último visto" inventada, que vale hasta que lo vuelvas a ver.
    fun moved(): JSONObject = try { JSONObject(p.getString("mv", "{}")!!) } catch (e: Exception) { JSONObject() }
    fun setMoved(id: String, v: Long, r: Long) { p.edit().putString("mv", moved().put(id, JSONObject().put("v", v).put("r", r)).toString()).apply() }
    fun setMovedRaw(id: String, o: JSONObject) { p.edit().putString("mv", moved().put(id, o).toString()).apply() }
    fun clearMoved(id: String) { val j = moved(); j.remove(id); p.edit().putString("mv", j.toString()).apply() }
    fun leftFor(id: String): Int = try { JSONObject(p.getString("lc", "{}")!!).optJSONObject(id)?.optInt("n", -1) ?: -1 } catch (e: Exception) { -1 }
    fun leftAge(id: String): Long = try {
        val t = JSONObject(p.getString("lc", "{}")!!).optJSONObject(id)?.optLong("t", 0L) ?: 0L
        if (t == 0L) Long.MAX_VALUE else System.currentTimeMillis() - t
    } catch (e: Exception) { Long.MAX_VALUE }
    fun setLeft(id: String, n: Int, lw: Long = 0L) {
        val j = JSONObject(p.getString("lc", "{}")!!)
        p.edit().putString("lc", j.put(id, JSONObject().put("n", n).put("t", System.currentTimeMillis()).put("w", lw)).toString()).apply()
    }
    fun hidden(): JSONObject = JSONObject(p.getString("h", "{}")!!)
    fun hide(id: String) { p.edit().putString("h", hidden().put(id, System.currentTimeMillis()).toString()).apply() }
}
