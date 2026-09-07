package com.oppowatch.gemini

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.content.FileProvider
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.concurrent.thread

class WatchUpdateReceiverService : WearableListenerService() {

    companion object {
        private const val TAG = "WatchUpdateReceiver"
        private const val UPDATE_CHANNEL_PATH = "/watch_update_apk"
    }

    override fun onChannelOpened(channel: ChannelClient.Channel) {
        super.onChannelOpened(channel)
        Log.d(TAG, "onChannelOpened: path=${channel.path}")

        if (channel.path == UPDATE_CHANNEL_PATH) {
            receiveApkFromChannel(channel)
        }
    }

    private fun receiveApkFromChannel(channel: ChannelClient.Channel) {
        val channelClient = Wearable.getChannelClient(this)
        channelClient.getInputStream(channel).addOnSuccessListener { inputStream ->
            thread(name = "WatchApkReceiverThread") {
                try {
                    val updateFile = File(cacheDir, "gemini_watch_update.apk")
                    if (updateFile.exists()) {
                        updateFile.delete()
                    }

                    Log.d(TAG, "Receiving APK stream to ${updateFile.absolutePath}...")
                    notifyVibrate(longArrayOf(0, 100, 100, 100))

                    FileOutputStream(updateFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalBytes = 0L
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalBytes += bytesRead
                        }
                        output.flush()
                        Log.d(TAG, "APK received successfully! Total size: $totalBytes bytes")
                    }

                    channelClient.close(channel)

                    // Notify user and launch PackageInstaller
                    notifyVibrate(longArrayOf(0, 250, 150, 250))
                    launchInstaller(updateFile)

                } catch (e: Exception) {
                    Log.e(TAG, "Error saving APK from channel", e)
                } finally {
                    try { inputStream.close() } catch (_: Exception) {}
                }
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to get InputStream for channel", e)
        }
    }

    private fun launchInstaller(apkFile: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", apkFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            Log.d(TAG, "Launching package installer for $uri")
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch installer", e)
        }
    }

    private fun notifyVibrate(pattern: LongArray) {
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, -1)
            }
        } catch (_: Exception) {}
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        if (messageEvent.path == "/watch_update_trigger_install") {
            val updateFile = File(cacheDir, "gemini_watch_update.apk")
            if (updateFile.exists()) {
                launchInstaller(updateFile)
            }
        } else if (messageEvent.path == "/gemini_api_key_sync") {
            val apiKey = String(messageEvent.data, Charsets.UTF_8).trim()
            val prefs = getSharedPreferences("gemini_prefs", Context.MODE_PRIVATE)
            if (apiKey.isNotEmpty()) {
                prefs.edit().putString("custom_api_key", apiKey).apply()
                Log.d(TAG, "Đã lưu Gemini API Key đồng bộ từ điện thoại!")
            } else {
                prefs.edit().remove("custom_api_key").apply()
                Log.d(TAG, "Đã xóa custom Gemini API Key, khôi phục mặc định!")
            }
        } else if (messageEvent.path == "/gemini_model_sync") {
            val model = String(messageEvent.data, Charsets.UTF_8).trim()
            if (model.isNotEmpty()) {
                getSharedPreferences("gemini_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("selected_model", model)
                    .apply()
                Log.d(TAG, "Đã lưu Gemini Model đồng bộ từ điện thoại: $model")
                notifyVibrate(longArrayOf(0, 80, 60, 80))
            }
        } else if (messageEvent.path == "/app_theme_sync") {
            val theme = String(messageEvent.data, Charsets.UTF_8).trim()
            val prefs = getSharedPreferences("gemini_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("app_theme_mode", theme)
                .putString("watch_color_theme", if (theme == "light") "light" else "dark")
                .apply()
            Log.d(TAG, "Đã nhận lệnh đồng bộ giao diện toàn diện: $theme")
            val intent = Intent("com.oppowatch.gemini.WATCH_THEME_CHANGED").apply {
                putExtra("theme", theme)
                setPackage(packageName)
            }
            sendBroadcast(intent)
            notifyVibrate(longArrayOf(0, 80))
        } else if (messageEvent.path == "/watch_color_theme") {
            val theme = String(messageEvent.data, Charsets.UTF_8).trim()
            getSharedPreferences("gemini_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("watch_color_theme", theme)
                .putString("app_theme_mode", if (theme == "light") "light" else "skeuo")
                .apply()
            Log.d(TAG, "Đã nhận lệnh đổi màu đồng hồ: $theme")
            val intent = Intent("com.oppowatch.gemini.WATCH_THEME_CHANGED").apply {
                putExtra("theme", theme)
                setPackage(packageName)
            }
            sendBroadcast(intent)
            notifyVibrate(longArrayOf(0, 80))
        }
    }
}
