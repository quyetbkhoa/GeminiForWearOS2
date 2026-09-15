package com.oppowatch.gemini

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

object AdbBtBridge {
    private const val TAG = "AdbBtBridge"
    val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private const val SERVICE_NAME = "KiwiAdbBridge"
    private const val ADB_PORT = 5555

    private val isRunning = AtomicBoolean(false)
    private var serverSocket: BluetoothServerSocket? = null
    private var activeBtSocket: BluetoothSocket? = null
    private var activeTcpSocket: Socket? = null
    private var listenerThread: Thread? = null

    fun start() {
        if (isRunning.compareAndSet(false, true)) {
            Log.d(TAG, "Bắt đầu dịch vụ AdbBtBridge...")
            listenerThread = Thread({ runServer() }, "AdbBtBridge-Listener").apply {
                isDaemon = true
                start()
            }
        } else {
            Log.d(TAG, "AdbBtBridge đã đang chạy")
        }
    }

    fun stop() {
        if (isRunning.compareAndSet(true, false)) {
            Log.d(TAG, "Đang dừng AdbBtBridge...")
            try {
                serverSocket?.close()
            } catch (_: Exception) {}
            serverSocket = null

            closeActiveConnections()
            listenerThread?.interrupt()
            listenerThread = null
            Log.d(TAG, "Đã dừng AdbBtBridge")
        }
    }

    private fun closeActiveConnections() {
        try {
            activeBtSocket?.close()
        } catch (_: Exception) {}
        activeBtSocket = null

        try {
            activeTcpSocket?.close()
        } catch (_: Exception) {}
        activeTcpSocket = null
    }

    private fun ensureAdbListening(): Boolean {
        // 1. Kiểm tra xem port 5555 đã mở chưa
        try {
            Socket("127.0.0.1", ADB_PORT).use {
                Log.d(TAG, "Port $ADB_PORT đã đang mở và sẵn sàng")
                return true
            }
        } catch (_: Exception) {}

        Log.w(TAG, "Port $ADB_PORT chưa mở, đang thử khởi động qua root (su)...")
        // 2. Thử kích hoạt port 5555 qua su
        try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("setprop service.adb.tcp.port 5555\n")
            os.writeBytes("stop adbd\n")
            os.writeBytes("start adbd\n")
            os.writeBytes("exit\n")
            os.flush()
            val exitCode = process.waitFor()
            Log.d(TAG, "Lệnh su khởi động adbd kết thúc với exitCode: $exitCode")
            Thread.sleep(1500)

            // Thử kết nối lại
            Socket("127.0.0.1", ADB_PORT).use {
                Log.d(TAG, "✓ Port $ADB_PORT đã được khởi động thành công qua su!")
                return true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Không thể khởi động adbd qua su: ${e.message}")
        }
        return false
    }

    private fun runServer() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || !adapter.isEnabled) {
            Log.e(TAG, "Bluetooth không khả dụng hoặc chưa bật")
            isRunning.set(false)
            return
        }

        // Đảm bảo adbd đang lắng nghe ở 127.0.0.1:5555
        ensureAdbListening()

        while (isRunning.get()) {
            try {
                Log.d(TAG, "Đang mở BluetoothServerSocket RFCOMM...")
                serverSocket = try {
                    adapter.listenUsingInsecureRfcommWithServiceRecord(SERVICE_NAME, SPP_UUID)
                } catch (e: Exception) {
                    adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SPP_UUID)
                }

                Log.d(TAG, "Đang chờ điện thoại (Kiwi Manager) kết nối Bluetooth...")
                val btSocket = serverSocket?.accept()
                serverSocket?.close()
                serverSocket = null

                if (btSocket != null) {
                    Log.d(TAG, "✓ Đã nhận kết nối Bluetooth từ ${btSocket.remoteDevice.name ?: btSocket.remoteDevice.address}")
                    // Đánh thức màn hình đồng hồ để nếu có dialog RSA thì người dùng nhìn thấy
                    try {
                        Runtime.getRuntime().exec("input keyevent KEYCODE_WAKEUP")
                    } catch (_: Exception) {}

                    handleClient(btSocket)
                }
            } catch (e: Exception) {
                if (isRunning.get()) {
                    Log.w(TAG, "Lỗi trong vòng lặp server: ${e.message}, thử lại sau 2 giây...")
                    try {
                        Thread.sleep(2000)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }
        }
    }

    private fun handleClient(btSocket: BluetoothSocket) {
        activeBtSocket = btSocket
        var tcpSocket: Socket? = null
        try {
            // Đảm bảo adbd đã sẵn sàng trước khi kết nối
            if (!ensureAdbListening()) {
                Log.e(TAG, "✗ Không thể mở cổng ADB $ADB_PORT trên đồng hồ!")
                return
            }

            Log.d(TAG, "Đang kết nối tới adbd cục bộ (127.0.0.1:$ADB_PORT)...")
            tcpSocket = Socket("127.0.0.1", ADB_PORT)
            activeTcpSocket = tcpSocket
            Log.d(TAG, "✓ Đã kết nối tới adbd. Bắt đầu chuyển tiếp luồng dữ liệu...")

            val btIn = btSocket.inputStream
            val btOut = btSocket.outputStream
            val tcpIn = tcpSocket.getInputStream()
            val tcpOut = tcpSocket.getOutputStream()

            val isSessionAlive = AtomicBoolean(true)

            val t1 = Thread({
                pipeCoordinated(btIn, tcpOut, "BT->ADB", isSessionAlive, btSocket, tcpSocket)
            }, "Pipe-BT-to-ADB")

            val t2 = Thread({
                pipeCoordinated(tcpIn, btOut, "ADB->BT", isSessionAlive, btSocket, tcpSocket)
            }, "Pipe-ADB-to-BT")

            t1.start()
            t2.start()

            t1.join()
            t2.join()
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi phiên kết nối ADB: ${e.message}")
        } finally {
            closeActiveConnections()
            Log.d(TAG, "Phiên kết nối Bluetooth ADB đã kết thúc.")
        }
    }

    private fun pipeCoordinated(
        input: InputStream,
        output: OutputStream,
        name: String,
        isSessionAlive: AtomicBoolean,
        btSocket: BluetoothSocket,
        tcpSocket: Socket
    ) {
        val buffer = ByteArray(8192)
        try {
            while (isRunning.get() && isSessionAlive.get()) {
                val bytesRead = input.read(buffer)
                if (bytesRead == -1) break
                output.write(buffer, 0, bytesRead)
                output.flush()
            }
        } catch (e: Exception) {
            Log.d(TAG, "Pipe $name đóng: ${e.message}")
        } finally {
            isSessionAlive.set(false)
            try { output.close() } catch (_: Exception) {}
            try { input.close() } catch (_: Exception) {}
            // Đóng cả 2 socket để đánh thức luồng đối ứng ngay lập tức, chống deadlock
            try { btSocket.close() } catch (_: Exception) {}
            try { tcpSocket.close() } catch (_: Exception) {}
        }
    }
}
