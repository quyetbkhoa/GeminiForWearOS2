package com.oppowatch.gemini.phone

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast

/**
 * Activity trong suốt (Transparent Trampoline) giúp ghi dữ liệu vào Clipboard an toàn
 * trên Android 10 - 14 kể cả khi lệnh được phát ra từ background service.
 */
class ClipboardTrampolineActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val text = intent?.getStringExtra("text")
            ?: intent?.getStringExtra(Intent.EXTRA_TEXT)
            ?: ""

        if (text.isNotEmpty()) {
            try {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clip = ClipData.newPlainText("Gemini Dictation", text)
                clipboard?.setPrimaryClip(clip)

                val preview = if (text.length > 40) "${text.take(40)}..." else text
                Toast.makeText(this, "📋 Đã chép vào clipboard: $preview", Toast.LENGTH_SHORT).show()
                Log.d("ClipboardTrampoline", "Đã chép text vào clipboard thành công: $preview")

                val broadcastIntent = Intent("com.oppowatch.gemini.CLIPBOARD_COPIED").apply {
                    setPackage(packageName)
                    putExtra("text", text)
                }
                sendBroadcast(broadcastIntent)
            } catch (e: Exception) {
                Log.e("ClipboardTrampoline", "Lỗi khi ghi clipboard: ${e.message}", e)
            }
        }

        finish()
        overridePendingTransition(0, 0)
    }
}
