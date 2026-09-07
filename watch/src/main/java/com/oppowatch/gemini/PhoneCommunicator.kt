package com.oppowatch.gemini

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import kotlin.concurrent.thread

object PhoneCommunicator {

    private const val TAG = "PhoneCommunicator"
    const val PATH_TTS = "/gemini_tts_payload"
    const val PATH_ERROR_LOG = "/gemini_error_log"
    const val PATH_REPLY_MESSAGE = "/gemini_reply_message"
    const val PATH_TASK = "/gemini_task"
    const val PATH_REMINDER = "/gemini_reminder"
    const val PATH_CLIPBOARD = "/gemini_clipboard"
    const val PATH_WATCH_ADB_INFO = "/watch_adb_info"
    const val PATH_MEDIA_CONTROL = "/gemini_media_control"

    fun sendReplyMessageToPhone(context: Context, recipient: String, message: String) {
        val nodeClient = Wearable.getNodeClient(context)
        val messageClient = Wearable.getMessageClient(context)

        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            if (nodes.isEmpty()) {
                Log.w(TAG, "No connected phone found to send reply message")
                return@addOnSuccessListener
            }
            val payload = org.json.JSONObject().apply {
                put("recipient", recipient)
                put("message", message)
                put("timestamp", System.currentTimeMillis())
            }.toString()

            val bytes = payload.toByteArray(Charsets.UTF_8)
            for (node in nodes) {
                messageClient.sendMessage(node.id, PATH_REPLY_MESSAGE, bytes)
                    .addOnSuccessListener {
                        Log.d(TAG, "Sent reply message command to phone node: ${node.displayName}")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed sending reply message command: ${e.message}")
                    }
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed finding connected nodes for reply message: ${e.message}")
        }
    }

    fun sendTextToPhone(context: Context, text: String) {
        val nodeClient = Wearable.getNodeClient(context)
        val messageClient = Wearable.getMessageClient(context)

        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            if (nodes.isEmpty()) {
                Log.w(TAG, "No connected phone found to send TTS")
                return@addOnSuccessListener
            }
            val bytes = text.toByteArray(Charsets.UTF_8)
            for (node in nodes) {
                messageClient.sendMessage(node.id, PATH_TTS, bytes)
                    .addOnSuccessListener {
                        Log.d(TAG, "Sent TTS payload to phone node: ${node.displayName}")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed sending TTS payload: ${e.message}")
                    }
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed finding connected nodes: ${e.message}")
        }
    }

    fun sendErrorLogToPhone(context: Context, errorJson: String) {
        val nodeClient = Wearable.getNodeClient(context)
        val messageClient = Wearable.getMessageClient(context)

        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            if (nodes.isEmpty()) {
                Log.w(TAG, "No connected phone found to send error log")
                return@addOnSuccessListener
            }
            val bytes = errorJson.toByteArray(Charsets.UTF_8)
            for (node in nodes) {
                messageClient.sendMessage(node.id, PATH_ERROR_LOG, bytes)
                    .addOnSuccessListener {
                        Log.d(TAG, "Sent error log payload to phone node: ${node.displayName}")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed sending error log payload: ${e.message}")
                    }
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed finding connected nodes for error log: ${e.message}")
        }
    }

