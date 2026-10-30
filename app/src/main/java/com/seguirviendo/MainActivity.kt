package com.seguirviendo
import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.work.*
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var st: TextView
    private lateinit var list: LinearLayout
    private lateinit var login: LinearLayout
    private lateinit var go: Button
    private val cache = HashMap<String, Bitmap>()
    private val sky = 0xFF45B0E6.toInt()
    private val night = 0xFF0F1720.toInt()
    private val soft = 0xFF8FA3B3.toInt()

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun tv(t: String, sp: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = t; setTextSize(TypedValue.COMPLEX_UNIT_SP, sp); setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    private fun bg(f: Boolean) = GradientDrawable().apply {
        cornerRadius = dp(14).toFloat(); setColor(if (f) 0xFF2A6F96.toInt() else 0xFF1B2733.toInt())
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val s = Store(this)
        val email = EditText(this).apply {
            hint = "Email de Stremio"; setTextColor(0xFFFFFFFF.toInt()); setHintTextColor(soft)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        val pass = EditText(this).apply {
            hint = "Contraseña"; setTextColor(0xFFFFFFFF.toInt()); setHintTextColor(soft)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        st = tv(if (s.authKey != null) "Buscando tus series y películas..." else "Iniciá sesión en Stremio.", 14f, soft)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        login = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; addView(email); addView(pass)
            visibility = if (s.authKey != null) View.GONE else View.VISIBLE
        }
        go = Button(this).apply { text = if (s.authKey != null) "Sincronizar ahora" else "Entrar y sincronizar" }
        val row = Button(this).apply { text = "Activar fila en el inicio" }
        go.setOnClickListener { sync(email.text.toString().trim(), pass.text.toString()) }
        row.setOnClickListener { Sync.askRow(this) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(48), dp(32), dp(48), dp(32))
            addView(tv("Seguir viendo para Javi", 30f, sky, true))
            addView(st); addView(list); addView(login); addView(go); addView(row)
        }
        setContentView(ScrollView(this).apply { setBackgroundColor(night); isFillViewport = true; addView(root) })
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("sync", ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).build())
    }

    override fun onResume() {
        super.onResume()
        if (Store(this).authKey != null) sync("", "")
    }

    private fun sync(e: String, p: String) = thread {
        val s = Store(this)
        try {
            if (e.isNotBlank()) s.authKey = Stremio.login(e, p)
            if (s.authKey == null) throw Exception("Falta iniciar sesión")
            val msg = Sync.run(this)
            runOnUiThread {
                st.text = msg; login.visibility = View.GONE; go.text = "Sincronizar ahora"
                render(); Sync.askRowOnce(this)
            }
        } catch (x: Exception) { runOnUiThread { st.text = "Error: ${x.message}" } }
    }

    private fun render() {
        list.removeAllViews()
        for (e in Sync.entries) list.addView(card(e))
        list.addView(tv(if (Sync.entries.isEmpty()) "No hay nada a medias en Stremio por ahora."
            else "OK abre en Stremio. Mantené OK apretado para quitar de la lista.", 14f, soft))
        list.getChildAt(0)?.requestFocus()
    }

    private fun card(e: Entry): View {
        val x = e.item
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; isFocusable = true; isClickable = true
            setPadding(dp(14), dp(14), dp(14), dp(14)); background = bg(false)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) }
            setOnFocusChangeListener { v, f -> v.background = bg(f) }
            setOnClickListener { Sync.open(this@MainActivity, e) }
            setOnLongClickListener { Store(this@MainActivity).hide(x.id); sync("", ""); true }
        }
        val img = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(80), dp(120))
            scaleType = ImageView.ScaleType.CENTER_CROP; setBackgroundColor(night)
        }
        x.poster?.let { poster(it, img) }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, 0, 0); layoutParams = LinearLayout.LayoutParams(0, dp(120), 1f)
        }
        col.addView(tv(e.label, 20f, 0xFFFFFFFF.toInt(), true))
        x.videoId?.split(":")?.takeIf { it.size >= 3 }?.let {
            col.addView(tv("Temporada ${it[1]} · Episodio ${it[2]}", 15f, 0xFFB8C4CE.toInt()))
        }
        if (x.duration > 0) {
            col.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 1000; progress = (x.offset * 1000 / x.duration).toInt().coerceIn(0, 1000)
                progressTintList = ColorStateList.valueOf(sky)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(6)).apply { topMargin = dp(8) }
            })
            col.addView(tv("${x.offset / 60000} de ${x.duration / 60000} min", 13f, soft))
        }
        row.addView(img); row.addView(col)
        return row
    }

    private fun poster(u: String, img: ImageView) {
        cache[u]?.let { img.setImageBitmap(it); return }
        thread {
            try {
                val bmp = URL(u).openStream().use { BitmapFactory.decodeStream(it) }
                if (bmp != null) runOnUiThread { cache[u] = bmp; img.setImageBitmap(bmp) }
            } catch (e: Exception) { }
        }
    }
}
