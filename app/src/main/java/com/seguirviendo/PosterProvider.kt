package com.seguirviendo
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException
import java.net.URL

// Arma la ficha de la fila: casete de VHS de fondo y el póster de alto completo encima, como una etiqueta.
class PosterProvider : ContentProvider() {
    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val src = uri.getQueryParameter("u") ?: throw FileNotFoundException()
        if (!src.startsWith("https://") && !src.startsWith("http://")) throw FileNotFoundException()
        val ctx = context!!
        val f = File(ctx.cacheDir, "pv12_" + src.hashCode() + ".png")
        if (!f.exists()) {
            try {
                val conn = URL(src.replaceFirst("http://", "https://")).openConnection()
                conn.connectTimeout = 10000
                conn.readTimeout = 15000
                val poster = conn.getInputStream().use { BitmapFactory.decodeStream(it) } ?: throw FileNotFoundException()
                val bg = BitmapFactory.decodeResource(ctx.resources, R.drawable.cassette_card)
                val out = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
                val c = Canvas(out)
                c.drawBitmap(bg, null, RectF(0f, 0f, 640f, 360f), Paint(Paint.FILTER_BITMAP_FLAG))
                var w = poster.width * 360f / poster.height
                var h = 360f
                if (w > 600f) { w = 600f; h = w * poster.height / poster.width }
                val left = (640f - w) / 2f
                val top = (360f - h) / 2f
                val sh = Paint()
                sh.color = 0xFF000000.toInt()
                for ((grow, alpha) in listOf(10f to 30, 6f to 45, 3f to 60)) {
                    sh.alpha = alpha
                    c.drawRect(left - grow + 4f, top, left + w + grow + 4f, top + h, sh)
                }
                val border = Paint()
                border.color = 0xFFF4F1E8.toInt()
                c.drawRect(left - 3f, top, left + w + 3f, top + h, border)
                c.drawBitmap(poster, null, RectF(left, top, left + w, top + h), Paint(Paint.FILTER_BITMAP_FLAG))
                val tmp = File(f.path + ".tmp")
                tmp.outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
                tmp.renameTo(f)
            } catch (e: Exception) { throw FileNotFoundException() }
        }
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(uri: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, v: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<String>?) = 0
}
