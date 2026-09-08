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
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.concurrent.thread

data class GoogleTaskItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val completed: Boolean = false
)

enum class SyncMode(val code: String, val displayName: String) {
    LOCAL_ONLY("LOCAL_ONLY", "Lưu cục bộ trong máy (100% ngầm)"),
    WEBHOOK("WEBHOOK", "Đồng bộ Google Tasks qua Webhook"),
    BOTH("BOTH", "Song song cả hai (Máy + Google Tasks)"),
    SHARE_DIALOG("SHARE_DIALOG", "Mở popup Google Tasks (Thủ công)");

    companion object {
        fun fromCode(code: String): SyncMode {
            return values().firstOrNull { it.code == code } ?: LOCAL_ONLY
        }
    }
}

object GoogleTasksManager {

    private const val TAG = "GoogleTasksManager"
    private const val PREFS_NAME = "gemini_tasks_prefs"
    private const val KEY_TASKS_JSON = "google_tasks_list"
    private const val KEY_SYNC_MODE = "tasks_sync_mode"
    private const val KEY_WEBHOOK_URL = "tasks_webhook_url"

    const val CHANNEL_ID = "gemini_google_tasks_channel"
    const val GOOGLE_TASKS_PKG = "com.google.android.apps.tasks"
    const val GOOGLE_TASKS_SHARE_ACTIVITY = "com.google.android.apps.tasks.ui.ShareWithTaskListsActivity"

    const val APPS_SCRIPT_SAMPLE_CODE = """function doPost(e) {
  var data = JSON.parse(e.postData.contents);
  Tasks.Tasks.insert({title: data.title, notes: data.notes || ""}, '@default');
  return ContentService.createTextOutput("OK");
}"""

