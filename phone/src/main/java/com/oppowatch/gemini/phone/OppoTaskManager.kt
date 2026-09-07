package com.oppowatch.gemini.phone

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class OppoTaskItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val completed: Boolean = false
)

object OppoTaskManager {

    private const val TAG = "OppoTaskManager"
    private const val PREFS_NAME = "gemini_tasks_prefs"
    private const val KEY_TASKS_JSON = "oppo_tasks_list"
    const val CHANNEL_ID = "gemini_tasks_channel"

    /**
     * Thêm một task mới, lưu trữ cục bộ và hiển thị thông báo liên kết với Ghi chú OPPO
     */
    fun addTask(context: Context, title: String, notes: String = ""): OppoTaskItem {
        val item = OppoTaskItem(
            title = title.trim(),
            notes = notes.trim(),
            timestamp = System.currentTimeMillis()
        )

        // 1. Lưu vào SharedPreferences
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val currentJson = prefs.getString(KEY_TASKS_JSON, "[]") ?: "[]"
            val array = JSONArray(currentJson)

            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("notes", item.notes)
                put("timestamp", item.timestamp)
                put("completed", item.completed)
            }
            array.put(obj)

            prefs.edit().putString(KEY_TASKS_JSON, array.toString()).apply()
            Log.d(TAG, "Đã lưu việc cần làm mới: ${item.title}")
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi lưu task vào prefs: ${e.message}", e)
        }

        // 2. Hiển thị thông báo Heads-up kèm nút mở Ghi chú OPPO
        showTaskNotification(context, item)

        // 3. Gửi broadcast cho UI
        val broadcastIntent = Intent("com.oppowatch.gemini.TASK_ADDED").apply {
            setPackage(context.packageName)
            putExtra("title", item.title)
            putExtra("notes", item.notes)
        }
        context.sendBroadcast(broadcastIntent)

        return item
    }

    /**
     * Lấy toàn bộ danh sách task
     */
    fun getTasks(context: Context): List<OppoTaskItem> {
        val result = mutableListOf<OppoTaskItem>()
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val currentJson = prefs.getString(KEY_TASKS_JSON, "[]") ?: "[]"
            val array = JSONArray(currentJson)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                result.add(
                    OppoTaskItem(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        title = obj.optString("title", ""),
                        notes = obj.optString("notes", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        completed = obj.optBoolean("completed", false)
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi đọc danh sách task: ${e.message}")
        }
        return result
    }

    private fun showTaskNotification(context: Context, item: OppoTaskItem) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Việc cần làm (OPPO Task)",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Thông báo danh sách việc cần làm và ghi chú đồng bộ từ đồng hồ"
                }
                notificationManager.createNotificationChannel(channel)
            }

            // Tạo Intent mở ứng dụng Ghi chú ColorOS (com.coloros.note hoặc chuẩn Android CREATE_NOTE)
            val launchIntent = context.packageManager.getLaunchIntentForPackage("com.coloros.note")
                ?: context.packageManager.getLaunchIntentForPackage("com.oppo.quicknote")
                ?: Intent("android.intent.action.CREATE_NOTE").apply {
                    putExtra(Intent.EXTRA_TEXT, item.title)
                }

            val pendingIntent = PendingIntent.getActivity(
                context,
                (System.currentTimeMillis() % 10000).toInt(),
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("📝 Việc cần làm mới: ${item.title}")
                .setContentText("Đã thêm vào danh sách việc cần làm của bạn.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .addAction(
                    android.R.drawable.ic_menu_agenda,
                    "Mở Ghi chú ColorOS",
                    pendingIntent
                )

            notificationManager.notify(item.id.hashCode(), builder.build())
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi hiển thị task notification: ${e.message}")
        }
    }
}
