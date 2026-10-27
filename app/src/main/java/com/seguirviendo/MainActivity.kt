package com.seguirviendo
import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.widget.*
import androidx.work.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class MainActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val s = Store(this)
        val email = EditText(this).apply { hint = "Email de Stremio"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS }
        val pass = EditText(this).apply { hint = "Contraseña"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        val st = TextView(this).apply { text = if (s.authKey != null) "Sesión iniciada. Podés tocar Sincronizar." else "Iniciá sesión en Stremio." }
        val go = Button(this).apply { text = "Entrar y sincronizar" }
        val say = { t: String -> runOnUiThread { st.text = t } }
        go.setOnClickListener {
            thread {
                try {
                    if (email.text.isNotBlank()) s.authKey = Stremio.login(email.text.toString().trim(), pass.text.toString())
                    if (s.authKey == null) throw Exception("Falta iniciar sesión")
                    Sync.run(this@MainActivity)
                    say("Listo: ya está en Seguir viendo.")
                } catch (e: Exception) { say("Error: ${e.message}") }
            }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(80, 60, 80, 60)
            addView(st); addView(email); addView(pass); addView(go)
        })
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("sync", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES).build())
        if (s.authKey != null) thread { runCatching { Sync.run(this@MainActivity) } }
    }
}
