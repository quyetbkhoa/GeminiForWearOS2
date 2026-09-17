package com.oppowatch.gemini.phone

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import kotlin.concurrent.thread

/**
 * WatchDirectCommunicator: Bộ điều phối đồng bộ đa tầng từ điện thoại sang OPPO Watch.
 * 1. Kênh Bluetooth RFCOMM SPP (chạy thẳng giữa 2 máy đã ghép đôi, không cần Google Play Services Wearable).
 * 2. Kênh Wi-Fi TCP Socket nội bộ (Port 8765).
 * 3. Kênh Google Play Services Wearable Data Layer (dự phòng).
 */
object WatchDirectCommunicator {

    private const val TAG = "WatchDirectCommunicator"
    val SYNC_UUID: UUID = UUID.fromString("e0cbf070-0701-4f1e-8e8e-c19b8a3e7b10")
    const val TCP_PORT = 8765

    interface SyncCallback {
        fun onSuccess(channel: String)
        fun onFailure(error: String)
    }

    fun syncModel(context: Context, modelId: String, modelName: String, callback: SyncCallback? = null) {
        thread(name = "SyncModelThread") {
            val payload = JSONObject().apply {
                put("action", "SET_MODEL")
                put("model", modelId)
                put("timestamp", System.currentTimeMillis())
            }.toString()

            var syncedChannel: String? = null

            // 1. Thử qua Bluetooth RFCOMM SPP
            if (tryBluetoothSync(context, payload)) {
                syncedChannel = "Bluetooth SPP"
                Log.i(TAG, "✓ Đã đồng bộ model '$modelId' sang đồng hồ qua Bluetooth SPP")
            }

            // 2. Thử qua Wi-Fi TCP Socket (nếu Bluetooth chưa được hoặc để chắc chắn)
            val watchIp = getSavedWatchIp(context)
            if (syncedChannel == null && watchIp.isNotEmpty()) {
                if (tryTcpSync(watchIp, payload)) {
                    syncedChannel = "Wi-Fi Socket ($watchIp)"
                    Log.i(TAG, "✓ Đã đồng bộ model '$modelId' sang đồng hồ qua Wi-Fi TCP Socket")
                }
            }

            // 3. Đẩy thêm qua Google Play Services Wearable Data Layer (dự phòng)
            tryWearableSync(context, "/gemini_model_sync", modelId)

            if (syncedChannel != null) {
                callback?.onSuccess(syncedChannel)
            } else {
                callback?.onFailure("Không thể kết nối trực tiếp tới đồng hồ qua Bluetooth hoặc Wi-Fi")
            }
        }
    }

    fun syncApiKey(context: Context, apiKey: String, callback: SyncCallback? = null) {
        thread(name = "SyncApiKeyThread") {
            val payload = JSONObject().apply {
                put("action", "SET_API_KEY")
                put("key", apiKey)
                put("timestamp", System.currentTimeMillis())
            }.toString()

            var syncedChannel: String? = null

            if (tryBluetoothSync(context, payload)) {
                syncedChannel = "Bluetooth SPP"
                Log.i(TAG, "✓ Đã đồng bộ API Key sang đồng hồ qua Bluetooth SPP")
            }

            val watchIp = getSavedWatchIp(context)
            if (syncedChannel == null && watchIp.isNotEmpty()) {
                if (tryTcpSync(watchIp, payload)) {
                    syncedChannel = "Wi-Fi Socket ($watchIp)"
                    Log.i(TAG, "✓ Đã đồng bộ API Key sang đồng hồ qua Wi-Fi TCP Socket")
                }
            }

            tryWearableSync(context, "/gemini_api_key_sync", apiKey)

            if (syncedChannel != null) {
                callback?.onSuccess(syncedChannel)
            } else {
                callback?.onFailure("Không thể kết nối trực tiếp tới đồng hồ qua Bluetooth hoặc Wi-Fi")
            }
        }
    }

