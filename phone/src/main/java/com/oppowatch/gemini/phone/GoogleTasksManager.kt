package com.oppowatch.gemini.phone

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class GoogleTaskItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val completed: Boolean = false
)

object GoogleTasksManager {

    private const val TAG = "GoogleTasksManager"
    private const val PREFS_NAME = "gemini_tasks_prefs"
    private const val KEY_TASKS_JSON = "google_tasks_list"
    const val CHANNEL_ID = "gemini_google_tasks_channel"
    const val GOOGLE_TASKS_PKG = "com.google.android.apps.tasks"
    const val GOOGLE_TASKS_SHARE_ACTIVITY = "com.google.android.apps.tasks.ui.ShareWithTaskListsActivity"

    /**
     * Thêm một task mới vào Google Tasks:
     * 1. Kích hoạt trực tiếp ShareWithTaskListsActivity của Google Tasks với nội dung điền sẵn
     * 2. Bắn broadcast com.google.android.apps.tasks.AddTask
     * 3. Lưu vào danh mục nội bộ của app và hiện Notification kèm nút mở Google Tasks
     */
    fun addTask(context: Context, title: String, notes: String = ""): GoogleTaskItem {
        val item = GoogleTaskItem(
            title = title.trim(),
            notes = notes.trim(),
            timestamp = System.currentTimeMillis()
        )

        val fullText = if (item.notes.isNotEmpty()) "${item.title}\n${item.notes}" else item.title

        // 1. Kích hoạt trực tiếp ShareWithTaskListsActivity của Google Tasks (nếu máy đang mở khóa)
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, fullText)
                putExtra(Intent.EXTRA_SUBJECT, item.title)
                component = ComponentName(GOOGLE_TASKS_PKG, GOOGLE_TASKS_SHARE_ACTIVITY)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(shareIntent)
            Log.d(TAG, "Đã mở giao diện thêm task của Google Tasks thành công.")
        } catch (e: Exception) {
            Log.w(TAG, "Không thể mở trực tiếp ShareWithTaskListsActivity: ${e.message}")
        }

        // 2. Gửi broadcast AddTask tới Google Tasks
        try {
            val addTaskBroadcast = Intent("com.google.android.apps.tasks.AddTask").apply {
                setPackage(GOOGLE_TASKS_PKG)
                putExtra("task_title", item.title)
                putExtra("task_notes", item.notes)
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            context.sendBroadcast(addTaskBroadcast)
            Log.d(TAG, "Đã gửi broadcast AddTask tới Google Tasks.")
        } catch (e: Exception) {
            Log.w(TAG, "Lỗi khi gửi broadcast AddTask: ${e.message}")
        }

        // 3. Lưu vào SharedPreferences nội bộ để không bao giờ mất dữ liệu
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
            Log.d(TAG, "Đã lưu Google Task vào prefs: ${item.title}")
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi lưu task vào prefs: ${e.message}", e)
        }

        // 4. Hiển thị thông báo Heads-up kèm nút mở Google Tasks
        showTaskNotification(context, item, fullText)

        // 5. Gửi broadcast cập nhật UI
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
    fun getTasks(context: Context): List<GoogleTaskItem> {
        val result = mutableListOf<GoogleTaskItem>()
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val currentJson = prefs.getString(KEY_TASKS_JSON, "[]") ?: "[]"
            val array = JSONArray(currentJson)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                result.add(
                    GoogleTaskItem(
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

    private fun showTaskNotification(context: Context, item: GoogleTaskItem, fullText: String) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Google Tasks",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Thông báo danh sách Google Tasks đồng bộ từ trợ lý Gemini trên đồng hồ"
                }
                notificationManager.createNotificationChannel(channel)
            }

            // Intent mở thẳng Google Tasks
            val openTasksIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, fullText)
                putExtra(Intent.EXTRA_SUBJECT, item.title)
                component = ComponentName(GOOGLE_TASKS_PKG, GOOGLE_TASKS_SHARE_ACTIVITY)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                (System.currentTimeMillis() % 10000).toInt(),
                openTasksIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("📝 Thêm vào Google Tasks: ${item.title}")
                .setContentText("Chạm để mở và xem task trong ứng dụng Google Tasks.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .addAction(
                    android.R.drawable.ic_menu_agenda,
                    "Mở Google Tasks",
                    pendingIntent
                )

            notificationManager.notify(item.id.hashCode(), builder.build())
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi hiển thị task notification: ${e.message}")
        }
    }
}
