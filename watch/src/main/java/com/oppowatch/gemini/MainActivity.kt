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
import android.os.PowerManager
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
    // Người dùng chủ động huỷ (vuốt Back): huỷ toàn bộ tác vụ
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
                if (recorderHelper.isRecording && !isUserExplicitlyCancelled && !isFinishing) {
                    finishVoiceRecording()
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

        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))

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
        return when (currentThemeCombined) {
            "skeuo_light" -> R.drawable.bg_watch_ptt_skeuo_light
            "glass_dark" -> R.drawable.bg_watch_ptt_glass_dark
            "glass_light" -> R.drawable.bg_watch_ptt_glass_light
            "material_dark" -> R.drawable.bg_watch_ptt_m3
            "material_light" -> R.drawable.bg_watch_ptt_light
            else -> R.drawable.bg_watch_ptt_dark // skeuo_dark
        }
    }

    private fun getStatusIdleColor(): Int {
        return when (currentThemeCombined) {
            "skeuo_light" -> Color.parseColor("#475569")
            "glass_dark" -> Color.parseColor("#7DD3FC")
            "glass_light" -> Color.parseColor("#0284C7")
            "material_dark" -> Color.parseColor("#A7F3D0")
            "material_light" -> Color.parseColor("#334155")
            else -> Color.parseColor("#94A3B8") // skeuo_dark
        }
    }

    private fun getStatusAccentColor(): Int {
        return when (currentThemeCombined) {
            "skeuo_light" -> Color.parseColor("#B45309")
            "glass_dark" -> Color.parseColor("#38BDF8")
            "glass_light" -> Color.parseColor("#0284C7")
            "material_dark" -> Color.parseColor("#80CBC4")
            "material_light" -> Color.parseColor("#0F766E")
            else -> Color.parseColor("#E5C158") // skeuo_dark
        }
    }

    private fun getStatusSuccessColor(): Int {
        return when (currentThemeCombined) {
            "skeuo_light", "glass_light", "material_light" -> Color.parseColor("#059669")
            else -> Color.parseColor("#34D399")
        }
    }

    private fun applyWatchTheme() {
        val isRec = if (::recorderHelper.isInitialized) recorderHelper.isRecording else false

        when (currentThemeCombined) {
            "skeuo_light" -> {
                // Skeuomorphism Light: Thép không gỉ chải xước viền đồng cổ
                layoutRoot.setBackgroundColor(Color.parseColor("#E2E8F0"))
                tvHeaderTitle.setTextColor(Color.parseColor("#92400E"))
                tvStatus.setTextColor(Color.parseColor("#475569"))
                containerResultCard.setBackgroundResource(R.drawable.bg_watch_plate_skeuo_light)
                tvResult.setTextColor(Color.parseColor("#0F172A"))
                if (!isRec) pttContainer.setBackgroundResource(R.drawable.bg_watch_ptt_skeuo_light)
            }
            "glass_dark" -> {
                // Liquid Glass Dark: Kính mờ acrylic phát quang trên nền Cosmic Deep Sapphire
                layoutRoot.setBackgroundColor(Color.parseColor("#070B18"))
                tvHeaderTitle.setTextColor(Color.parseColor("#38BDF8"))
                tvStatus.setTextColor(Color.parseColor("#7DD3FC"))
                containerResultCard.setBackgroundResource(R.drawable.bg_watch_plate_glass_dark)
                tvResult.setTextColor(Color.parseColor("#F8FAFC"))
                if (!isRec) pttContainer.setBackgroundResource(R.drawable.bg_watch_ptt_glass_dark)
            }
            "glass_light" -> {
                // Liquid Glass Light: Kính băng tuyết mờ acrylic trên nền Crystal Ice
                layoutRoot.setBackgroundColor(Color.parseColor("#EDF5FC"))
                tvHeaderTitle.setTextColor(Color.parseColor("#0284C7"))
                tvStatus.setTextColor(Color.parseColor("#0369A1"))
                containerResultCard.setBackgroundResource(R.drawable.bg_watch_plate_glass_light)
                tvResult.setTextColor(Color.parseColor("#0C4A6E"))
                if (!isRec) pttContainer.setBackgroundResource(R.drawable.bg_watch_ptt_glass_light)
            }
            "material_dark" -> {
                // Material 3 Dark
                layoutRoot.setBackgroundColor(Color.parseColor("#121418"))
                tvHeaderTitle.setTextColor(Color.parseColor("#80CBC4"))
                tvStatus.setTextColor(Color.parseColor("#A7F3D0"))
                containerResultCard.setBackgroundResource(R.drawable.bg_watch_plate_m3)
                tvResult.setTextColor(Color.parseColor("#E2E8F0"))
                if (!isRec) pttContainer.setBackgroundResource(R.drawable.bg_watch_ptt_m3)
            }
            "material_light" -> {
                // Material 3 Light: Ceramic trắng tinh tế
                layoutRoot.setBackgroundColor(Color.parseColor("#F8FAFC"))
                tvHeaderTitle.setTextColor(Color.parseColor("#0F766E"))
                tvStatus.setTextColor(Color.parseColor("#334155"))
                containerResultCard.setBackgroundResource(R.drawable.bg_watch_plate_light)
                tvResult.setTextColor(Color.parseColor("#0F172A"))
                if (!isRec) pttContainer.setBackgroundResource(R.drawable.bg_watch_ptt_light)
            }
            else -> {
                // Skeuomorphism Dark: Cơ khí Obsidian Titanium sang trọng
                layoutRoot.setBackgroundColor(Color.parseColor("#000000"))
                tvHeaderTitle.setTextColor(Color.parseColor("#E5C158"))
                tvStatus.setTextColor(Color.parseColor("#94A3B8"))
                containerResultCard.setBackgroundResource(R.drawable.bg_watch_plate_dark)
                tvResult.setTextColor(Color.parseColor("#F1F5F9"))
                if (!isRec) pttContainer.setBackgroundResource(R.drawable.bg_watch_ptt_dark)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(themeReceiver)
        } catch (_: Exception) {}
        try {
            unregisterReceiver(screenOffReceiver)
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
        isUserExplicitlyCancelled = false
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
        isUserExplicitlyCancelled = false
        isScreenOffPendingExit = false
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
        if (!recorderHelper.isRecording || isUserExplicitlyCancelled || isFinishing) return

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

                tvStatus.text = if (success) "✓ ĐÃ TRẢ LỜI" else "LỖI"
                tvStatus.setTextColor(if (success) getStatusSuccessColor() else Color.parseColor("#EF4444"))
                tvResult.text = answer
                scrollResult.smoothScrollTo(0, 0)

                if (success) {
                    // Rung haptic nhịp kép xác nhận thành công (chuyên dụng khi đi đường)
                    vibrateRoadHaptic(success = true)

                    // Tự động thực thi tác vụ báo thức / hẹn giờ nếu Gemini nhận diện được
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
                                else -> ""
                            }
                            if (actionLabel.isNotEmpty()) {
                                tvResult.text = "$answer\n\n$actionLabel"
                            }
                            tvStatus.text = "✓ ĐÃ THỰC HIỆN"
                        }
                    }

                    // Gửi payload sang điện thoại để đọc TTS vào tai nghe / nón bảo hiểm
                    val payload = org.json.JSONObject().apply {
                        put("question", question)
                        put("answer", answer)
                        put("timestamp", System.currentTimeMillis())
                    }.toString()
                    PhoneCommunicator.sendTextToPhone(this, payload)
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