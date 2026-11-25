package com.seguirviendo
import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap

// Navegador propio: solo abre las páginas de la lista, bloquea anuncios y ventanas emergentes, y se maneja con las flechas (cursor).
class WebActivity : Activity() {
    private val MP = ViewGroup.LayoutParams.MATCH_PARENT
    private lateinit var root: FrameLayout
    private lateinit var content: LinearLayout
    private lateinit var web: WebView
    private lateinit var bar: LinearLayout
    private lateinit var cursor: View
    private var btnSerie: Button? = null
    private var itemId = ""
    private var siteUrl = ""
    private var seriesUrl = ""
    private val allowed = HashSet<String>()
    private var cx = 0f
    private var cy = 0f
    private var full: View? = null
    private var fullCb: WebChromeClient.CustomViewCallback? = null
    private val ui = Handler(Looper.getMainLooper())
    private var ready = false
    private val seen = ConcurrentHashMap<String, Boolean>()   // servidores que cargó la página (true = bloqueado)
    private var maxed = false
    private var lastOk = 0L

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun say(t: String) { Toast.makeText(this, t, Toast.LENGTH_SHORT).show() }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        itemId = intent.getStringExtra("i") ?: ""
        val s = Store(this)
        val a = s.manual()
        var item: JSONObject? = null
        for (k in 0 until a.length()) if (a.getJSONObject(k).optString("id") == itemId) item = a.getJSONObject(k)
        siteUrl = item?.optString("wsite") ?: ""
        seriesUrl = item?.optString("wurl") ?: ""
        if (siteUrl.isBlank()) { say("Esta caja no tiene página asociada"); finish(); return }
        allowed.add(WebUtil.host(siteUrl))
        val ws = s.webSites()
        for (k in 0 until ws.length()) allowed.add(WebUtil.host(ws.getString(k)))
        buildUi()
        ready = true
        web.loadUrl(if (seriesUrl.isNotBlank()) seriesUrl else siteUrl)   // si ya marcaste la serie, abre directo ahí
    }

    private fun button(text: String, color: Int, act: () -> Unit): Button {
        val bt = Button(this)
        bt.text = text; bt.isAllCaps = false; bt.textSize = 13f; bt.setTextColor(color)
        bt.isFocusable = false
        bt.background = GradientDrawable().apply { setColor(0xFF06101F.toInt()); setStroke(dp(2), color); cornerRadius = dp(8).toFloat() }
        bt.setOnClickListener { act() }
        bar.addView(bt, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)).apply { rightMargin = dp(8) })
        return bt
    }

    private fun buildUi() {
        root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setBackgroundColor(0xFF0B1B4D.toInt())
        bar.setPadding(dp(10), dp(4), dp(10), dp(4))
        web = WebView(this)
        setupWeb()

        val sky = 0xFF19E3FF.toInt(); val yellow = 0xFFFFD23F.toInt(); val magenta = 0xFFFF2E93.toInt()
        button("Atrás", sky) { if (web.canGoBack()) web.goBack() else say("No hay más atrás") }
        button("Inicio", sky) { web.loadUrl(siteUrl) }
        btnSerie = button("Serie", if (seriesUrl.isBlank()) 0xFF7A8AB8.toInt() else yellow) {
            if (seriesUrl.isBlank()) say("Todavía no marcaste el enlace de esta serie") else web.loadUrl(seriesUrl)
        }
        button("Marcar serie", yellow) { saveLink() }
        button("Maximizar", sky) { toggleMax() }
        button("Limpiar", sky) { cleanOverlays() }
        button("Dominios", sky) { showHosts() }
        button("Salir", magenta) { finish() }

        content.addView(bar, LinearLayout.LayoutParams(MP, dp(56)))
        content.addView(web, LinearLayout.LayoutParams(MP, 0, 1f))
        root.addView(content, FrameLayout.LayoutParams(MP, MP))

        cursor = object : View(this@WebActivity) {
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 3f }
            val path = Path()
            override fun onDraw(c: Canvas) {
                val u = resources.displayMetrics.density
                path.reset()
                path.moveTo(cx, cy); path.lineTo(cx, cy + 22f * u); path.lineTo(cx + 6f * u, cy + 17f * u)
                path.lineTo(cx + 11f * u, cy + 27f * u); path.lineTo(cx + 15f * u, cy + 25f * u)
                path.lineTo(cx + 10f * u, cy + 15f * u); path.lineTo(cx + 17f * u, cy + 15f * u); path.close()
                c.drawPath(path, fill); c.drawPath(path, line)
            }
        }
        root.addView(cursor, FrameLayout.LayoutParams(MP, MP))
        setContentView(root)
        root.post { cx = root.width / 2f; cy = root.height / 2f; cursor.invalidate() }
    }

    private fun setupWeb() {
        val st = web.settings
        st.javaScriptEnabled = true
        st.domStorageEnabled = true
        st.javaScriptCanOpenWindowsAutomatically = false
        st.setSupportMultipleWindows(true)
        st.allowFileAccess = false
        st.allowContentAccess = false
        st.builtInZoomControls = false
        st.useWideViewPort = true
        st.loadWithOverviewMode = true
        st.userAgentString = st.userAgentString.replace("; wv", "").replace(" Mobile", "")
        if (Build.VERSION.SDK_INT >= 26) st.safeBrowsingEnabled = true
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false)
        web.setDownloadListener { _, _, _, _, _ -> say("Las descargas están bloqueadas") }

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                val u = r.url
                val sch = u.scheme ?: ""
                if (sch != "http" && sch != "https") return true
                if (!r.isForMainFrame) return false
                if (hostAllowed(u.host ?: "")) return false
                say("Bloqueado: " + (u.host ?: ""))
                return true
            }

            override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? {
                val h = r.url.host ?: ""
                val bl = AdBlock.blocked(r.url)
                if (h.isNotEmpty() && seen.size < 300) seen[h] = bl || (seen[h] == true)
                if (bl) return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                return null
            }

            override fun onPageStarted(v: WebView, url: String?, icon: Bitmap?) {
                if (maxed) { maxed = false; bar.visibility = View.VISIBLE }
            }

            override fun onPageFinished(v: WebView, url: String?) { v.evaluateJavascript(AdBlock.CLEAN_JS, null); v.evaluateJavascript(AdBlock.FS_JS, null) }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(v: WebView?, dialog: Boolean, gesture: Boolean, msg: Message?): Boolean = false   // sin ventanas nuevas
            override fun onShowCustomView(view: View, cb: CustomViewCallback) {
                if (full != null) { cb.onCustomViewHidden(); return }
                full = view; fullCb = cb
                content.visibility = View.GONE
                root.addView(view, FrameLayout.LayoutParams(MP, MP))
                cursor.bringToFront()
            }
            override fun onHideCustomView() { hideFull() }
        }
    }

    private fun hideFull() {
        val v = full ?: return
        full = null
        root.removeView(v)
        content.visibility = View.VISIBLE
        val cb = fullCb
        fullCb = null
        cb?.onCustomViewHidden()
    }

    private fun hostAllowed(h: String): Boolean {
        val x = WebUtil.strip(h)
        return allowed.any { x == it || x.endsWith(".$it") }
    }

    private fun saveLink() {
        val u = web.url ?: return
        if (!u.startsWith("https://") || !hostAllowed(WebUtil.host(u))) { say("Esta página no se puede marcar"); return }
        Store(this).setWebLink(itemId, u)
        seriesUrl = u
        btnSerie?.let { it.setTextColor(0xFFFFD23F.toInt()); (it.background as? GradientDrawable)?.setStroke(dp(2), 0xFFFFD23F.toInt()) }
        say("Listo: ese enlace queda para esta serie")
    }

    override fun dispatchKeyEvent(ev: KeyEvent): Boolean {
        if (!ready) return super.dispatchKeyEvent(ev)
        val k = ev.keyCode
        if (k == KeyEvent.KEYCODE_BACK) {
            if (ev.action == KeyEvent.ACTION_UP) { if (full != null) hideFull() else if (maxed) toggleMax() else if (web.canGoBack()) web.goBack() else finish() }
            return true
        }
        if (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN || k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (ev.action == KeyEvent.ACTION_DOWN) moveCursor(k, ev.repeatCount)
            return true
        }
        if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            if (ev.action == KeyEvent.ACTION_DOWN && ev.repeatCount == 0) {
                val now = SystemClock.uptimeMillis()
                tap()
                // Doble OK sobre la página = maximizar el video (o volver a lo normal)
                if (now - lastOk < 500 && (maxed || cy > bar.height)) { toggleMax(); lastOk = 0L } else lastOk = now
            }
            return true
        }
        return super.dispatchKeyEvent(ev)
    }

    // Las flechas mueven el cursor; contra el borde de abajo o de arriba de la página, la desplazan.
    private fun moveCursor(k: Int, rep: Int) {
        val d = resources.displayMetrics.density
        val step = (14 + minOf(rep, 20) * 3) * d
        val w = root.width.toFloat()
        val h = root.height.toFloat()
        val barH = bar.height.toFloat()
        val scroll = ((70 + minOf(rep, 10) * 12) * d).toInt()
        when (k) {
            KeyEvent.KEYCODE_DPAD_LEFT -> cx -= step
            KeyEvent.KEYCODE_DPAD_RIGHT -> cx += step
            KeyEvent.KEYCODE_DPAD_UP ->
                if (full == null && cy <= barH + 40 * d && cy > barH && web.canScrollVertically(-1)) web.scrollBy(0, -scroll) else cy -= step
            KeyEvent.KEYCODE_DPAD_DOWN ->
                if (full == null && cy >= h - 40 * d && web.canScrollVertically(1)) web.scrollBy(0, scroll) else cy += step
        }
        cx = cx.coerceIn(0f, w - 1f)
        cy = cy.coerceIn(0f, h - 1f)
        cursor.invalidate()
    }

    // OK = un toque en el lugar donde está el cursor.
    private fun tap() {
        val t = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, cx, cy, 0)
        down.source = InputDevice.SOURCE_TOUCHSCREEN
        root.dispatchTouchEvent(down)
        down.recycle()
        val x = cx; val y = cy
        ui.postDelayed({
            val t2 = SystemClock.uptimeMillis()
            val up = MotionEvent.obtain(t, t2, MotionEvent.ACTION_UP, x, y, 0)
            up.source = InputDevice.SOURCE_TOUCHSCREEN
            root.dispatchTouchEvent(up)
            up.recycle()
        }, 60)
    }

    private fun toggleMax() {
        web.evaluateJavascript(AdBlock.MAX_JS) { r ->
            when ((r ?: "").replace("\"", "")) {
                "on" -> { maxed = true; bar.visibility = View.GONE; say("Maximizado. Atrás para volver") }
                "off" -> { maxed = false; bar.visibility = View.VISIBLE }
                else -> say("No encontré un video en esta página")
            }
        }
    }

    private fun cleanOverlays() {
        web.evaluateJavascript(AdBlock.CLEAN_OVERLAYS_JS) { r ->
            val n = (r ?: "0").replace("\"", "").toIntOrNull() ?: 0
            say(if (n > 0) "Ocultadas $n capas" else "No encontré carteles para ocultar")
        }
    }

    // Lista de los servidores que cargó la página; sirve para sumar los de publicidad a la lista de bloqueo.
    private fun showHosts() {
        val txt = seen.entries.sortedBy { it.key }.joinToString("\n") { (if (it.value) "[bloqueado] " else "") + it.key }
        AlertDialog.Builder(this).setTitle("Servidores de esta página").setMessage(if (txt.isBlank()) "Todavía no cargó nada." else txt)
            .setPositiveButton("Cerrar", null).show()
    }

    override fun onResume() { super.onResume(); if (ready) web.onResume() }
    override fun onPause() { if (ready) web.onPause(); super.onPause() }
    override fun onDestroy() { if (ready) { try { web.destroy() } catch (e: Exception) { } }; super.onDestroy() }
}
