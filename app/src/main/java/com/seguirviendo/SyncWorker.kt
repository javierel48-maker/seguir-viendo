package com.seguirviendo
import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class SyncWorker(c: Context, p: WorkerParameters) : Worker(c, p) {
    override fun doWork(): Result = try {
        Sync.run(applicationContext)
        if (TraktSync.refresh(applicationContext)) Sync.rebuild(applicationContext)   // capítulos y orden al día aunque no abras la app
        Result.success()
    } catch (e: Exception) { Result.retry() }
}
