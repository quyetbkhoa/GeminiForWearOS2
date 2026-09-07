package com.oppowatch.gemini

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
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
import java.io.BufferedReader
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.LinkedHashSet
import java.util.Locale
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
            if (!apkFile.exists() || apkFile.length() < 1000L) {
                Log.e(TAG, "File APK không tồn tại hoặc quá nhỏ (${apkFile.length()} bytes)")
                return
            }

            // Kiểm tra header zip (PK..)
            val isValidZip = try {
                FileInputStream(apkFile).use { fis ->
                    val header = ByteArray(4)
                    fis.read(header) == 4 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()
                }
            } catch (_: Exception) { false }

            if (!isValidZip) {
                Log.e(TAG, "File APK nhận được bị lỗi định dạng (không phải file ZIP/APK hợp lệ)!")
                return
            }

            apkFile.setReadable(true, false)

            val contentUri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            }

            // Cấp quyền rõ ràng cho package xử lý
            val resInfoList = packageManager.queryIntentActivities(installIntent, 0)
            for (resolveInfo in resInfoList) {
                val pkg = resolveInfo.activityInfo.packageName
                grantUriPermission(pkg, contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
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

        if (messageEvent.path == "/watch_wifi_transfer_request") {
            handleWifiTransferRequest(messageEvent)
        } else if (messageEvent.path == "/watch_update_trigger_install") {
            val updateFile = File(cacheDir, "gemini_watch_update.apk")
            if (updateFile.exists() && updateFile.length() > 0) {
                Log.i(TAG, "Nhận tín hiệu trigger cài đặt APK từ điện thoại (${updateFile.length()} bytes)")
                triggerApkInstall(updateFile)
            }
        } else if (messageEvent.path == "/ota_watch_apk") {
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

    private fun handleWifiTransferRequest(messageEvent: MessageEvent) {
        val rawJson = String(messageEvent.data, Charsets.UTF_8)
        Log.i(TAG, "Nhận yêu cầu ghép nối Wi-Fi từ điện thoại: $rawJson")

        val json = try {
            JSONObject(rawJson)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi parse JSON Wi-Fi request: ${e.message}")
            return
        }

        val ipsArray = json.optJSONArray("ips")
        val port = json.optInt("port", 0)
        val fileSize = json.optLong("size", 0L)
        val token = json.optString("token", "")

        if (port <= 0 || token.isEmpty()) {
            Log.e(TAG, "Thông tin Wi-Fi không hợp lệ: port=$port, token=$token")
            return
        }

        thread(name = "WatchWifiReceiverThread") {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            val wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Gemini:WifiUpdateWakeLock")
            wakeLock?.acquire(90000L) // Tối đa 90s

            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val wifiLock = wifiManager?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Gemini:WifiUpdateLock")
            try {
                wifiLock?.acquire()
            } catch (_: Exception) {}

            try {
                // Thu thập danh sách candidate IP
                val candidateIps = LinkedHashSet<String>()

                // 1. DHCP Gateway IP (khi đồng hồ kết nối vào Hotspot của điện thoại)
                val dhcp = wifiManager?.dhcpInfo
                if (dhcp != null && dhcp.gateway != 0) {
                    val gw = dhcp.gateway
                    val gwIp = String.format(
                        Locale.US,
                        "%d.%d.%d.%d",
                        gw and 0xff,
                        gw shr 8 and 0xff,
                        gw shr 16 and 0xff,
                        gw shr 24 and 0xff
                    )
                    candidateIps.add(gwIp)
                    Log.d(TAG, "Tìm thấy DHCP Gateway IP (Hotspot): $gwIp")
                }

                // 2. Thêm các IP điện thoại gửi sang
                if (ipsArray != null) {
                    for (i in 0 until ipsArray.length()) {
                        val ip = ipsArray.optString(i, "").trim()
                        if (ip.isNotEmpty()) {
                            candidateIps.add(ip)
                        }
                    }
                }

                // 3. Fallback IP Hotspot mặc định
                candidateIps.add("192.168.43.1")

                Log.d(TAG, "Bắt đầu dò kết nối tới các IP: $candidateIps trên port $port")

                var connectedSocket: Socket? = null
                for (targetIp in candidateIps) {
                    try {
                        val testSocket = Socket()
                        testSocket.connect(InetSocketAddress(targetIp, port), 1200) // 1.2s timeout
                        connectedSocket = testSocket
                        Log.i(TAG, "✓ Ghép nối Wi-Fi thành công tới điện thoại tại $targetIp:$port!")
                        break
                    } catch (e: Exception) {
                        Log.d(TAG, "Không kết nối được $targetIp:$port: ${e.message}")
                    }
                }

                if (connectedSocket == null) {
                    Log.w(TAG, "Đồng hồ không thể kết nối tới IP nào qua Wi-Fi (sẽ fallback sang Bluetooth)")
                    return@thread
                }

                connectedSocket.use { socket ->
                    socket.tcpNoDelay = true
                    socket.receiveBufferSize = 131072

                    val dos = java.io.DataOutputStream(socket.getOutputStream())
                    val dis = java.io.DataInputStream(socket.getInputStream())

                    // Gửi 8 bytes token xác nhận
                    val tokenBytes = token.toByteArray(Charsets.UTF_8).copyOf(8)
                    dos.write(tokenBytes)
                    dos.flush()

                    // Nhận Magic 4 bytes ("GEMI")
                    val magic = ByteArray(4)
                    dis.readFully(magic)
                    if (magic[0] != 'G'.code.toByte() || magic[1] != 'E'.code.toByte() ||
                        magic[2] != 'M'.code.toByte() || magic[3] != 'I'.code.toByte()) {
                        Log.e(TAG, "Magic header không hợp lệ từ điện thoại!")
                        dos.writeByte(0)
                        dos.flush()
                        return@use
                    }

                    val streamLength = dis.readLong()
                    Log.i(TAG, "Bắt đầu nhận file APK qua Wi-Fi ($streamLength bytes)...")

                    val updateFile = File(cacheDir, "gemini_watch_update.apk")
                    if (updateFile.exists()) {
                        updateFile.delete()
                    }

                    FileOutputStream(updateFile).use { fos ->
                        val buffer = ByteArray(65536)
                        var remaining = streamLength
                        while (remaining > 0L) {
                            val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                            val read = dis.read(buffer, 0, toRead)
                            if (read == -1) break
                            fos.write(buffer, 0, read)
                            remaining -= read
                        }
                        fos.flush()
                    }

                    if (updateFile.length() == streamLength) {
                        dos.writeByte(1)
                        dos.flush()
                        Log.i(TAG, "✓ Đã nhận trọn vẹn 100% APK qua Wi-Fi (${updateFile.length()} bytes)!")
                        notifyVibrate(longArrayOf(0, 100, 80, 150))
                        triggerApkInstall(updateFile)
                    } else {
                        Log.e(TAG, "File APK bị thiếu bytes: ${updateFile.length()} / $streamLength")
                        dos.writeByte(0)
                        dos.flush()
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Lỗi trong tiến trình nhận APK qua Wi-Fi: ${e.message}", e)
            } finally {
                try {
                    if (wifiLock?.isHeld == true) wifiLock.release()
                } catch (_: Exception) {}
                try {
                    if (wakeLock?.isHeld == true) wakeLock.release()
                } catch (_: Exception) {}
            }
        }
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
