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

// Entrega la portada completa, centrada sobre un fondo oscuro apaisado (16:9), para que la fila no la recorte.
class PosterProvider : ContentProvider() {
    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val src = uri.getQueryParameter("u") ?: throw FileNotFoundException()
        if (!src.startsWith("https://") && !src.startsWith("http://")) throw FileNotFoundException()
        val f = File(context!!.cacheDir, "p16_" + src.hashCode() + ".png")
        if (!f.exists()) {
            try {
                val bmp = URL(src).openStream().use { BitmapFactory.decodeStream(it) } ?: throw FileNotFoundException()
                val out = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
                val c = Canvas(out)
                c.drawColor(0xFF0F1720.toInt())
                val h = 360f
                val w = bmp.width * h / bmp.height
                val left = (640f - w) / 2f
                c.drawBitmap(bmp, null, RectF(left, 0f, left + w, h), Paint(Paint.FILTER_BITMAP_FLAG))
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
