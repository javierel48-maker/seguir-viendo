package com.seguirviendo
import android.content.Context
import android.os.Process
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicInteger

// Diagnóstico de arranque: anota en qué segundo (desde que arrancó la app) pasó cada paso. Se ve en Más… → Diagnóstico de arranque.
object Diag {
    private var started = false
    private val marks = ArrayList<String>()
    private val covers = AtomicInteger(0)

    @Synchronized
    fun mark(c: Context, name: String) {
        val sinceStart = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
        if (!started) {   // proceso nuevo: arranca una lista nueva
            started = true; marks.clear(); covers.set(0)
            val up = (SystemClock.elapsedRealtime() - sinceStart) / 1000
            marks.add("El aparato llevaba ${up}s encendido cuando se abrió la app")
        }
        if (marks.size < 40) marks.add(String.format("%5.1fs  %s", sinceStart / 1000.0, name))
        c.applicationContext.getSharedPreferences("s", 0).edit().putString("dg", marks.joinToString("\n")).apply()
    }

    fun cover(c: Context) {
        val n = covers.incrementAndGet()
        if (n == 1) mark(c, "primera portada en pantalla")
        else if (n == 9) mark(c, "9 portadas en pantalla")
    }

    fun report(c: Context): String = c.getSharedPreferences("s", 0).getString("dg", null) ?: "Todavía no hay datos. Cerrá la app, volvela a abrir y mirá de nuevo."
}
