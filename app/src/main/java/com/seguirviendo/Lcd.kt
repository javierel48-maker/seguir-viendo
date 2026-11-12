package com.seguirviendo
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

// Sticker de "capítulos que faltan": pantalla de reloj digital de los 80 (fondo oscuro, borde amarillo, números celestes).
// Solo se enciende lo que forma el número; no hay segmentos apagados a la vista. text="" = pantalla vacía.
object Lcd {
    private val SEG = mapOf('0' to "abcdef", '1' to "bc", '2' to "abdeg", '3' to "abcdg", '4' to "bcfg",
        '5' to "acdfg", '6' to "acdefg", '7' to "abc", '8' to "abcdefg", '9' to "abcdfg", '-' to "g")

    fun size(text: String, u: Float): Pair<Float, Float> {
        val n = if (text.isEmpty()) 3 else text.length
        return Pair(n * 11f * u + (n - 1) * 3f * u + 14f * u, 34f * u)
    }

    private fun poly(c: Canvas, p: Paint, pts: List<Pair<Float, Float>>) {
        val path = Path()
        pts.forEachIndexed { i, q -> if (i == 0) path.moveTo(q.first, q.second) else path.lineTo(q.first, q.second) }
        path.close(); c.drawPath(path, p)
    }

    private fun digit(c: Canvas, p: Paint, x: Float, y: Float, w: Float, h: Float, t: Float, ch: Char) {
        val on = SEG[ch] ?: return
        val g = t * 0.15f
        val l = w - t - g * 2
        val v = (h - t) / 2f - g
        fun hor(cx: Float, cy: Float) = listOf(Pair(cx - l / 2, cy), Pair(cx - l / 2 + t * .6f, cy - t / 2), Pair(cx + l / 2 - t * .6f, cy - t / 2),
            Pair(cx + l / 2, cy), Pair(cx + l / 2 - t * .6f, cy + t / 2), Pair(cx - l / 2 + t * .6f, cy + t / 2))
        fun ver(cx: Float, cy: Float) = listOf(Pair(cx, cy - v / 2), Pair(cx + t / 2, cy - v / 2 + t * .6f), Pair(cx + t / 2, cy + v / 2 - t * .6f),
            Pair(cx, cy + v / 2), Pair(cx - t / 2, cy + v / 2 - t * .6f), Pair(cx - t / 2, cy - v / 2 + t * .6f))
        if ('a' in on) poly(c, p, hor(x + w / 2, y + t / 2))
        if ('g' in on) poly(c, p, hor(x + w / 2, y + h / 2))
        if ('d' in on) poly(c, p, hor(x + w / 2, y + h - t / 2))
        if ('f' in on) poly(c, p, ver(x + t / 2, y + h / 4 + t / 4))
        if ('b' in on) poly(c, p, ver(x + w - t / 2, y + h / 4 + t / 4))
        if ('e' in on) poly(c, p, ver(x + t / 2, y + 3 * h / 4 - t / 4))
        if ('c' in on) poly(c, p, ver(x + w - t / 2, y + 3 * h / 4 - t / 4))
    }

    // right/bottom = esquina inferior derecha de la pantalla; u = tamaño de la unidad (alto total = 34u)
    fun draw(c: Canvas, right: Float, bottom: Float, text: String, u: Float) {
        val (w, h) = size(text, u)
        val x0 = right - w; val y0 = bottom - h
        val r = 5f * u
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.style = Paint.Style.FILL; p.color = 0x6E000000
        c.drawRoundRect(RectF(x0 + u, y0 + 2 * u, x0 + w + u, y0 + h + 2 * u), r, r, p)
        p.color = 0xFF06101F.toInt()
        c.drawRoundRect(RectF(x0, y0, x0 + w, y0 + h), r, r, p)
        p.style = Paint.Style.STROKE; p.color = 0xFFFFD23F.toInt(); p.strokeWidth = maxOf(2f, 2f * u)
        c.drawRoundRect(RectF(x0, y0, x0 + w, y0 + h), r, r, p)
        p.style = Paint.Style.FILL; p.color = 0xFF19E3FF.toInt()
        var x = x0 + 7f * u
        for (ch in text) { digit(c, p, x, y0 + 7f * u, 11f * u, 20f * u, 3.4f * u, ch); x += 14f * u }
    }
}

class LcdView(ctx: Context, private val text: String, private val u: Float) : View(ctx) {
    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val s = Lcd.size(text, u)
        setMeasuredDimension(Math.ceil((s.first + 3 * u).toDouble()).toInt(), Math.ceil((s.second + 4 * u).toDouble()).toInt())
    }
    override fun onDraw(c: Canvas) { Lcd.draw(c, width - 3f * u, height - 3f * u, text, u) }
}
