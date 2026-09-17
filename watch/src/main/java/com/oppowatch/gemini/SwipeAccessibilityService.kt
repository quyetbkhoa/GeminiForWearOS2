package com.oppowatch.gemini

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

import android.os.Build

class SwipeAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: SwipeAccessibilityService? = null

        fun lockScreen(): Boolean {
            val service = instance ?: return false
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                } catch (e: Exception) {
                    android.util.Log.e("SwipeAccessibility", "Lỗi lockScreen: ${e.message}")
                    false
                }
            } else {
                false
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        android.util.Log.d("SwipeAccessibility", "onServiceConnected")
        // Khởi động cầu nối Bluetooth ADB để Kiwi Manager có thể kết nối mọi lúc
        try {
            AdbBtBridge.start()
        } catch (e: Exception) {
            android.util.Log.e("SwipeAccessibility", "Lỗi khởi động AdbBtBridge: ${e.message}")
        }
        // Khởi động GeminiSyncServer để đồng bộ cài đặt từ điện thoại mọi lúc
        try {
            GeminiSyncServer.start(this)
        } catch (e: Exception) {
            android.util.Log.e("SwipeAccessibility", "Lỗi khởi động GeminiSyncServer: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) {
            instance = null
        }
        try {
            AdbBtBridge.stop()
        } catch (_: Exception) {}
    }

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