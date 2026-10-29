package com.seguirviendo
import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.widget.*
import androidx.work.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var st: TextView
    private lateinit var list: LinearLayout

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val s = Store(this)
        val email = EditText(this).apply { hint = "Email de Stremio"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS }
        val pass = EditText(this).apply { hint = "Contraseña"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        st = TextView(this).apply { text = if (s.authKey != null) "Sesión iniciada." else "Iniciá sesión en Stremio." }
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val go = Button(this).apply { text = "Entrar y sincronizar" }
        val row = Button(this).apply { text = "Activar fila en el inicio" }
        go.setOnClickListener { sync(email.text.toString().trim(), pass.text.toString()) }
        row.setOnClickListener { Sync.askRow(this) }
        setContentView(ScrollView(this).apply {
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL; setPadding(80, 60, 80, 60)
                addView(st); addView(list); addView(email); addView(pass); addView(go); addView(row)
            })
        })
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("sync", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES).build())
        if (s.authKey != null) sync("", "")
    }

    private fun sync(e: String, p: String) = thread {
        val s = Store(this)
        try {
            if (e.isNotBlank()) s.authKey = Stremio.login(e, p)
            if (s.authKey == null) throw Exception("Falta iniciar sesión")
            val msg = Sync.run(this)
            runOnUiThread { st.text = msg; render(); Sync.askRowOnce(this) }
        } catch (x: Exception) { runOnUiThread { st.text = "Error: ${x.message}" } }
    }

    private fun render() {
        list.removeAllViews()
        for (x in Sync.items) {
            val ep = x.videoId?.split(":")?.takeIf { it.size >= 3 }?.let { "   T${it[1]} E${it[2]}" } ?: ""
            list.addView(Button(this).apply {
                text = x.name + ep
                isAllCaps = false
                setOnClickListener { Sync.open(this@MainActivity, x) }
                setOnLongClickListener { Store(this@MainActivity).hide(x.id); sync("", ""); true }
            })
        }
        if (Sync.items.isNotEmpty()) list.addView(TextView(this).apply {
            text = "OK abre en Stremio. Mantené OK apretado para quitar de la lista."
        })
    }
}
