package com.seguirviendo
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.work.*
import org.json.JSONObject
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val COLS = 9
    private val MAX = 9 * 50   // 50 estantes de 9 cajas
    private val pool = java.util.concurrent.Executors.newFixedThreadPool(4)
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
    private lateinit var btnSearch: Button
    private lateinit var btnAdd: Button
    private lateinit var btnMore: Button
    private lateinit var info1: TextView
    private lateinit var info2: TextView
    private lateinit var info3: TextView
    private lateinit var bar: ProgressBar
    private var statusMsg = ""
    private var sig = ""
    private val covers = ArrayList<View>()
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
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; clipChildren = false
            addView(tv(title, 22f, cream, false, true), LinearLayout.LayoutParams(0, WC, 1f))
        }
        btnSearch = Button(this).apply { text = "Buscar en Stremio" }; neon(btnSearch, magenta)
        btnAdd = Button(this).apply { text = "Agregar manual" }; neon(btnAdd, sky)
        btnMore = Button(this).apply { text = "Más…" }; neon(btnMore, orange)
        btnSearch.setOnClickListener { Sync.openStremioSearch(this) }
        btnAdd.setOnClickListener { startAdd() }
        btnMore.setOnClickListener {
            AlertDialog.Builder(this).setItems(arrayOf("Sincronizar ahora", "Activar fila en el inicio", "Clave de TMDB")) { _, w ->
                when (w) { 0 -> sync("", ""); 1 -> Sync.askRow(this); else -> askKey { } }
            }.show()
        }
        header.addView(btnSearch, LinearLayout.LayoutParams(WC, WC).apply { rightMargin = dp(10) })
        header.addView(btnAdd, LinearLayout.LayoutParams(WC, WC).apply { rightMargin = dp(10) })
        header.addView(btnMore)

        shelf = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false; setPadding(dp(18), 0, dp(18), dp(10)) }
        val scroll = ScrollView(this).apply { setBackgroundColor(0xFFE3D2AC.toInt()); clipChildren = false; clipToPadding = false; addView(shelf) }
        shelfFrame = LinearLayout(this).apply {
            clipChildren = false; clipToPadding = false
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
            clipChildren = false; clipToPadding = false
            addView(shelfFrame, FrameLayout.LayoutParams(MP, MP)); addView(login, FrameLayout.LayoutParams(MP, MP))
        }
        val hasKey = s.authKey != null
        val showShelf = hasKey || s.manual().length() > 0
        login.visibility = if (showShelf) View.GONE else View.VISIBLE
        shelfFrame.visibility = if (showShelf) View.VISIBLE else View.GONE
        info1.text = if (hasKey) "Buscando tus series y películas…" else ""

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(night); setPadding(dp(24), dp(14), dp(24), dp(14))
            clipChildren = false; clipToPadding = false
            addView(header, LinearLayout.LayoutParams(MP, WC).apply { bottomMargin = dp(8) })
            addView(content, LinearLayout.LayoutParams(MP, 0, 1f))
            addView(wood, LinearLayout.LayoutParams(MP, WC).apply { topMargin = dp(8) })
        })
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("sync", ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).build())
    }

    override fun onResume() {
        super.onResume()
        val s = Store(this)
        if (s.authKey != null) {
            if (Sync.entries.isNotEmpty()) render()   // muestra ya el orden nuevo (lo último abierto, primero)
            sync("", "")
        } else if (s.manual().length() > 0) refreshLocal()
    }

    // Navegación con el control: por posición exacta en la grilla, sin depender de la geometría de Android.
    override fun dispatchKeyEvent(ev: KeyEvent): Boolean {
        if (ev.action == KeyEvent.ACTION_DOWN && covers.isNotEmpty()) {
            if (ev.keyCode == KeyEvent.KEYCODE_DPAD_DOWN && (btnSearch.isFocused || btnAdd.isFocused || btnMore.isFocused)) { covers[0].requestFocus(); return true }
            val cur = covers.indexOfFirst { it.isFocused }
            if (cur >= 0) {
                val n = covers.size
                when (ev.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> { covers[maxOf(cur - 1, 0)].requestFocus(); return true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { covers[minOf(cur + 1, n - 1)].requestFocus(); return true }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        val t = cur + COLS
                        if (t < n) covers[t].requestFocus() else if (cur / COLS < (n - 1) / COLS) covers[n - 1].requestFocus()
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if (cur >= COLS) covers[cur - COLS].requestFocus() else btnSearch.requestFocus()
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(ev)
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

    // Cambios locales (agregar, borrar, marcar vista): reordena y actualiza sin red.
    private fun refreshLocal() {
        thread { Sync.rebuild(this); runOnUiThread { login.visibility = View.GONE; shelfFrame.visibility = View.VISIBLE; render() } }
    }

    private fun showEmpty() {
        info1.text = if (statusMsg.startsWith("Error")) statusMsg else "No hay nada a medias por ahora. Usá \"Buscar películas\" para agregar."
        info2.text = ""; info3.text = ""; bar.visibility = View.INVISIBLE
    }

    private fun ledge() = View(this).apply {
        background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFFE6CF9A.toInt(), 0xFFC9A96E.toInt(), 0xFF8A6D3B.toInt()))
        layoutParams = LinearLayout.LayoutParams(MP, dp(9))
    }

    private fun render() {
        val es = Sync.entries
        val nsig = es.joinToString("|") { it.item.id + it.label + (it.item.videoId ?: "") }
        if (nsig == sig && covers.isNotEmpty()) return   // nada cambió: no se rearma (evita el pestañeo)
        sig = nsig
        shelf.removeAllViews(); covers.clear()
        val rows = maxOf(3, (es.size + COLS - 1) / COLS)
        val avail = resources.displayMetrics.widthPixels - dp(48 + 12 + 36 + 8)
        val gap = dp(8)
        val w = minOf(dp(100), (avail - gap * (COLS - 1)) / COLS)
        val h = (w * 1.45f).toInt()
        for (r in 0 until rows) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false
                gravity = Gravity.BOTTOM or Gravity.START; setPadding(0, if (r == 0) dp(22) else dp(16), 0, 0)
            }
            for (c in 0 until COLS) {
                val i = r * COLS + c
                val lp = LinearLayout.LayoutParams(w, h).apply { rightMargin = if (c < COLS - 1) gap else 0 }
                if (i < es.size) { val v = cover(es[i], i, w, h); covers.add(v); row.addView(v, lp) }
                else row.addView(View(this), lp)   // lugar vacío: no se dibuja ninguna caja
            }
            shelf.addView(row, LinearLayout.LayoutParams(MP, WC)); shelf.addView(ledge())
        }
        if (es.isEmpty()) showEmpty() else covers.firstOrNull()?.requestFocus()
    }

    private fun coverBg(f: Boolean) = GradientDrawable().apply {
        cornerRadius = dp(3).toFloat(); setColor(0xFF111111.toInt()); setStroke(dp(if (f) 4 else 1), if (f) sky else 0xFF1A1A1A.toInt())
    }

    private fun select(i: Int) {
        covers.forEachIndexed { j, v -> v.animate().alpha(if (j == i) 1f else 0.8f).setDuration(120).start() }
    }

    private fun cover(e: Entry, idx: Int, w: Int, h: Int): View {
        val x = e.item
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; isFocusable = true; isClickable = true
            setPadding(dp(2), dp(2), dp(2), dp(2)); background = coverBg(false)
            pivotX = w / 2f; pivotY = h.toFloat()   // crece hacia arriba, apoyada en la repisa
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
            if (f) {
                v.animate().scaleX(1.35f).scaleY(1.35f).translationY(-dp(8).toFloat()).translationZ(dp(12).toFloat()).setDuration(120).start()
                select(idx); showInfo(e)
            } else v.animate().scaleX(1f).scaleY(1f).translationY(0f).translationZ(0f).setDuration(120).start()
        }
        box.setOnClickListener { Sync.open(this, e) }
        box.setOnLongClickListener { showMenu(e); true }
        return box
    }

    private fun showMenu(e: Entry) {
        val manual = e.pkg != null
        AlertDialog.Builder(this).setTitle(e.item.name).setItems(arrayOf("Borrar de la estantería", "Salir")) { _, w ->
            if (w == 0) {
                if (manual) { Store(this).removeManual(e.item.id); refreshLocal() }
                else { Store(this).hide(e.item.id); sync("", "") }
            }   // "Salir": no hace nada
        }.show()
    }

    // ---- Agregar a mano (búsqueda en TMDB por título original / inglés) ----
    private fun askKey(then: () -> Unit) {
        val input = EditText(this).apply { hint = "Clave de TMDB"; inputType = InputType.TYPE_CLASS_TEXT; setSingleLine() }
        AlertDialog.Builder(this).setTitle("Clave gratuita de TMDB (themoviedb.org → Ajustes → API)").setView(input)
            .setPositiveButton("Guardar") { _, _ -> Store(this).tmdbKey = input.text.toString().trim(); then() }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun lleno(): Boolean {
        if (Sync.entries.size < MAX) return false
        AlertDialog.Builder(this).setTitle("La estantería está llena")
            .setMessage("No puedo agregar más: ya hay $MAX cajas (50 estantes). Borrá alguna (mantené OK sobre una caja) y probá de nuevo.")
            .setPositiveButton("Entendido", null).show()
        return true
    }

    private fun startAdd() {
        if (lleno()) return
        val key = Store(this).tmdbKey
        if (key.isNullOrBlank()) { askKey { startAdd() }; return }
        val input = EditText(this).apply { hint = "Título original o en inglés (ej. Carrie)"; inputType = InputType.TYPE_CLASS_TEXT; setSingleLine() }
        AlertDialog.Builder(this).setTitle("Agregar manual (datos: TMDB)").setView(input)
            .setPositiveButton("Buscar") { _, _ -> doSearch(key, input.text.toString().trim()) }
            .setNegativeButton("Salir", null).show()
    }

    private fun doSearch(key: String, q: String) {
        if (q.isBlank()) return
        thread {
            try { val hits = Tmdb.search(key, q); runOnUiThread { showHits(hits) } }
            catch (e: Exception) { runOnUiThread { Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show() } }
        }
    }

    private fun showHits(h: List<Tmdb.Hit>) {
        if (h.isEmpty()) { Toast.makeText(this, "Sin resultados", Toast.LENGTH_LONG).show(); return }
        val names = h.map {
            it.title + (if (it.english.isNotBlank() && it.english != it.title) " / ${it.english}" else "") +
                (if (it.year.isNotBlank()) " (${it.year})" else "") + " · " + (if (it.tv) "Serie" else "Película")
        }.toTypedArray()
        AlertDialog.Builder(this).setTitle("¿Cuál es?").setItems(names) { _, i -> pickApp(h[i]) }.setNegativeButton("Salir", null).show()
    }

    private fun installedApps(): List<Pair<String, String>> {
        val pm = packageManager
        val q = Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.LEANBACK_LAUNCHER")
        return pm.queryIntentActivities(q, 0).map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != packageName }.distinctBy { it.first }.sortedBy { it.second.lowercase() }
    }

    // Opciones reducidas: Netflix, Flow, Disney+ y "Otra…"
    private fun pickApp(hit: Tmdb.Hit) {
        val installed = installedApps()
        val names = ArrayList<String>(); val apps = ArrayList<Pair<String, String>?>()
        for ((nm, kw) in listOf("Netflix" to "netflix", "Flow" to "flow", "Disney+" to "disney")) {
            val a = installed.firstOrNull { it.second.lowercase().contains(kw) }
            if (a != null) { names.add(nm); apps.add(a.first to nm) }
        }
        names.add("Otra…"); apps.add(null)
        AlertDialog.Builder(this).setTitle("¿Dónde la vas a ver?").setItems(names.toTypedArray()) { _, i ->
            val a = apps[i]
            if (a != null) addManual(hit, a.first, a.second) else pickOther(hit, installed)
        }.setNegativeButton("Salir", null).show()
    }

    private fun pickOther(hit: Tmdb.Hit, apps: List<Pair<String, String>>) {
        if (apps.isEmpty()) { Toast.makeText(this, "No encontré apps instaladas para elegir", Toast.LENGTH_LONG).show(); return }
        AlertDialog.Builder(this).setTitle("¿En qué app?").setItems(apps.map { it.second }.toTypedArray()) { _, i ->
            addManual(hit, apps[i].first, apps[i].second)
        }.setNegativeButton("Salir", null).show()
    }

    private fun addManual(hit: Tmdb.Hit, pkg: String, label: String) {
        if (lleno()) return
        Store(this).addManual(JSONObject().put("id", (if (hit.tv) "mt" else "mm") + hit.id).put("name", hit.title)
            .put("type", if (hit.tv) "series" else "movie").put("poster", hit.poster ?: "")
            .put("pkg", pkg).put("label", label).put("last", System.currentTimeMillis()))
        refreshLocal()
    }

    private fun showInfo(e: Entry) {
        val x = e.item
        val svc = e.label.removePrefix(x.name).trim()
        val sp = SpannableString(x.name + " " + svc)
        sp.setSpan(ForegroundColorSpan(yellow), x.name.length + 1, sp.length, 0)
        sp.setSpan(StyleSpan(Typeface.BOLD_ITALIC), x.name.length + 1, sp.length, 0)
        info1.text = sp
        val p = x.videoId?.split(":")?.takeIf { it.size >= 3 }
        info2.text = if (x.type == "movie") "Película" else if (p != null) "Temporada ${p[1]} · Episodio ${p[2]}" else "Serie"
        if (x.duration > 0) {
            bar.visibility = View.VISIBLE; bar.progress = (x.offset * 1000 / x.duration).toInt().coerceIn(0, 1000)
            info3.text = "Minuto ${x.offset / 60000} de ${x.duration / 60000}"
        } else { bar.visibility = View.INVISIBLE; info3.text = "" }
    }

    private fun poster(u: String, img: ImageView) {
        cache[u]?.let { img.setImageBitmap(it); return }
        pool.execute {
            try {
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }   // a mitad de tamaño: con 450 cajas cuida la memoria
                val bmp = URL(u.replaceFirst("http://", "https://")).openStream().use { BitmapFactory.decodeStream(it, null, opts) }
                if (bmp != null) runOnUiThread { cache[u] = bmp; img.setImageBitmap(bmp) }
            } catch (e: Exception) { }
        }
    }
}
