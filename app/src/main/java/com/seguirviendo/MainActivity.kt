package com.seguirviendo
import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.InputType
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.work.*
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val COLS = 9
    private val sky = 0xFF19E3FF.toInt()
    private val night = 0xFF0B1B4D.toInt()
    private val cream = 0xFFEAF6FF.toInt()
    private val yellow = 0xFFFFD23F.toInt()
    private val orange = 0xFFFF7A1A.toInt()
    private val magenta = 0xFFFF2E93.toInt()
    private val soft = 0xFF8FA3B3.toInt()
    private val MP = ViewGroup.LayoutParams.MATCH_PARENT
    private val WC = ViewGroup.LayoutParams.WRAP_CONTENT

    private lateinit var shelfFrame: LinearLayout
    private lateinit var shelf: LinearLayout
    private lateinit var login: LinearLayout
    private lateinit var go: Button
    private lateinit var info1: TextView
    private lateinit var info2: TextView
    private lateinit var info3: TextView
    private lateinit var bar: ProgressBar
    private var statusMsg = ""
    private val cache = HashMap<String, Bitmap>()

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun tv(t: CharSequence, sp: Float, color: Int, bold: Boolean = false, italic: Boolean = false) = TextView(this).apply {
        text = t; setTextSize(TypedValue.COMPLEX_UNIT_SP, sp); setTextColor(color)
        setTypeface(Typeface.defaultFromStyle(if (bold && italic) Typeface.BOLD_ITALIC else if (bold) Typeface.BOLD else if (italic) Typeface.ITALIC else Typeface.NORMAL))
    }
    private fun neon(b: TextView, color: Int) {
        fun bg(f: Boolean) = GradientDrawable().apply {
            cornerRadius = dp(30).toFloat(); setColor(if (f) 0xFF1B3FA0.toInt() else night); setStroke(dp(if (f) 3 else 2), color)
        }
        b.background = bg(false); b.isFocusable = true; b.isClickable = true; b.isAllCaps = false
        b.setTextColor(cream); b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); b.setPadding(dp(18), dp(6), dp(18), dp(6))
        b.setTypeface(Typeface.defaultFromStyle(Typeface.ITALIC))
        b.setOnFocusChangeListener { v, f -> v.background = bg(f) }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val s = Store(this)
        val title = SpannableString("Seguir viendo (Javi Videoclub)").apply {
            val i = indexOf("(Javi")
            setSpan(ForegroundColorSpan(yellow), i, length, 0); setSpan(StyleSpan(Typeface.BOLD_ITALIC), i, length, 0)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(tv(title, 22f, cream, false, true), LinearLayout.LayoutParams(0, WC, 1f))
        }
        val btnSearch = Button(this).apply { text = "Buscar películas" }; neon(btnSearch, magenta)
        val btnMore = Button(this).apply { text = "Más…" }; neon(btnMore, sky)
        btnSearch.setOnClickListener { Toast.makeText(this, "El buscador de películas llega en la próxima versión.", Toast.LENGTH_LONG).show() }
        btnMore.setOnClickListener {
            AlertDialog.Builder(this).setItems(arrayOf("Sincronizar ahora", "Activar fila en el inicio")) { _, w ->
                if (w == 0) sync("", "") else Sync.askRow(this)
            }.show()
        }
        header.addView(btnSearch, LinearLayout.LayoutParams(WC, WC).apply { rightMargin = dp(10) })
        header.addView(btnMore)

        shelf = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false; setPadding(dp(10), 0, dp(10), dp(6)) }
        val scroll = ScrollView(this).apply { setBackgroundColor(0xFFE3D2AC.toInt()); clipChildren = false; clipToPadding = false; addView(shelf) }
        shelfFrame = LinearLayout(this).apply {
            setBackgroundColor(0xFFCDB88C.toInt()); setPadding(dp(6), dp(6), dp(6), dp(6)); addView(scroll, LinearLayout.LayoutParams(MP, MP))
        }

        val email = EditText(this).apply { hint = "Email de Stremio"; setTextColor(cream); setHintTextColor(soft); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS }
        val pass = EditText(this).apply { hint = "Contraseña"; setTextColor(cream); setHintTextColor(soft); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        go = Button(this).apply { text = "Entrar y sincronizar" }
        go.setOnClickListener { sync(email.text.toString().trim(), pass.text.toString()) }
        login = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(30), dp(20), dp(30), dp(20))
            addView(tv("Iniciá sesión en Stremio para ver tus series y películas a medias.", 16f, cream))
            addView(email); addView(pass); addView(go)
        }

        info1 = tv("", 20f, cream, false, true); info2 = tv("", 15f, sky, false, true); info3 = tv("", 15f, yellow, true, true)
        bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000; progressTintList = ColorStateList.valueOf(orange); progressBackgroundTintList = ColorStateList.valueOf(0xFF112A70.toInt())
        }
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(info1); addView(info2) }
        val right = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.END
            addView(info3); addView(bar, LinearLayout.LayoutParams(dp(170), dp(8)).apply { topMargin = dp(4) })
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8))
            background = GradientDrawable().apply { setColor(night); cornerRadius = dp(8).toFloat(); setStroke(dp(2), sky) }
            addView(left, LinearLayout.LayoutParams(0, WC, 1f)); addView(right)
        }
        val wood = LinearLayout(this).apply { setBackgroundColor(0xFF8B5A2B.toInt()); setPadding(dp(6), dp(6), dp(6), dp(6)); addView(panel, LinearLayout.LayoutParams(MP, WC)) }

        val content = FrameLayout(this).apply {
            addView(shelfFrame, FrameLayout.LayoutParams(MP, MP)); addView(login, FrameLayout.LayoutParams(MP, MP))
        }
        val hasKey = s.authKey != null
        login.visibility = if (hasKey) View.GONE else View.VISIBLE
        shelfFrame.visibility = if (hasKey) View.VISIBLE else View.GONE
        info1.text = if (hasKey) "Buscando tus series y películas…" else ""

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(night); setPadding(dp(24), dp(14), dp(24), dp(14))
            addView(header, LinearLayout.LayoutParams(MP, WC).apply { bottomMargin = dp(8) })
            addView(content, LinearLayout.LayoutParams(MP, 0, 1f))
            addView(wood, LinearLayout.LayoutParams(MP, WC).apply { topMargin = dp(8) })
        })
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
            statusMsg = Sync.run(this)
            runOnUiThread { login.visibility = View.GONE; shelfFrame.visibility = View.VISIBLE; render(); Sync.askRowOnce(this) }
        } catch (x: Exception) { runOnUiThread { statusMsg = "Error: ${x.message}"; if (Sync.entries.isEmpty()) showEmpty() } }
    }

    private fun showEmpty() {
        info1.text = if (statusMsg.startsWith("Error")) statusMsg else "No hay nada a medias en Stremio por ahora."
        info2.text = ""; info3.text = ""; bar.visibility = View.INVISIBLE
    }

    private fun ledge() = View(this).apply {
        background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFFE6CF9A.toInt(), 0xFFC9A96E.toInt(), 0xFF8A6D3B.toInt()))
        layoutParams = LinearLayout.LayoutParams(MP, dp(9))
    }

    private fun render() {
        shelf.removeAllViews()
        val es = Sync.entries
        val rows = maxOf(3, (es.size + COLS - 1) / COLS)
        val avail = resources.displayMetrics.widthPixels - dp(48 + 12 + 20 + 8)
        val gap = dp(8)
        val w = minOf(dp(100), (avail - gap * (COLS - 1)) / COLS)
        val h = (w * 1.45f).toInt()
        var first: View? = null
        for (r in 0 until rows) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false
                gravity = Gravity.BOTTOM; setPadding(0, dp(12), 0, 0)
            }
            for (c in 0 until COLS) {
                val i = r * COLS + c
                val lp = LinearLayout.LayoutParams(w, h).apply { rightMargin = if (c < COLS - 1) gap else 0 }
                if (i < es.size) { val v = cover(es[i]); row.addView(v, lp); if (first == null) first = v }
                else row.addView(View(this), lp)   // lugar vacío: no se dibuja ninguna caja
            }
            shelf.addView(row, LinearLayout.LayoutParams(MP, WC)); shelf.addView(ledge())
        }
        if (es.isEmpty()) showEmpty() else first?.requestFocus()
    }

    private fun coverBg(f: Boolean) = GradientDrawable().apply {
        cornerRadius = dp(3).toFloat(); setColor(0xFF111111.toInt()); setStroke(dp(if (f) 3 else 1), if (f) sky else 0xFF1A1A1A.toInt())
    }

    private fun cover(e: Entry): View {
        val x = e.item
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; isFocusable = true; isClickable = true
            setPadding(dp(2), dp(2), dp(2), dp(2)); background = coverBg(false)
        }
        val area = FrameLayout(this).apply { setBackgroundColor(0xFF2A2F36.toInt()) }
        val img = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        area.addView(img, FrameLayout.LayoutParams(MP, MP))
        if (x.poster != null) poster(x.poster, img)
        else area.addView(tv(x.name, 11f, cream).apply { gravity = Gravity.CENTER }, FrameLayout.LayoutParams(MP, MP))
        box.addView(area, LinearLayout.LayoutParams(MP, 0, 1f))
        box.addView(View(this).apply { setBackgroundColor(0xFFEEEEEE.toInt()) }, LinearLayout.LayoutParams(MP, dp(7)).apply { topMargin = dp(2) })
        box.setOnFocusChangeListener { v, f ->
            v.background = coverBg(f)
            if (f) { v.bringToFront(); v.animate().scaleX(1.16f).scaleY(1.16f).setDuration(120).start(); showInfo(e) }
            else v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
        }
        box.setOnClickListener { Sync.open(this, e) }
        box.setOnLongClickListener { Store(this).hide(x.id); sync("", ""); true }
        return box
    }

    private fun showInfo(e: Entry) {
        val x = e.item
        val svc = e.label.removePrefix(x.name).trim()
        val sp = SpannableString(x.name + " " + svc)
        sp.setSpan(ForegroundColorSpan(yellow), x.name.length + 1, sp.length, 0)
        sp.setSpan(StyleSpan(Typeface.BOLD_ITALIC), x.name.length + 1, sp.length, 0)
        info1.text = sp
        val p = x.videoId?.split(":")?.takeIf { it.size >= 3 }
        info2.text = if (x.type == "movie") "Película" else if (p != null) "Temporada ${p[1]} · Episodio ${p[2]}" else ""
        if (x.duration > 0) {
            bar.visibility = View.VISIBLE; bar.progress = (x.offset * 1000 / x.duration).toInt().coerceIn(0, 1000)
            info3.text = "Minuto ${x.offset / 60000} de ${x.duration / 60000}"
        } else { bar.visibility = View.INVISIBLE; info3.text = "" }
    }

    private fun poster(u: String, img: ImageView) {
        cache[u]?.let { img.setImageBitmap(it); return }
        thread {
            try {
                val bmp = URL(u.replaceFirst("http://", "https://")).openStream().use { BitmapFactory.decodeStream(it) }
                if (bmp != null) runOnUiThread { cache[u] = bmp; img.setImageBitmap(bmp) }
            } catch (e: Exception) { }
        }
    }
}
