package com.oppowatch.gemini

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class SwipeAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: ""
        val cls = event.className?.toString() ?: ""

        android.util.Log.d("SwipeAccessibility", "onAccessibilityEvent: pkg=$pkg, cls=$cls")

        // If user swiped into Assistant feed or Google feed
        if ((pkg.contains("clockwork") || pkg.contains("google") || pkg.contains("assistant") || pkg.contains("breeno") || pkg.contains("heytap")) &&
            (cls.contains("Assistant") || cls.contains("Feed") || cls.contains("Card") || cls.contains("Stream") || cls.contains("Home"))) {
            android.util.Log.d("SwipeAccessibility", "Detected assistant swipe! Launching Gemini...")
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
        }
    }

    override fun onInterrupt() {}
}