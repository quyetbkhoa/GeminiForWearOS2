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
        }
    }
}
