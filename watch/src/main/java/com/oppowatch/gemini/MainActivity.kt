package com.oppowatch.gemini

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var recorderHelper: AudioRecorderHelper
    private lateinit var pttContainer: FrameLayout
    private lateinit var tvStatus: TextView
    private lateinit var tvResult: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recorderHelper = AudioRecorderHelper(this)
        pttContainer = findViewById(R.id.btn_ptt_container)
        tvStatus = findViewById(R.id.tv_status)
        tvResult = findViewById(R.id.tv_result)

        checkMicrophonePermission()
        setupPttListener()
    }

    private fun checkMicrophonePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                101
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                tvStatus.text = "NHẤN GIỮ ĐỂ NÓI"
            } else {
                tvStatus.text = "CẦN CẤP QUYỀN MICRO"
            }
        }
    }

    private fun setupPttListener() {
        pttContainer.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {
                        checkMicrophonePermission()
                        tvStatus.text = "CẦN CẤP QUYỀN MICRO"
                        return@setOnTouchListener true
                    }
                    startVoiceRecording()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    finishVoiceRecording()
                    true
                }
                else -> false
            }
        }
    }

    private fun startVoiceRecording() {
        vibrateTick(80, 100)
        pttContainer.setBackgroundResource(R.drawable.bg_ptt_recording)
        tvStatus.text = "🔴 ĐANG LẮNG NGHE..."
        tvStatus.setTextColor(resources.getColor(R.color.red_recording))

        val started = recorderHelper.startRecording()
        if (!started) {
            tvStatus.text = "LỖI MICROPHONE"
            pttContainer.setBackgroundResource(R.drawable.bg_ptt_idle)
        }
    }

    private fun finishVoiceRecording() {
        if (!recorderHelper.isRecording) return

        vibrateTick(120, 150)
        pttContainer.setBackgroundResource(R.drawable.bg_ptt_idle)
        tvStatus.text = "⚡ ĐANG GỌI GEMINI..."
        tvStatus.setTextColor(resources.getColor(R.color.gold_accent))

        val audioBase64 = recorderHelper.stopRecording()
        if (audioBase64.isNullOrEmpty()) {
            tvStatus.text = "NHẤN GIỮ ĐỂ NÓI"
            tvStatus.setTextColor(resources.getColor(R.color.gold_light))
            return
        }

        GeminiClient.askGemini(audioBase64) { success, answer ->
            runOnUiThread {
                tvStatus.text = if (success) "✓ ĐÃ TRẢ LỜI" else "LỖI"
                tvStatus.setTextColor(if (success) resources.getColor(R.color.gold_accent) else resources.getColor(R.color.red_recording))
                tvResult.text = answer

                if (success) {
                    vibrateTick(180, 200)
                    // Push to paired phone for Bluetooth TTS
                    PhoneCommunicator.sendTextToPhone(this, answer)
                }
            }
        }
    }

    private fun vibrateTick(amplitude: Int, duration: Long) {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
            } catch (e: Exception) {
                vibrator.vibrate(duration)
            }
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }
}