package com.seguirviendo
import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.view.inputmethod.EditorInfo
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
    private lateinit var scroll: ScrollView
    private val ledges = ArrayList<View>()
    private var selIdx = -1
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
        val ver = try { packageManager.getPackageInfo(packageName, 0).versionName ?: "" } catch (e: Exception) { "" }
        val title = SpannableString("Seguir viendo (Javi Videoclub) $ver").apply {
            val i = indexOf("(Javi"); val j = indexOf(")") + 1
            setSpan(ForegroundColorSpan(yellow), i, j, 0); setSpan(StyleSpan(Typeface.BOLD_ITALIC), i, j, 0)
            if (ver.isNotEmpty()) setSpan(ForegroundColorSpan(sky), j + 1, length, 0)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; clipChildren = false
            addView(ImageView(this@MainActivity).apply { setImageResource(R.drawable.banner); scaleType = ImageView.ScaleType.FIT_CENTER },
                LinearLayout.LayoutParams(dp(82), dp(46)).apply { rightMargin = dp(12) })   // banner antes del título
            addView(tv(title, 22f, cream, false, true), LinearLayout.LayoutParams(0, WC, 1f))
        }
        btnSearch = Button(this).apply { text = "Buscar en Stremio" }; neon(btnSearch, magenta)
        btnAdd = Button(this).apply { text = "Agregar manual" }; neon(btnAdd, sky)
        btnMore = Button(this).apply { text = "Más…" }; neon(btnMore, orange)
        btnSearch.setOnClickListener { Sync.openStremioSearch(this) }
        btnAdd.setOnClickListener { startAdd() }
        btnMore.setOnClickListener {
            val tr = if (Trakt.connected(Store(this))) "Trakt: conectado (volver a conectar)" else "Conectar Trakt"
            AlertDialog.Builder(this).setItems(arrayOf("Sincronizar ahora", "Activar fila en el inicio", "Clave de TMDB", tr, "Verificar backup", "Restaurar backup")) { _, w ->
                when (w) { 0 -> sync("", ""); 1 -> Sync.askRow(this); 2 -> askKey { }; 3 -> connectTrakt(); 4 -> verifyBackup(); else -> restoreBackup() }
            }.show()
        }
        header.addView(btnSearch, LinearLayout.LayoutParams(WC, WC).apply { rightMargin = dp(10) })
        header.addView(btnAdd, LinearLayout.LayoutParams(WC, WC).apply { rightMargin = dp(10) })
        header.addView(btnMore)

        shelf = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false; setPadding(dp(18), 0, dp(18), dp(10)) }
        scroll = ScrollView(this).apply { setBackgroundColor(0xFFE3D2AC.toInt()); clipChildren = false; clipToPadding = false; addView(shelf) }
        scroll.setOnScrollChangeListener { _, _, _, _, _ -> updateClip() }
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
        backfillInfo()
        refreshNext(s)
        offerRestoreOnStart(s)
        if (s.authKey != null) {
            if (Sync.entries.isNotEmpty()) render()   // muestra ya el orden nuevo (lo último abierto, primero)
            else loadLocal()                           // arranque en frío: muestra al instante lo guardado
            sync("", "")
        } else if (s.manual().length() > 0) refreshLocal()
        thread { try { Backup.save(applicationContext) } catch (e: Exception) { } }   // backup al abrir
    }

    // Con la lista guardada: sin red y sin tocar la fila del inicio.
    private fun loadLocal() {
        thread { Sync.load(this); runOnUiThread { if (Sync.entries.isNotEmpty()) { login.visibility = View.GONE; shelfFrame.visibility = View.VISIBLE; render() } } }
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
            statusMsg = Sync.run(this) { runOnUiThread { login.visibility = View.GONE; shelfFrame.visibility = View.VISIBLE; render() } }   // muestra en cuanto llega lo de Stremio
            runOnUiThread { login.visibility = View.GONE; shelfFrame.visibility = View.VISIBLE; render(); Sync.askRowOnce(this) }
            refreshStremioCounts()
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
        val nsig = es.joinToString("|") { it.item.id + it.label + (it.item.videoId ?: "") + it.left }
        if (nsig == sig && covers.isNotEmpty()) return   // nada cambió: no se rearma (evita el pestañeo)
        sig = nsig
        shelf.removeAllViews(); covers.clear(); ledges.clear(); selIdx = -1
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
            shelf.addView(row, LinearLayout.LayoutParams(MP, WC)); val lg = ledge(); ledges.add(lg); shelf.addView(lg)
        }
        if (es.isEmpty()) showEmpty() else covers.firstOrNull()?.requestFocus()
        shelf.post { updateClip() }
    }

    private fun coverBg(f: Boolean) = GradientDrawable().apply {
        cornerRadius = dp(3).toFloat(); setColor(0xFF111111.toInt()); setStroke(dp(if (f) 4 else 1), if (f) sky else 0xFF1A1A1A.toInt())
    }

    private fun select(i: Int) { selIdx = i; updateClip() }

    // Las filas de arriba y de abajo se cortan a la mitad en el borde de la madera. Solo con el scroll al principio
    // (primera fila) se deja sobresalir por arriba la caja elegida.
    private fun updateClip() {
        val w = scroll.width; val h = scroll.height
        if (w == 0 || h == 0) return
        scroll.clipBounds = if (scroll.scrollY <= 0) android.graphics.Rect(0, -dp(80), w, h) else android.graphics.Rect(0, 0, w, h)
        covers.forEachIndexed { j, v -> v.alpha = if (j == selIdx || selIdx < 0) 1f else 0.8f }
    }

    // Mantiene a la vista media fila de arriba y de abajo de la caja elegida (da sensación de scroll).
    private fun ensureVisible(v: View) {
        val vh = scroll.height
        if (vh == 0) return
        val y = (v.parent as View).top + v.top
        val up = dp(25) + v.height / 2
        val down = v.height + dp(25) + v.height / 2
        val cur = scroll.scrollY
        var target = cur
        if (y - cur < up) target = maxOf(0, y - up)
        else if (y + down - cur > vh) target = y + down - vh
        if (target != cur) scroll.smoothScrollTo(0, target)
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
        Sync.badgeText(e)?.let { bt ->   // sticker de capítulos que faltan, abajo a la derecha
            area.addView(LcdView(this, bt, h * 0.138f / 34f), FrameLayout.LayoutParams(WC, WC, Gravity.BOTTOM or Gravity.END).apply { rightMargin = dp(2); bottomMargin = dp(2) })
        }
        box.addView(area, LinearLayout.LayoutParams(MP, 0, 1f))
        box.addView(View(this).apply { setBackgroundColor(0xFFEEEEEE.toInt()) }, LinearLayout.LayoutParams(MP, dp(7)).apply { topMargin = dp(2) })
        box.setOnFocusChangeListener { v, f ->
            v.background = coverBg(f)
            if (f) {
                v.animate().scaleX(1.35f).scaleY(1.35f).translationY(-dp(8).toFloat()).translationZ(dp(12).toFloat()).setDuration(120).start()
                select(idx); showInfo(e); ensureVisible(v)
            } else v.animate().scaleX(1f).scaleY(1f).translationY(0f).translationZ(0f).setDuration(120).start()
        }
        box.setOnClickListener { Sync.open(this, e) }
        box.setOnLongClickListener { showMenu(e); true }
        return box
    }

    private fun showMenu(e: Entry) {
        val manual = e.pkg != null
        val opts = ArrayList<String>(); val acts = ArrayList<() -> Unit>()
        if (manual) {
            if (e.item.type == "movie") { opts.add("Marcar como visto en Trakt"); acts.add { traktMovie(e) } }
            else { opts.add("Capítulos pendientes en Trakt"); acts.add { traktSeries(e) } }
        }
        opts.add("Borrar de la estantería"); acts.add { removeEntry(e) }
        opts.add("Salir"); acts.add { }
        AlertDialog.Builder(this).setTitle(e.item.name).setItems(opts.toTypedArray()) { _, w -> acts[w]() }.show()
    }

    private fun removeEntry(e: Entry) {
        if (e.pkg != null) { Store(this).removeManual(e.item.id); refreshLocal() }
        else { Store(this).hide(e.item.id); sync("", "") }
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()

    // ---- Trakt ----
    private fun needTrakt(): Boolean {
        if (Trakt.connected(Store(this))) return false
        AlertDialog.Builder(this).setTitle("Trakt no está conectado").setMessage("Conectalo una sola vez y queda listo.")
            .setPositiveButton("Conectar ahora") { _, _ -> connectTrakt() }.setNegativeButton("Salir", null).show()
        return true
    }

    private fun connectTrakt() {
        val s = Store(this)
        val id = EditText(this).apply { hint = if (s.trClient.isNullOrBlank()) "Client ID de Trakt" else "Client ID (vacío = el mismo)"; setSingleLine() }
        val sec = EditText(this).apply { hint = if (s.trSecret.isNullOrBlank()) "Client Secret (si tu app no tiene, dejalo vacío)" else "Client Secret (vacío = el mismo)"; setSingleLine() }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0); addView(id); addView(sec) }
        AlertDialog.Builder(this).setTitle("Conectar Trakt").setView(box)
            .setPositiveButton("Conectar") { _, _ ->
                val a = id.text.toString().trim(); val b = sec.text.toString().trim()
                if (a.isNotEmpty() && a != s.trClient) { s.trClient = a; s.trAccess = null; s.trRefresh = null }
                if (b.isNotEmpty() && b != s.trSecret) { s.trSecret = b; s.trAccess = null; s.trRefresh = null }
                if (Trakt.hasKeys(s)) startDevice() else toast("Falta el Client ID de Trakt")
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun startDevice() {
        val s = Store(this)
        val cancel = java.util.concurrent.atomic.AtomicBoolean(false)
        val holder = arrayOfNulls<AlertDialog>(1)
        thread {
            try {
                val d = Trakt.deviceCode(s)
                runOnUiThread {
                    holder[0] = AlertDialog.Builder(this).setTitle("Conectar Trakt")
                        .setMessage("Desde el celular o la compu entrá a\n${d.url}\ny escribí este código:\n\n${d.userCode}\n\nCuando lo aprobés, esto se cierra solo.")
                        .setNegativeButton("Cancelar") { _, _ -> cancel.set(true) }
                        .setOnCancelListener { cancel.set(true) }.show()
                }
                val err = Trakt.waitForApproval(s, d) { cancel.get() }
                runOnUiThread {
                    holder[0]?.dismiss()
                    if (err == null) toast("Trakt conectado") else if (err != "Cancelado") toast("No se conectó: $err")
                }
            } catch (x: Exception) { runOnUiThread { holder[0]?.dismiss(); toast("Error: ${x.message}") } }
        }
    }

    private fun notFound(e: Entry) {
        AlertDialog.Builder(this).setTitle("No se encontró en Trakt")
            .setMessage("\"${e.item.name}\" no figura en Trakt (puede ser muy nueva o poco común). Queda en la estantería; si querés, cargala vos desde trakt.tv.")
            .setPositiveButton("Dejarla", null).setNegativeButton("Quitar igual de la estantería") { _, _ -> removeEntry(e) }.show()
    }

    private fun traktMovie(e: Entry) {
        if (needTrakt()) return
        val id = e.item.id.drop(2).toIntOrNull() ?: return
        toast("Registrando en Trakt…")
        thread {
            try {
                val ok = Trakt.markMovie(Store(this), id)
                runOnUiThread { if (ok) { toast("Registrada como vista en Trakt"); removeEntry(e) } else notFound(e) }
            } catch (x: Exception) { runOnUiThread { toast("Error: ${x.message}") } }
        }
    }

    private fun traktSeries(e: Entry) {
        if (needTrakt()) return
        val id = e.item.id.drop(2).toIntOrNull() ?: return
        toast("Consultando Trakt…")
        thread {
            try {
                val s = Store(this)
                val tid = Trakt.showId(s, id)
                if (tid == null) { runOnUiThread { notFound(e) } }
                else { val pend = Trakt.pending(s, tid); runOnUiThread { showPending(e, tid, pend) } }
            } catch (x: Exception) { runOnUiThread { toast("Error: ${x.message}") } }
        }
    }

    private fun showPending(e: Entry, tid: Int, pend: List<Trakt.Pending>) {
        if (pend.isEmpty()) {
            AlertDialog.Builder(this).setTitle(e.item.name).setMessage("No te falta ningún capítulo emitido: en Trakt ya figura todo como visto.")
                .setPositiveButton("Quitar de la estantería") { _, _ -> removeEntry(e) }.setNegativeButton("Dejarla", null).show()
            return
        }
        val shown = pend.take(150)
        val names = shown.map { "T${it.season} E${it.number}" + (if (it.title.isNotBlank()) " · ${it.title}" else "") }.toTypedArray()
        val checked = BooleanArray(shown.size)
        val dlg = AlertDialog.Builder(this).setTitle("Sin ver en Trakt: ${pend.size}")
            .setMultiChoiceItems(names, checked) { _, i, on -> checked[i] = on }
            .setPositiveButton("Marcar elegidos") { _, _ ->
                val sel = shown.filterIndexed { i, _ -> checked[i] }
                if (sel.isNotEmpty()) markEps(e, tid, sel, pend.size)
            }
            .setNeutralButton("Marcar todos (${pend.size})") { _, _ -> markEps(e, tid, pend, pend.size) }
            .setNegativeButton("Salir", null).create()
        // izquierda / derecha en la lista: salta directo a los botones de abajo
        dlg.setOnShowListener {
            dlg.listView.setOnKeyListener { _, code, ev ->
                if (ev.action == KeyEvent.ACTION_DOWN && (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT)) {
                    dlg.getButton(DialogInterface.BUTTON_POSITIVE).requestFocus(); true
                } else false
            }
        }
        dlg.show()
    }

    // Cuántos capítulos faltan de las series de Stremio (consulta a Trakt por el código de IMDb; se recuerda 6 horas).
    private fun refreshStremioCounts() {
        val s = Store(this)
        if (!Trakt.connected(s)) return
        thread {
            var changed = false; var n = 0
            for (x in Sync.lastItems) {
                if (x.type != "series" || !x.id.startsWith("tt")) continue
                if (s.leftAge(x.id) < 6 * 3600 * 1000L) continue
                if (n++ >= 25) break
                try {
                    val pr = Trakt.progress(s, x.id)
                    if (s.leftFor(x.id) != pr.left) changed = true
                    s.setLeft(x.id, pr.left)
                } catch (e: Trakt.TraktHttp) { if (e.code == 404) s.setLeft(x.id, -1) }   // no está en Trakt: pantalla vacía, sin reintentar enseguida
                catch (e: Exception) { }
            }
            if (changed) refreshLocal()
        }
    }

    // Capítulo siguiente de las series manuales (lo calcula Trakt). Sin Trakt o sin la serie en Trakt: queda solo "Serie".
    private fun refreshNext(s: Store) {
        if (!Trakt.connected(s)) return
        thread {
            var changed = false
            val a = s.manual()
            var n = 0
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                if (o.optString("type") == "movie") continue
                val id = o.getString("id")
                val tmdb = id.drop(2).toIntOrNull() ?: continue
                if (n++ >= 30) break
                try {
                    var tid = o.optInt("tid", 0)
                    if (tid <= 0) tid = Trakt.showId(s, tmdb) ?: continue
                    val pr = Trakt.progress(s, tid.toString())
                    if (s.setNext(id, tid, pr.next?.first ?: 0, pr.next?.second ?: 0, pr.left)) changed = true
                } catch (x: Exception) { }
            }
            if (changed) refreshLocal()
        }
    }

    private fun markEps(e: Entry, tid: Int, sel: List<Trakt.Pending>, total: Int) {
        toast("Registrando en Trakt…")
        thread {
            try {
                val n = Trakt.markEpisodes(Store(this), tid, sel)
                val quedan = total - sel.size
                try {
                    val pr = Trakt.progress(Store(this), tid.toString())
                    Store(this).setNext(e.item.id, tid, pr.next?.first ?: 0, pr.next?.second ?: 0, pr.left)
                } catch (x: Exception) { }
                runOnUiThread {
                    if (quedan <= 0 && n > 0) { toast("Registré $n capítulos. Ya no te falta ninguno: la saco de la estantería."); removeEntry(e) }
                    else { toast(if (n > 0) "Registré $n capítulos como vistos en Trakt" else "Trakt no registró ningún capítulo"); refreshLocal() }
                }
            } catch (x: Exception) { runOnUiThread { toast("Error: ${x.message}") } }
        }
    }

    // ---- Backup de lo agregado a mano ----
    private var asking = false

    private fun offerRestoreOnStart(s: Store) {
        if (asking || s.manual().length() > 0 || s.backupSig != null) return
        asking = true
        thread {
            val b = Backup.find(applicationContext)
            runOnUiThread {
                if (b == null || b.count == 0) { asking = false }
                else AlertDialog.Builder(this).setTitle("Encontré un backup")
                    .setMessage("Del ${Backup.fmt(b.ts)}, con ${b.count} títulos agregados a mano. ¿Los restauro?")
                    .setPositiveButton("Restaurar") { _, _ -> doRestore(b) }
                    .setNegativeButton("No, empezar de cero") { _, _ -> Store(this).backupSig = "x" }
                    .setOnDismissListener { asking = false }.show()
            }
        }
    }

    private fun doRestore(b: Backup.Info) {
        thread {
            val n = Backup.restore(applicationContext, b)
            runOnUiThread { toast(if (n > 0) "Restauré $n títulos" else "No faltaba nada: ya estaba todo"); refreshLocal() }
        }
    }

    private fun verifyBackup() {
        thread {
            val b = Backup.find(applicationContext)
            val cur = Store(this).manual().length()
            runOnUiThread {
                val msg = if (b == null) "No encontré ningún backup en Descargas (${Backup.NAME}). Se crea solo al abrir la app o al agregar algo a mano."
                else "Último backup: ${Backup.fmt(b.ts)}\nTítulos a mano guardados: ${b.count}\nTítulos a mano en la estantería: $cur\n\n" +
                    (if (b.count == cur) "Todo bien: el backup está al día." else "No coinciden: tocá \"Guardar ahora\".")
                AlertDialog.Builder(this).setTitle("Verificar backup").setMessage(msg)
                    .setPositiveButton("Guardar ahora") { _, _ ->
                        thread { val r = Backup.save(applicationContext, false, true); runOnUiThread { toast(r) } }
                    }.setNegativeButton("Cerrar", null).show()
            }
        }
    }

    private fun restoreBackup() {
        thread {
            val b = Backup.find(applicationContext)
            runOnUiThread {
                if (b == null) AlertDialog.Builder(this).setTitle("Restaurar backup")
                    .setMessage("No encontré un backup en Descargas. Si lo tenés en otro lado, elegí el archivo.")
                    .setPositiveButton("Elegir archivo…") { _, _ -> pickBackupFile() }.setNegativeButton("Salir", null).show()
                else confirmRestore(b)
            }
        }
    }

    private fun confirmRestore(b: Backup.Info) {
        AlertDialog.Builder(this).setTitle("Restaurar backup")
            .setMessage("Del ${Backup.fmt(b.ts)}, con ${b.count} títulos. Solo agrega lo que falte: no borra ni duplica nada.")
            .setPositiveButton("Restaurar") { _, _ -> doRestore(b) }.setNegativeButton("Salir", null).show()
    }

    private fun pickBackupFile() {
        try {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), 77)
        } catch (x: Exception) { toast("Este aparato no tiene un explorador de archivos") }
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        val u = data?.data
        if (req == 77 && res == RESULT_OK && u != null) {
            thread {
                val b = try { Backup.parse(contentResolver.openInputStream(u)?.bufferedReader()?.use { it.readText() }) } catch (x: Exception) { null }
                runOnUiThread { if (b == null) toast("Ese archivo no es un backup de Javi Videoclub") else confirmRestore(b) }
            }
        }
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
        val input = EditText(this).apply {
            hint = "Título original o en inglés (ej. Carrie)"; inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(); imeOptions = EditorInfo.IME_ACTION_SEARCH
        }
        val dlg = AlertDialog.Builder(this).setTitle("Agregar manual (datos: TMDB)").setView(input)
            .setPositiveButton("Buscar", null).setNegativeButton("Salir", null).create()
        val go = { dlg.dismiss(); doSearch(key, input.text.toString().trim()) }
        dlg.setOnShowListener { dlg.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener { go() } }
        input.setOnEditorActionListener { _, _, _ -> go(); true }          // Enter / "Buscar" del teclado
        input.setOnKeyListener { _, code, ev ->                             // flecha abajo: va siempre a "Buscar"
            if (code == KeyEvent.KEYCODE_DPAD_DOWN && ev.action == KeyEvent.ACTION_DOWN) { dlg.getButton(DialogInterface.BUTTON_POSITIVE).requestFocus(); true } else false
        }
        dlg.show()
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
            .filter { it.first != packageName }.distinctBy { it.first }
    }

    private fun packageFor(label: String): String {
        val kw = when (label) { "Netflix" -> "netflix"; "Flow" -> "flow"; "YouTube" -> "youtube"; "Mercado Play" -> "mercado"; else -> return "" }
        return installedApps().firstOrNull { it.second.lowercase().contains(kw) }?.first ?: ""
    }

    // Opciones fijas y en este orden; "Otro" no abre ninguna lista.
    private fun pickApp(hit: Tmdb.Hit) {
        val names = arrayOf("Netflix", "Flow", "YouTube", "Mercado Play", "Otro")
        AlertDialog.Builder(this).setTitle("¿Dónde la vas a ver?").setItems(names) { _, i ->
            addManual(hit, if (names[i] == "Otro") "" else packageFor(names[i]), names[i])
        }.setNegativeButton("Salir", null).show()
    }

    private fun addManual(hit: Tmdb.Hit, pkg: String, label: String) {
        if (lleno()) return
        val key = Store(this).tmdbKey
        thread {
            val inf = try { if (key.isNullOrBlank()) Tmdb.Info(0, hit.year) else Tmdb.info(key, hit.id, hit.tv) } catch (e: Exception) { Tmdb.Info(0, hit.year) }
            Store(this).addManual(JSONObject().put("id", (if (hit.tv) "mt" else "mm") + hit.id).put("name", hit.title)
                .put("type", if (hit.tv) "series" else "movie").put("poster", hit.poster ?: "")
                .put("pkg", pkg).put("label", label).put("rt", if (inf.runtime > 0) inf.runtime else -1)
                .put("yr", inf.year.ifBlank { hit.year }).put("last", System.currentTimeMillis()))
            val st = Store(this)
            if (hit.tv && Trakt.connected(st)) {   // serie nueva: el capítulo que dice Trakt; si no figura, queda solo "Serie"
                try {
                    val tid = Trakt.showId(st, hit.id)
                    if (tid != null) { val pr = Trakt.progress(st, tid.toString()); st.setNext("mt" + hit.id, tid, pr.next?.first ?: 0, pr.next?.second ?: 0, pr.left) }
                } catch (x: Exception) { }
            }
            refreshLocal()
        }
    }

    // Completa duración y años de lo agregado antes de tener esta función.
    private fun backfillInfo() {
        val s = Store(this); val key = s.tmdbKey
        if (key.isNullOrBlank()) return
        thread {
            var changed = false
            val a = s.manual()
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                if (o.has("rt") && o.has("yr")) continue
                val id = o.getString("id")
                val n = id.drop(2).toIntOrNull() ?: continue
                val inf = try { Tmdb.info(key, n, id.startsWith("mt")) } catch (e: Exception) { continue }
                s.setInfo(id, if (inf.runtime > 0) inf.runtime else -1, inf.year); changed = true
            }
            if (changed) refreshLocal()
        }
    }

    private fun showInfo(e: Entry) {
        val x = e.item
        val svc = e.label.removePrefix(x.name).trim()
        val yr = Sync.fmtYear(x.year).let { if (it.isEmpty()) "" else " $it" }
        val sp = SpannableString(x.name + yr + " " + svc)
        if (yr.isNotEmpty()) sp.setSpan(ForegroundColorSpan(sky), x.name.length, x.name.length + yr.length, 0)
        sp.setSpan(ForegroundColorSpan(yellow), x.name.length + yr.length + 1, sp.length, 0)
        sp.setSpan(StyleSpan(Typeface.BOLD_ITALIC), x.name.length + yr.length + 1, sp.length, 0)
        info1.text = sp
        val p = x.videoId?.split(":")?.takeIf { it.size >= 3 }
        info2.text = if (x.type == "movie") "Película" else if (p != null) "Temporada ${p[1]} · Episodio ${p[2]}" else "Serie"
        if (e.pkg != null) {   // lo agregado a mano: muestra cuánto dura
            bar.visibility = View.INVISIBLE
            info3.text = if (x.duration > 0) (if (x.type == "movie") "Duración: ${x.duration} min" else "Capítulos de ${x.duration} min") else ""
        } else if (x.duration > 0) {
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
