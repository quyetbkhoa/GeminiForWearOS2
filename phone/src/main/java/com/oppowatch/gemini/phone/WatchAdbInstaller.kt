package com.oppowatch.gemini.phone

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import dadb.AdbKeyPair
import dadb.Dadb
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale
import kotlin.concurrent.thread

object WatchAdbInstaller {
    private const val TAG = "WatchAdbInstaller"
    private val mainHandler = Handler(Looper.getMainLooper())

    interface AdbInstallCallback {
        fun onStatus(message: String)
        fun onSuccess()
        fun onError(error: String)
    }

    /**
     * Lấy hoặc tạo cặp khóa RSA cho ADB client, lưu trong thư mục files riêng của app
     */
    private fun getOrCreateAdbKeyPair(context: Context): AdbKeyPair {
        val privKeyFile = File(context.filesDir, "gemini_adbkey")
        val pubKeyFile = File(context.filesDir, "gemini_adbkey.pub")

        return try {
            if (!privKeyFile.exists() || !pubKeyFile.exists() || privKeyFile.length() == 0L) {
                Log.i(TAG, "Đang sinh cặp khóa RSA ADB mới...")
                AdbKeyPair.generate(privKeyFile, pubKeyFile)
            }
            AdbKeyPair.read(privKeyFile, pubKeyFile)
        } catch (e: Exception) {
            Log.w(TAG, "Lỗi đọc/tạo key, thử tạo lại: ${e.message}")
            try { privKeyFile.delete() } catch (_: Exception) {}
            try { pubKeyFile.delete() } catch (_: Exception) {}
            AdbKeyPair.generate(privKeyFile, pubKeyFile)
            AdbKeyPair.read(privKeyFile, pubKeyFile)
        }
    }

    /**
     * Dò tìm địa chỉ IP của đồng hồ khi đồng hồ kết nối vào Hotspot hoặc chung mạng Wi-Fi
     */
    fun findWatchIpCandidates(context: Context): List<String> {
        val candidates = LinkedHashSet<String>()

        // 1. Quét bảng ARP (/proc/net/arp) tìm các máy khách kết nối vào Hotspot điện thoại
        try {
            val arpReader = BufferedReader(FileReader("/proc/net/arp"))
            var line: String?
            while (arpReader.readLine().also { line = it } != null) {
                val parts = line!!.split("\\s+".toRegex())
                if (parts.size >= 4 && parts[0] != "IP") {
                    val ip = parts[0]
                    val flags = parts[2]
                    // 0x2 = complete ARP entry
                    if (flags != "0x0" && ip != "0.0.0.0" && !ip.startsWith("127.")) {
                        candidates.add(ip)
                    }
                }
            }
            arpReader.close()
        } catch (e: Exception) {
            Log.d(TAG, "Không thể đọc ARP table: ${e.message}")
        }

        // 2. Lấy dải IP từ Wi-Fi DHCP nếu điện thoại đang bắt Wi-Fi
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val dhcp = wifiManager?.dhcpInfo
            if (dhcp != null && dhcp.ipAddress != 0) {
                val ip = dhcp.ipAddress
                val base = String.format(
                    Locale.US,
                    "%d.%d.%d.",
                    ip and 0xff,
                    ip shr 8 and 0xff,
                    ip shr 16 and 0xff
                )
                // Thêm các IP phổ biến trong dải
                for (last in 2..20) {
                    candidates.add("$base$last")
                }
            }
        } catch (_: Exception) {}

        // 3. Các IP điển hình khi bật Hotspot trên Android (dải 192.168.43.2 -> 192.168.43.20)
        for (last in 2..20) {
            candidates.add("192.168.43.$last")
        }

