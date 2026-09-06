package com.oppowatch.gemini

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class TileLaunchActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            putExtra("FROM_TILE", true)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(launchIntent)
        finish()
    }
}
