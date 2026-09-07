package com.oppowatch.gemini.phone

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat

class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "gemini_reminders_channel"
        private const val TAG = "ReminderReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val message = intent.getStringExtra("message") ?: "Đã đến giờ nhắc nhở công việc!"
        Log.i(TAG, "Đến giờ kích hoạt nhắc nhở: $message")

        // 1. Tạo Notification Channel nếu cần
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Nhắc nhở Gemini",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Kênh thông báo nhắc nhở theo ngữ cảnh từ Gemini Assistant"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // 2. Hiện thông báo Heads-up ưu tiên tối đa
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("⏰ NHẮC NHỞ: $message")
            .setContentText("Đã đến thời gian nhắc việc do bạn cài đặt qua trợ lý Gemini.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)

        // 3. Rung thiết bị
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 500, 200, 500), -1)
            }
        } catch (_: Exception) {}

        // 4. Phát TTS qua tai nghe / loa điện thoại
        val filterManager = BluetoothFilterManager(context)
        val ttsText = "Đã đến giờ nhắc nhở: $message"
        filterManager.checkAndPlayTtsIfAllowed(ttsText) { allowed, _ ->
            if (allowed) {
                TtsSpeaker.speak(context, ttsText)
            } else {
                // Kể cả khi chưa tick Bluetooth, vẫn đọc qua loa ngoài cho nhắc nhở quan trọng
                TtsSpeaker.speak(context, ttsText)
            }
        }

        // 5. Broadcast cập nhật UI
        val broadcastIntent = Intent("com.oppowatch.gemini.REMINDER_FIRED").apply {
            setPackage(context.packageName)
            putExtra("message", message)
        }
        context.sendBroadcast(broadcastIntent)
    }
}
