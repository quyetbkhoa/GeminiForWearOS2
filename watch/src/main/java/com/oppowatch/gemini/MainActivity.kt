package com.oppowatch.gemini

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var recorderHelper: AudioRecorderHelper
    private lateinit var pttContainer: FrameLayout
    private lateinit var tvStatus: TextView
    private lateinit var tvResult: TextView
    private lateinit var scrollResult: ScrollView

    private var touchDownTime = 0L

    // Chỉ tự động kích hoạt thu âm khi người dùng chủ động mở app (từ launcher, shortcut, tile...)
    private var isAppActivelyLaunched = false
    // Đánh dấu người dùng đã thoát app (swipe back) hoặc tắt màn hình để hủy bỏ toàn bộ tác vụ
    private var isDismissedOrCancelled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Khi khởi tạo app lần đầu (bật chủ động từ menu / nút tắt)
        if (savedInstanceState == null) {
            isAppActivelyLaunched = true
        }

        recorderHelper = AudioRecorderHelper(this)
        recorderHelper.onSilenceDetected = {
            runOnUiThread {
                if (recorderHelper.isRecording && !isDismissedOrCancelled && !isFinishing) {
                    finishVoiceRecording()
                }
            }
        }

        pttContainer = findViewById(R.id.btn_ptt_container)
        tvStatus = findViewById(R.id.tv_status)
        tvResult = findViewById(R.id.tv_result)
        scrollResult = findViewById(R.id.scroll_result)

        setupPttListener()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                101
            )
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Khi người dùng bấm lại vào icon hoặc phím tắt trong khi app đang ở background
        isAppActivelyLaunched = true
        isDismissedOrCancelled = false
    }

    override fun onResume() {
        super.onResume()
        isDismissedOrCancelled = false

        // CHỈ tự động thu âm khi người dùng vừa chủ động bấm mở app
        if (isAppActivelyLaunched) {
            isAppActivelyLaunched = false // Tiêu thụ cờ để các lần resume sau (như bật lại màn hình) không tự ghi âm
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED && !recorderHelper.isRecording) {
                startVoiceRecording()
            }
        } else {
            // Khi người dùng tắt màn hình đi vào lại: TUYỆT ĐỐI KHÔNG TỰ ĐỘNG GHI ÂM
            if (!recorderHelper.isRecording) {
                pttContainer.setBackgroundResource(R.drawable.bg_ptt_idle)
                tvStatus.text = "NHẤN ĐỂ NÓI"
                tvStatus.setTextColor(resources.getColor(R.color.gold_light))
            }
        }
    }

    override fun onPause() {
        super.onPause()
        isAppActivelyLaunched = false
        isDismissedOrCancelled = true

        // Thoát app bằng swipe back hoặc tắt màn hình: HỦY NGAY ghi âm và HỦY cuộc gọi API Gemini đang dở
        if (recorderHelper.isRecording) {
            recorderHelper.cancelRecording()
        }
        GeminiClient.cancelCurrentRequest()

        pttContainer.setBackgroundResource(R.drawable.bg_ptt_idle)
        tvStatus.text = "NHẤN ĐỂ NÓI"
        tvStatus.setTextColor(resources.getColor(R.color.gold_light))
    }

    override fun onStop() {
        super.onStop()
        isAppActivelyLaunched = false
        isDismissedOrCancelled = true
        recorderHelper.cancelRecording()
        GeminiClient.cancelCurrentRequest()
    }

    override fun finish() {
        isDismissedOrCancelled = true
        recorderHelper.cancelRecording()
        GeminiClient.cancelCurrentRequest()
        super.finish()
    }

    override fun onBackPressed() {
        isDismissedOrCancelled = true
        recorderHelper.cancelRecording()
        GeminiClient.cancelCurrentRequest()
        super.onBackPressed()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVoiceRecording()
            } else {
                tvStatus.text = "CẦN CẤP QUYỀN MICRO"
            }
        }
    }

    private fun setupPttListener() {
        pttContainer.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownTime = System.currentTimeMillis()
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(
                            this,
                            arrayOf(Manifest.permission.RECORD_AUDIO),
                            101
                        )
                        return@setOnTouchListener true
                    }
                    if (!recorderHelper.isRecording) {
                        startVoiceRecording()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (recorderHelper.isRecording) {
                        // Nhấn giữ nhả ra HOẶC chạm vào khi đang tự động lắng nghe đều gửi câu hỏi
                        finishVoiceRecording()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (recorderHelper.isRecording) {
                        finishVoiceRecording()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun startVoiceRecording() {
        isDismissedOrCancelled = false
        vibrateTick(80, 100)
        pttContainer.setBackgroundResource(R.drawable.bg_ptt_recording)
        tvStatus.text = "🔴 ĐANG LẮNG NGHE..."
        tvStatus.setTextColor(resources.getColor(R.color.red_recording))
        tvResult.text = "Đang lắng nghe bạn nói...\n(Dừng nói 1.3s để tự động gửi)"

        val started = recorderHelper.startRecording()
        if (!started) {
            tvStatus.text = "LỖI MICROPHONE"
            tvResult.text = "Không thể khởi động micro."
            pttContainer.setBackgroundResource(R.drawable.bg_ptt_idle)
        }
    }

    private fun finishVoiceRecording() {
        if (!recorderHelper.isRecording || isDismissedOrCancelled || isFinishing) return

        vibrateTick(120, 150)
        pttContainer.setBackgroundResource(R.drawable.bg_ptt_idle)
        tvStatus.text = "⚡ ĐANG GỌI GEMINI..."
        tvStatus.setTextColor(resources.getColor(R.color.gold_accent))
        tvResult.text = "Gemini đang xử lý câu trả lời..."

        val audioBase64 = recorderHelper.stopRecording()
        if (audioBase64.isNullOrEmpty()) {
            tvStatus.text = "NHẤN ĐỂ NÓI"
            tvStatus.setTextColor(resources.getColor(R.color.gold_light))
            tvResult.text = "Chưa thu được âm thanh. Hãy nhấn giữ hoặc chạm để nói lại."
            return
        }

        GeminiClient.askGemini(this, audioBase64) { success, question, answer ->
            runOnUiThread {
                // Nếu người dùng đã vuốt thoát app hoặc màn hình tắt thì bỏ qua kết quả
                if (isDismissedOrCancelled || isFinishing || isDestroyed) {
                    Log.d("MainActivity", "Bỏ qua kết quả vì người dùng đã thoát app hoặc hủy.")
                    return@runOnUiThread
                }

                tvStatus.text = if (success) "✓ ĐÃ TRẢ LỜI" else "LỖI"
                tvStatus.setTextColor(if (success) resources.getColor(R.color.gold_accent) else resources.getColor(R.color.red_recording))
                tvResult.text = answer
                scrollResult.smoothScrollTo(0, 0)

                if (success) {
                    vibrateTick(180, 200)
                    // Gửi JSON cấu trúc sang điện thoại để phát TTS và lưu lịch sử
                    val payload = org.json.JSONObject().apply {
                        put("question", question)
                        put("answer", answer)
                        put("timestamp", System.currentTimeMillis())
                    }.toString()
                    PhoneCommunicator.sendTextToPhone(this, payload)
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