    private fun tryBluetoothSync(context: Context, jsonPayload: String): Boolean {
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return false
            if (!adapter.isEnabled) return false

            val bonded = try {
                adapter.bondedDevices
            } catch (e: SecurityException) {
                Log.w(TAG, "Thiếu quyền Bluetooth: ${e.message}")
                return false
            } ?: return false

            // Tìm đồng hồ trong danh sách thiết bị đã ghép đôi
            val watchCandidates = bonded.filter { device ->
                val name = device.name ?: ""
                name.contains("Watch", ignoreCase = true) ||
                name.contains("OPPO", ignoreCase = true) ||
                name.contains("Quýt", ignoreCase = true) ||
                name.contains("Beluga", ignoreCase = true)
            }

            val targets = if (watchCandidates.isNotEmpty()) watchCandidates else bonded.toList()

            for (device in targets) {
                if (sendViaBluetoothSocket(device, jsonPayload)) {
                    return true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "tryBluetoothSync failed: ${e.message}")
        }
        return false
    }

    private fun sendViaBluetoothSocket(device: BluetoothDevice, jsonPayload: String): Boolean {
        var socket: BluetoothSocket? = null
        try {
            Log.d(TAG, "Đang mở Bluetooth RFCOMM tới ${device.name} (${device.address})...")
            socket = device.createRfcommSocketToServiceRecord(SYNC_UUID)
            socket.connect()

            val writer = PrintWriter(OutputStreamWriter(socket.outputStream, Charsets.UTF_8), true)
            val reader = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8))

            writer.println(jsonPayload)
            writer.flush()

            val response = reader.readLine()
            Log.d(TAG, "Nhận phản hồi từ đồng hồ qua BT: $response")
            if (response != null && response.contains("SUCCESS")) {
                return true
            }
        } catch (e: Exception) {
            Log.d(TAG, "Kết nối Bluetooth tới ${device.name ?: device.address} không thành công: ${e.message}")
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
        return false
    }

    private fun tryTcpSync(host: String, jsonPayload: String): Boolean {
        var socket: Socket? = null
        try {
            Log.d(TAG, "Đang kết nối TCP tới $host:$TCP_PORT...")
            socket = Socket()
            socket.connect(InetSocketAddress(host, TCP_PORT), 1500)

            val writer = PrintWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8), true)
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))

            writer.println(jsonPayload)
            writer.flush()

            val response = reader.readLine()
            Log.d(TAG, "Nhận phản hồi từ đồng hồ qua TCP: $response")
            if (response != null && response.contains("SUCCESS")) {
                return true
            }
        } catch (e: Exception) {
            Log.d(TAG, "Kết nối TCP tới $host:$TCP_PORT thất bại: ${e.message}")
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
        return false
    }

    private fun tryWearableSync(context: Context, path: String, data: String) {
        try {
            Wearable.getNodeClient(context).connectedNodes.addOnSuccessListener { nodes ->
                for (node in nodes) {
                    Wearable.getMessageClient(context).sendMessage(
                        node.id,
                        path,
                        data.toByteArray(Charsets.UTF_8)
                    )
                }
            }.addOnFailureListener { e ->
                Log.d(TAG, "Wearable.API không khả dụng: ${e.message}")
            }
        } catch (e: Exception) {
            Log.d(TAG, "tryWearableSync exception: ${e.message}")
        }
    }

    fun saveWatchIp(context: Context, ip: String) {
        if (ip.isNotEmpty() && !ip.startsWith("127.")) {
            context.getSharedPreferences("gemini_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("last_watch_ip", ip)
                .apply()
        }
    }

    fun getSavedWatchIp(context: Context): String {
        val prefs1 = context.getSharedPreferences("gemini_companion_prefs", Context.MODE_PRIVATE)
        val ip1 = prefs1.getString("saved_watch_adb_ip", "") ?: ""
        if (ip1.isNotEmpty() && !ip1.startsWith("127.")) return ip1

        val prefs2 = context.getSharedPreferences("gemini_prefs", Context.MODE_PRIVATE)
        return prefs2.getString("last_watch_ip", "") ?: ""
    }
}