        return candidates.toList()
    }

    /**
     * Kiểm tra cổng 5555 có đang mở trên địa chỉ IP không
     */
    fun isAdbPortOpen(host: String, port: Int = 5555, timeoutMs: Int = 1000): Boolean {
        return try {
            Socket().use { s ->
                s.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Tự động dò IP đồng hồ đang mở cổng ADB 5555
     */
    fun autoDetectWatchAdbIp(context: Context, onResult: (String?) -> Unit) {
        thread(name = "AutoDetectWatchIpThread") {
            val candidates = findWatchIpCandidates(context)
            Log.d(TAG, "Đang quét ADB port 5555 trên ${candidates.size} địa chỉ IP...")

            var foundIp: String? = null
            for (ip in candidates) {
                if (isAdbPortOpen(ip, 5555, 600)) {
                    Log.i(TAG, "✓ Tìm thấy thiết bị mở ADB tại $ip:5555!")
                    foundIp = ip
                    break
                }
            }
            mainHandler.post { onResult(foundIp) }
        }
    }

    /**
     * Thực hiện cài đặt file APK lên đồng hồ qua Wireless ADB
     */
    fun installApkOverAdb(
        context: Context,
        watchIp: String,
        watchPort: Int = 5555,
        apkFile: File,
        callback: AdbInstallCallback
    ) {
        thread(name = "AdbInstallThread") {
            try {
                if (!apkFile.exists() || apkFile.length() == 0L) {
                    mainHandler.post { callback.onError("File APK không tồn tại hoặc rỗng!") }
                    return@thread
                }

                mainHandler.post {
                    callback.onStatus("Đang kiểm tra kết nối tới đồng hồ ($watchIp:$watchPort)...")
                }

                if (!isAdbPortOpen(watchIp, watchPort, 2500)) {
                    mainHandler.post {
                        callback.onError(
                            "Không thể kết nối tới $watchIp:$watchPort!\n" +
                            "Vui lòng đảm bảo:\n" +
                            "1. Đồng hồ đã bật 'Gỡ lỗi ADB' và 'Gỡ lỗi qua Wi-Fi'.\n" +
                            "2. Đồng hồ và điện thoại đang kết nối cùng Wi-Fi hoặc Hotspot."
                        )
                    }
                    return@thread
                }

                mainHandler.post {
                    callback.onStatus("Đang thiết lập khóa bảo mật RSA và bắt tay ADB...")
                }

                val keyPair = getOrCreateAdbKeyPair(context)

                mainHandler.post {
                    callback.onStatus("Đang kết nối ADB... (Nếu đồng hồ hiện popup 'Cho phép gỡ lỗi?', hãy bấm OK trên đồng hồ)")
                }

                val dadb = try {
                    Dadb.create(watchIp, watchPort, keyPair)
                } catch (e: Exception) {
                    Log.e(TAG, "Lỗi xác thực ADB: ${e.message}", e)
                    mainHandler.post {
                        callback.onError(
                            "Lỗi xác thực ADB: ${e.localizedMessage}\n" +
                            "👉 Hãy nhìn mặt đồng hồ, tích chọn 'Luôn cho phép' và bấm OK để cấp quyền gỡ lỗi cho điện thoại!"
                        )
                    }
                    return@thread
                }

                dadb.use { adb ->
                    mainHandler.post {
                        callback.onStatus("✓ Đã kết nối ADB thành công! Đang truyền file APK sang đồng hồ...")
                    }

                    val startTime = System.currentTimeMillis()
                    val remotePath = "/data/local/tmp/gemini_watch_update.apk"

                    Log.i(TAG, "Đang đẩy file APK (${apkFile.length()} bytes) sang $remotePath...")
                    adb.push(apkFile, remotePath)

                    mainHandler.post {
                        callback.onStatus("Đang ghi đè cài đặt trên đồng hồ (pm install -r -d -t)...")
                    }

                    Log.i(TAG, "Thực thi: pm install -r -d -t -g $remotePath")
                    var installResp = adb.shell("pm install -r -d -t -g $remotePath")
                    var output = (installResp.output + "\n" + installResp.errorOutput).trim()
                    Log.i(TAG, "Kết quả pm install: $output (exitCode=${installResp.exitCode})")

                    // Nếu lỗi chữ ký chứng chỉ không khớp (INSTALL_FAILED_UPDATE_INCOMPATIBLE)
                    if (output.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE", ignoreCase = true) ||
                        output.contains("INSTALL_FAILED_SHARED_USER_INCOMPATIBLE", ignoreCase = true)
                    ) {
                        Log.w(TAG, "Phát hiện khác chữ ký bảo mật. Tự động gỡ sạch bản cũ và cài lại...")
                        mainHandler.post {
                            callback.onStatus("Phát hiện bản cũ khác chữ ký bảo mật. Đang tự động gỡ sạch để cài bản mới...")
                        }
                        val uninstResp = adb.shell("pm uninstall com.oppowatch.gemini")
                        Log.i(TAG, "Kết quả gỡ bỏ: ${uninstResp.allOutput}")
                        installResp = adb.shell("pm install -r -d -t -g $remotePath")
                        output = (installResp.output + "\n" + installResp.errorOutput).trim()
                        Log.i(TAG, "Kết quả cài lại: $output")
                    }

                    // Dọn dẹp file tạm trên đồng hồ
                    try {
                        adb.shell("rm -f $remotePath")
                    } catch (_: Exception) {}

                    if (!output.contains("Success", ignoreCase = true)) {
                        throw java.io.IOException("Package Manager từ chối cài đặt:\n$output")
                    }

                    val elapsedSec = (System.currentTimeMillis() - startTime) / 1000.0
                    Log.i(TAG, "✓ Cài đặt APK hoàn tất thành công trong ${elapsedSec}s!")

                    // Kiểm tra phiên bản thực tế đã cài đặt trên đồng hồ
                    var actualVersionName = ""
                    try {
                        val dumpResp = adb.shell("dumpsys package com.oppowatch.gemini")
                        val match = Regex("versionName=([^\\s]+)").find(dumpResp.allOutput)
                        if (match != null) {
                            actualVersionName = match.groupValues[1]
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Không đọc được versionName từ dumpsys: ${e.message}")
                    }

                    // Khởi chạy ứng dụng trên đồng hồ
                    try {
                        adb.shell("am start -n com.oppowatch.gemini/.MainActivity")
                    } catch (_: Exception) {}

                    val statusMsg = if (actualVersionName.isNotEmpty()) {
                        String.format(
                            Locale.US,
                            "✓ CÀI ĐẶT THÀNH CÔNG v%s trong %.1fs! Ứng dụng trên đồng hồ đã cập nhật.",
                            actualVersionName,
                            elapsedSec
                        )
                    } else {
                        String.format(
                            Locale.US,
                            "✓ CÀI ĐẶT THÀNH CÔNG trong %.1fs! Ứng dụng trên đồng hồ đã sẵn sàng.",
                            elapsedSec
                        )
                    }

                    mainHandler.post {
                        callback.onStatus(statusMsg)
                        callback.onSuccess()
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Lỗi cài đặt qua ADB", e)
                mainHandler.post {
                    callback.onError("Lỗi cài đặt ADB: ${e.localizedMessage}")
                }
            }
        }
    }
}
