package com.seguirviendo
import android.app.Activity
import android.os.Bundle

class OpenActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Sync.openLink(this, intent.getStringExtra("t") ?: "", intent.getStringExtra("i") ?: "", intent.getStringExtra("v"))
        finish()
    }
}
