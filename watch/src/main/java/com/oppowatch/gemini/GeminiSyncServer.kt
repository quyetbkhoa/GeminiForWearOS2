package com.oppowatch.gemini

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * GeminiSyncServer: Máy chủ đồng bộ trực tiếp trên đồng hồ OPPO Watch.
 * Chạy 2 kênh song song:
 * 1. Bluetooth RFCOMM SPP (UUID riêng) - Hoạt động độc lập hoàn toàn với Google Play Services Wearable.
 * 2. Local Wi-Fi TCP Socket (Port 8765) - Dự phòng siêu tốc khi cùng mạng Wi-Fi.
 */
object GeminiSyncServer {

    private const val TAG = "GeminiSyncServer"
    val SYNC_UUID: UUID = UUID.fromString("e0cbf070-0701-4f1e-8e8e-c19b8a3e7b10")
    const val TCP_PORT = 8765
    private const val SERVICE_NAME = "GeminiWatchSync"

    const val ACTION_MODEL_CHANGED = "com.oppowatch.gemini.MODEL_CHANGED"
    const val ACTION_API_KEY_CHANGED = "com.oppowatch.gemini.API_KEY_CHANGED"
    const val EXTRA_MODEL = "extra_model"

    val SUPPORTED_MODELS = setOf(
        "gemini-3.6-flash",
        "gemini-3.8-flash",
        "gemini-3.7-flash",
        "gemini-3.5-flash-lite",
        "gemini-3.1-pro-preview"
    )

    private val isRunning = AtomicBoolean(false)
    private var btServerSocket: BluetoothServerSocket? = null
    private var tcpServerSocket: ServerSocket? = null
    private var btThread: Thread? = null
    private var tcpThread: Thread? = null
    private var appContext: Context? = null

    fun start(context: Context) {
        appContext = context.applicationContext
        if (isRunning.compareAndSet(false, true)) {
            Log.d(TAG, "Khởi động GeminiSyncServer...")
            startBluetoothServer()
            startTcpServer()
        }
    }

    fun stop() {
        if (isRunning.compareAndSet(true, false)) {
            Log.d(TAG, "Dừng GeminiSyncServer...")
            try { btServerSocket?.close() } catch (_: Exception) {}
            btServerSocket = null
            try { tcpServerSocket?.close() } catch (_: Exception) {}
            tcpServerSocket = null

            btThread?.interrupt()
            btThread = null
            tcpThread?.interrupt()
            tcpThread = null
        }
    }

    private fun startBluetoothServer() {
        btThread = thread(name = "GeminiBtSyncThread") {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter == null || !adapter.isEnabled) {
                Log.w(TAG, "Bluetooth không khả dụng hoặc chưa bật")
                return@thread
            }

            while (isRunning.get()) {
                try {
                    Log.d(TAG, "Mở BluetoothServerSocket (UUID: $SYNC_UUID)...")
                    btServerSocket = adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SYNC_UUID)
                    val socket = btServerSocket?.accept()
                    btServerSocket?.close()
                    btServerSocket = null

                    if (socket != null) {
                        Log.d(TAG, "✓ Nhận kết nối Bluetooth từ ${socket.remoteDevice?.name ?: "Unknown"}")
                        handleSocketConnection(socket.inputStream, socket.outputStream, "BT")
                        try { socket.close() } catch (_: Exception) {}
                    }
                } catch (e: Exception) {
                    if (isRunning.get()) {
                        Log.w(TAG, "BluetoothServer loop error: ${e.message}")
                        try { Thread.sleep(2000) } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    private fun startTcpServer() {
        tcpThread = thread(name = "GeminiTcpSyncThread") {
            try {
                tcpServerSocket = ServerSocket()
                tcpServerSocket?.reuseAddress = true
                tcpServerSocket?.bind(InetSocketAddress(TCP_PORT))
                Log.d(TAG, "✓ TCP Server lắng nghe tại port $TCP_PORT...")

                while (isRunning.get()) {
                    try {
                        val client = tcpServerSocket?.accept()
                        if (client != null) {
                            thread(name = "GeminiTcpClient") {
                                try {
                                    handleSocketConnection(client.getInputStream(), client.getOutputStream(), "TCP")
                                } finally {
                                    try { client.close() } catch (_: Exception) {}
                                }
                            }
                        }
                    } catch (e: Exception) {
                        if (isRunning.get()) {
                            try { Thread.sleep(1000) } catch (_: Exception) {}
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Không thể mở TCP Server port $TCP_PORT: ${e.message}")
            }
        }
    }

    private fun handleSocketConnection(input: java.io.InputStream, output: java.io.OutputStream, channelName: String) {
        val context = appContext ?: return
        try {
            val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8))
            val writer = PrintWriter(OutputStreamWriter(output, Charsets.UTF_8), true)

            val line = reader.readLine() ?: return
            Log.d(TAG, "[$channelName] Nhận packet: $line")

            val json = JSONObject(line)
            val action = json.optString("action", "")
            val response = JSONObject()

            val prefs = context.getSharedPreferences("gemini_prefs", Context.MODE_PRIVATE)

            when (action) {
                "SET_MODEL" -> {
                    var model = json.optString("model", "gemini-3.6-flash").trim()
                    if (!SUPPORTED_MODELS.contains(model)) {
                        model = "gemini-3.6-flash"
                    }
                    prefs.edit().putString("selected_model", model).apply()
                    Log.i(TAG, "[$channelName] Đã lưu model: $model")

                    // Gửi broadcast nội bộ cho MainActivity cập nhật UI ngay lập tức
                    val broadcast = Intent(ACTION_MODEL_CHANGED).apply {
                        putExtra(EXTRA_MODEL, model)
                        setPackage(context.packageName)
                    }
                    context.sendBroadcast(broadcast)

                    vibrateTick(context, longArrayOf(0, 70, 50, 70))
                    response.put("status", "SUCCESS")
                    response.put("model", model)
                }

                "SET_API_KEY" -> {
                    val key = json.optString("key", "").trim()
                    if (key.isNotEmpty()) {
                        prefs.edit().putString("custom_api_key", key).apply()
                        Log.i(TAG, "[$channelName] Đã lưu custom API key thành công")
                    } else {
                        prefs.edit().remove("custom_api_key").apply()
                        Log.i(TAG, "[$channelName] Đã gỡ custom API key")
                    }
                    val broadcast = Intent(ACTION_API_KEY_CHANGED).apply {
                        setPackage(context.packageName)
                    }
                    context.sendBroadcast(broadcast)
                    vibrateTick(context, longArrayOf(0, 80, 60, 80))
                    response.put("status", "SUCCESS")
                }

                "GET_STATUS" -> {
                    val currentModel = prefs.getString("selected_model", "gemini-3.6-flash") ?: "gemini-3.6-flash"
                    val hasKey = !prefs.getString("custom_api_key", "").isNullOrEmpty()
                    response.put("status", "SUCCESS")
                    response.put("model", currentModel)
                    response.put("hasApiKey", hasKey)
                    response.put("version", "1.4.5")
                }

                else -> {
                    response.put("status", "ERROR")
                    response.put("message", "Unknown action: $action")
                }
            }

            writer.println(response.toString())
            writer.flush()
        } catch (e: Exception) {
            Log.e(TAG, "[$channelName] Lỗi xử lý kết nối: ${e.message}", e)
        }
    }

    private fun vibrateTick(context: Context, pattern: LongArray) {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, -1)
            }
        } catch (_: Exception) {}
    }
}
