package com.oppowatch.gemini.phone

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

object ReminderManager {

    private const val TAG = "ReminderManager"

    fun scheduleReminder(context: Context, message: String, delaySeconds: Int): Boolean {
        return try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val triggerAtMillis = System.currentTimeMillis() + (delaySeconds * 1000L)

            val intent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra("message", message)
            }

            val requestCode = (System.currentTimeMillis() % 100000).toInt()
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }

            Log.i(TAG, "Đã lên lịch nhắc nhở thành công sau $delaySeconds giây: $message")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi đặt lịch nhắc nhở: ${e.message}", e)
            false
        }
    }
}
