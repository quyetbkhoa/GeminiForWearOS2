package com.oppowatch.gemini.phone

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import org.json.JSONObject

class PhoneMainActivity : AppCompatActivity() {

    private lateinit var filterManager: BluetoothFilterManager
    private lateinit var qaHistoryManager: QaHistoryManager

    // Containers & Roots for Theme Engine
    private lateinit var scrollRoot: NestedScrollView
    private lateinit var cardThemeSelector: LinearLayout
    private lateinit var cardTitlePlate: LinearLayout
    private lateinit var cardApiKey: LinearLayout
    private lateinit var cardModelSelector: LinearLayout
    private lateinit var cardBluetoothRack: LinearLayout
    private lateinit var cardHistoryRack: LinearLayout
    private lateinit var cardUpdatePanel: LinearLayout

    // Theme Engine Views
    private lateinit var tvThemeLabel: TextView
    private lateinit var tvThemeSublabel: TextView
    private lateinit var btnThemeSkeuo: Button
    private lateinit var btnThemeGlass: Button
    private lateinit var btnThemeMaterial: Button
    private lateinit var btnWatchThemeDark: Button
    private lateinit var btnWatchThemeLight: Button

    // Gemini Model Selector Views
    private lateinit var tvModelHeader: TextView
    private lateinit var tvModelDesc: TextView
    private lateinit var rgGeminiModels: RadioGroup
    private lateinit var rbModel38Flash: RadioButton
    private lateinit var rbModel37Flash: RadioButton
    private lateinit var rbModel35Flash: RadioButton
    private lateinit var rbModel35FlashLite: RadioButton
    private lateinit var rbModel25Flash: RadioButton
    private lateinit var rbModel31Pro: RadioButton
    private lateinit var tvModelStatus: TextView

    // UI elements - Header
    private lateinit var tvMainTitle: TextView
    private lateinit var tvMainSubtitle: TextView

    // UI elements - Custom API Key
    private lateinit var tvApiKeyHeader: TextView
    private lateinit var tvApiKeyDesc: TextView
    private lateinit var etGeminiApiKey: EditText
    private lateinit var btnToggleApiVisibility: Button
    private lateinit var tvApiKeyStatus: TextView
    private lateinit var btnSaveApiKey: Button
    private var isApiKeyVisible = false

    // Bluetooth Section
    private lateinit var tvBluetoothHeader: TextView
    private lateinit var llBluetoothDevices: LinearLayout
    private lateinit var tvEmptyDevices: TextView
    private lateinit var btnReloadBluetooth: Button
    private lateinit var btnTestTts: Button

    // Q&A History
    private lateinit var tvHistoryHeader: TextView
    private lateinit var llQaHistory: LinearLayout
    private lateinit var tvEmptyHistory: TextView
    private lateinit var btnClearHistory: Button

    // GitHub Update Section
    private lateinit var tvAppVersion: TextView
    private lateinit var tvRepoInfo: TextView
    private lateinit var tvUpdateStatus: TextView
    private lateinit var pbUpdateProgress: ProgressBar
    private lateinit var btnCheckUpdate: Button