    fun getSyncMode(context: Context): SyncMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val defaultMode = if (getWebhookUrl(context).isNotBlank()) SyncMode.BOTH.code else SyncMode.LOCAL_ONLY.code
        val code = prefs.getString(KEY_SYNC_MODE, defaultMode) ?: defaultMode
        return SyncMode.fromCode(code)
    }

    fun setSyncMode(context: Context, mode: SyncMode) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SYNC_MODE, mode.code).apply()
        Log.d(TAG, "Đã lưu chế độ đồng bộ: ${mode.code}")
    }

    fun getWebhookUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_WEBHOOK_URL, "")?.trim() ?: ""
    }

    fun setWebhookUrl(context: Context, url: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_WEBHOOK_URL, url.trim()).apply()
        Log.d(TAG, "Đã lưu Webhook URL: $url")
    }

    /**
     * Thêm task mới theo chế độ đã chọn
     */
    fun addTask(
        context: Context,
        title: String,
        notes: String = "",
        onCompleted: ((Boolean, String) -> Unit)? = null
    ): GoogleTaskItem {
        val item = GoogleTaskItem(
            title = title.trim(),
            notes = notes.trim(),
            timestamp = System.currentTimeMillis()
        )
        val mode = getSyncMode(context)
        val webhookUrl = getWebhookUrl(context)
        val fullText = if (item.notes.isNotEmpty()) "${item.title}\n${item.notes}" else item.title

        Log.i(TAG, "Thêm task: '${item.title}' với chế độ $mode")

        when (mode) {
            SyncMode.LOCAL_ONLY -> {
                saveTaskLocally(context, item)
                showTaskNotification(context, item, fullText, "Đã lưu việc cần làm vào máy")
                notifyUiUpdated(context, item)
                onCompleted?.invoke(true, "Đã lưu vào danh mục việc cần làm")
            }
            SyncMode.WEBHOOK -> {
                if (webhookUrl.isBlank()) {
                    // Chưa cài đặt URL -> Tự động chuyển sang lưu nội bộ
                    saveTaskLocally(context, item)
                    showTaskNotification(context, item, fullText, "Chưa cài Webhook, đã lưu vào máy")
                    notifyUiUpdated(context, item)
                    onCompleted?.invoke(false, "Chưa cấu hình URL Webhook, đã lưu cục bộ")
                } else {
                    sendTaskToWebhook(webhookUrl, item.title, item.notes) { success, msg ->
                        if (success) {
                            showTaskNotification(context, item, fullText, "Đã đồng bộ lên Google Tasks")
                        } else {
                            // Nếu webhook lỗi, lưu dự phòng vào máy
                            saveTaskLocally(context, item)
                            notifyUiUpdated(context, item)
                            showTaskNotification(context, item, fullText, "Lỗi Webhook ($msg), đã lưu vào máy")
                        }
                        onCompleted?.invoke(success, msg)
                    }
                }
            }
            SyncMode.BOTH -> {
                saveTaskLocally(context, item)
                notifyUiUpdated(context, item)
                if (webhookUrl.isNotBlank()) {
                    sendTaskToWebhook(webhookUrl, item.title, item.notes) { success, msg ->
                        val sub = if (success) "Đã lưu máy & đồng bộ Google Tasks" else "Đã lưu máy (Lỗi Webhook: $msg)"
                        showTaskNotification(context, item, fullText, sub)
                        onCompleted?.invoke(success, msg)
                    }
                } else {
                    showTaskNotification(context, item, fullText, "Đã lưu vào máy (Chưa cài Webhook)")
                    onCompleted?.invoke(true, "Đã lưu vào máy")
                }
            }
            SyncMode.SHARE_DIALOG -> {
                saveTaskLocally(context, item)
                notifyUiUpdated(context, item)
                try {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, fullText)
                        putExtra(Intent.EXTRA_SUBJECT, item.title)
                        component = ComponentName(GOOGLE_TASKS_PKG, GOOGLE_TASKS_SHARE_ACTIVITY)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    context.startActivity(shareIntent)
                } catch (e: Exception) {
                    Log.w(TAG, "Không thể mở ShareWithTaskListsActivity: ${e.message}")
                }
                showTaskNotification(context, item, fullText, "Chạm để xác nhận lưu trên Google Tasks")
                onCompleted?.invoke(true, "Đã mở giao diện Google Tasks")
            }
        }

        return item
    }

    /**
     * Gửi HTTP POST trực tiếp đến Google Apps Script Webhook
     */
    fun sendTaskToWebhook(
        webhookUrl: String,
        title: String,
        notes: String,
        callback: (Boolean, String) -> Unit
    ) {
        thread(name = "GoogleTasksWebhookThread") {
            try {
                val payload = JSONObject().apply {
                    put("title", title)
                    put("notes", notes)
                    put("timestamp", System.currentTimeMillis())
                }.toString()

                val conn = (URL(webhookUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    setRequestProperty("User-Agent", "GeminiOppoWatch/1.3.4")
                    doOutput = true
                    instanceFollowRedirects = false // Xử lý redirect 302 của Apps Script
                    connectTimeout = 8000
                    readTimeout = 8000
                }

                conn.outputStream.use { os ->
                    os.write(payload.toByteArray(Charsets.UTF_8))
                    os.flush()
                }

                var responseCode = conn.responseCode
                Log.d(TAG, "Apps Script POST response code: $responseCode")

                // Google Apps Script trả về 302 sang script.googleusercontent.com
                if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                    responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                    responseCode == HttpURLConnection.HTTP_SEE_OTHER) {
                    val redirectUrl = conn.getHeaderField("Location")
                    conn.disconnect()

                    if (!redirectUrl.isNullOrEmpty()) {
                        Log.d(TAG, "Following redirect to: $redirectUrl")
                        val redirectConn = (URL(redirectUrl).openConnection() as HttpURLConnection).apply {
                            requestMethod = "GET"
                            setRequestProperty("User-Agent", "GeminiOppoWatch/1.3.4")
                            connectTimeout = 8000
                            readTimeout = 8000
                        }
                        val redirectCode = redirectConn.responseCode
                        val responseText = redirectConn.inputStream.bufferedReader().use { it.readText() }
                        redirectConn.disconnect()

                        if (redirectCode in 200..299) {
                            callback(true, "Google Tasks đã ghi nhận thành công ($responseText)")
                            return@thread
                        } else {
                            callback(false, "Redirect HTTP $redirectCode: $responseText")
                            return@thread
                        }
                    }
                }

                if (responseCode in 200..299) {
                    val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                    conn.disconnect()
                    callback(true, "Google Tasks đã ghi nhận ($responseText)")
                } else {
                    conn.disconnect()
                    callback(false, "HTTP $responseCode")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi kết nối Webhook: ${e.message}")
                callback(false, e.message ?: "Lỗi kết nối mạng")
            }
        }
    }

    /**
     * Gửi task kiểm tra kết nối Webhook
     */
    fun testWebhook(webhookUrl: String, callback: (Boolean, String) -> Unit) {
        val timeStr = java.text.SimpleDateFormat("HH:mm:ss dd/MM", java.util.Locale.getDefault()).format(java.util.Date())
        sendTaskToWebhook(
            webhookUrl = webhookUrl,
            title = "Thử nghiệm Gemini Assistant ($timeStr)",
            notes = "Task thử nghiệm đồng bộ tự động từ OPPO Watch",
            callback = callback
        )
    }

    private fun saveTaskLocally(context: Context, item: GoogleTaskItem) {
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
            Log.d(TAG, "Đã lưu task vào prefs: ${item.title}")
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi lưu task vào prefs: ${e.message}", e)
        }
    }

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
            // Sắp xếp task mới nhất lên đầu, task hoàn thành xuống dưới
            result.sortWith(compareBy<GoogleTaskItem> { it.completed }.thenByDescending { it.timestamp })
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi đọc danh sách task: ${e.message}")
        }
        return result
    }

    fun toggleTaskComplete(context: Context, id: String): Boolean {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val currentJson = prefs.getString(KEY_TASKS_JSON, "[]") ?: "[]"
            val array = JSONArray(currentJson)
            var found = false

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                if (obj.optString("id") == id) {
                    val currentStatus = obj.optBoolean("completed", false)
                    obj.put("completed", !currentStatus)
                    found = true
                    break
                }
            }

            if (found) {
                prefs.edit().putString(KEY_TASKS_JSON, array.toString()).apply()
                notifyUiUpdated(context, null)
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi toggle complete task: ${e.message}")
        }
        return false
    }

    fun deleteTask(context: Context, id: String): Boolean {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val currentJson = prefs.getString(KEY_TASKS_JSON, "[]") ?: "[]"
            val array = JSONArray(currentJson)
            val newArray = JSONArray()
            var found = false

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                if (obj.optString("id") == id) {
                    found = true
                } else {
                    newArray.put(obj)
                }
            }

            if (found) {
                prefs.edit().putString(KEY_TASKS_JSON, newArray.toString()).apply()
                notifyUiUpdated(context, null)
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi xóa task: ${e.message}")
        }
        return false
    }

    fun clearCompletedTasks(context: Context): Int {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val currentJson = prefs.getString(KEY_TASKS_JSON, "[]") ?: "[]"
            val array = JSONArray(currentJson)
            val newArray = JSONArray()
            var deletedCount = 0

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                if (obj.optBoolean("completed", false)) {
                    deletedCount++
                } else {
                    newArray.put(obj)
                }
            }

            if (deletedCount > 0) {
                prefs.edit().putString(KEY_TASKS_JSON, newArray.toString()).apply()
                notifyUiUpdated(context, null)
            }
            return deletedCount
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi xóa task hoàn thành: ${e.message}")
            return 0
        }
    }

    private fun notifyUiUpdated(context: Context, item: GoogleTaskItem?) {
        val broadcastIntent = Intent("com.oppowatch.gemini.TASK_ADDED").apply {
            setPackage(context.packageName)
            item?.let {
                putExtra("title", it.title)
                putExtra("notes", it.notes)
            }
        }
        context.sendBroadcast(broadcastIntent)
    }

    private fun showTaskNotification(
        context: Context,
        item: GoogleTaskItem,
        fullText: String,
        statusSubtitle: String
    ) {
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

            // Chạm vào notification để mở ứng dụng Phone Companion
            val launchAppIntent = Intent(context, PhoneMainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("OPEN_PAGE", "TASKS")
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                (System.currentTimeMillis() % 10000).toInt(),
                launchAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("📝 Việc cần làm: ${item.title}")
                .setContentText(statusSubtitle)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            // Nút mở Google Tasks chính chủ nếu người dùng có cài
            try {
                val openGoogleTasksIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, fullText)
                    putExtra(Intent.EXTRA_SUBJECT, item.title)
                    component = ComponentName(GOOGLE_TASKS_PKG, GOOGLE_TASKS_SHARE_ACTIVITY)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                val pendingTasksIntent = PendingIntent.getActivity(
                    context,
                    ((System.currentTimeMillis() + 1) % 10000).toInt(),
                    openGoogleTasksIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(
                    android.R.drawable.ic_menu_agenda,
                    "Mở Google Tasks",
                    pendingTasksIntent
                )
            } catch (_: Exception) {}

            notificationManager.notify(item.id.hashCode(), builder.build())
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi hiển thị task notification: ${e.message}")
        }
    }
}
