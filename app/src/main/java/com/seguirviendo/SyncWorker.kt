package com.seguirviendo
import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class SyncWorker(c: Context, p: WorkerParameters) : Worker(c, p) {
    override fun doWork(): Result = try {
        val c = applicationContext
        Sync.run(c)
        val changed = TraktSync.refresh(c)
        // Si Trakt ya sabe lo último que viste pero Stremio todavía no, espera y vuelve a leer Stremio (así capítulo y restantes cambian juntos).
        var tries = 0
        while (TraktSync.stale(c) && tries < 2) { Thread.sleep(45000); Sync.run(c); tries++ }
        if (changed || tries > 0) Sync.rebuild(c)   // capítulos y orden al día aunque no abras la app
        Result.success()
    } catch (e: Exception) { Result.retry() }
}
