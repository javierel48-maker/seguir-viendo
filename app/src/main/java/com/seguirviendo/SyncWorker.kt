package com.seguirviendo
import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class SyncWorker(c: Context, p: WorkerParameters) : Worker(c, p) {
    override fun doWork(): Result = try { Sync.run(applicationContext); Result.success() } catch (e: Exception) { Result.retry() }
}
