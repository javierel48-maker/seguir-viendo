package com.seguirviendo
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

// Backup de lo agregado a mano (lo de Stremio se vuelve a leer de la cuenta).
// Se guarda en la carpeta Descargas del aparato, fuera de la memoria privada de la app.
object Backup {
    const val NAME = "javi-videoclub-backup.json"
    class Info(val ts: Long, val count: Int, val manual: JSONArray)

    fun fmt(ts: Long): String = SimpleDateFormat("d/M/yyyy HH:mm", Locale("es", "AR")).format(Date(ts))

    private fun ids(a: JSONArray): Set<String> {
        val out = HashSet<String>()
        for (i in 0 until a.length()) out.add(a.getJSONObject(i).optString("id"))
        return out
    }

    fun parse(text: String?): Info? {
        if (text.isNullOrBlank()) return null
        return try {
            val j = JSONObject(text)
            if (j.optString("app") != "javi-videoclub") return null
            val a = j.getJSONArray("manual")
            Info(j.optLong("ts"), a.length(), a)
        } catch (e: Exception) { null }
    }

    private fun legacyFile(c: Context) = File(c.getExternalFilesDir(null) ?: c.filesDir, NAME)

    private fun write(c: Context, data: String): Boolean {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val res = c.contentResolver
                val uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                var target: Uri? = null
                res.query(uri, arrayOf(MediaStore.MediaColumns._ID),
                    MediaStore.MediaColumns.DISPLAY_NAME + "=?", arrayOf(NAME), null)?.use { cu ->
                    if (cu.moveToFirst()) target = ContentUris.withAppendedId(uri, cu.getLong(0))
                }
                val t = target
                if (t != null) {
                    try {
                        res.openOutputStream(t, "wt")?.use { it.write(data.toByteArray()) }
                        return true
                    } catch (e: Exception) { }   // el archivo viejo no es nuestro: se crea uno nuevo
                }
                val cv = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, NAME)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val nu = res.insert(uri, cv) ?: return false
                val os = res.openOutputStream(nu) ?: return false
                os.use { it.write(data.toByteArray()) }
                return true
            }
            legacyFile(c).writeText(data)
            return true
        } catch (e: Exception) { return false }
    }

    // El backup más nuevo que se pueda leer.
    fun find(c: Context): Info? {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val res = c.contentResolver
                val uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                val found = ArrayList<Uri>()
                res.query(uri, arrayOf(MediaStore.MediaColumns._ID),
                    MediaStore.MediaColumns.DISPLAY_NAME + " LIKE ?", arrayOf("javi-videoclub-backup%"),
                    MediaStore.MediaColumns.DATE_MODIFIED + " DESC")?.use { cu ->
                    while (cu.moveToNext()) found.add(ContentUris.withAppendedId(uri, cu.getLong(0)))
                }
                for (u in found) {
                    val b = try { parse(res.openInputStream(u)?.bufferedReader()?.use { it.readText() }) } catch (e: Exception) { null }
                    if (b != null) return b
                }
                return null
            }
            val f = legacyFile(c)
            return if (f.exists()) parse(f.readText()) else null
        } catch (e: Exception) { return null }
    }

    @Synchronized
    fun save(c: Context, allowEmpty: Boolean = false, force: Boolean = false): String {
        val s = Store(c)
        val items = s.manual()
        if (items.length() == 0 && !allowEmpty) return "Sin títulos manuales: no se toca el backup."
        if (s.backupSig == null) {   // instalación nueva: no pisar un backup anterior que todavía no se restauró
            val old = find(c)
            if (old != null && old.count > 0 && (ids(old.manual) - ids(items)).isNotEmpty()) return "Hay un backup anterior sin restaurar."
        }
        val sig = items.toString()
        if (!force && s.backupSig == sig && find(c) != null) return "Backup al día."
        val data = JSONObject().put("app", "javi-videoclub").put("version", 1).put("ts", System.currentTimeMillis())
            .put("count", items.length()).put("manual", items).toString()
        return if (write(c, data)) { s.backupSig = sig; "Backup guardado." } else "No pude guardar el backup."
    }

    fun saveAsync(c: Context, allowEmpty: Boolean = false) {
        thread { try { save(c, allowEmpty) } catch (e: Exception) { } }
    }

    // Agrega lo que falte (no borra ni duplica). Devuelve cuántos títulos agregó.
    fun restore(c: Context, b: Info): Int {
        val s = Store(c)
        val have = ids(s.manual())
        var n = 0
        for (i in 0 until b.manual.length()) {
            val o = b.manual.getJSONObject(i)
            if (o.optString("id") in have) continue
            s.addManual(o); n++
        }
        return n
    }
}