    private var currentThemeStyle = ThemeManager.ThemeStyle.SKEUOMORPHISM
    private var currentColorMode = ThemeManager.ColorMode.DARK

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadQaHistory()
        }
    }

    private val wearMessageListener = com.google.android.gms.wearable.MessageClient.OnMessageReceivedListener { messageEvent ->
        if (messageEvent.path == "/gemini_tts_payload") {
            val rawPayload = String(messageEvent.data, Charsets.UTF_8)
            var question = "Câu hỏi từ đồng hồ"
            var answer = rawPayload
            var timestamp = System.currentTimeMillis()
            try {
                val json = org.json.JSONObject(rawPayload)
                if (json.has("answer")) {
                    answer = json.optString("answer", rawPayload)
                    question = json.optString("question", "Câu hỏi bằng giọng nói")
                    timestamp = json.optLong("timestamp", System.currentTimeMillis())
                }
            } catch (_: Exception) {}
            qaHistoryManager.addEntry(question, answer, timestamp)
            runOnUiThread {
                loadQaHistory()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_phone_main)

        filterManager = BluetoothFilterManager(this)
        qaHistoryManager = QaHistoryManager(this)
        TtsSpeaker.init(this)

        initViews()
        setupThemeEngine()
        setupModelSection()
        setupApiKeySection()
        setupQaHistorySection()
        setupUpdateSection()

        checkPermissions()
        checkPermissionsAndLoadDevices(userInitiated = false)

        btnReloadBluetooth.setOnClickListener {
            checkPermissionsAndLoadDevices(userInitiated = true)
        }

        btnTestTts.setOnClickListener {
            TtsSpeaker.speak(this, "Đây là âm thanh thử nghiệm từ trợ lý Gemini trên đồng hồ OPPO Watch.")
            Toast.makeText(this, "Đang phát âm thanh mẫu...", Toast.LENGTH_SHORT).show()
        }

        val filter = IntentFilter("com.oppowatch.gemini.TTS_RECEIVED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    private fun initViews() {
        scrollRoot = findViewById(R.id.scroll_root)
        cardThemeSelector = findViewById(R.id.card_theme_selector)
        cardModelSelector = findViewById(R.id.card_model_selector)
        cardTitlePlate = findViewById(R.id.card_title_plate)
        cardApiKey = findViewById(R.id.card_api_key)
        cardBluetoothRack = findViewById(R.id.card_bluetooth_rack)
        cardHistoryRack = findViewById(R.id.card_history_rack)
        cardUpdatePanel = findViewById(R.id.card_update_panel)

        tvThemeLabel = findViewById(R.id.tv_theme_label)
        tvThemeSublabel = findViewById(R.id.tv_theme_sublabel)
        btnThemeSkeuo = findViewById(R.id.btn_theme_skeuo)
        btnThemeGlass = findViewById(R.id.btn_theme_glass)
        btnThemeMaterial = findViewById(R.id.btn_theme_material)
        btnWatchThemeDark = findViewById(R.id.btn_watch_theme_dark)
        btnWatchThemeLight = findViewById(R.id.btn_watch_theme_light)

        tvModelHeader = findViewById(R.id.tv_model_header)
        tvModelDesc = findViewById(R.id.tv_model_desc)
        rgGeminiModels = findViewById(R.id.rg_gemini_models)
        rbModel38Flash = findViewById(R.id.rb_model_38_flash)
        rbModel37Flash = findViewById(R.id.rb_model_37_flash)
        rbModel35Flash = findViewById(R.id.rb_model_35_flash)
        rbModel35FlashLite = findViewById(R.id.rb_model_35_flash_lite)
        rbModel25Flash = findViewById(R.id.rb_model_25_flash)
        rbModel31Pro = findViewById(R.id.rb_model_31_pro)
        tvModelStatus = findViewById(R.id.tv_model_status)

        tvMainTitle = findViewById(R.id.tv_main_title)
        tvMainSubtitle = findViewById(R.id.tv_main_subtitle)

        tvApiKeyHeader = findViewById(R.id.tv_api_key_header)
        tvApiKeyDesc = findViewById(R.id.tv_api_key_desc)
        etGeminiApiKey = findViewById(R.id.et_gemini_api_key)
        btnToggleApiVisibility = findViewById(R.id.btn_toggle_api_visibility)
        tvApiKeyStatus = findViewById(R.id.tv_api_key_status)
        btnSaveApiKey = findViewById(R.id.btn_save_api_key)

        tvBluetoothHeader = findViewById(R.id.tv_bluetooth_header)
        llBluetoothDevices = findViewById(R.id.ll_bluetooth_devices_list)
        tvEmptyDevices = findViewById(R.id.tv_empty_devices)
        btnReloadBluetooth = findViewById(R.id.btn_reload_bluetooth)
        btnTestTts = findViewById(R.id.btn_test_tts)

        tvHistoryHeader = findViewById(R.id.tv_history_header)
        llQaHistory = findViewById(R.id.ll_qa_history_list)
        tvEmptyHistory = findViewById(R.id.tv_empty_history)
        btnClearHistory = findViewById(R.id.btn_clear_history)

        tvAppVersion = findViewById(R.id.tv_app_version)
        tvRepoInfo = findViewById(R.id.tv_repo_info)
        tvUpdateStatus = findViewById(R.id.tv_update_status)
        pbUpdateProgress = findViewById(R.id.pb_update_progress)
        btnCheckUpdate = findViewById(R.id.btn_check_update)
    }

    private fun setupThemeEngine() {
        currentThemeStyle = ThemeManager.getStyle(this)
        currentColorMode = ThemeManager.getColorMode(this)
        applyTheme(currentThemeStyle, currentColorMode, syncToWatch = false)

        btnThemeSkeuo.setOnClickListener {
            applyTheme(ThemeManager.ThemeStyle.SKEUOMORPHISM, currentColorMode, syncToWatch = true)
        }
        btnThemeGlass.setOnClickListener {
            applyTheme(ThemeManager.ThemeStyle.LIQUID_GLASS, currentColorMode, syncToWatch = true)
        }
        btnThemeMaterial.setOnClickListener {
            applyTheme(ThemeManager.ThemeStyle.MATERIAL, currentColorMode, syncToWatch = true)
        }
        btnWatchThemeDark.setOnClickListener {
            applyTheme(currentThemeStyle, ThemeManager.ColorMode.DARK, syncToWatch = true)
        }
        btnWatchThemeLight.setOnClickListener {
            applyTheme(currentThemeStyle, ThemeManager.ColorMode.LIGHT, syncToWatch = true)
        }
    }

    private fun applyTheme(
        style: ThemeManager.ThemeStyle,
        mode: ThemeManager.ColorMode,
        syncToWatch: Boolean
    ) {
        currentThemeStyle = style
        currentColorMode = mode
        ThemeManager.setStyle(this, style)
        ThemeManager.setColorMode(this, mode)
        val isLight = (mode == ThemeManager.ColorMode.LIGHT)
        val config = ThemeManager.getConfig(style, mode)

        // Root Background
        scrollRoot.setBackgroundColor(config.rootBgColor)

        // Cards & Containers
        cardThemeSelector.setBackgroundResource(config.cardDrawable)
        cardModelSelector.setBackgroundResource(config.cardDrawable)
        cardTitlePlate.setBackgroundResource(config.cardDrawable)
        cardApiKey.setBackgroundResource(config.cardDrawable)
        cardBluetoothRack.setBackgroundResource(config.bezelDrawable)
        cardHistoryRack.setBackgroundResource(config.bezelDrawable)
        cardUpdatePanel.setBackgroundResource(config.panelDrawable)

        // Input & Controls
        etGeminiApiKey.setBackgroundResource(config.inputDrawable)
        btnToggleApiVisibility.setBackgroundResource(config.btnPrimaryDrawable)
        btnSaveApiKey.setBackgroundResource(config.btnEmeraldDrawable)
        btnReloadBluetooth.setBackgroundResource(config.btnPrimaryDrawable)
        btnClearHistory.setBackgroundResource(config.btnCrimsonDrawable)
        btnTestTts.setBackgroundResource(config.btnEmeraldDrawable)
        btnCheckUpdate.setBackgroundResource(config.btnGoldDrawable)

        // Typography Colors
        tvThemeLabel.setTextColor(config.titleTextColor)
        tvThemeSublabel.setTextColor(config.textSecondaryColor)
        tvModelHeader.setTextColor(config.titleTextColor)
        tvModelDesc.setTextColor(config.textSecondaryColor)
        tvMainTitle.setTextColor(config.titleTextColor)
        tvApiKeyHeader.setTextColor(config.headerApiKeyColor)
        tvApiKeyDesc.setTextColor(config.textSecondaryColor)
        tvBluetoothHeader.setTextColor(config.headerBluetoothColor)
        tvHistoryHeader.setTextColor(config.headerHistoryColor)

        val radioTextColor = if (isLight) Color.parseColor("#0F172A") else Color.parseColor("#E2E8F0")
        rbModel38Flash.setTextColor(if (isLight) Color.parseColor("#B45309") else Color.parseColor("#F59E0B"))
        rbModel37Flash.setTextColor(radioTextColor)
        rbModel35Flash.setTextColor(radioTextColor)
        rbModel35FlashLite.setTextColor(radioTextColor)
        rbModel25Flash.setTextColor(radioTextColor)
        rbModel31Pro.setTextColor(radioTextColor)

        // Highlight Active Style Button
        btnThemeSkeuo.setBackgroundResource(
            if (style == ThemeManager.ThemeStyle.SKEUOMORPHISM) config.btnGoldDrawable else config.btnPrimaryDrawable
        )
        btnThemeGlass.setBackgroundResource(
            if (style == ThemeManager.ThemeStyle.LIQUID_GLASS) config.btnGoldDrawable else config.btnPrimaryDrawable
        )
        btnThemeMaterial.setBackgroundResource(
            if (style == ThemeManager.ThemeStyle.MATERIAL) config.btnGoldDrawable else config.btnPrimaryDrawable
        )

        // Highlight Active Mode Button
        btnWatchThemeDark.setBackgroundResource(
            if (!isLight) config.btnGoldDrawable else config.btnPrimaryDrawable
        )
        btnWatchThemeLight.setBackgroundResource(
            if (isLight) config.btnGoldDrawable else config.btnPrimaryDrawable
        )

        // Refresh dynamic device & history views to adopt new theme drawables
        loadPairedBluetoothDevices(userInitiated = false)
        loadQaHistory()

        if (syncToWatch) {
            syncThemeToWatch(style.id, mode.id)
            Toast.makeText(
                this,
                "✓ Đã áp dụng: ${style.title} - ${mode.title} (Đồng bộ Phone & Watch)!",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun syncThemeToWatch(styleId: String, modeId: String) {
        val combined = "${styleId}_${modeId}"
        val payload = JSONObject().apply {
            put("style", styleId)
            put("mode", modeId)
            put("combined", combined)
        }.toString()

        // 1. Persistent sync qua DataClient (tự động đồng bộ ngay khi đồng hồ kết nối)
        try {
            val putDataReq = PutDataMapRequest.create("/gemini_theme_config").apply {
                dataMap.putString("style", styleId)
                dataMap.putString("mode", modeId)
                dataMap.putString("combined", combined)
                dataMap.putLong("timestamp", System.currentTimeMillis())
                setUrgent()
            }.asPutDataRequest().setUrgent()

            Wearable.getDataClient(this).putDataItem(putDataReq).addOnSuccessListener {
                android.util.Log.d("PhoneMainActivity", "Đã lưu theme vào DataClient: $combined")
            }.addOnFailureListener { e ->
                android.util.Log.e("PhoneMainActivity", "Lỗi lưu theme DataClient: ${e.message}")
            }
        } catch (e: Exception) {
            android.util.Log.e("PhoneMainActivity", "Exception PutDataMapRequest: ${e.message}")
        }

        // 2. Real-time broadcast qua MessageClient tới các node đang kết nối
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            for (node in nodes) {
                Wearable.getMessageClient(this).sendMessage(
                    node.id,
                    "/app_theme_sync",
                    payload.toByteArray(Charsets.UTF_8)
                )
                Wearable.getMessageClient(this).sendMessage(
                    node.id,
                    "/watch_color_theme",
                    modeId.toByteArray(Charsets.UTF_8)
                )
            }
        }
    }

    private fun setupModelSection() {
        val currentModel = ThemeManager.getSelectedModel(this)
        when (currentModel) {
            "gemini-3.8-flash" -> rbModel38Flash.isChecked = true
            "gemini-3.7-flash" -> rbModel37Flash.isChecked = true
            "gemini-3.5-flash" -> rbModel35Flash.isChecked = true
            "gemini-3.5-flash-lite" -> rbModel35FlashLite.isChecked = true
            "gemini-2.5-flash" -> rbModel25Flash.isChecked = true
            "gemini-3.1-pro-preview" -> rbModel31Pro.isChecked = true
            else -> rbModel38Flash.isChecked = true
        }
        updateModelStatusText(currentModel)

        rgGeminiModels.setOnCheckedChangeListener { _, checkedId ->
            val (selectedId, name) = when (checkedId) {
                R.id.rb_model_38_flash -> Pair("gemini-3.8-flash", "Gemini 3.8 Flash")
                R.id.rb_model_37_flash -> Pair("gemini-3.7-flash", "Gemini 3.7 Flash")
                R.id.rb_model_35_flash -> Pair("gemini-3.5-flash", "Gemini 3.5 Flash")
                R.id.rb_model_35_flash_lite -> Pair("gemini-3.5-flash-lite", "Gemini 3.5 Flash-Lite")
                R.id.rb_model_25_flash -> Pair("gemini-2.5-flash", "Gemini 2.5 Flash")
                R.id.rb_model_31_pro -> Pair("gemini-3.1-pro-preview", "Gemini 3.1 Pro Preview")
                else -> Pair("gemini-3.8-flash", "Gemini 3.8 Flash")
            }
            ThemeManager.setSelectedModel(this, selectedId)
            updateModelStatusText(selectedId)
            syncModelToWatch(selectedId, name)
        }
    }

    private fun updateModelStatusText(modelId: String) {
        val displayName = when (modelId) {
            "gemini-3.8-flash" -> "Gemini 3.8 Flash (Mới nhất)"
            "gemini-3.7-flash" -> "Gemini 3.7 Flash"
            "gemini-3.5-flash" -> "Gemini 3.5 Flash"
            "gemini-3.5-flash-lite" -> "Gemini 3.5 Flash-Lite"
            "gemini-2.5-flash" -> "Gemini 2.5 Flash"
            "gemini-3.1-pro-preview" -> "Gemini 3.1 Pro Preview"
            else -> modelId
        }
        tvModelStatus.text = "✓ Đang sử dụng: $displayName"
    }

    private fun syncModelToWatch(modelId: String, modelName: String) {
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            for (node in nodes) {
                Wearable.getMessageClient(this).sendMessage(
                    node.id,
                    "/gemini_model_sync",
                    modelId.toByteArray(Charsets.UTF_8)
                )
            }
            Toast.makeText(
                this,
                "✓ Đã chọn $modelName (Đồng bộ sang đồng hồ)",
                Toast.LENGTH_SHORT
            ).show()
        }.addOnFailureListener {
            Toast.makeText(this, "✓ Đã chọn $modelName", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupApiKeySection() {
        val prefs = getSharedPreferences("gemini_prefs", Context.MODE_PRIVATE)
        val savedKey = prefs.getString("custom_api_key", "") ?: ""
        if (savedKey.isNotEmpty()) {
            etGeminiApiKey.setText(savedKey)
            tvApiKeyStatus.text = "✓ Đang dùng API Key riêng (đã lưu trên thiết bị)"
            tvApiKeyStatus.setTextColor(0xFF34D399.toInt())
        }

        btnToggleApiVisibility.setOnClickListener {
            isApiKeyVisible = !isApiKeyVisible
            if (isApiKeyVisible) {
                etGeminiApiKey.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                btnToggleApiVisibility.text = "🔒"
            } else {
                etGeminiApiKey.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                btnToggleApiVisibility.text = "👁️"
            }
            etGeminiApiKey.setSelection(etGeminiApiKey.text.length)
        }

        btnSaveApiKey.setOnClickListener {
            val key = etGeminiApiKey.text.toString().trim()
            if (key.isEmpty()) {
                prefs.edit().remove("custom_api_key").apply()
                tvApiKeyStatus.text = "Đã xóa API Key riêng. Đang dùng cấu hình mặc định."
                tvApiKeyStatus.setTextColor(0xFF94A3B8.toInt())
                Toast.makeText(this, "Đã xóa API Key riêng", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prefs.edit().putString("custom_api_key", key).apply()
            tvApiKeyStatus.text = "✓ Đang đồng bộ sang đồng hồ qua Bluetooth..."
            tvApiKeyStatus.setTextColor(0xFFF59E0B.toInt())

            Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
                if (nodes.isEmpty()) {
                    tvApiKeyStatus.text = "✓ Đã lưu trên máy. Đang chờ kết nối đồng hồ..."
                    tvApiKeyStatus.setTextColor(0xFFF59E0B.toInt())
                    Toast.makeText(this, "Đã lưu API Key! Đồng hồ chưa kết nối qua Bluetooth.", Toast.LENGTH_LONG).show()
                } else {
                    for (node in nodes) {
                        Wearable.getMessageClient(this).sendMessage(
                            node.id,
                            "/gemini_api_key_sync",
                            key.toByteArray(Charsets.UTF_8)
                        )
                    }
                    tvApiKeyStatus.text = "✓ Đã đồng bộ sang ${nodes.size} đồng hồ qua Wearable Layer!"
                    tvApiKeyStatus.setTextColor(0xFF34D399.toInt())
                    Toast.makeText(this, "✓ Đã đồng bộ API Key sang đồng hồ!", Toast.LENGTH_SHORT).show()
                }
            }.addOnFailureListener {
                tvApiKeyStatus.text = "✓ Đã lưu trên điện thoại."
                Toast.makeText(this, "Đã lưu trên điện thoại.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupQaHistorySection() {
        btnClearHistory.setOnClickListener {
            qaHistoryManager.clearHistory()
            loadQaHistory()
            Toast.makeText(this, "Đã xóa toàn bộ lịch sử hỏi đáp.", Toast.LENGTH_SHORT).show()
        }
        loadQaHistory()
    }

    private fun loadQaHistory() {
        val history = qaHistoryManager.getHistory()
        llQaHistory.removeAllViews()

        if (history.isEmpty()) {
            tvEmptyHistory.visibility = View.VISIBLE
            return
        }

        tvEmptyHistory.visibility = View.GONE
        val config = ThemeManager.getConfig(currentThemeStyle, currentColorMode)

        for (item in history) {
            val itemView = LayoutInflater.from(this).inflate(
                R.layout.item_qa_history,
                llQaHistory,
                false
            )

            itemView.setBackgroundResource(config.cardDrawable)

            val tvTime = itemView.findViewById<TextView>(R.id.tv_qa_time)
            val tvQuestion = itemView.findViewById<TextView>(R.id.tv_qa_question)
            val tvAnswer = itemView.findViewById<TextView>(R.id.tv_qa_answer)
            val btnReplay = itemView.findViewById<Button>(R.id.btn_replay_tts)

            btnReplay.setBackgroundResource(config.btnEmeraldDrawable)

            tvTime.text = "🕒 ${item.getFormattedTime()}"
            tvQuestion.text = item.question
            tvAnswer.text = item.answer

            btnReplay.setOnClickListener {
                TtsSpeaker.speak(this, item.answer)
                Toast.makeText(this, "Đang phát lại câu trả lời...", Toast.LENGTH_SHORT).show()
            }

            llQaHistory.addView(itemView)
        }
    }

    private fun setupUpdateSection() {
        val currentVersion = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.0"
        } catch (_: Exception) { "1.0.0" }
        tvAppVersion.text = "Phiên bản: v$currentVersion"

        btnCheckUpdate.setOnClickListener {
            btnCheckUpdate.isEnabled = false
            tvUpdateStatus.text = "Đang kiểm tra GitHub Releases..."
            pbUpdateProgress.visibility = View.VISIBLE
            pbUpdateProgress.isIndeterminate = true

            GitHubUpdateManager.checkUpdate(this) { result ->
                pbUpdateProgress.isIndeterminate = false
                btnCheckUpdate.isEnabled = true

                result.onSuccess { info ->
                    if (info.hasUpdate) {
                        tvUpdateStatus.text = "Phát hiện bản mới: ${info.tagName}!\nBấm để bắt đầu cập nhật cả Phone & Watch."
                        btnCheckUpdate.text = "CẬP NHẬT NGAY LÊN ${info.tagName}"
                        btnCheckUpdate.setOnClickListener {
                            performUpdate(info)
                        }
                    } else {
                        tvUpdateStatus.text = "Bạn đang dùng bản mới nhất (${info.tagName}).\n(Bấm lại nếu muốn tải đè bản mới nhất từ GitHub)."
                        btnCheckUpdate.text = "CẬP NHẬT ĐÈ BẢN HIỆN TẠI"
                        btnCheckUpdate.setOnClickListener {
                            performUpdate(info)
                        }
                    }
                }.onFailure { err ->
                    tvUpdateStatus.text = "Lỗi kiểm tra: ${err.message}"
                    pbUpdateProgress.visibility = View.GONE
                }
            }
        }
    }

    private fun performUpdate(info: GitHubUpdateManager.UpdateInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!packageManager.canRequestPackageInstalls()) {
                Toast.makeText(this, "Vui lòng bật quyền 'Cài đặt ứng dụng không rõ nguồn' để cập nhật", Toast.LENGTH_LONG).show()
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = android.net.Uri.parse("package:$packageName")
                }
                startActivity(intent)
                return
            }
        }

        btnCheckUpdate.isEnabled = false
        pbUpdateProgress.visibility = View.VISIBLE
        pbUpdateProgress.progress = 0

        GitHubUpdateManager.startFullUpdate(this, info, object : GitHubUpdateManager.UpdateProgressListener {
            override fun onStatus(message: String) {
                tvUpdateStatus.text = message
            }

            override fun onProgress(stage: String, percent: Int) {
                pbUpdateProgress.progress = percent
                tvUpdateStatus.text = "$stage: $percent%"
            }

            override fun onComplete() {
                tvUpdateStatus.text = "✓ Đã hoàn tất! Đồng hồ đang mở hộp thoại cài đặt bản mới."
                pbUpdateProgress.visibility = View.GONE
                btnCheckUpdate.isEnabled = true
                btnCheckUpdate.text = "KIỂM TRA CẬP NHẬT"
            }

            override fun onError(error: String) {
                tvUpdateStatus.text = "Lỗi: $error"
                pbUpdateProgress.visibility = View.GONE
                btnCheckUpdate.isEnabled = true
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
    }

    override fun onResume() {
        super.onResume()
        checkPermissionsAndLoadDevices(userInitiated = false)
        loadQaHistory()
        try {
            Wearable.getMessageClient(this).addListener(wearMessageListener)
        } catch (_: Exception) {}
    }

    override fun onPause() {
        super.onPause()
        try {
            Wearable.getMessageClient(this).removeListener(wearMessageListener)
        } catch (_: Exception) {}
    }

    private fun checkPermissions() {
        val permissionsNeeded = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toTypedArray(), 201)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 201) {
            val connectGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            } else true

            if (connectGranted) {
                loadPairedBluetoothDevices(userInitiated = true)
            } else {
                tvEmptyDevices.text = "Bạn chưa cấp quyền 'Thiết bị ở gần (Bluetooth)'.\nBấm vào đây để mở Cài đặt ứng dụng và cấp quyền."
                tvEmptyDevices.visibility = View.VISIBLE
                tvEmptyDevices.setOnClickListener {
                    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                }
            }
        }
    }

    private fun checkPermissionsAndLoadDevices(userInitiated: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
                tvEmptyDevices.text = "Cần quyền 'Thiết bị ở gần' để tìm tai nghe Bluetooth.\nBấm vào đây để cấp quyền."
                tvEmptyDevices.visibility = View.VISIBLE
                tvEmptyDevices.setOnClickListener {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN),
                        201
                    )
                }
                if (userInitiated) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN),
                        201
                    )
                }
                return
            }
        }

        loadPairedBluetoothDevices(userInitiated)
    }

    private fun loadPairedBluetoothDevices(userInitiated: Boolean = false) {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        llBluetoothDevices.removeAllViews()

        if (adapter == null) {
            tvEmptyDevices.text = "Thiết bị này không hỗ trợ Bluetooth."
            tvEmptyDevices.visibility = View.VISIBLE
            return
        }

        if (!adapter.isEnabled) {
            tvEmptyDevices.text = "Bluetooth trên điện thoại đang TẮT.\nVui lòng bật Bluetooth và bấm '🔄 LÀM MỚI'."
            tvEmptyDevices.visibility = View.VISIBLE
            if (userInitiated) {
                Toast.makeText(this, "Vui lòng bật Bluetooth!", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val paired = try {
            adapter.bondedDevices?.toList() ?: emptyList()
        } catch (e: SecurityException) {
            android.util.Log.e("PhoneMainActivity", "Lỗi quyền đọc bondedDevices", e)
            emptyList()
        }

        if (paired.isEmpty()) {
            tvEmptyDevices.text = "Chưa tìm thấy thiết bị Bluetooth nào đã ghép nối.\nHãy vào Cài đặt điện thoại kết nối tai nghe, sau đó bấm '🔄 LÀM MỚI'."
            tvEmptyDevices.visibility = View.VISIBLE
            if (userInitiated) {
                Toast.makeText(this, "Chưa tìm thấy thiết bị Bluetooth nào!", Toast.LENGTH_SHORT).show()
            }
            return
        }

        tvEmptyDevices.visibility = View.GONE
        val config = ThemeManager.getConfig(currentThemeStyle, currentColorMode)

        for (device in paired) {
            val itemView = LayoutInflater.from(this).inflate(
                R.layout.item_bluetooth_device,
                llBluetoothDevices,
                false
            )

            itemView.setBackgroundResource(config.cardDrawable)

            val name = try { device.name ?: "Thiết bị không tên" } catch (_: SecurityException) { "Thiết bị Bluetooth" }
            val mac = device.address

            val tvName = itemView.findViewById<TextView>(R.id.tv_device_name)
            val tvMac = itemView.findViewById<TextView>(R.id.tv_device_mac)
            val tvSwitch = itemView.findViewById<TextView>(R.id.tv_switch_status)
            val tvIcon = itemView.findViewById<TextView>(R.id.tv_device_icon)

            tvName.text = name
            tvMac.text = mac

            fun updateSwitchUi(isSelected: Boolean) {
                if (isSelected) {
                    tvSwitch.text = "BẬT"
                    tvSwitch.setTextColor(config.switchOnTextColor)
                    tvSwitch.setBackgroundResource(config.switchOnDrawable)
                } else {
                    tvSwitch.text = "TẮT"
                    tvSwitch.setTextColor(config.switchOffTextColor)
                    tvSwitch.setBackgroundResource(config.switchOffDrawable)
                }
            }

            updateSwitchUi(filterManager.isDeviceSelected(mac))

            val lowerName = name.lowercase()
            tvIcon.text = when {
                lowerName.contains("watch") -> "⌚"
                lowerName.contains("soundcore") || lowerName.contains("buds") || lowerName.contains("ear") || lowerName.contains("headphone") -> "🎧"
                lowerName.contains("speaker") || lowerName.contains("loa") -> "🔊"
                else -> "📻"
            }

            itemView.setOnClickListener {
                val wasSelected = filterManager.isDeviceSelected(mac)
                val newSelected = !wasSelected
                filterManager.setDeviceSelected(mac, newSelected)
                updateSwitchUi(newSelected)
                Toast.makeText(
                    this,
                    "${if (newSelected) "Đã bật phát TTS" else "Đã tắt phát TTS"}: $name",
                    Toast.LENGTH_SHORT
                ).show()
            }

            llBluetoothDevices.addView(itemView)
        }

        if (userInitiated) {
            Toast.makeText(this, "Đã làm mới: ${paired.size} thiết bị", Toast.LENGTH_SHORT).show()
        }
    }
}