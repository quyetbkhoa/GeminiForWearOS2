package com.oppowatch.gemini

import android.content.Context
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import java.io.File
import java.io.FileInputStream

class AudioRecorderHelper(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    var isRecording: Boolean = false
        private set

    // Tự động dừng ghi âm khi người dùng ngừng nói (VAD - Voice Activity Detection)
    var onSilenceDetected: (() -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())
    private var silenceRunnable: Runnable? = null
    private var hasSpoken = false
    private var silenceDurationMs = 0L
    private var recordingStartTime = 0L

    companion object {
        private const val CHECK_INTERVAL_MS = 120L
        private const val SPEECH_AMPLITUDE_THRESHOLD = 2000
        private const val SILENCE_AMPLITUDE_THRESHOLD = 1300
        private const val SILENCE_DURATION_TO_STOP_MS = 1300L // 1.3s sau khi ngừng nói
        private const val NO_SPEECH_TIMEOUT_MS = 7000L // 7s nếu không có tiếng nói
        private const val MAX_RECORDING_TIME_MS = 30000L // 30s tối đa
    }

    fun startRecording(): Boolean {
        return try {
            outputFile = File(context.cacheDir, "voice_prompt.m4a")
            if (outputFile?.exists() == true) outputFile?.delete()

            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16000)
                setAudioEncodingBitRate(32000)
                setOutputFile(outputFile?.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            hasSpoken = false
            silenceDurationMs = 0L
            recordingStartTime = System.currentTimeMillis()

            startSilenceMonitoring()
            true
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Error starting recording: ${e.message}")
            isRecording = false
            stopSilenceMonitoring()
            false
        }
    }

    private fun startSilenceMonitoring() {
        stopSilenceMonitoring()
        // Gọi maxAmplitude một lần ban đầu để reset đồng hồ đo biên độ
        try { recorder?.maxAmplitude } catch (_: Exception) {}

        silenceRunnable = object : Runnable {
            override fun run() {
                if (!isRecording) return
                val currentRecorder = recorder ?: return

                try {
                    val amplitude = currentRecorder.maxAmplitude
                    val elapsed = System.currentTimeMillis() - recordingStartTime

                    if (amplitude >= SPEECH_AMPLITUDE_THRESHOLD) {
                        hasSpoken = true
                        silenceDurationMs = 0L // Đang có giọng nói, reset bộ đếm khoảng lặng
                    } else if (hasSpoken && amplitude < SILENCE_AMPLITUDE_THRESHOLD) {
                        // Người dùng đã nói xong và đang im lặng
                        silenceDurationMs += CHECK_INTERVAL_MS
                        if (silenceDurationMs >= SILENCE_DURATION_TO_STOP_MS) {
                            Log.d("AudioRecorder", "Phát hiện im lặng ${silenceDurationMs}ms sau lời nói -> Tự động dừng ghi âm.")
                            stopSilenceMonitoring()
                            onSilenceDetected?.invoke()
                            return
                        }
                    } else if (!hasSpoken && elapsed >= NO_SPEECH_TIMEOUT_MS) {
                        // Mở app nhưng không phát hiện tiếng nói sau 7 giây
                        Log.d("AudioRecorder", "Không phát hiện tiếng nói sau ${elapsed}ms -> Dừng ghi âm.")
                        stopSilenceMonitoring()
                        onSilenceDetected?.invoke()
                        return
                    }

                    if (elapsed >= MAX_RECORDING_TIME_MS) {
                        Log.d("AudioRecorder", "Đã đạt giới hạn tối đa 30s -> Tự động dừng ghi âm.")
                        stopSilenceMonitoring()
                        onSilenceDetected?.invoke()
                        return
                    }

                    handler.postDelayed(this, CHECK_INTERVAL_MS)
                } catch (e: Exception) {
                    Log.w("AudioRecorder", "Lỗi kiểm tra biên độ âm thanh: ${e.message}")
                }
            }
        }
        handler.postDelayed(silenceRunnable!!, CHECK_INTERVAL_MS)
    }

    private fun stopSilenceMonitoring() {
        silenceRunnable?.let { handler.removeCallbacks(it) }
        silenceRunnable = null
    }

    fun stopRecording(): String? {
        stopSilenceMonitoring()
        if (!isRecording) return null
        return try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            isRecording = false

            val file = outputFile ?: return null
            if (!file.exists() || file.length() == 0L) return null

            val bytes = ByteArray(file.length().toInt())
            FileInputStream(file).use { it.read(bytes) }
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Error stopping recording: ${e.message}")
            isRecording = false
            null
        }
    }
}