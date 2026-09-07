package com.oppowatch.gemini.phone

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import java.io.File
import java.io.FileInputStream
import kotlin.concurrent.thread

object WatchApkPusher {
    private const val TAG = "WatchApkPusher"
    private const val CHANNEL_PATH = "/watch_update_apk"

    interface PushCallback {
        fun onProgress(percentage: Int)
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
                    callback.onError("Không tìm thấy đồng hồ OPPO Watch đang kết nối Bluetooth!")
                    return@thread
                }

                val targetNode = nodes[0]
                Log.d(TAG, "Đã tìm thấy đồng hồ: ${targetNode.displayName} (${targetNode.id})")

                val channelClient = Wearable.getChannelClient(context)
                Log.d(TAG, "Đang mở channel $CHANNEL_PATH...")
                val channel = Tasks.await(channelClient.openChannel(targetNode.id, CHANNEL_PATH))

                Log.d(TAG, "Channel mở thành công, đang mở OutputStream...")
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
                            callback.onProgress(percent)
                        }
                    }
                    outputStream.flush()
                }

                outputStream.close()
                channelClient.close(channel)
                Log.d(TAG, "Đã truyền xong 100% file APK sang đồng hồ!")

                // Gửi thêm tín hiệu kích hoạt cài đặt đề phòng
                val messageClient = Wearable.getMessageClient(context)
                Tasks.await(messageClient.sendMessage(targetNode.id, "/watch_update_trigger_install", ByteArray(0)))

                callback.onSuccess()

            } catch (e: Exception) {
                Log.e(TAG, "Lỗi khi đẩy APK sang đồng hồ", e)
                callback.onError("Lỗi truyền file qua Bluetooth: ${e.localizedMessage}")
            }
        }
    }
}
