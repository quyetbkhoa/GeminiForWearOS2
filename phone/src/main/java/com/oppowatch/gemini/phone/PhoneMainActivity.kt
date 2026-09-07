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
import android.widget.HorizontalScrollView
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
import android.util.Log
import com.google.android.gms.wearable.Wearable
import org.json.JSONObject
import java.io.File
import kotlin.concurrent.thread

class PhoneMainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PhoneMainActivity"
    }

    private lateinit var filterManager: BluetoothFilterManager
    private lateinit var qaHistoryManager: QaHistoryManager
    private lateinit var apiErrorLogManager: ApiErrorLogManager

    // Containers & Roots for Theme Engine
    private lateinit var scrollRoot: NestedScrollView
    private lateinit var cardThemeSelector: LinearLayout
    private lateinit var cardTitlePlate: LinearLayout
    private lateinit var cardApiKey: LinearLayout
    private lateinit var cardModelSelector: LinearLayout
    private lateinit var cardBluetoothRack: LinearLayout
    private lateinit var cardHistoryRack: LinearLayout
    private lateinit var cardErrorLogsRack: LinearLayout
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
    private lateinit var btnTestApiKey: Button
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

    // API Error Logs
    private lateinit var tvErrorLogsHeader: TextView
    private lateinit var btnClearErrorLogs: Button
    private lateinit var llErrorLogsList: LinearLayout
    private lateinit var tvEmptyErrorLogs: TextView

    // GitHub Update Section
    private lateinit var tvAppVersion: TextView
    private lateinit var tvRepoInfo: TextView
    private lateinit var tvUpdateStatus: TextView
    private lateinit var pbUpdateProgress: ProgressBar
    private lateinit var btnCheckUpdate: Button

    // Wireless ADB Section
    private lateinit var cardAdbPanel: LinearLayout
    private lateinit var tvAdbHeader: TextView
    private lateinit var tvAdbBadge: TextView
    private lateinit var tvAdbInstructions: TextView
    private lateinit var etWatchAdbIp: EditText
    private lateinit var etWatchAdbPort: EditText
    private lateinit var btnAutoDetectIp: Button
    private lateinit var btnAdbInstall: Button
    private lateinit var pbAdbProgress: ProgressBar
    private lateinit var tvAdbStatus: TextView

    private var currentThemeStyle = ThemeManager.ThemeStyle.SKEUOMORPHISM
    private var currentColorMode = ThemeManager.ColorMode.DARK

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadQaHistory()
            loadErrorLogs()
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
        } else if (messageEvent.path == "/gemini_error_log") {
            val rawJson = String(messageEvent.data, Charsets.UTF_8)
            try {
                val json = org.json.JSONObject(rawJson)
                val item = ApiErrorItem(
                    id = java.util.UUID.randomUUID().toString(),
                    timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                    statusCode = json.optInt("statusCode", 0),
                    errorType = json.optString("errorType", "HTTP_ERROR"),
                    model = json.optString("model", ""),
                    apiKeyMasked = json.optString("apiKeyMasked", ""),
                    errorMessage = json.optString("errorMessage", ""),
                    errorStatus = json.optString("errorStatus", ""),
                    rawResponse = json.optString("rawResponse", ""),
                    suggestion = json.optString("suggestion", ""),
                    source = json.optString("source", "WATCH")
                )
                apiErrorLogManager.addError(item)
            } catch (_: Exception) {}
            runOnUiThread {
                loadErrorLogs()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_phone_main)

        filterManager = BluetoothFilterManager(this)
        qaHistoryManager = QaHistoryManager(this)
        apiErrorLogManager = ApiErrorLogManager(this)
        TtsSpeaker.init(this)

        initViews()
        setupThemeEngine()
        setupModelSection()
        setupApiKeySection()
        setupQaHistorySection()
        setupErrorLogsSection()
        setupUpdateSection()
        setupAdbSection()

        checkPermissions()
        checkPermissionsAndLoadDevices(userInitiated = false)
        autoSyncApiKeyToWatch()

        btnReloadBluetooth.setOnClickListener {
            checkPermissionsAndLoadDevices(userInitiated = true)
        }

        btnTestTts.setOnClickListener {
            TtsSpeaker.speak(this, "Đây là âm thanh thử nghiệm từ trợ lý Gemini trên đồng hồ OPPO Watch.")
            Toast.makeText(this, "Đang phát âm thanh mẫu...", Toast.LENGTH_SHORT).show()
        }

        val filter = IntentFilter().apply {
            addAction("com.oppowatch.gemini.TTS_RECEIVED")
            addAction("com.oppowatch.gemini.ERROR_LOG_RECEIVED")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }

        Wearable.getMessageClient(this).addListener(wearMessageListener)
    }

    private fun initViews() {
        scrollRoot = findViewById(R.id.scroll_root)
        cardThemeSelector = findViewById(R.id.card_theme_selector)
        cardModelSelector = findViewById(R.id.card_model_selector)
        cardTitlePlate = findViewById(R.id.card_title_plate)
        cardApiKey = findViewById(R.id.card_api_key)
        cardBluetoothRack = findViewById(R.id.card_bluetooth_rack)
        cardHistoryRack = findViewById(R.id.card_history_rack)
        cardErrorLogsRack = findViewById(R.id.card_error_logs_rack)
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
        btnTestApiKey = findViewById(R.id.btn_test_api_key)

        tvBluetoothHeader = findViewById(R.id.tv_bluetooth_header)
        llBluetoothDevices = findViewById(R.id.ll_bluetooth_devices_list)
        tvEmptyDevices = findViewById(R.id.tv_empty_devices)
        btnReloadBluetooth = findViewById(R.id.btn_reload_bluetooth)
        btnTestTts = findViewById(R.id.btn_test_tts)

        tvHistoryHeader = findViewById(R.id.tv_history_header)
        llQaHistory = findViewById(R.id.ll_qa_history_list)
        tvEmptyHistory = findViewById(R.id.tv_empty_history)
        btnClearHistory = findViewById(R.id.btn_clear_history)

        tvErrorLogsHeader = findViewById(R.id.tv_error_logs_header)
        btnClearErrorLogs = findViewById(R.id.btn_clear_error_logs)
        llErrorLogsList = findViewById(R.id.ll_error_logs_list)
        tvEmptyErrorLogs = findViewById(R.id.tv_empty_error_logs)

        tvAppVersion = findViewById(R.id.tv_app_version)
        tvRepoInfo = findViewById(R.id.tv_repo_info)
        tvUpdateStatus = findViewById(R.id.tv_update_status)
        pbUpdateProgress = findViewById(R.id.pb_update_progress)
        btnCheckUpdate = findViewById(R.id.btn_check_update)

        cardAdbPanel = findViewById(R.id.card_adb_panel)
        tvAdbHeader = findViewById(R.id.tv_adb_header)
        tvAdbBadge = findViewById(R.id.tv_adb_badge)
        tvAdbInstructions = findViewById(R.id.tv_adb_instructions)
        etWatchAdbIp = findViewById(R.id.et_watch_adb_ip)
        etWatchAdbPort = findViewById(R.id.et_watch_adb_port)
        btnAutoDetectIp = findViewById(R.id.btn_auto_detect_ip)
        btnAdbInstall = findViewById(R.id.btn_adb_install)
        pbAdbProgress = findViewById(R.id.pb_adb_progress)
        tvAdbStatus = findViewById(R.id.tv_adb_status)
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
        cardErrorLogsRack.setBackgroundResource(config.bezelDrawable)
        cardUpdatePanel.setBackgroundResource(config.panelDrawable)
        cardAdbPanel.setBackgroundResource(config.panelDrawable)

        // Input & Controls
        etGeminiApiKey.setBackgroundResource(config.inputDrawable)
        etWatchAdbIp.setBackgroundResource(config.inputDrawable)
        etWatchAdbPort.setBackgroundResource(config.inputDrawable)
        btnAutoDetectIp.setBackgroundResource(config.btnPrimaryDrawable)
        btnAdbInstall.setBackgroundResource(config.btnEmeraldDrawable)
        btnToggleApiVisibility.setBackgroundResource(config.btnPrimaryDrawable)
        btnSaveApiKey.setBackgroundResource(config.btnEmeraldDrawable)
        btnTestApiKey.setBackgroundResource(config.btnPrimaryDrawable)
        btnReloadBluetooth.setBackgroundResource(config.btnPrimaryDrawable)
        btnClearHistory.setBackgroundResource(config.btnCrimsonDrawable)
        btnClearErrorLogs.setBackgroundResource(config.btnCrimsonDrawable)
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
        tvErrorLogsHeader.setTextColor(config.headerHistoryColor)
        tvAdbHeader.setTextColor(config.titleTextColor)
        tvAdbInstructions.setTextColor(config.textSecondaryColor)
        etWatchAdbIp.setTextColor(if (isLight) Color.parseColor("#0F172A") else Color.parseColor("#FFFFFF"))
        etWatchAdbPort.setTextColor(if (isLight) Color.parseColor("#0F172A") else Color.parseColor("#38BDF8"))

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
        loadErrorLogs()

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

        btnTestApiKey.setOnClickListener {
            val key = etGeminiApiKey.text.toString().trim()
            if (key.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập API Key để kiểm tra!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnTestApiKey.isEnabled = false
            tvApiKeyStatus.text = "🧪 Đang kiểm tra API Key với Google Gemini..."
            tvApiKeyStatus.setTextColor(0xFFF59E0B.toInt())

            val modelId = ThemeManager.getSelectedModel(this)
            val maskedKey = if (key.length > 8) "${key.take(4)}...${key.takeLast(4)}" else "••••"

            thread(name = "TestGeminiApiKeyThread") {
                try {
                    val urlStr = "https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent?key=$key"
                    val connection = java.net.URL(urlStr).openConnection() as java.net.HttpURLConnection
                    connection.requestMethod = "POST"
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.doOutput = true
                    connection.connectTimeout = 12000
                    connection.readTimeout = 15000

                    val requestBody = JSONObject().apply {
                        put("contents", org.json.JSONArray().apply {
                            put(JSONObject().apply {
                                put("parts", org.json.JSONArray().apply {
                                    put(JSONObject().apply {
                                        put("text", "Xin chào, đây là kiểm tra kết nối API.")
                                    })
                                })
                            })
                        })
                    }.toString()

                    connection.outputStream.use { os ->
                        os.write(requestBody.toByteArray(Charsets.UTF_8))
                    }

                    val code = connection.responseCode
                    if (code in 200..299) {
                        runOnUiThread {
                            btnTestApiKey.isEnabled = true
                            tvApiKeyStatus.text = "✓ API Key hoạt động hoàn hảo (HTTP $code)!"
                            tvApiKeyStatus.setTextColor(0xFF34D399.toInt())
                            Toast.makeText(this@PhoneMainActivity, "✓ API Key hợp lệ và hoạt động tốt!", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        val errorStream = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                        var errorMsg = "HTTP $code"
                        var errorStatus = ""
                        try {
                            val errJson = JSONObject(errorStream)
                            val innerErr = errJson.optJSONObject("error")
                            if (innerErr != null) {
                                errorMsg = innerErr.optString("message", errorMsg)
                                errorStatus = innerErr.optString("status", "")
                            }
                        } catch (_: Exception) {}

                        val suggestion = when (code) {
                            403 -> "API Key không hợp lệ hoặc tài khoản Google Cloud/AI Studio chưa kích hoạt Gemini API. Hãy kiểm tra lại key trên Google AI Studio."
                            400 -> "Yêu cầu không hợp lệ hoặc mô hình '$modelId' không được hỗ trợ bởi API Key này."
                            429 -> "Đã vượt quá hạn ngạch (Quota exceeded) của API Key. Vui lòng chờ hoặc dùng key khác."
                            else -> "Mã lỗi HTTP $code. Vui lòng kiểm tra lại API Key hoặc mạng internet."
                        }

                        val errorItem = ApiErrorItem(
                            id = java.util.UUID.randomUUID().toString(),
                            timestamp = System.currentTimeMillis(),
                            statusCode = code,
                            errorType = "TEST_HTTP_ERROR",
                            model = modelId,
                            apiKeyMasked = maskedKey,
                            errorMessage = errorMsg,
                            errorStatus = errorStatus,
                            rawResponse = errorStream,
                            suggestion = suggestion,
                            source = "PHONE_TEST"
                        )
                        apiErrorLogManager.addError(errorItem)

                        runOnUiThread {
                            btnTestApiKey.isEnabled = true
                            tvApiKeyStatus.text = "❌ Lỗi: HTTP $code - $errorMsg"
                            tvApiKeyStatus.setTextColor(0xFFEF4444.toInt())
                            loadErrorLogs()
                            Toast.makeText(this@PhoneMainActivity, "Lỗi kiểm tra API Key ($code): $errorMsg", Toast.LENGTH_LONG).show()
                        }
                    }
                    connection.disconnect()
                } catch (e: Exception) {
                    val errorItem = ApiErrorItem(
                        id = java.util.UUID.randomUUID().toString(),
                        timestamp = System.currentTimeMillis(),
                        statusCode = 0,
                        errorType = "NETWORK_EXCEPTION",
                        model = modelId,
                        apiKeyMasked = maskedKey,
                        errorMessage = e.message ?: "Không thể kết nối tới Google Gemini",
                        errorStatus = "IO_ERROR",
                        rawResponse = e.stackTraceToString(),
                        suggestion = "Kiểm tra kết nối Wi-Fi / 4G trên điện thoại. Nếu dùng VPN hoặc mạng công ty, hãy thử tắt để kiểm tra.",
                        source = "PHONE_TEST"
                    )
                    apiErrorLogManager.addError(errorItem)

                    runOnUiThread {
                        btnTestApiKey.isEnabled = true
                        tvApiKeyStatus.text = "❌ Lỗi kết nối: ${e.message}"
                        tvApiKeyStatus.setTextColor(0xFFEF4444.toInt())
                        loadErrorLogs()
                        Toast.makeText(this@PhoneMainActivity, "Lỗi kết nối: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
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

    private fun setupErrorLogsSection() {
        btnClearErrorLogs.setOnClickListener {
            apiErrorLogManager.clearLogs()
            loadErrorLogs()
            Toast.makeText(this, "Đã xóa toàn bộ nhật ký lỗi API.", Toast.LENGTH_SHORT).show()
        }
        loadErrorLogs()
    }

    private fun loadErrorLogs() {
        val errorLogs = apiErrorLogManager.getErrorLogs()
        llErrorLogsList.removeAllViews()

        if (errorLogs.isEmpty()) {
            tvEmptyErrorLogs.visibility = View.VISIBLE
            return
        }

        tvEmptyErrorLogs.visibility = View.GONE
        val config = ThemeManager.getConfig(currentThemeStyle, currentColorMode)

        for (item in errorLogs) {
            val itemView = LayoutInflater.from(this).inflate(
                R.layout.item_api_error_log,
                llErrorLogsList,
                false
            )
            itemView.setBackgroundResource(config.cardDrawable)

            val tvStatusBadge = itemView.findViewById<TextView>(R.id.tv_error_status_badge)
            val tvSourceBadge = itemView.findViewById<TextView>(R.id.tv_error_source_badge)
            val tvTime = itemView.findViewById<TextView>(R.id.tv_error_time)
            val tvModelKey = itemView.findViewById<TextView>(R.id.tv_error_model_key)
            val tvMessage = itemView.findViewById<TextView>(R.id.tv_error_message)
            val layoutSuggestion = itemView.findViewById<LinearLayout>(R.id.layout_error_suggestion)
            val tvSuggestion = itemView.findViewById<TextView>(R.id.tv_error_suggestion)
            val btnToggleRaw = itemView.findViewById<TextView>(R.id.btn_toggle_raw_json)
            val btnCopy = itemView.findViewById<Button>(R.id.btn_copy_error_json)
            val scrollRaw = itemView.findViewById<HorizontalScrollView>(R.id.scroll_raw_json)
            val tvRaw = itemView.findViewById<TextView>(R.id.tv_raw_json)

            btnCopy.setBackgroundResource(config.btnPrimaryDrawable)

            // Status Badge
            tvStatusBadge.text = item.getStatusBadgeText()
            when (item.statusCode) {
                403 -> {
                    tvStatusBadge.setTextColor(Color.parseColor("#EF4444"))
                    tvStatusBadge.setBackgroundColor(Color.parseColor("#33EF4444"))
                }
                429 -> {
                    tvStatusBadge.setTextColor(Color.parseColor("#F59E0B"))
                    tvStatusBadge.setBackgroundColor(Color.parseColor("#33F59E0B"))
                }
                400 -> {
                    tvStatusBadge.setTextColor(Color.parseColor("#F97316"))
                    tvStatusBadge.setBackgroundColor(Color.parseColor("#33F97316"))
                }
                else -> {
                    tvStatusBadge.setTextColor(Color.parseColor("#EF4444"))
                    tvStatusBadge.setBackgroundColor(Color.parseColor("#33EF4444"))
                }
            }

            // Source Badge
            if (item.source == "WATCH") {
                tvSourceBadge.text = "⌚ ĐỒNG HỒ"
                tvSourceBadge.setTextColor(Color.parseColor("#38BDF8"))
                tvSourceBadge.setBackgroundColor(Color.parseColor("#2038BDF8"))
            } else {
                tvSourceBadge.text = "📱 TEST TRÊN MÁY"
                tvSourceBadge.setTextColor(Color.parseColor("#A78BFA"))
                tvSourceBadge.setBackgroundColor(Color.parseColor("#20A78BFA"))
            }

            tvTime.text = item.getFormattedTime()
            tvModelKey.text = "Model: ${if (item.model.isNotEmpty()) item.model else "Mặc định"} | Key: ${if (item.apiKeyMasked.isNotEmpty()) item.apiKeyMasked else "Trống"}"
            tvMessage.text = "Nguyên nhân: ${item.errorMessage}"

            if (item.suggestion.isNotEmpty()) {
                layoutSuggestion.visibility = View.VISIBLE
                tvSuggestion.text = "👉 Hướng dẫn khắc phục:\n${item.suggestion}"
            } else {
                layoutSuggestion.visibility = View.GONE
            }

            if (item.rawResponse.isNotEmpty()) {
                tvRaw.text = item.rawResponse
                btnToggleRaw.visibility = View.VISIBLE
                btnToggleRaw.setOnClickListener {
                    if (scrollRaw.visibility == View.VISIBLE) {
                        scrollRaw.visibility = View.GONE
                        btnToggleRaw.text = "▶ Xem chi tiết phản hồi JSON từ Google"
                    } else {
                        scrollRaw.visibility = View.VISIBLE
                        btnToggleRaw.text = "▼ Ẩn chi tiết phản hồi JSON"
                    }
                }
            } else {
                btnToggleRaw.visibility = View.GONE
                scrollRaw.visibility = View.GONE
            }

            btnCopy.setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val textToCopy = buildString {
                    appendLine("=== CHI TIẾT LỖI GEMINI ===")
                    appendLine("Thời gian: ${item.getFormattedTime()}")
                    appendLine("Nguồn: ${item.source}")
                    appendLine("Mã lỗi: ${item.statusCode} (${item.errorStatus})")
                    appendLine("Mô hình: ${item.model}")
                    appendLine("API Key: ${item.apiKeyMasked}")
                    appendLine("Thông báo: ${item.errorMessage}")
                    if (item.suggestion.isNotEmpty()) {
                        appendLine("Khắc phục: ${item.suggestion}")
                    }
                    if (item.rawResponse.isNotEmpty()) {
                        appendLine("JSON phản hồi:")
                        appendLine(item.rawResponse)
                    }
                }
                val clip = android.content.ClipData.newPlainText("Gemini Api Error", textToCopy)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "📋 Đã sao chép chi tiết lỗi vào bộ nhớ tạm!", Toast.LENGTH_SHORT).show()
            }

            llErrorLogsList.addView(itemView)
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
                val ip = etWatchAdbIp.text.toString().trim()
                val port = etWatchAdbPort.text.toString().trim().toIntOrNull() ?: 5555
                val watchApk = File(cacheDir, "Gemini_Watch_App_Update.apk")

                if (ip.isNotEmpty() && watchApk.exists() && watchApk.length() > 0L) {
                    tvUpdateStatus.text = "✓ Phone xong! Đang tự động cài sang Watch qua Wireless ADB ($ip)..."
                    executeAdbInstall(ip, port, watchApk)
                } else {
                    tvUpdateStatus.text = "✓ Đã hoàn tất tải! Bấm nút '⚡ KẾT NỐI ADB & CÀI ĐẶT' bên dưới để cài thẳng lên đồng hồ."
                }
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

    private fun setupAdbSection() {
        val prefs = getSharedPreferences("gemini_companion_prefs", Context.MODE_PRIVATE)
        val savedIp = prefs.getString("saved_watch_adb_ip", "")
        if (!savedIp.isNullOrEmpty()) {
            etWatchAdbIp.setText(savedIp)
        }

        // Tự động quét IP đồng hồ khi mở app
        WatchAdbInstaller.autoDetectWatchAdbIp(this) { foundIp ->
            if (foundIp != null) {
                etWatchAdbIp.setText(foundIp)
                prefs.edit().putString("saved_watch_adb_ip", foundIp).apply()
                tvAdbStatus.text = "✓ Đã tìm thấy đồng hồ tại $foundIp:5555 (ADB sẵn sàng)"
            }
        }

        btnAutoDetectIp.setOnClickListener {
            btnAutoDetectIp.isEnabled = false
            tvAdbStatus.text = "🔍 Đang quét các dải IP trên Wi-Fi & Hotspot tìm cổng 5555..."
            pbAdbProgress.visibility = View.VISIBLE
            WatchAdbInstaller.autoDetectWatchAdbIp(this) { foundIp ->
                btnAutoDetectIp.isEnabled = true
                pbAdbProgress.visibility = View.GONE
                if (foundIp != null) {
                    etWatchAdbIp.setText(foundIp)
                    prefs.edit().putString("saved_watch_adb_ip", foundIp).apply()
                    tvAdbStatus.text = "✓ Đã tìm thấy đồng hồ tại $foundIp:5555!"
                    Toast.makeText(this, "Đã tìm thấy đồng hồ: $foundIp", Toast.LENGTH_SHORT).show()
                } else {
                    tvAdbStatus.text = "⚠️ Không quét thấy đồng hồ mở cổng 5555.\nHãy đảm bảo: 1) Đã bật 'Gỡ lỗi qua Wi-Fi' trên đồng hồ, 2) Kết nối cùng Wi-Fi hoặc Hotspot của điện thoại."
                    Toast.makeText(this, "Không tìm thấy. Bạn có thể nhập IP hiển thị trên đồng hồ vào ô.", Toast.LENGTH_LONG).show()
                }
            }
        }

        btnAdbInstall.setOnClickListener {
            val ip = etWatchAdbIp.text.toString().trim()
            val portStr = etWatchAdbPort.text.toString().trim()
            val port = portStr.toIntOrNull() ?: 5555

            if (ip.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập IP đồng hồ hoặc bấm 'DÒ TỰ ĐỘNG'!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prefs.edit().putString("saved_watch_adb_ip", ip).apply()

            btnAdbInstall.isEnabled = false
            pbAdbProgress.visibility = View.VISIBLE
            tvAdbStatus.text = "🔍 Đang kiểm tra phiên bản APK mới nhất trên GitHub..."

            val watchApkFile = File(cacheDir, "Gemini_Watch_App_Update.apk")

            GitHubUpdateManager.checkUpdate(this) { result ->
                result.onSuccess { info ->
                    var needDownload = !watchApkFile.exists() || watchApkFile.length() == 0L
                    if (!needDownload) {
                        try {
                            val archiveInfo = packageManager.getPackageArchiveInfo(watchApkFile.absolutePath, 0)
                            val cachedVer = archiveInfo?.versionName ?: ""
                            // Nếu file trong cache cũ hơn tag mới nhất trên GitHub, xóa đi tải mới
                            if (cachedVer.isNotEmpty() && !info.tagName.contains(cachedVer)) {
                                Log.i(TAG, "File APK trong cache ($cachedVer) cũ hơn bản mới (${info.tagName}), xóa để tải lại...")
                                watchApkFile.delete()
                                needDownload = true
                            }
                        } catch (_: Exception) {
                            watchApkFile.delete()
                            needDownload = true
                        }
                    }

                    if (needDownload) {
                        if (!info.watchDownloadUrl.isNullOrEmpty()) {
                            tvAdbStatus.text = "Đang tải APK bản ${info.tagName} từ GitHub..."
                            thread(name = "DownloadWatchApkThread") {
                                try {
                                    var currentUrl = info.watchDownloadUrl
                                    var connection: java.net.HttpURLConnection
                                    var redirects = 0
                                    while (true) {
                                        connection = java.net.URL(currentUrl).openConnection() as java.net.HttpURLConnection
                                        connection.instanceFollowRedirects = false
                                        connection.connectTimeout = 15000
                                        connection.readTimeout = 30000
                                        connection.connect()

                                        val code = connection.responseCode
                                        if (code in 301..308) {
                                            currentUrl = connection.getHeaderField("Location")
                                            connection.disconnect()
                                            redirects++
                                            if (redirects > 5) throw java.io.IOException("Quá nhiều lần chuyển hướng mạng")
                                            continue
                                        }
                                        break
                                    }

                                    connection.inputStream.use { input ->
                                        watchApkFile.outputStream().use { output ->
                                            input.copyTo(output)
                                        }
                                    }
                                    connection.disconnect()

                                    runOnUiThread {
                                        tvAdbStatus.text = "✓ Tải APK ${info.tagName} xong (${watchApkFile.length() / 1024} KB). Đang kết nối ADB tới $ip..."
                                        executeAdbInstall(ip, port, watchApkFile)
                                    }
                                } catch (e: Exception) {
                                    runOnUiThread {
                                        btnAdbInstall.isEnabled = true
                                        pbAdbProgress.visibility = View.GONE
                                        tvAdbStatus.text = "Lỗi tải APK: ${e.message}"
                                    }
                                }
                            }
                        } else {
                            btnAdbInstall.isEnabled = true
                            pbAdbProgress.visibility = View.GONE
                            tvAdbStatus.text = "Không tìm thấy link tải APK đồng hồ trên GitHub Release!"
                        }
                    } else {
                        tvAdbStatus.text = "✓ APK bản ${info.tagName} đã sẵn sàng. Đang kết nối ADB tới $ip..."
                        executeAdbInstall(ip, port, watchApkFile)
                    }
                }.onFailure { err ->
                    // Nếu mất mạng nhưng máy đã có APK sẵn
                    if (watchApkFile.exists() && watchApkFile.length() > 0L) {
                        tvAdbStatus.text = "⚠️ Không kiểm tra được GitHub, đang cài file APK có sẵn sang $ip..."
                        executeAdbInstall(ip, port, watchApkFile)
                    } else {
                        btnAdbInstall.isEnabled = true
                        pbAdbProgress.visibility = View.GONE
                        tvAdbStatus.text = "Lỗi kiểm tra cập nhật: ${err.message}"
                    }
                }
            }
        }
    }

    private fun executeAdbInstall(ip: String, port: Int, apkFile: File) {
        btnAdbInstall.isEnabled = false
        pbAdbProgress.visibility = View.VISIBLE

        WatchAdbInstaller.installApkOverAdb(this, ip, port, apkFile, object : WatchAdbInstaller.AdbInstallCallback {
            override fun onStatus(message: String) {
                tvAdbStatus.text = message
            }

            override fun onSuccess() {
                btnAdbInstall.isEnabled = true
                pbAdbProgress.visibility = View.GONE
                Toast.makeText(this@PhoneMainActivity, "🎉 ĐÃ CÀI ĐẶT THÀNH CÔNG LÊN ĐỒNG HỒ!", Toast.LENGTH_LONG).show()
            }

            override fun onError(error: String) {
                btnAdbInstall.isEnabled = true
                pbAdbProgress.visibility = View.GONE
                Toast.makeText(this@PhoneMainActivity, "Lỗi cài đặt qua ADB: $error", Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun autoSyncApiKeyToWatch() {
        val prefs = getSharedPreferences("gemini_prefs", Context.MODE_PRIVATE)
        val savedKey = prefs.getString("custom_api_key", "") ?: ""
        if (savedKey.isNotEmpty()) {
            Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
                for (node in nodes) {
                    Wearable.getMessageClient(this).sendMessage(
                        node.id,
                        "/gemini_api_key_sync",
                        savedKey.toByteArray(Charsets.UTF_8)
                    )
                }
                Log.d(TAG, "autoSyncApiKeyToWatch: synced key to ${nodes.size} node(s)")
            }.addOnFailureListener { e ->
                Log.w(TAG, "autoSyncApiKeyToWatch failed: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(receiver)
        } catch (_: Exception) {}
        try {
            Wearable.getMessageClient(this).removeListener(wearMessageListener)
        } catch (_: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        checkPermissionsAndLoadDevices(userInitiated = false)
        loadQaHistory()
        loadErrorLogs()
        autoSyncApiKeyToWatch()
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