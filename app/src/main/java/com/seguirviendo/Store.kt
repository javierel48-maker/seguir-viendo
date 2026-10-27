package com.seguirviendo
import android.content.Context
import org.json.JSONObject

class Store(c: Context) {
    private val p = c.getSharedPreferences("s", 0)
    var authKey: String?
        get() = p.getString("k", null)
        set(v) { p.edit().putString("k", v).apply() }
    fun hidden(): JSONObject = JSONObject(p.getString("h", "{}")!!)
    fun hide(id: String) { p.edit().putString("h", hidden().put(id, System.currentTimeMillis()).toString()).apply() }
}
