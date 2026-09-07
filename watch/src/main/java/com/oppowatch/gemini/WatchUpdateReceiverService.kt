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
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import org.json.JSONObject
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
                    FileOutputStream(updateFile).use { outputStream ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                        }
                        outputStream.flush()
                    }

                    Log.i(TAG, "APK received successfully (${updateFile.length()} bytes). Triggering installation...")
                    triggerApkInstall(updateFile)

                } catch (e: Exception) {
                    Log.e(TAG, "Failed to receive APK stream: ${e.message}", e)
                } finally {
                    try {
                        inputStream.close()
                    } catch (_: Exception) {}
                    channelClient.close(channel)
                }
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to get input stream from channel: ${e.message}", e)
        }
    }

    private fun triggerApkInstall(apkFile: File) {
        try {
            val contentUri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            notifyVibrate(longArrayOf(0, 150, 100, 150))
            startActivity(installIntent)
            Log.i(TAG, "Install prompt activity launched for ${apkFile.name}")

        } catch (e: Exception) {
            Log.e(TAG, "Cannot launch installer: ${e.message}", e)
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        super.onDataChanged(dataEvents)
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                val item = event.dataItem
                if (item.uri.path == "/gemini_theme_config") {
                    try {
                        val dataMap = DataMapItem.fromDataItem(item).dataMap
                        val style = dataMap.getString("style", "skeuo")
                        val mode = dataMap.getString("mode", "dark")
                        applyThemeSettings(style, mode)
                    } catch (e: Exception) {
                        Log.e(TAG, "Lỗi đọc theme từ DataClient: ${e.message}")
                    }
                }
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        Log.d(TAG, "onMessageReceived: path=${messageEvent.path}")

        if (messageEvent.path == "/ota_watch_apk") {
            thread(name = "WatchApkStreamThread") {
                try {
                    val updateFile = File(cacheDir, "gemini_watch_update.apk")
                    if (updateFile.exists()) {
                        updateFile.delete()
                    }
                    FileOutputStream(updateFile).use { fos ->
                        fos.write(messageEvent.data)
                        fos.flush()
                    }
                    Log.i(TAG, "Nhận APK qua MessageClient thành công: ${updateFile.length()} bytes")
                    triggerApkInstall(updateFile)
                } catch (e: Exception) {
                    Log.e(TAG, "Lỗi nhận APK: ${e.message}", e)
                }
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
            val raw = String(messageEvent.data, Charsets.UTF_8).trim()
            var style = "skeuo"
            var mode = "dark"
            if (raw.startsWith("{") && raw.endsWith("}")) {
                try {
                    val json = JSONObject(raw)
                    style = json.optString("style", "skeuo")
                    mode = json.optString("mode", "dark")
                } catch (_: Exception) {}
            } else if (raw.contains("_")) {
                val parts = raw.split("_")
                style = parts[0]
                mode = if (parts.size > 1) parts[1] else "dark"
            } else if (raw == "light" || raw == "ceramic_light") {
                style = "skeuo"
                mode = "light"
            } else if (raw == "glass" || raw == "liquid_glass") {
                style = "glass"
                mode = "dark"
            } else if (raw == "material") {
                style = "material"
                mode = "dark"
            } else {
                style = "skeuo"
                mode = "dark"
            }
            applyThemeSettings(style, mode)
        } else if (messageEvent.path == "/watch_color_theme") {
            val mode = String(messageEvent.data, Charsets.UTF_8).trim()
            val prefs = getSharedPreferences("gemini_prefs", Context.MODE_PRIVATE)
            val style = prefs.getString("app_theme_style", "skeuo") ?: "skeuo"
            applyThemeSettings(style, mode)
        }
    }

    private fun applyThemeSettings(style: String, mode: String) {
        val combined = "${style}_${mode}"
        val prefs = getSharedPreferences("gemini_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("app_theme_style", style)
            .putString("app_theme_mode", mode)
            .putString("app_theme_combined", combined)
            .putString("watch_color_theme", mode)
            .apply()

        Log.i(TAG, "Đã áp dụng theme: style=$style, mode=$mode -> combined=$combined")
        val intent = Intent("com.oppowatch.gemini.WATCH_THEME_CHANGED").apply {
            putExtra("style", style)
            putExtra("mode", mode)
            putExtra("theme", combined)
            setPackage(packageName)
        }
        sendBroadcast(intent)
        notifyVibrate(longArrayOf(0, 80))
    }

    private fun notifyVibrate(pattern: LongArray) {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }
}
