package com.oppowatch.gemini

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var recorderHelper: AudioRecorderHelper
    private lateinit var layoutRoot: LinearLayout
    private lateinit var tvHeaderTitle: TextView
    private lateinit var tvStatus: TextView
    private lateinit var containerResultCard: LinearLayout
    private lateinit var tvResult: TextView
    private lateinit var scrollResult: ScrollView
    private lateinit var pttContainer: FrameLayout

    private var touchDownTime = 0L

    // Chỉ tự động kích hoạt thu âm khi người dùng chủ động mở app (từ launcher, shortcut, tile...)
    private var isAppActivelyLaunched = false
    // Đánh dấu người dùng đã thoát app (swipe back) hoặc tắt màn hình để hủy bỏ toàn bộ tác vụ
    private var isDismissedOrCancelled = false

    // Chế độ giao diện đồng hồ: skeuo, glass, material, light
    private var currentThemeMode = "skeuo"

    private val themeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val theme = intent?.getStringExtra("theme") ?: "skeuo"
            currentThemeMode = theme
            getSharedPreferences("gemini_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("app_theme_mode", theme)
                .apply()
            runOnUiThread {
                applyWatchTheme()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Khi khởi tạo app lần đầu: nếu mở từ thẻ thông tin thì KHÔNG tự động ghi âm
        val fromTile = intent?.getBooleanExtra("FROM_TILE", false) == true
        if (savedInstanceState == null && !fromTile) {
            isAppActivelyLaunched = true
        } else {
            isAppActivelyLaunched = false
        }

        layoutRoot = findViewById(R.id.layout_root)
        tvHeaderTitle = findViewById(R.id.tv_header_title)
        tvStatus = findViewById(R.id.tv_status)
        containerResultCard = findViewById(R.id.container_result_card)
        tvResult = findViewById(R.id.tv_result)
        scrollResult = findViewById(R.id.scroll_result)
        pttContainer = findViewById(R.id.btn_ptt_container)

        recorderHelper = AudioRecorderHelper(this)
        recorderHelper.onSilenceDetected = {
            runOnUiThread {
                if (recorderHelper.isRecording && !isDismissedOrCancelled && !isFinishing) {
                    finishVoiceRecording()
                }
            }
        }

        // Đọc giao diện đã lưu (mặc định skeuo)
        val prefs = getSharedPreferences("gemini_prefs", Context.MODE_PRIVATE)
        currentThemeMode = prefs.getString("app_theme_mode", null)
            ?: (if (prefs.getString("watch_color_theme", "dark") == "light") "light" else "skeuo")
        applyWatchTheme()

        setupPttListener()

        val filter = IntentFilter("com.oppowatch.gemini.WATCH_THEME_CHANGED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(themeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(themeReceiver, filter)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                101
            )
        }
    }

    private fun getPttIdleDrawable(): Int {
        return when (currentThemeMode) {
            "light", "ceramic_light" -> R.drawable.bg_watch_ptt_light
            "glass", "liquid_glass" -> R.drawable.bg_watch_ptt_glass
            "material" -> R.drawable.bg_watch_ptt_m3
            else -> R.drawable.bg_watch_ptt_dark
        }
    }

    private fun getStatusIdleColor(): Int {
        return when (currentThemeMode) {
            "light", "ceramic_light" -> Color.parseColor("#475569")
            "glass", "liquid_glass" -> Color.parseColor("#7DD3FC")
            "material" -> Color.parseColor("#A7F3D0")
            else -> Color.parseColor("#94A3B8")
        }
    }

    private fun getStatusAccentColor(): Int {
        return when (currentThemeMode) {
            "light", "ceramic_light" -> Color.parseColor("#B45309")
            "glass", "liquid_glass" -> Color.parseColor("#38BDF8")
            "material" -> Color.parseColor("#80CBC4")
            else -> Color.parseColor("#E5C158")
        }
    }

    private fun applyWatchTheme() {
        val isRec = if (::recorderHelper.isInitialized) recorderHelper.isRecording else false

        when (currentThemeMode) {
            "light", "ceramic_light" -> {
                // Chế độ Trắng Ceramic sang trọng cho đồng hồ
                layoutRoot.setBackgroundColor(Color.parseColor("#F1F5F9"))
                tvHeaderTitle.setTextColor(Color.parseColor("#B45309")) // Champagne Gold
                tvStatus.setTextColor(Color.parseColor("#475569"))
                containerResultCard.setBackgroundResource(R.drawable.bg_watch_plate_light)
                tvResult.setTextColor(Color.parseColor("#0F172A")) // Slate Black cực kỳ sắc nét
                if (!isRec) {
                    pttContainer.setBackgroundResource(R.drawable.bg_watch_ptt_light)
                }
            }
            "glass", "liquid_glass" -> {
                // Chế độ Kính lỏng dạ quang (Liquid Glass)
                layoutRoot.setBackgroundColor(Color.parseColor("#0A1128")) // Deep cosmic sapphire
                tvHeaderTitle.setTextColor(Color.parseColor("#38BDF8")) // Glowing Cyan
                tvStatus.setTextColor(Color.parseColor("#7DD3FC"))
                containerResultCard.setBackgroundResource(R.drawable.bg_watch_plate_glass)
                tvResult.setTextColor(Color.parseColor("#F8FAFC")) // Crystal Diamond White
                if (!isRec) {
                    pttContainer.setBackgroundResource(R.drawable.bg_watch_ptt_glass)
                }
            }
            "material" -> {
                // Chế độ Google Material 3 Slate Dark
                layoutRoot.setBackgroundColor(Color.parseColor("#121418"))
                tvHeaderTitle.setTextColor(Color.parseColor("#80CBC4")) // Mint Teal
                tvStatus.setTextColor(Color.parseColor("#A7F3D0"))
                containerResultCard.setBackgroundResource(R.drawable.bg_watch_plate_m3)
                tvResult.setTextColor(Color.parseColor("#E2E8F0"))
                if (!isRec) {
                    pttContainer.setBackgroundResource(R.drawable.bg_watch_ptt_m3)
                }
            }
            else -> {
                // Chế độ Đen Obsidian AMOLED Skeuomorphism tiết kiệm pin chuẩn OPPO Watch
                layoutRoot.setBackgroundColor(Color.parseColor("#000000"))
                tvHeaderTitle.setTextColor(Color.parseColor("#E5C158")) // Pure Gold
                tvStatus.setTextColor(Color.parseColor("#94A3B8"))
                containerResultCard.setBackgroundResource(R.drawable.bg_watch_plate_dark)
                tvResult.setTextColor(Color.parseColor("#F1F5F9"))
                if (!isRec) {
                    pttContainer.setBackgroundResource(R.drawable.bg_watch_ptt_dark)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(themeReceiver)
        } catch (_: Exception) {}
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        val fromTile = intent?.getBooleanExtra("FROM_TILE", false) == true
        if (fromTile) {
            isAppActivelyLaunched = false // Tuyệt đối KHÔNG tự động ghi âm khi mở qua thẻ thông tin
        } else {
            isAppActivelyLaunched = true // Bật từ launcher icon hoặc shortcut phím tắt
        }
        isDismissedOrCancelled = false
    }

    override fun onResume() {
        super.onResume()
        isDismissedOrCancelled = false

        // Kiểm tra lại theme khi vào lại app
        val prefs = getSharedPreferences("gemini_prefs", Context.MODE_PRIVATE)
        currentThemeMode = prefs.getString("app_theme_mode", null)
            ?: (if (prefs.getString("watch_color_theme", "dark") == "light") "light" else "skeuo")
        applyWatchTheme()

        // CHỈ tự động thu âm khi người dùng vừa chủ động bấm mở app
        if (isAppActivelyLaunched) {
            isAppActivelyLaunched = false
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED && !recorderHelper.isRecording) {
                startVoiceRecording()
            }
        } else {
            // Khi người dùng tắt màn hình đi vào lại: TUYỆT ĐỐI KHÔNG TỰ ĐỘNG GHI ÂM
            if (!recorderHelper.isRecording) {
                pttContainer.setBackgroundResource(getPttIdleDrawable())
                tvStatus.text = "NHẤN ĐỂ NÓI"
                tvStatus.setTextColor(getStatusIdleColor())
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

        pttContainer.setBackgroundResource(getPttIdleDrawable())
        tvStatus.text = "NHẤN ĐỂ NÓI"
        tvStatus.setTextColor(getStatusIdleColor())
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
        pttContainer.setBackgroundResource(R.drawable.bg_watch_ptt_recording)
        tvStatus.text = "🔴 ĐANG LẮNG NGHE..."
        tvStatus.setTextColor(Color.parseColor("#EF4444"))
        tvResult.text = "Đang lắng nghe bạn nói...\n(Dừng nói 1.3s để tự động gửi)"

        val started = recorderHelper.startRecording()
        if (!started) {
            tvStatus.text = "LỖI MICROPHONE"
            tvResult.text = "Không thể khởi động micro."
            pttContainer.setBackgroundResource(getPttIdleDrawable())
        }
    }

    private fun finishVoiceRecording() {
        if (!recorderHelper.isRecording || isDismissedOrCancelled || isFinishing) return

        vibrateTick(120, 150)
        pttContainer.setBackgroundResource(getPttIdleDrawable())
        tvStatus.text = "⚡ ĐANG GỌI GEMINI..."
        tvStatus.setTextColor(getStatusAccentColor())
        tvResult.text = "Gemini đang xử lý câu trả lời..."

        val audioBase64 = recorderHelper.stopRecording()
        if (audioBase64.isNullOrEmpty()) {
            tvStatus.text = "NHẤN ĐỂ NÓI"
            tvStatus.setTextColor(getStatusIdleColor())
            tvResult.text = "Chưa thu được âm thanh. Hãy nhấn giữ hoặc chạm để nói lại."
            return
        }

        GeminiClient.askGemini(this, audioBase64) { success, question, answer ->
            runOnUiThread {
                if (isDismissedOrCancelled || isFinishing || isDestroyed) {
                    Log.d("MainActivity", "Bỏ qua kết quả vì người dùng đã thoát app hoặc hủy.")
                    return@runOnUiThread
                }

                tvStatus.text = if (success) "✓ ĐÃ TRẢ LỜI" else "LỖI"
                tvStatus.setTextColor(if (success) (if (currentThemeMode == "light" || currentThemeMode == "ceramic_light") Color.parseColor("#059669") else Color.parseColor("#34D399")) else Color.parseColor("#EF4444"))
                tvResult.text = answer
                scrollResult.smoothScrollTo(0, 0)

                if (success) {
                    vibrateTick(180, 200)
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