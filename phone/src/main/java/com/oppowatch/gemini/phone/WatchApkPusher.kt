package com.oppowatch.gemini.phone

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.Collections
import java.util.Locale
import java.util.UUID
import kotlin.concurrent.thread

object WatchApkPusher {
    private const val TAG = "WatchApkPusher"
    private const val CHANNEL_PATH = "/watch_update_apk"

    interface PushCallback {
        fun onStatus(message: String) {}
        fun onProgress(stage: String, percentage: Int)
        fun onSuccess()
        fun onError(error: String)
    }

    fun pushApkToWatch(context: Context, apkFile: File, callback: PushCallback) {
        thread(name = "WatchApkPushThread") {
            try {
                if (!apkFile.exists() || apkFile.length() == 0L) {
                    callback.onError("File APK đồng hồ không tồn tại hoặc rỗng!")
                    return@thread
                }

                Log.d(TAG, "Đang tìm kiếm đồng hồ kết nối qua Wearable Data Layer...")
                val nodeClient = Wearable.getNodeClient(context)
                val nodes = Tasks.await(nodeClient.connectedNodes)

                if (nodes.isEmpty()) {
                    callback.onError("Không tìm thấy đồng hồ OPPO Watch đang kết nối!")
                    return@thread
                }

                val targetNode = nodes[0]
                Log.d(TAG, "Đã tìm thấy đồng hồ: ${targetNode.displayName} (${targetNode.id})")

                // 1. Thử gửi file siêu tốc qua Wi-Fi trước (tự động ghép nối với đồng hồ)
                val wifiSuccess = tryWifiTransfer(context, targetNode.id, apkFile, callback)
                if (wifiSuccess) {
                    Log.i(TAG, "Đã gửi APK sang đồng hồ thành công qua Wi-Fi!")
                    callback.onSuccess()
                    return@thread
                }

                // 2. Nếu Wi-Fi không khả dụng (chưa phát hotspot / chưa cùng Wi-Fi), tự động fallback sang Bluetooth Channel
                Log.i(TAG, "Chuyển sang cơ chế truyền qua Bluetooth Channel...")
                callback.onStatus("Đang truyền APK qua kết nối Bluetooth...")
                pushViaBluetooth(context, targetNode.id, apkFile, callback)

            } catch (e: Exception) {
                Log.e(TAG, "Lỗi khi đẩy APK sang đồng hồ", e)
                callback.onError("Lỗi truyền file sang đồng hồ: ${e.localizedMessage}")
            }
        }
    }

