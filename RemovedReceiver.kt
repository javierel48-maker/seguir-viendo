package com.seguirviendo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram

class RemovedReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getLongExtra(TvContractCompat.EXTRA_WATCH_NEXT_PROGRAM_ID, -1)
        if (id < 0) return
        c.contentResolver.query(TvContractCompat.buildWatchNextProgramUri(id), null, null, null, null)?.use {
            if (it.moveToFirst()) WatchNextProgram.fromCursor(it).internalProviderId?.let { p -> Store(c).hide(p) }
        }
    }
}
