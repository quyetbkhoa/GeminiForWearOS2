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
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var recorderHelper: AudioRecorderHelper
    private lateinit var layoutRoot: LinearLayout
    private lateinit var tvStatus: TextView
    private lateinit var tvResult: TextView
    private lateinit var scrollResult: ScrollView
    private lateinit var pttContainer: FrameLayout
    private lateinit var ivMicIcon: ImageView
    private lateinit var tvAppVersion: TextView
    private lateinit var btnCancel: FrameLayout
    private lateinit var ivCancelIcon: ImageView
    private lateinit var viewDimOverlay: View
    private lateinit var viewMicPulseRing: View

    private val taskResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val status = intent?.getStringExtra("status") ?: "✓ KẾT QUẢ"
            val result = intent?.getStringExtra("result") ?: ""
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    tvStatus.text = status
                    tvStatus.setTextColor(Color.WHITE)
                    if (result.isNotEmpty()) {
                        tvResult.text = result
                        scrollResult.smoothScrollTo(0, 0)
                    }
                }
            }
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var touchDownTime = 0L
    private var wasRecordingWhenTouchDown = false

    // Chỉ tự động kích hoạt thu âm khi người dùng chủ động mở app (từ launcher, shortcut, tile...)
    private var isAppActivelyLaunched = false
    // Người dùng chủ động huỷ: huỷ toàn bộ tác vụ, không gửi gì
    private var isUserExplicitlyCancelled = false

    // Trạng thái xử lý nền khi đi đường (Road Mode)
    @Volatile
    private var isProcessingGemini = false
    private var isScreenOffPendingExit = false
    private var wakeLock: PowerManager.WakeLock? = null

    // Chế độ giao diện đồng hồ: 3 Styles x 2 Modes = 6 biến thể
    private var currentThemeStyle = "skeuo"
    private var currentThemeMode = "dark"
    private var currentThemeCombined = "skeuo_dark"

    private val themeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val combined = intent?.getStringExtra("theme") ?: "skeuo_dark"
            val style = intent?.getStringExtra("style") ?: combined.substringBefore("_", "skeuo")
            val mode = intent?.getStringExtra("mode") ?: combined.substringAfter("_", "dark")
            currentThemeStyle = style
            currentThemeMode = mode
            currentThemeCombined = "${style}_${mode}"
            runOnUiThread {
                applyWatchTheme()
            }
        }
    }

    // Khi người dùng đập tay tắt màn hình (palm gesture) hoặc màn hình tắt do timeout khi đi đường:
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                Log.d("MainActivity", "Màn hình đã tắt (Screen Off). isProcessing=$isProcessingGemini, isRecording=${if (::recorderHelper.isInitialized) recorderHelper.isRecording else false}")

                // 1. Nếu người dùng đang nói dở mà màn hình vụt tắt: tự động chốt câu nói và gửi đi (Auto-commit)
                if (::recorderHelper.isInitialized && recorderHelper.isRecording) {
                    Log.d("MainActivity", "Road Mode: Tự động chốt câu nói và gửi Gemini khi màn hình tắt")
                    isScreenOffPendingExit = true
                    finishVoiceRecording()
                    return
                }

                // 2. Nếu đang chờ Gemini xử lý: giữ nguyên tác vụ nền, đánh dấu thoát sau khi gửi TTS
                if (isProcessingGemini) {
                    Log.d("MainActivity", "Road Mode: Đang chờ Gemini trả lời -> Giữ WakeLock, chờ phát TTS vào tai nghe")
                    isScreenOffPendingExit = true
                    acquireWakeLock()
                    return
                }

                // 3. Nếu đang ở màn hình tĩnh (nhàn rỗi): thoát êm về Màn hình chính (Watch Face)
                finishAndRemoveTask()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Đảm bảo cầu nối Bluetooth ADB luôn sẵn sàng
        try {
            AdbBtBridge.start()
        } catch (_: Exception) {}

        // Khi khởi tạo app lần đầu: mở từ thẻ thông tin (Tile), launcher hay phím tắt đều tự động ghi âm ngay
        val fromTile = intent?.getBooleanExtra("FROM_TILE", false) == true
        if (savedInstanceState == null || fromTile) {
            isAppActivelyLaunched = true
        } else {
            isAppActivelyLaunched = false
        }

        layoutRoot = findViewById(R.id.layout_root)
        tvStatus = findViewById(R.id.tv_status)
        tvResult = findViewById(R.id.tv_result)
        scrollResult = findViewById(R.id.scroll_result)
        pttContainer = findViewById(R.id.btn_ptt_container)
        ivMicIcon = findViewById(R.id.iv_mic_icon)
        btnCancel = findViewById(R.id.btn_cancel)
        ivCancelIcon = findViewById(R.id.iv_cancel_icon)
        tvAppVersion = findViewById(R.id.tv_app_version)
        viewDimOverlay = findViewById(R.id.view_dim_overlay)
        viewMicPulseRing = findViewById(R.id.view_mic_pulse_ring)

        // Hiển thị số phiên bản ứng dụng động ở góc màn hình
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.4.2"
        } catch (_: Exception) { "1.4.2" }
        tvAppVersion.text = "v$versionName"

        btnCancel.setOnClickListener {
            handleCancelAction()
        }

        recorderHelper = AudioRecorderHelper(this)
        recorderHelper.onSilenceDetected = {
            runOnUiThread {
                if (recorderHelper.isRecording && !isUserExplicitlyCancelled && !isFinishing) {
                    finishVoiceRecording()
                }
            }
        }
        recorderHelper.onAmplitudeChanged = { normalizedAmp ->
            runOnUiThread {
                if (recorderHelper.isRecording && !isUserExplicitlyCancelled && !isFinishing) {
                    updateVoiceWaveform(normalizedAmp)
                }
            }
        }

        // Đọc giao diện đã lưu (Style + Mode)
        val prefs = getSharedPreferences("gemini_prefs", Context.MODE_PRIVATE)
        currentThemeStyle = prefs.getString("app_theme_style", "skeuo") ?: "skeuo"
        currentThemeMode = prefs.getString("app_theme_mode", null)
            ?: (if (prefs.getString("watch_color_theme", "dark") == "light") "light" else "dark")
        currentThemeCombined = prefs.getString("app_theme_combined", "${currentThemeStyle}_${currentThemeMode}") ?: "skeuo_dark"
        applyWatchTheme()

        setupPttListener()

        val filter = IntentFilter("com.oppowatch.gemini.WATCH_THEME_CHANGED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(themeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(themeReceiver, filter)
        }

        val taskFilter = IntentFilter("com.oppowatch.gemini.TASK_RESULT_UPDATE")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(taskResultReceiver, taskFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(taskResultReceiver, taskFilter)
        }

        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                101
            )
        }

        // Tự động phát hiện IP và kiểm tra cổng ADB 5555 gửi sang điện thoại qua Bluetooth
        PhoneCommunicator.sendWatchAdbInfoToPhone(this)
    }

    private fun getPttIdleDrawable(): Int = R.drawable.bg_watch_btn_mic_idle

    private fun getStatusIdleColor(): Int = Color.parseColor("#9E9E9E")

    private fun getStatusAccentColor(): Int = Color.WHITE

    private fun getStatusSuccessColor(): Int = Color.WHITE

    private fun applyWatchTheme() {
        val isRec = if (::recorderHelper.isInitialized) recorderHelper.isRecording else false

        layoutRoot.setBackgroundColor(Color.BLACK)
        tvResult.setTextColor(Color.WHITE)
        tvStatus.setTextColor(Color.parseColor("#9E9E9E"))
        tvAppVersion.setTextColor(Color.parseColor("#94A3B8"))

        if (isRec) {
            pttContainer.setBackgroundResource(R.drawable.bg_watch_btn_mic_recording)
            if (::ivMicIcon.isInitialized) ivMicIcon.setColorFilter(Color.WHITE)
        } else {
            pttContainer.setBackgroundResource(R.drawable.bg_watch_btn_mic_idle)
            if (::ivMicIcon.isInitialized) ivMicIcon.setColorFilter(Color.WHITE)
        }

        if (::btnCancel.isInitialized) {
            btnCancel.setBackgroundResource(R.drawable.bg_watch_btn_cancel_red)
            if (::ivCancelIcon.isInitialized) ivCancelIcon.setColorFilter(Color.WHITE)
        }
    }

    /**
     * Nút Hủy: Hủy tác vụ thu âm/xử lý đang diễn ra và thoát app về màn hình chính,
     * để hệ thống đồng hồ tự quản lý việc tắt màn hình theo thời gian chờ bình thường.
     */
    private fun handleCancelAction() {
        Log.d("MainActivity", "Nút Hủy được bấm: Hủy tác vụ và thoát app.")
        vibrateTick(80, 100)
        isUserExplicitlyCancelled = true

        if (::recorderHelper.isInitialized && recorderHelper.isRecording) {
            recorderHelper.cancelRecording()
        }

        if (isProcessingGemini) {
            GeminiClient.cancelCurrentRequest()
            isProcessingGemini = false
        }

        releaseWakeLock()

        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(themeReceiver)
        } catch (_: Exception) {}
        try {
            unregisterReceiver(taskResultReceiver)
        } catch (_: Exception) {}
        try {
            unregisterReceiver(screenOffReceiver)
        } catch (_: Exception) {}
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        isAppActivelyLaunched = true // Bật từ Tile, launcher icon hoặc shortcut phím tắt đều tự động thu âm
        isUserExplicitlyCancelled = false
        isScreenOffPendingExit = false

        if (isProcessingGemini) {
            GeminiClient.cancelCurrentRequest()
            isProcessingGemini = false
        }

        if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            isAppActivelyLaunched = false
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED && !recorderHelper.isRecording) {
                startVoiceRecording()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isUserExplicitlyCancelled = false

        // Kiểm tra lại theme khi vào lại app
        val prefs = getSharedPreferences("gemini_prefs", Context.MODE_PRIVATE)
        currentThemeStyle = prefs.getString("app_theme_style", "skeuo") ?: "skeuo"
        currentThemeMode = prefs.getString("app_theme_mode", null)
            ?: (if (prefs.getString("watch_color_theme", "dark") == "light") "light" else "dark")
        currentThemeCombined = prefs.getString("app_theme_combined", "${currentThemeStyle}_${currentThemeMode}") ?: "skeuo_dark"
        applyWatchTheme()

        // CHỈ tự động thu âm khi người dùng vừa chủ động bấm mở app
        if (isAppActivelyLaunched) {
            isAppActivelyLaunched = false
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED && !recorderHelper.isRecording) {
                startVoiceRecording()
            }
        } else {
            // Khi người dùng bật lại màn hình: TUYỆT ĐỐI KHÔNG TỰ ĐỘNG GHI ÂM
            if (!recorderHelper.isRecording && !isProcessingGemini) {
                pttContainer.setBackgroundResource(getPttIdleDrawable())
                if (::ivMicIcon.isInitialized) ivMicIcon.setColorFilter(Color.WHITE)
                tvStatus.text = "NHẤN ĐỂ NÓI"
                tvStatus.setTextColor(getStatusIdleColor())
            }
        }
    }

    override fun onPause() {
        super.onPause()
        isAppActivelyLaunched = false
        // LƯU Ý: Không huỷ Gemini request ở đây để hỗ trợ Road Mode khi màn hình tắt / hạ tay lái xe
    }

    override fun onStop() {
        super.onStop()
        isAppActivelyLaunched = false
        // Nếu không có request Gemini nào đang xử lý nền và không ghi âm:
        if (!isProcessingGemini && (::recorderHelper.isInitialized && !recorderHelper.isRecording)) {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (pm?.isInteractive == false) {
                // Màn hình tắt khi rảnh rỗi: đóng task để lần mở tới về Watch Face
                finishAndRemoveTask()
            }
        }
    }

    override fun finish() {
        isUserExplicitlyCancelled = true
        if (::recorderHelper.isInitialized) {
            recorderHelper.cancelRecording()
        }
        GeminiClient.cancelCurrentRequest()
        releaseWakeLock()
        super.finish()
    }

    override fun onBackPressed() {
        // Người dùng chủ động vuốt Back: HUỶ TOÀN BỘ tác vụ
        isUserExplicitlyCancelled = true
        if (::recorderHelper.isInitialized) {
            recorderHelper.cancelRecording()
        }
        GeminiClient.cancelCurrentRequest()
        releaseWakeLock()
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

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        return super.dispatchTouchEvent(ev)
    }

    private fun setupPttListener() {
        pttContainer.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownTime = System.currentTimeMillis()
                    wasRecordingWhenTouchDown = if (::recorderHelper.isInitialized) recorderHelper.isRecording else false

                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(
                            this,
                            arrayOf(Manifest.permission.RECORD_AUDIO),
                            101
                        )
                        return@setOnTouchListener true
                    }

                    if (wasRecordingWhenTouchDown) {
                        // Đang ghi âm mà chạm vào nút đỏ -> rung nhẹ phản hồi nhận diện thao tác bấm dừng
                        vibrateTick(40, 50)
                    } else if (!isProcessingGemini) {
                        // Đang ở nút xanh (chưa ghi âm) -> bắt đầu thu âm ngay
                        startVoiceRecording()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val duration = System.currentTimeMillis() - touchDownTime
                    if (recorderHelper.isRecording) {
                        if (isUserExplicitlyCancelled) {
                            recorderHelper.cancelRecording()
                        } else if (wasRecordingWhenTouchDown) {
                            // Kịch bản 1 & 3: Bấm vào nút đỏ khi đang ghi âm -> ngắt ghi âm và gửi server ngay!
                            Log.d("MainActivity", "Bấm nút đỏ: Dừng ghi âm và gửi server ngay.")
                            finishVoiceRecording()
                        } else if (duration >= 500L) {
                            // Kịch bản 2: Nhấn giữ nút xanh (PTT) rồi nhả tay -> ngắt ghi âm và gửi server ngay!
                            Log.d("MainActivity", "Nhả tay sau khi nhấn giữ PTT: Dừng ghi âm và gửi server.")
                            finishVoiceRecording()
                        } else {
                            // Kịch bản 3 (bước 1): Chạm nhanh nút xanh -> tiếp tục thu âm chờ bấm lại nút đỏ hoặc im lặng 1.3s
                            Log.d("MainActivity", "Chạm nhanh nút xanh: Tiếp tục ghi âm (chờ bấm lại nút đỏ hoặc im lặng 1.3s).")
                        }
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (recorderHelper.isRecording) {
                        cancelVoiceRecording()
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    // Trượt ngón tay ra xa nút mic: tự động hủy ghi âm, tuyệt đối không gửi
                    val distanceX = Math.abs(event.x - pttContainer.width / 2f)
                    val distanceY = Math.abs(event.y - pttContainer.height / 2f)
                    if (distanceX > pttContainer.width * 1.5f || distanceY > pttContainer.height * 1.5f) {
                        if (recorderHelper.isRecording && !isUserExplicitlyCancelled) {
                            cancelVoiceRecording()
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    /**
     * Hủy bỏ toàn bộ quá trình thu âm / gọi Gemini khi trượt ngón tay ra xa - TUYỆT ĐỐI KHÔNG GỬI
     */
    private fun cancelVoiceRecording() {
        isUserExplicitlyCancelled = true
        vibrateTick(80, 120)

        val wasActive = (::recorderHelper.isInitialized && recorderHelper.isRecording) || isProcessingGemini

        if (::recorderHelper.isInitialized && recorderHelper.isRecording) {
            recorderHelper.cancelRecording()
        }

        if (isProcessingGemini) {
            GeminiClient.cancelCurrentRequest()
            isProcessingGemini = false
            releaseWakeLock()
        }

        resetVoiceWaveform()
        pttContainer.setBackgroundResource(getPttIdleDrawable())
        if (::ivMicIcon.isInitialized) ivMicIcon.setColorFilter(Color.WHITE)

        if (wasActive) {
            tvStatus.text = "ĐÃ HỦY"
            tvStatus.setTextColor(Color.parseColor("#9E9E9E"))
            tvResult.text = "Đã hủy bỏ câu lệnh.\nChạm micro bên dưới để nói lại."

            mainHandler.postDelayed({
                if (!recorderHelper.isRecording && !isProcessingGemini && !isFinishing) {
                    tvStatus.text = "NHẤN ĐỂ NÓI"
                    tvStatus.setTextColor(getStatusIdleColor())
                    isUserExplicitlyCancelled = false
                }
            }, 2200)
        } else {
            tvStatus.text = "NHẤN ĐỂ NÓI"
            tvStatus.setTextColor(getStatusIdleColor())
            tvResult.text = "Chạm hoặc nhấn giữ micro bên dưới để hỏi..."
            isUserExplicitlyCancelled = false
        }
    }

    /**
     * 4.1: Hiệu ứng sóng âm thanh thời gian thực (Audio Waveform & Halo Pulse Animation)
     */
    private fun updateVoiceWaveform(normalizedAmp: Float) {
        val targetScale = 1.0f + (normalizedAmp * 0.18f)
        val ringScale = 1.0f + (normalizedAmp * 0.38f)
        val ringAlpha = (0.25f + normalizedAmp * 0.75f).coerceIn(0f, 1f)

        pttContainer.animate()
            .scaleX(targetScale)
            .scaleY(targetScale)
            .setDuration(90L)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()

        if (::viewMicPulseRing.isInitialized) {
            viewMicPulseRing.visibility = View.VISIBLE
            viewMicPulseRing.animate()
                .scaleX(ringScale)
                .scaleY(ringScale)
                .alpha(ringAlpha)
                .setDuration(90L)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        }
    }

    private fun resetVoiceWaveform() {
        pttContainer.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .setDuration(150L)
            .start()

        if (::viewMicPulseRing.isInitialized) {
            viewMicPulseRing.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .alpha(0f)
                .setDuration(150L)
                .withEndAction { viewMicPulseRing.visibility = View.GONE }
                .start()
        }
    }

    private fun startVoiceRecording() {
        isUserExplicitlyCancelled = false
        isScreenOffPendingExit = false
        resetVoiceWaveform()
        vibrateTick(80, 100)
        pttContainer.setBackgroundResource(R.drawable.bg_watch_btn_mic_recording)
        if (::ivMicIcon.isInitialized) ivMicIcon.setColorFilter(Color.WHITE)
        tvStatus.text = "● ĐANG LẮNG NGHE..."
        tvStatus.setTextColor(Color.WHITE)
        tvResult.text = "Đang lắng nghe bạn nói...\n(Dừng nói 1.3s để tự động gửi)"

        val started = recorderHelper.startRecording()
        if (!started) {
            tvStatus.text = "✕ LỖI MICRO"
            tvStatus.setTextColor(Color.parseColor("#9E9E9E"))
            tvResult.text = "Không thể khởi động micro."
            resetVoiceWaveform()
            pttContainer.setBackgroundResource(getPttIdleDrawable())
            if (::ivMicIcon.isInitialized) ivMicIcon.setColorFilter(Color.WHITE)
        }
    }

    private fun finishVoiceRecording() {
        if (!recorderHelper.isRecording || isUserExplicitlyCancelled || isFinishing) return

        resetVoiceWaveform()
        vibrateTick(120, 150)
        pttContainer.setBackgroundResource(getPttIdleDrawable())
        if (::ivMicIcon.isInitialized) ivMicIcon.setColorFilter(Color.WHITE)
        tvStatus.text = "ĐANG XỬ LÝ..."
        tvStatus.setTextColor(Color.parseColor("#9E9E9E"))
        tvResult.text = "Gemini đang xử lý câu trả lời..."

        val audioBase64 = recorderHelper.stopRecording()
        if (audioBase64.isNullOrEmpty()) {
            tvStatus.text = "NHẤN ĐỂ NÓI"
            tvStatus.setTextColor(getStatusIdleColor())
            tvResult.text = "Chưa thu được âm thanh. Hãy chạm hoặc nhấn giữ micro để nói lại."
            if (isScreenOffPendingExit) {
                finishAndRemoveTask()
            }
            return
        }

        // Kích hoạt chế độ xử lý nền (Road Mode WakeLock)
        isProcessingGemini = true
        acquireWakeLock()

        GeminiClient.askGemini(this, audioBase64) { success, question, answer, voiceAction ->
            runOnUiThread {
                isProcessingGemini = false

                if (isUserExplicitlyCancelled) {
                    Log.d("MainActivity", "Bỏ qua kết quả vì người dùng đã chủ động vuốt Back.")
                    releaseWakeLock()
                    return@runOnUiThread
                }

                // Sentinel: Gemini không nhận ra giọng nói (bật mic nhưng không nói gì)
                // -> Đặt lại trạng thái im lặng, không TTS, không hiển thị
                if (success && question.isEmpty() && answer.isEmpty()) {
                    Log.d("MainActivity", "Không có giọng nói -> reset im lặng")
                    tvStatus.text = "NHẤN ĐỂ NÓI"
                    tvStatus.setTextColor(getStatusIdleColor())
                    pttContainer.setBackgroundResource(getPttIdleDrawable())
                    if (::ivMicIcon.isInitialized) ivMicIcon.setColorFilter(Color.WHITE)
                    if (isScreenOffPendingExit) {
                        releaseWakeLock()
                        finishAndRemoveTask()
                    } else {
                        releaseWakeLock()
                    }
                    return@runOnUiThread
                }

                tvStatus.text = if (success) "✓ KẾT QUẢ" else "✕ LỖI"
                tvStatus.setTextColor(if (success) Color.WHITE else Color.parseColor("#9E9E9E"))
                tvResult.text = answer
                scrollResult.smoothScrollTo(0, 0)

                if (success) {
                    // Rung haptic nhịp kép xác nhận thành công (chuyên dụng khi đi đường)
                    vibrateRoadHaptic(success = true)

                    // Tự động thực thi tác vụ báo thức / hẹn giờ / trả lời tin nhắn nếu Gemini nhận diện được
                    if (voiceAction != null) {
                        val executed = VoiceActionHelper.execute(this, voiceAction)
                        if (executed) {
                            val actionLabel = when (voiceAction.type) {
                                "SET_ALARM" -> "⏰ Đã đặt báo thức ${voiceAction.hour}:${String.format("%02d", voiceAction.minute)}"
                                "SET_TIMER" -> {
                                    val m = voiceAction.seconds / 60
                                    val s = voiceAction.seconds % 60
                                    if (s > 0) "⏱ Đã hẹn giờ ${m} phút ${s} giây"
                                    else "⏱ Đã hẹn giờ ${m} phút"
                                }
                                "REPLY_MESSAGE" -> {
                                    if (voiceAction.recipient.isNotEmpty()) {
                                        "💬 Đã gửi trả lời cho ${voiceAction.recipient}: \"${voiceAction.message}\""
                                    } else {
                                        "💬 Đã gửi trả lời tin nhắn: \"${voiceAction.message}\""
                                    }
                                }
                                "CREATE_TASK" -> {
                                    if (voiceAction.due.isNotEmpty()) {
                                        "📝 Đã thêm Google Task (Hạn: ${voiceAction.due}): \"${voiceAction.message}\""
                                    } else {
                                        "📝 Đã thêm Google Task: \"${voiceAction.message}\""
                                    }
                                }
                                "READ_TASKS" -> "📋 Đang lấy danh sách việc cần làm từ điện thoại..."
                                "COMPLETE_TASK" -> "✅ Đang đánh dấu hoàn thành: \"${voiceAction.message}\"..."
                                "SET_REMINDER" -> {
                                    val m = voiceAction.delaySeconds / 60
                                    if (m > 0) "⏰ Đã hẹn nhắc nhở sau $m phút: \"${voiceAction.message}\""
                                    else "⏰ Đã hẹn nhắc nhở: \"${voiceAction.message}\""
                                }
                                "COPY_CLIPBOARD" -> "📋 Đã sao chép vào bộ nhớ tạm điện thoại"
                                else -> ""
                            }
                            if (actionLabel.isNotEmpty()) {
                                tvResult.text = "$answer\n\n$actionLabel"
                            }
                            tvStatus.text = when (voiceAction.type) {
                                "REPLY_MESSAGE" -> "✓ ĐÃ GỬI TIN"
                                "COPY_CLIPBOARD" -> "✓ ĐÃ SAO CHÉP"
                                "CREATE_TASK" -> "✓ ĐÃ THÊM TASK"
                                "READ_TASKS" -> "⏳ ĐANG LẤY TASK"
                                "COMPLETE_TASK" -> "✓ HOÀN THÀNH"
                                "SET_REMINDER" -> "✓ ĐÃ HẸN NHẮC"
                                "MEDIA_CONTROL" -> when (voiceAction.command) {
                                    "OPEN_VIDEO" -> "✓ ĐANG MỞ VIDEO"
                                    "PAUSE" -> "✓ ĐÃ TẠM DỪNG"
                                    "PLAY" -> "✓ ĐANG PHÁT"
                                    "NEXT" -> "✓ CHUYỂN BÀI"
                                    "PREV" -> "✓ BÀI TRƯỚC"
                                    else -> "✓ ĐÃ ĐIỀU KHIỂN"
                                }
                                else -> "✓ ĐÃ THỰC HIỆN"
                            }

                            // Xây dựng câu xác nhận TTS cho báo thức / hẹn giờ
                            // Các tác vụ qua điện thoại (REPLY_MESSAGE, CREATE_TASK, READ_TASKS, COMPLETE_TASK, SET_REMINDER, COPY_CLIPBOARD, MEDIA_CONTROL)
                            // sẽ do Phone Companion tự phát TTS sau khi xử lý thành công để tránh phát lặp
                            val handledByPhoneDirectly = voiceAction.type in listOf(
                                "REPLY_MESSAGE", "CREATE_TASK", "READ_TASKS", "COMPLETE_TASK", "SET_REMINDER", "COPY_CLIPBOARD", "MEDIA_CONTROL"
                            )
                            if (!handledByPhoneDirectly) {
                                val ttsConfirm = when (voiceAction.type) {
                                    "SET_ALARM" -> {
                                        val h = voiceAction.hour
                                        val m = voiceAction.minute
                                        val period = if (h < 12) "sáng" else if (h < 18) "chiều" else "tối"
                                        val displayH = if (h == 0) 12 else if (h > 12) h - 12 else h
                                        if (m == 0) "Đã đặt báo thức lúc $displayH giờ $period"
                                        else "Đã đặt báo thức lúc $displayH giờ $m phút $period"
                                    }
                                    "SET_TIMER" -> {
                                        val totalM = voiceAction.seconds / 60
                                        val totalS = voiceAction.seconds % 60
                                        if (totalS > 0) "Đã hẹn giờ $totalM phút $totalS giây"
                                        else "Đã hẹn giờ $totalM phút"
                                    }
                                    else -> answer
                                }

                                // Gửi câu xác nhận sang điện thoại để đọc TTS
                                val confirmPayload = org.json.JSONObject().apply {
                                    put("question", question)
                                    put("answer", ttsConfirm)
                                    put("timestamp", System.currentTimeMillis())
                                }.toString()
                                PhoneCommunicator.sendTextToPhone(this, confirmPayload)
                            }
                        }
                    }

                    // Nếu không phải voiceAction → gửi answer bình thường sang điện thoại
                    if (voiceAction == null) {
                        val payload = org.json.JSONObject().apply {
                            put("question", question)
                            put("answer", answer)
                            put("timestamp", System.currentTimeMillis())
                        }.toString()
                        PhoneCommunicator.sendTextToPhone(this, payload)
                    }
                } else {
                    // Rung 1 nhịp dài báo lỗi kết nối
                    vibrateRoadHaptic(success = false)
                }

                // Nếu màn hình đã tắt trong lúc chờ Gemini (người dùng hạ tay lái xe):
                // Sau khi đã phát kết quả / gửi Bluetooth TTS xong, tự động thoát app về Màn hình chính
                val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
                val isInteractive = pm?.isInteractive ?: true
                if (isScreenOffPendingExit || !isInteractive) {
                    Log.d("MainActivity", "Road Mode: Đã gửi TTS xong trong nền -> Tự động đóng task về Watch Face")
                    releaseWakeLock()
                    finishAndRemoveTask()
                } else {
                    releaseWakeLock()
                }
            }
        }
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Gemini:RoadModeProcessing")
            }
            wakeLock?.let {
                if (!it.isHeld) {
                    it.acquire(15000) // Tối đa 15s tự động nhả
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Lỗi acquireWakeLock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
        } catch (_: Exception) {}
    }

    private fun vibrateRoadHaptic(success: Boolean) {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        val pattern = if (success) longArrayOf(0, 80, 60, 100) else longArrayOf(0, 300)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
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