    private fun tryWifiTransfer(
        context: Context,
        nodeId: String,
        apkFile: File,
        callback: PushCallback
    ): Boolean {
        var serverSocket: ServerSocket? = null
        try {
            callback.onStatus("Đang tự động ghép nối Wi-Fi với đồng hồ...")
            val localIps = getCandidateIpAddresses()
            Log.d(TAG, "Candidate IPs for Wi-Fi transfer: $localIps")

            serverSocket = ServerSocket(0)
            serverSocket.soTimeout = 4500 // Chờ đồng hồ kết nối tối đa 4.5s
            val localPort = serverSocket.localPort
            val token = UUID.randomUUID().toString().substring(0, 8)

            val payload = JSONObject().apply {
                put("action", "wifi_apk_transfer")
                put("ips", JSONArray(localIps))
                put("port", localPort)
                put("size", apkFile.length())
                put("token", token)
            }.toString()

            // Gửi tin nhắn bắt tay qua Bluetooth MessageClient
            val messageClient = Wearable.getMessageClient(context)
            Tasks.await(
                messageClient.sendMessage(
                    nodeId,
                    "/watch_wifi_transfer_request",
                    payload.toByteArray(Charsets.UTF_8)
                )
            )
            Log.d(TAG, "Đã gửi yêu cầu ghép nối Wi-Fi tới đồng hồ trên port $localPort")

            // Chờ đồng hồ socket connect
            val clientSocket: Socket = try {
                serverSocket.accept()
            } catch (_: SocketTimeoutException) {
                Log.w(TAG, "Đồng hồ không kết nối Wi-Fi trong 4.5s (chưa bật Wi-Fi/Hotspot)")
                return false
            }

            clientSocket.use { socket ->
                socket.tcpNoDelay = true
                socket.sendBufferSize = 131072

                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                val writer = PrintWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8), true)

                // Kiểm tra handshake
                val handshakeLine = reader.readLine()
                if (handshakeLine == null || !handshakeLine.startsWith("READY:$token")) {
                    Log.w(TAG, "Handshake token không hợp lệ: $handshakeLine")
                    return false
                }

                Log.i(TAG, "✓ Ghép nối Wi-Fi thành công với đồng hồ tại ${socket.inetAddress.hostAddress}!")
                callback.onStatus("✓ Đã ghép nối Wi-Fi! Đang truyền siêu tốc...")

                // Gửi xác nhận bắt đầu truyền
                writer.println("START")

                // Truyền dữ liệu file APK
                val totalBytes = apkFile.length()
                var bytesSent = 0L
                val buffer = ByteArray(65536)
                val out = socket.getOutputStream()
                val startTime = System.currentTimeMillis()

                FileInputStream(apkFile).use { input ->
                    var read: Int
                    var lastReportedPercent = -1
                    while (input.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                        bytesSent += read

                        val percent = ((bytesSent * 100) / totalBytes).toInt()
                        if (percent != lastReportedPercent) {
                            lastReportedPercent = percent
                            val elapsedSec = maxOf(0.1, (System.currentTimeMillis() - startTime) / 1000.0)
                            val speedMBps = (bytesSent / (1024.0 * 1024.0)) / elapsedSec
                            callback.onProgress(
                                String.format(Locale.US, "Gửi Wi-Fi (%.1f MB/s)", speedMBps),
                                percent
                            )
                        }
                    }
                    out.flush()
                }

                // Chờ đồng hồ xác nhận nhận xong
                val doneLine = reader.readLine()
                Log.i(TAG, "Đồng hồ phản hồi hoàn tất: $doneLine")

                val totalTime = (System.currentTimeMillis() - startTime) / 1000.0
                val avgSpeed = (totalBytes / (1024.0 * 1024.0)) / maxOf(0.1, totalTime)
                callback.onStatus(
                    String.format(
                        Locale.US,
                        "✓ Truyền Wi-Fi hoàn tất trong %.1fs (%.1f MB/s)! Đang mở cài đặt trên đồng hồ.",
                        totalTime,
                        avgSpeed
                    )
                )

                // Gửi thêm trigger cài đặt đề phòng
                try {
                    Tasks.await(messageClient.sendMessage(nodeId, "/watch_update_trigger_install", ByteArray(0)))
                } catch (_: Exception) {}

                return true
            }

        } catch (e: Exception) {
            Log.w(TAG, "Quá trình truyền Wi-Fi thất bại: ${e.message}")
            return false
        } finally {
            try {
                serverSocket?.close()
            } catch (_: Exception) {}
        }
    }

    private fun pushViaBluetooth(
        context: Context,
        nodeId: String,
        apkFile: File,
        callback: PushCallback
    ) {
        val channelClient = Wearable.getChannelClient(context)
        Log.d(TAG, "Đang mở Bluetooth channel $CHANNEL_PATH...")
        val channel = Tasks.await(channelClient.openChannel(nodeId, CHANNEL_PATH))

        val outputStream = Tasks.await(channelClient.getOutputStream(channel))
        val totalBytes = apkFile.length()
        var bytesSent = 0L
        val buffer = ByteArray(65536)

        FileInputStream(apkFile).use { input ->
            var read: Int
            var lastReportedPercent = -1
            while (input.read(buffer).also { read = it } != -1) {
                outputStream.write(buffer, 0, read)
                bytesSent += read
                val percent = ((bytesSent * 100) / totalBytes).toInt()
                if (percent != lastReportedPercent) {
                    lastReportedPercent = percent
                    callback.onProgress("Gửi Bluetooth", percent)
                }
            }
            outputStream.flush()
        }

        outputStream.close()
        channelClient.close(channel)
        Log.d(TAG, "Đã truyền xong 100% file APK qua Bluetooth!")

        // Gửi tín hiệu kích hoạt cài đặt
        val messageClient = Wearable.getMessageClient(context)
        Tasks.await(messageClient.sendMessage(nodeId, "/watch_update_trigger_install", ByteArray(0)))
        callback.onSuccess()
    }

    private fun getCandidateIpAddresses(): List<String> {
        val ips = LinkedHashSet<String>()
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress
                        if (!host.isNullOrEmpty() && host != "127.0.0.1") {
                            ips.add(host)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Lỗi lấy IP: ${e.message}")
        }
        // Điểm phát sóng Android thông dụng (Hotspot Tethering)
        ips.add("192.168.43.1")
        return ips.toList()
    }
}