    fun sendTaskToPhone(context: Context, title: String, notes: String) {
        val nodeClient = Wearable.getNodeClient(context)
        val messageClient = Wearable.getMessageClient(context)

        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            if (nodes.isEmpty()) return@addOnSuccessListener
            val payload = org.json.JSONObject().apply {
                put("title", title)
                put("notes", notes)
                put("timestamp", System.currentTimeMillis())
            }.toString()
            val bytes = payload.toByteArray(Charsets.UTF_8)
            for (node in nodes) {
                messageClient.sendMessage(node.id, PATH_TASK, bytes)
            }
        }
    }

    fun sendReminderToPhone(context: Context, message: String, delaySeconds: Int) {
        val nodeClient = Wearable.getNodeClient(context)
        val messageClient = Wearable.getMessageClient(context)

        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            if (nodes.isEmpty()) return@addOnSuccessListener
            val payload = org.json.JSONObject().apply {
                put("message", message)
                put("delay_seconds", delaySeconds)
                put("timestamp", System.currentTimeMillis())
            }.toString()
            val bytes = payload.toByteArray(Charsets.UTF_8)
            for (node in nodes) {
                messageClient.sendMessage(node.id, PATH_REMINDER, bytes)
            }
        }
    }

    fun sendClipboardToPhone(context: Context, text: String) {
        val nodeClient = Wearable.getNodeClient(context)
        val messageClient = Wearable.getMessageClient(context)

        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            if (nodes.isEmpty()) return@addOnSuccessListener
            val payload = org.json.JSONObject().apply {
                put("text", text)
                put("timestamp", System.currentTimeMillis())
            }.toString()
            val bytes = payload.toByteArray(Charsets.UTF_8)
            for (node in nodes) {
                messageClient.sendMessage(node.id, PATH_CLIPBOARD, bytes)
            }
        }
    }

    fun sendMediaControlToPhone(context: Context, command: String, query: String = "") {
        val nodeClient = Wearable.getNodeClient(context)
        val messageClient = Wearable.getMessageClient(context)

        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            if (nodes.isEmpty()) return@addOnSuccessListener
            val payload = org.json.JSONObject().apply {
                put("command", command)
                put("query", query)
                put("timestamp", System.currentTimeMillis())
            }.toString()
            val bytes = payload.toByteArray(Charsets.UTF_8)
            for (node in nodes) {
                messageClient.sendMessage(node.id, PATH_MEDIA_CONTROL, bytes)
            }
        }
    }

    /**
     * Tự động phát hiện IP Wi-Fi và trạng thái cổng ADB (5555) trên đồng hồ
     * Sau đó gửi qua Bluetooth sang điện thoại để tự kết nối Wireless ADB
     */
    fun sendWatchAdbInfoToPhone(context: Context) {
        thread(name = "WatchAdbDetectorThread") {
            try {
                var watchIp: String? = null
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces != null && interfaces.hasMoreElements()) {
                    val intf = interfaces.nextElement()
                    val addrs = intf.inetAddresses
                    while (addrs.hasMoreElements()) {
                        val addr = addrs.nextElement()
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            val host = addr.hostAddress ?: ""
                            if (host.isNotEmpty() && !host.startsWith("127.")) {
                                watchIp = host
                                break
                            }
                        }
                    }
                    if (watchIp != null) break
                }

                if (watchIp.isNullOrEmpty()) {
                    Log.d(TAG, "Watch has no active IPv4 Wi-Fi connection.")
                    return@thread
                }

                // Kiểm tra xem cổng 5555 có đang lắng nghe (đã bật Gỡ lỗi qua Wi-Fi) không
                var isAdbOpen = false
                try {
                    val socket = Socket()
                    socket.connect(InetSocketAddress("127.0.0.1", 5555), 400)
                    socket.close()
                    isAdbOpen = true
                } catch (_: Exception) {
                    try {
                        val socket = Socket()
                        socket.connect(InetSocketAddress(watchIp, 5555), 400)
                        socket.close()
                        isAdbOpen = true
                    } catch (_: Exception) {
                        isAdbOpen = false
                    }
                }

                Log.d(TAG, "Detected Watch ADB: $watchIp:5555 (ready=$isAdbOpen). Syncing to phone...")

                val payload = org.json.JSONObject().apply {
                    put("ip", watchIp)
                    put("port", 5555)
                    put("adb_ready", isAdbOpen)
                    put("timestamp", System.currentTimeMillis())
                }.toString()

                val bytes = payload.toByteArray(Charsets.UTF_8)
                val nodeClient = Wearable.getNodeClient(context)
                val messageClient = Wearable.getMessageClient(context)

                nodeClient.connectedNodes.addOnSuccessListener { nodes ->
                    for (node in nodes) {
                        messageClient.sendMessage(node.id, PATH_WATCH_ADB_INFO, bytes)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in sendWatchAdbInfoToPhone: ${e.message}")
            }
        }
    }
}