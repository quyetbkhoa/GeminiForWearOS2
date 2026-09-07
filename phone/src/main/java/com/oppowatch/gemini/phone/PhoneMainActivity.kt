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
import android.util.Log
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
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import org.json.JSONObject
import java.io.File
import kotlin.concurrent.thread

class PhoneMainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PhoneMainActivity"
    }

    enum class NavPage {
        HUB,
        GEMINI_API,
        BLUETOOTH,
        ADB_UPDATE,
        THEME,
        QA_HISTORY,
        ERROR_LOGS
    }

    private var currentPage = NavPage.HUB

    private lateinit var filterManager: BluetoothFilterManager
    private lateinit var qaHistoryManager: QaHistoryManager
    private lateinit var apiErrorLogManager: ApiErrorLogManager

    // Root & Navigation Views
    private lateinit var rootLayout: LinearLayout
    private lateinit var layoutTopBar: LinearLayout
    private lateinit var btnNavBack: Button
    private lateinit var tvNavTitle: TextView
    private lateinit var tvNavSubtitle: TextView
    private lateinit var tvNavStatusPill: TextView
    private lateinit var scrollRoot: NestedScrollView

    // Page Containers
    private lateinit var layoutHub: LinearLayout
    private lateinit var pageGeminiApi: LinearLayout
    private lateinit var pageBluetooth: LinearLayout
    private lateinit var pageAdbUpdate: LinearLayout
    private lateinit var pageTheme: LinearLayout
    private lateinit var pageQaHistory: LinearLayout
    private lateinit var pageErrorLogs: LinearLayout

    // Hub Menu Rows & Badges
    private lateinit var cardTitlePlate: LinearLayout
    private lateinit var tvMainTitle: TextView
    private lateinit var tvMainSubtitle: TextView
    private lateinit var tvWatchConnectionBadge: TextView

    private lateinit var rowMenuGeminiApi: LinearLayout
    private lateinit var tvHubModelSummary: TextView
    private lateinit var tvHubModelBadge: TextView

    private lateinit var rowMenuBluetooth: LinearLayout
    private lateinit var tvHubBluetoothSummary: TextView
    private lateinit var tvHubBluetoothBadge: TextView

    private lateinit var rowMenuAdbUpdate: LinearLayout
    private lateinit var tvHubAdbSummary: TextView
    private lateinit var tvHubAdbBadge: TextView

    private lateinit var rowMenuTheme: LinearLayout
    private lateinit var tvHubThemeSummary: TextView
    private lateinit var tvHubThemeBadge: TextView

    private lateinit var rowMenuQaHistory: LinearLayout
    private lateinit var tvHubHistorySummary: TextView
    private lateinit var tvHubHistoryBadge: TextView

    private lateinit var rowMenuErrorLogs: LinearLayout
    private lateinit var tvHubErrorSummary: TextView
    private lateinit var tvHubErrorBadge: TextView

    // Hub Category Headers
    private lateinit var tvCatHeaderAi: TextView
    private lateinit var tvCatHeaderDevices: TextView
    private lateinit var tvCatHeaderTheme: TextView
    private lateinit var tvCatHeaderLogs: TextView

    // Hub Menu Titles
    private lateinit var tvMenuTitleGemini: TextView
    private lateinit var tvMenuTitleBluetooth: TextView
    private lateinit var tvMenuTitleAdb: TextView
    private lateinit var tvMenuTitleTheme: TextView
    private lateinit var tvMenuTitleHistory: TextView
    private lateinit var tvMenuTitleError: TextView

    // Hub Chevrons
    private lateinit var tvChevronGemini: TextView
    private lateinit var tvChevronBluetooth: TextView
    private lateinit var tvChevronAdb: TextView
    private lateinit var tvChevronTheme: TextView
    private lateinit var tvChevronHistory: TextView
    private lateinit var tvChevronError: TextView

    // Subpage Labels & Notes
    private lateinit var tvApiKeyNote: TextView
    private lateinit var tvBluetoothDesc: TextView
    private lateinit var tvUpdateHeader: TextView
    private lateinit var tvStyleLabel: TextView
    private lateinit var tvColorModeLabel: TextView

    private lateinit var backCallback: OnBackPressedCallback

    // Sub-Page 1: Gemini & API Key Views
    private lateinit var cardModelSelector: LinearLayout
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

    private lateinit var cardApiKey: LinearLayout
    private lateinit var tvApiKeyHeader: TextView
    private lateinit var tvApiKeyDesc: TextView
    private lateinit var etGeminiApiKey: EditText
    private lateinit var btnToggleApiVisibility: Button
    private lateinit var tvApiKeyStatus: TextView
    private lateinit var btnSaveApiKey: Button
    private lateinit var btnTestApiKey: Button
    private var isApiKeyVisible = false

    // Sub-Page 2: Bluetooth Views
    private lateinit var cardBluetoothRack: LinearLayout
    private lateinit var tvBluetoothHeader: TextView
    private lateinit var llBluetoothDevices: LinearLayout
    private lateinit var tvEmptyDevices: TextView
    private lateinit var btnReloadBluetooth: Button
    private lateinit var btnTestTts: Button

    // Quick Reply Views
    private lateinit var cardQuickReplyPanel: LinearLayout
    private lateinit var tvQuickReplyHeader: TextView
    private lateinit var tvNotificationAccessBadge: TextView
    private lateinit var tvQuickReplyDesc: TextView
    private lateinit var btnGrantNotificationAccess: Button

    // Sub-Page 3: Wireless ADB & GitHub Update Views
    private lateinit var cardUpdatePanel: LinearLayout
    private lateinit var tvAppVersion: TextView
    private lateinit var tvAndroidVersion: TextView
    private lateinit var tvRepoInfo: TextView
    private lateinit var tvUpdateStatus: TextView
    private lateinit var pbUpdateProgress: ProgressBar
    private lateinit var btnCheckUpdate: Button

    private lateinit var cardStep1Panel: LinearLayout
    private lateinit var tvStep1Header: TextView
    private lateinit var tvStep1Badge: TextView
    private lateinit var tvStep1Desc: TextView
    private lateinit var pbStep1Progress: ProgressBar
    private lateinit var btnUpdateStep1: Button

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

    private var latestUpdateInfo: GitHubUpdateManager.UpdateInfo? = null

    // Sub-Page 4: Theme Selector Views
    private lateinit var cardThemeSelector: LinearLayout
    private lateinit var tvThemeLabel: TextView
    private lateinit var tvThemeSublabel: TextView
    private lateinit var btnThemeSkeuo: Button
    private lateinit var btnThemeGlass: Button
    private lateinit var btnThemeMaterial: Button
    private lateinit var btnWatchThemeDark: Button
    private lateinit var btnWatchThemeLight: Button

    // Sub-Page 5: Q&A History Views
    private lateinit var cardHistoryRack: LinearLayout
    private lateinit var tvHistoryHeader: TextView
    private lateinit var llQaHistory: LinearLayout
    private lateinit var tvEmptyHistory: TextView
    private lateinit var btnClearHistory: Button

    // Sub-Page 6: API Error Logs Views
    private lateinit var cardErrorLogsRack: LinearLayout
    private lateinit var tvErrorLogsHeader: TextView
    private lateinit var btnClearErrorLogs: Button
    private lateinit var llErrorLogsList: LinearLayout
    private lateinit var tvEmptyErrorLogs: TextView

    private var currentThemeStyle = ThemeManager.ThemeStyle.SKEUOMORPHISM
    private var currentColorMode = ThemeManager.ColorMode.DARK

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadQaHistory()
            loadErrorLogs()
            updateHubSummaries()
        }
    }

    private val wearMessageListener = com.google.android.gms.wearable.MessageClient.OnMessageReceivedListener { messageEvent ->
        if (messageEvent.path == "/gemini_tts_payload") {
            val rawPayload = String(messageEvent.data, Charsets.UTF_8)
            var question = "Câu hỏi từ đồng hồ"
            var answer = rawPayload
            var timestamp = System.currentTimeMillis()
            try {
                val json = JSONObject(rawPayload)
                if (json.has("answer")) {
                    answer = json.optString("answer", rawPayload)
                    question = json.optString("question", "Câu hỏi bằng giọng nói")
                    timestamp = json.optLong("timestamp", System.currentTimeMillis())
                }
            } catch (_: Exception) {}
            qaHistoryManager.addEntry(question, answer, timestamp)
            runOnUiThread {
                loadQaHistory()
                updateHubSummaries()
            }
        } else if (messageEvent.path == "/gemini_error_log") {
            val rawJson = String(messageEvent.data, Charsets.UTF_8)
            try {
                val json = JSONObject(rawJson)
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
                updateHubSummaries()
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
        setupNavigationFlow()
        setupThemeEngine()
        setupModelSection()
        setupApiKeySection()
        setupBluetoothSection()
        setupAdbSection()
        setupUpdateSection()
        setupQaHistorySection()
        setupErrorLogsSection()

        checkPermissions()
        checkPermissionsAndLoadDevices(userInitiated = false)
        autoSyncApiKeyToWatch()
        updateHubSummaries()

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
        rootLayout = findViewById(R.id.root_layout)
        layoutTopBar = findViewById(R.id.layout_top_bar)
        btnNavBack = findViewById(R.id.btn_nav_back)
        tvNavTitle = findViewById(R.id.tv_nav_title)
        tvNavSubtitle = findViewById(R.id.tv_nav_subtitle)
        tvNavStatusPill = findViewById(R.id.tv_nav_status_pill)
        scrollRoot = findViewById(R.id.scroll_root)

        // Containers
        layoutHub = findViewById(R.id.layout_hub)
        pageGeminiApi = findViewById(R.id.page_gemini_api)
        pageBluetooth = findViewById(R.id.page_bluetooth)
        pageAdbUpdate = findViewById(R.id.page_adb_update)
        pageTheme = findViewById(R.id.page_theme)
        pageQaHistory = findViewById(R.id.page_qa_history)
        pageErrorLogs = findViewById(R.id.page_error_logs)

        // Hub Views
        cardTitlePlate = findViewById(R.id.card_title_plate)
        tvMainTitle = findViewById(R.id.tv_main_title)
        tvMainSubtitle = findViewById(R.id.tv_main_subtitle)
        tvWatchConnectionBadge = findViewById(R.id.tv_watch_connection_badge)

        rowMenuGeminiApi = findViewById(R.id.row_menu_gemini_api)
        tvHubModelSummary = findViewById(R.id.tv_hub_model_summary)
        tvHubModelBadge = findViewById(R.id.tv_hub_model_badge)

        rowMenuBluetooth = findViewById(R.id.row_menu_bluetooth)
        tvHubBluetoothSummary = findViewById(R.id.tv_hub_bluetooth_summary)
        tvHubBluetoothBadge = findViewById(R.id.tv_hub_bluetooth_badge)

        rowMenuAdbUpdate = findViewById(R.id.row_menu_adb_update)
        tvHubAdbSummary = findViewById(R.id.tv_hub_adb_summary)
        tvHubAdbBadge = findViewById(R.id.tv_hub_adb_badge)

        rowMenuTheme = findViewById(R.id.row_menu_theme)
        tvHubThemeSummary = findViewById(R.id.tv_hub_theme_summary)
        tvHubThemeBadge = findViewById(R.id.tv_hub_theme_badge)

        rowMenuQaHistory = findViewById(R.id.row_menu_qa_history)
        tvHubHistorySummary = findViewById(R.id.tv_hub_history_summary)
        tvHubHistoryBadge = findViewById(R.id.tv_hub_history_badge)

        rowMenuErrorLogs = findViewById(R.id.row_menu_error_logs)
        tvHubErrorSummary = findViewById(R.id.tv_hub_error_summary)
        tvHubErrorBadge = findViewById(R.id.tv_hub_error_badge)

        // Subpage 1 Views
        cardModelSelector = findViewById(R.id.card_model_selector)
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

        cardApiKey = findViewById(R.id.card_api_key)
        tvApiKeyHeader = findViewById(R.id.tv_api_key_header)
        tvApiKeyDesc = findViewById(R.id.tv_api_key_desc)
        etGeminiApiKey = findViewById(R.id.et_gemini_api_key)
        btnToggleApiVisibility = findViewById(R.id.btn_toggle_api_visibility)
        tvApiKeyStatus = findViewById(R.id.tv_api_key_status)
        btnSaveApiKey = findViewById(R.id.btn_save_api_key)
        btnTestApiKey = findViewById(R.id.btn_test_api_key)

        // Subpage 2 Views
        cardBluetoothRack = findViewById(R.id.card_bluetooth_rack)
        tvBluetoothHeader = findViewById(R.id.tv_bluetooth_header)
        llBluetoothDevices = findViewById(R.id.ll_bluetooth_devices_list)
        tvEmptyDevices = findViewById(R.id.tv_empty_devices)
        btnReloadBluetooth = findViewById(R.id.btn_reload_bluetooth)
        btnTestTts = findViewById(R.id.btn_test_tts)

        cardQuickReplyPanel = findViewById(R.id.card_quick_reply_panel)
        tvQuickReplyHeader = findViewById(R.id.tv_quick_reply_header)
        tvNotificationAccessBadge = findViewById(R.id.tv_notification_access_badge)
        tvQuickReplyDesc = findViewById(R.id.tv_quick_reply_desc)
        btnGrantNotificationAccess = findViewById(R.id.btn_grant_notification_access)

        btnGrantNotificationAccess.setOnClickListener {
            try {
                startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
            } catch (e: Exception) {
                Toast.makeText(this, "Vui lòng mở Cài đặt > Ứng dụng > Quyền truy cập thông báo", Toast.LENGTH_LONG).show()
            }
        }

        // Subpage 3 Views
        cardUpdatePanel = findViewById(R.id.card_update_panel)
        tvAppVersion = findViewById(R.id.tv_app_version)
        tvAndroidVersion = findViewById(R.id.tv_android_version)
        tvRepoInfo = findViewById(R.id.tv_repo_info)
        tvUpdateStatus = findViewById(R.id.tv_update_status)
        pbUpdateProgress = findViewById(R.id.pb_update_progress)
        btnCheckUpdate = findViewById(R.id.btn_check_update)

        cardStep1Panel = findViewById(R.id.card_step1_panel)
        tvStep1Header = findViewById(R.id.tv_step1_header)
        tvStep1Badge = findViewById(R.id.tv_step1_badge)
        tvStep1Desc = findViewById(R.id.tv_step1_desc)
        pbStep1Progress = findViewById(R.id.pb_step1_progress)
        btnUpdateStep1 = findViewById(R.id.btn_update_step1)

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

        // Subpage 4 Views
        cardThemeSelector = findViewById(R.id.card_theme_selector)
        tvThemeLabel = findViewById(R.id.tv_theme_label)
        tvThemeSublabel = findViewById(R.id.tv_theme_sublabel)
        btnThemeSkeuo = findViewById(R.id.btn_theme_skeuo)
        btnThemeGlass = findViewById(R.id.btn_theme_glass)
        btnThemeMaterial = findViewById(R.id.btn_theme_material)
        btnWatchThemeDark = findViewById(R.id.btn_watch_theme_dark)
        btnWatchThemeLight = findViewById(R.id.btn_watch_theme_light)

        // Subpage 5 Views
        cardHistoryRack = findViewById(R.id.card_history_rack)
        tvHistoryHeader = findViewById(R.id.tv_history_header)
        llQaHistory = findViewById(R.id.ll_qa_history_list)
        tvEmptyHistory = findViewById(R.id.tv_empty_history)
        btnClearHistory = findViewById(R.id.btn_clear_history)

        // Subpage 6 Views
        cardErrorLogsRack = findViewById(R.id.card_error_logs_rack)
        tvErrorLogsHeader = findViewById(R.id.tv_error_logs_header)
        btnClearErrorLogs = findViewById(R.id.btn_clear_error_logs)
        llErrorLogsList = findViewById(R.id.ll_error_logs_list)
        tvEmptyErrorLogs = findViewById(R.id.tv_empty_error_logs)

        // Hub Category Headers
        tvCatHeaderAi = findViewById(R.id.tv_cat_header_ai)
        tvCatHeaderDevices = findViewById(R.id.tv_cat_header_devices)
        tvCatHeaderTheme = findViewById(R.id.tv_cat_header_theme)
        tvCatHeaderLogs = findViewById(R.id.tv_cat_header_logs)

        // Hub Menu Titles
        tvMenuTitleGemini = findViewById(R.id.tv_menu_title_gemini)
        tvMenuTitleBluetooth = findViewById(R.id.tv_menu_title_bluetooth)
        tvMenuTitleAdb = findViewById(R.id.tv_menu_title_adb)
        tvMenuTitleTheme = findViewById(R.id.tv_menu_title_theme)
        tvMenuTitleHistory = findViewById(R.id.tv_menu_title_history)
        tvMenuTitleError = findViewById(R.id.tv_menu_title_error)

        // Hub Chevrons
        tvChevronGemini = findViewById(R.id.tv_chevron_gemini)
        tvChevronBluetooth = findViewById(R.id.tv_chevron_bluetooth)
        tvChevronAdb = findViewById(R.id.tv_chevron_adb)
        tvChevronTheme = findViewById(R.id.tv_chevron_theme)
        tvChevronHistory = findViewById(R.id.tv_chevron_history)
        tvChevronError = findViewById(R.id.tv_chevron_error)

        // Subpage Notes & Descriptions
        tvApiKeyNote = findViewById(R.id.tv_api_key_note)
        tvBluetoothDesc = findViewById(R.id.tv_bluetooth_desc)
        tvUpdateHeader = findViewById(R.id.tv_update_header)
        tvStyleLabel = findViewById(R.id.tv_style_label)
        tvColorModeLabel = findViewById(R.id.tv_color_mode_label)
    }

    private fun setupNavigationFlow() {
        btnNavBack.setOnClickListener {
            navigateTo(NavPage.HUB)
        }

        rowMenuGeminiApi.setOnClickListener { navigateTo(NavPage.GEMINI_API) }
        rowMenuBluetooth.setOnClickListener { navigateTo(NavPage.BLUETOOTH) }
        rowMenuAdbUpdate.setOnClickListener { navigateTo(NavPage.ADB_UPDATE) }
        rowMenuTheme.setOnClickListener { navigateTo(NavPage.THEME) }
        rowMenuQaHistory.setOnClickListener { navigateTo(NavPage.QA_HISTORY) }
        rowMenuErrorLogs.setOnClickListener { navigateTo(NavPage.ERROR_LOGS) }

        backCallback = object : OnBackPressedCallback(currentPage != NavPage.HUB) {
            override fun handleOnBackPressed() {
                navigateTo(NavPage.HUB)
            }
        }
        onBackPressedDispatcher.addCallback(this, backCallback)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (currentPage != NavPage.HUB) {
            navigateTo(NavPage.HUB)
        } else {
            super.onBackPressed()
        }
    }

    private fun navigateTo(page: NavPage) {
        currentPage = page
        if (::backCallback.isInitialized) {
            backCallback.isEnabled = (page != NavPage.HUB)
        }

        layoutHub.visibility = if (page == NavPage.HUB) View.VISIBLE else View.GONE
        pageGeminiApi.visibility = if (page == NavPage.GEMINI_API) View.VISIBLE else View.GONE
        pageBluetooth.visibility = if (page == NavPage.BLUETOOTH) View.VISIBLE else View.GONE
        pageAdbUpdate.visibility = if (page == NavPage.ADB_UPDATE) View.VISIBLE else View.GONE
        pageTheme.visibility = if (page == NavPage.THEME) View.VISIBLE else View.GONE
        pageQaHistory.visibility = if (page == NavPage.QA_HISTORY) View.VISIBLE else View.GONE
        pageErrorLogs.visibility = if (page == NavPage.ERROR_LOGS) View.VISIBLE else View.GONE

        if (page == NavPage.HUB) {
            btnNavBack.visibility = View.GONE
            tvNavTitle.text = "GEMINI COMPANION"
            tvNavSubtitle.text = "Trợ lý giọng nói Wear OS"
            updateHubSummaries()
        } else {
            btnNavBack.visibility = View.VISIBLE
            when (page) {
                NavPage.GEMINI_API -> {
                    tvNavTitle.text = "🤖 CẤU HÌNH GEMINI"
                    tvNavSubtitle.text = "Cài đặt > Mô hình AI & API Key"
                }
                NavPage.BLUETOOTH -> {
                    tvNavTitle.text = "🎧 TAI NGHE BLUETOOTH"
                    tvNavSubtitle.text = "Cài đặt > Bộ lọc thiết bị phát âm TTS"
                }
                NavPage.ADB_UPDATE -> {
                    tvNavTitle.text = "⚡ WIRELESS ADB"
                    tvNavSubtitle.text = "Cài đặt > Cài APK qua Wi-Fi & Cập nhật"
                }
                NavPage.THEME -> {
                    tvNavTitle.text = "🎨 GIAO DIỆN & THEME"
                    tvNavSubtitle.text = "Cài đặt > Đồng bộ Phone & Watch"
                }
                NavPage.QA_HISTORY -> {
                    tvNavTitle.text = "💬 LỊCH SỬ VOICE Q&A"
                    tvNavSubtitle.text = "Cài đặt > Xem lại & phát lại âm thanh"
                }
                NavPage.ERROR_LOGS -> {
                    tvNavTitle.text = "🚨 NHẬT KÝ LỖI API"
                    tvNavSubtitle.text = "Cài đặt > Chi tiết mã lỗi & Google JSON"
                }
                else -> {}
            }
        }

        scrollRoot.smoothScrollTo(0, 0)
    }

    private fun updateHubSummaries() {
        // 1. Model & Key
        val model = ThemeManager.getSelectedModel(this)
        val prefs = getSharedPreferences("gemini_prefs", Context.MODE_PRIVATE)
        val hasKey = prefs.getString("custom_api_key", "")?.isNotEmpty() == true
        val shortModel = when (model) {
            "gemini-3.8-flash" -> "3.8 Flash"
            "gemini-3.7-flash" -> "3.7 Flash"
            "gemini-3.5-flash" -> "3.5 Flash"
            "gemini-3.5-flash-lite" -> "3.5 Lite"
            "gemini-2.5-flash" -> "2.5 Flash"
            "gemini-3.1-pro-preview" -> "3.1 Pro"
            else -> model
        }
        tvHubModelBadge.text = shortModel
        tvHubModelSummary.text = "Mô hình: $shortModel • ${if (hasKey) "Đã lưu API Key riêng" else "Dùng key mặc định"}"

        // 2. Bluetooth
        val selectedCount = filterManager.getSelectedMacAddresses().size
        tvHubBluetoothSummary.text = "Lọc phát âm TTS • $selectedCount thiết bị đang BẬT"
        tvHubBluetoothBadge.text = "$selectedCount Bật"

        // 3. ADB & Update
        val currentVersion = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.3.1"
        } catch (_: Exception) { "1.3.1" }
        tvHubAdbBadge.text = "v$currentVersion"
        tvHubAdbSummary.text = "Wireless ADB Sideload • Mobile v$currentVersion"
        if (::tvAppVersion.isInitialized) {
            tvAppVersion.text = "📱 Phiên bản Mobile: v$currentVersion"
        }
        if (::tvAndroidVersion.isInitialized) {
            tvAndroidVersion.text = "🤖 Hệ điều hành Android: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        }

        // 4. Theme
        val style = ThemeManager.getStyle(this)
        val mode = ThemeManager.getColorMode(this)
        tvHubThemeBadge.text = when (style) {
            ThemeManager.ThemeStyle.SKEUOMORPHISM -> "Skeuo"
            ThemeManager.ThemeStyle.LIQUID_GLASS -> "Glass"
            ThemeManager.ThemeStyle.MATERIAL -> "M3"
        }
        tvHubThemeSummary.text = "${style.title} • ${if (mode == ThemeManager.ColorMode.LIGHT) "Sáng (Light)" else "Tối (Dark)"}"

        // 5. Q&A History
        val historyCount = qaHistoryManager.getHistory().size
        tvHubHistoryBadge.text = "$historyCount mục"
        tvHubHistorySummary.text = "$historyCount câu hỏi đã ghi nhận từ đồng hồ"

        // 6. Error Logs
        val errorCount = apiErrorLogManager.getErrorLogs().size
        if (errorCount == 0) {
            tvHubErrorBadge.text = "0 lỗi"
            tvHubErrorBadge.setTextColor(Color.parseColor("#34D399"))
            tvHubErrorSummary.text = "Hệ thống hoạt động bình thường • 0 lỗi"
        } else {
            tvHubErrorBadge.text = "🔴 $errorCount lỗi"
            tvHubErrorBadge.setTextColor(Color.parseColor("#EF4444"))
            tvHubErrorSummary.text = "Phát hiện $errorCount lỗi kết nối gần đây"
        }

        // 7. Watch Connection Status
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            if (nodes.isNotEmpty()) {
                tvWatchConnectionBadge.text = "🟢 ${nodes.size} ĐỒNG HỒ"
                tvWatchConnectionBadge.setTextColor(Color.parseColor("#34D399"))
                tvNavStatusPill.text = "🟢 OPPO WATCH"
                tvNavStatusPill.setTextColor(Color.parseColor("#34D399"))
            } else {
                tvWatchConnectionBadge.text = "🟡 CHỜ KẾT NỐI"
                tvWatchConnectionBadge.setTextColor(Color.parseColor("#F59E0B"))
                tvNavStatusPill.text = "⌚ OPPO WATCH"
                tvNavStatusPill.setTextColor(Color.parseColor("#38BDF8"))
            }
        }.addOnFailureListener {
            tvWatchConnectionBadge.text = "⌚ WEAR OS"
        }
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

        // Root & Top Bar
        rootLayout.setBackgroundColor(config.rootBgColor)
        scrollRoot.setBackgroundColor(config.rootBgColor)
        layoutTopBar.setBackgroundColor(if (isLight) Color.parseColor("#FFFFFF") else Color.parseColor("#0F131D"))
        tvNavTitle.setTextColor(if (isLight) Color.parseColor("#0F172A") else config.titleTextColor)
        tvNavSubtitle.setTextColor(if (isLight) Color.parseColor("#475569") else config.textSecondaryColor)
        btnNavBack.setBackgroundResource(config.btnPrimaryDrawable)
        btnNavBack.setTextColor(if (isLight) Color.parseColor("#0F172A") else Color.parseColor("#F59E0B"))

        // Hub Elements
        cardTitlePlate.setBackgroundResource(config.cardDrawable)
        rowMenuGeminiApi.setBackgroundResource(config.cardDrawable)
        rowMenuBluetooth.setBackgroundResource(config.cardDrawable)
        rowMenuAdbUpdate.setBackgroundResource(config.cardDrawable)
        rowMenuTheme.setBackgroundResource(config.cardDrawable)
        rowMenuQaHistory.setBackgroundResource(config.cardDrawable)
        rowMenuErrorLogs.setBackgroundResource(config.cardDrawable)

        // Hub Category Headers
        tvCatHeaderAi.setTextColor(if (isLight) Color.parseColor("#1D4ED8") else Color.parseColor("#60A5FA"))
        tvCatHeaderDevices.setTextColor(if (isLight) Color.parseColor("#047857") else Color.parseColor("#34D399"))
        tvCatHeaderTheme.setTextColor(if (isLight) Color.parseColor("#B45309") else Color.parseColor("#F59E0B"))
        tvCatHeaderLogs.setTextColor(if (isLight) Color.parseColor("#6D28D9") else Color.parseColor("#A78BFA"))

        // Hub Menu Titles, Subtitles & Chevrons
        val menuTitleColor = if (isLight) Color.parseColor("#0F172A") else Color.parseColor("#F8FAFC")
        val menuSubColor = if (isLight) Color.parseColor("#475569") else Color.parseColor("#94A3B8")
        val chevronColor = if (isLight) Color.parseColor("#94A3B8") else Color.parseColor("#64748B")

        tvMenuTitleGemini.setTextColor(menuTitleColor)
        tvMenuTitleBluetooth.setTextColor(menuTitleColor)
        tvMenuTitleAdb.setTextColor(menuTitleColor)
        tvMenuTitleTheme.setTextColor(menuTitleColor)
        tvMenuTitleHistory.setTextColor(menuTitleColor)
        tvMenuTitleError.setTextColor(menuTitleColor)

        tvHubModelSummary.setTextColor(menuSubColor)
        tvHubBluetoothSummary.setTextColor(menuSubColor)
        tvHubAdbSummary.setTextColor(menuSubColor)
        tvHubThemeSummary.setTextColor(menuSubColor)
        tvHubHistorySummary.setTextColor(menuSubColor)
        tvHubErrorSummary.setTextColor(menuSubColor)

        tvChevronGemini.setTextColor(chevronColor)
        tvChevronBluetooth.setTextColor(chevronColor)
        tvChevronAdb.setTextColor(chevronColor)
        tvChevronTheme.setTextColor(chevronColor)
        tvChevronHistory.setTextColor(chevronColor)
        tvChevronError.setTextColor(chevronColor)

        // Subpage Cards & Containers
        cardThemeSelector.setBackgroundResource(config.cardDrawable)
        cardModelSelector.setBackgroundResource(config.cardDrawable)
        cardApiKey.setBackgroundResource(config.cardDrawable)
        cardBluetoothRack.setBackgroundResource(config.bezelDrawable)
        cardQuickReplyPanel.setBackgroundResource(config.bezelDrawable)
        cardHistoryRack.setBackgroundResource(config.bezelDrawable)
        cardErrorLogsRack.setBackgroundResource(config.bezelDrawable)
        cardUpdatePanel.setBackgroundResource(config.panelDrawable)
        cardStep1Panel.setBackgroundResource(config.panelDrawable)
        cardAdbPanel.setBackgroundResource(config.panelDrawable)

        // Inputs & Buttons
        etGeminiApiKey.setBackgroundResource(config.inputDrawable)
        etWatchAdbIp.setBackgroundResource(config.inputDrawable)
        etWatchAdbPort.setBackgroundResource(config.inputDrawable)

        val inputTextColor = if (isLight) Color.parseColor("#0F172A") else Color.parseColor("#F8FAFC")
        val inputHintColor = if (isLight) Color.parseColor("#94A3B8") else Color.parseColor("#475569")

        etGeminiApiKey.setTextColor(inputTextColor)
        etGeminiApiKey.setHintTextColor(inputHintColor)
        etWatchAdbIp.setTextColor(inputTextColor)
        etWatchAdbIp.setHintTextColor(inputHintColor)
        etWatchAdbPort.setTextColor(if (isLight) Color.parseColor("#0284C7") else Color.parseColor("#38BDF8"))
        etWatchAdbPort.setHintTextColor(inputHintColor)

        btnAutoDetectIp.setBackgroundResource(config.btnPrimaryDrawable)
        btnAutoDetectIp.setTextColor(if (isLight) Color.parseColor("#0F172A") else Color.parseColor("#FFFFFF"))
        btnAdbInstall.setBackgroundResource(config.btnEmeraldDrawable)
        btnToggleApiVisibility.setBackgroundResource(config.btnPrimaryDrawable)
        btnSaveApiKey.setBackgroundResource(config.btnEmeraldDrawable)
        btnTestApiKey.setBackgroundResource(config.btnPrimaryDrawable)
        btnTestApiKey.setTextColor(if (isLight) Color.parseColor("#0F172A") else Color.parseColor("#FFFFFF"))
        btnReloadBluetooth.setBackgroundResource(config.btnPrimaryDrawable)
        btnReloadBluetooth.setTextColor(if (isLight) Color.parseColor("#0F172A") else Color.parseColor("#FFFFFF"))
        btnClearHistory.setBackgroundResource(config.btnCrimsonDrawable)
        btnClearErrorLogs.setBackgroundResource(config.btnCrimsonDrawable)
        btnTestTts.setBackgroundResource(config.btnEmeraldDrawable)
        btnCheckUpdate.setBackgroundResource(config.btnGoldDrawable)
        btnUpdateStep1.setBackgroundResource(config.btnGoldDrawable)
        btnUpdateStep1.setTextColor(Color.parseColor("#0F172A"))

        // Typography Colors
        tvMainTitle.setTextColor(if (isLight) Color.parseColor("#B45309") else config.titleTextColor)
        tvMainSubtitle.setTextColor(if (isLight) Color.parseColor("#475569") else config.textSecondaryColor)

        tvModelHeader.setTextColor(if (isLight) Color.parseColor("#0284C7") else Color.parseColor("#38BDF8"))
        tvModelDesc.setTextColor(if (isLight) Color.parseColor("#475569") else config.textSecondaryColor)

        tvApiKeyHeader.setTextColor(if (isLight) Color.parseColor("#B45309") else config.headerApiKeyColor)
        tvApiKeyDesc.setTextColor(if (isLight) Color.parseColor("#475569") else config.textSecondaryColor)
        tvApiKeyNote.setTextColor(if (isLight) Color.parseColor("#475569") else Color.parseColor("#64748B"))

        tvBluetoothHeader.setTextColor(if (isLight) Color.parseColor("#047857") else config.headerBluetoothColor)
        tvBluetoothDesc.setTextColor(if (isLight) Color.parseColor("#475569") else config.textSecondaryColor)
        tvEmptyDevices.setTextColor(if (isLight) Color.parseColor("#64748B") else Color.parseColor("#94A3B8"))

        tvQuickReplyHeader.setTextColor(if (isLight) Color.parseColor("#0284C7") else Color.parseColor("#38BDF8"))
        tvQuickReplyDesc.setTextColor(if (isLight) Color.parseColor("#475569") else config.textSecondaryColor)
        btnGrantNotificationAccess.setBackgroundResource(config.btnPrimaryDrawable)
        btnGrantNotificationAccess.setTextColor(if (isLight) Color.parseColor("#0F172A") else Color.parseColor("#FFFFFF"))
        updateNotificationAccessStatus()

        tvUpdateHeader.setTextColor(if (isLight) Color.parseColor("#B45309") else Color.parseColor("#F59E0B"))
        tvAppVersion.setTextColor(if (isLight) Color.parseColor("#047857") else Color.parseColor("#34D399"))
        tvAndroidVersion.setTextColor(if (isLight) Color.parseColor("#0284C7") else Color.parseColor("#38BDF8"))
        tvRepoInfo.setTextColor(if (isLight) Color.parseColor("#64748B") else Color.parseColor("#94A3B8"))
        tvUpdateStatus.setTextColor(if (isLight) Color.parseColor("#475569") else config.textSecondaryColor)

        tvStep1Header.setTextColor(if (isLight) Color.parseColor("#B45309") else Color.parseColor("#F59E0B"))
        tvStep1Badge.setTextColor(if (isLight) Color.parseColor("#B45309") else Color.parseColor("#F59E0B"))
        tvStep1Desc.setTextColor(if (isLight) Color.parseColor("#475569") else config.textSecondaryColor)

        tvAdbHeader.setTextColor(if (isLight) Color.parseColor("#0284C7") else Color.parseColor("#38BDF8"))
        tvAdbInstructions.setTextColor(if (isLight) Color.parseColor("#475569") else config.textSecondaryColor)
        tvAdbStatus.setTextColor(if (isLight) Color.parseColor("#475569") else Color.parseColor("#94A3B8"))

        tvThemeLabel.setTextColor(if (isLight) Color.parseColor("#B45309") else config.titleTextColor)
        tvThemeSublabel.setTextColor(if (isLight) Color.parseColor("#475569") else config.textSecondaryColor)
        tvStyleLabel.setTextColor(if (isLight) Color.parseColor("#334155") else Color.parseColor("#CBD5E1"))
        tvColorModeLabel.setTextColor(if (isLight) Color.parseColor("#334155") else Color.parseColor("#CBD5E1"))

        tvHistoryHeader.setTextColor(if (isLight) Color.parseColor("#6D28D9") else config.headerHistoryColor)
        tvEmptyHistory.setTextColor(if (isLight) Color.parseColor("#64748B") else Color.parseColor("#94A3B8"))

        tvErrorLogsHeader.setTextColor(if (isLight) Color.parseColor("#DC2626") else Color.parseColor("#EF4444"))
        tvEmptyErrorLogs.setTextColor(if (isLight) Color.parseColor("#047857") else Color.parseColor("#64748B"))

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

        btnThemeSkeuo.setTextColor(if (style == ThemeManager.ThemeStyle.SKEUOMORPHISM) Color.parseColor("#0F172A") else (if (isLight) Color.parseColor("#0F172A") else Color.parseColor("#FFFFFF")))
        btnThemeGlass.setTextColor(if (style == ThemeManager.ThemeStyle.LIQUID_GLASS) Color.parseColor("#0F172A") else (if (isLight) Color.parseColor("#0F172A") else Color.parseColor("#FFFFFF")))
        btnThemeMaterial.setTextColor(if (style == ThemeManager.ThemeStyle.MATERIAL) Color.parseColor("#0F172A") else (if (isLight) Color.parseColor("#0F172A") else Color.parseColor("#FFFFFF")))

        btnWatchThemeDark.setTextColor(if (!isLight) Color.parseColor("#0F172A") else (if (isLight) Color.parseColor("#0F172A") else Color.parseColor("#FFFFFF")))
        btnWatchThemeLight.setTextColor(if (isLight) Color.parseColor("#0F172A") else (if (isLight) Color.parseColor("#0F172A") else Color.parseColor("#FFFFFF")))

        // Refresh child views
        loadPairedBluetoothDevices(userInitiated = false)
        loadQaHistory()
        loadErrorLogs()
        updateHubSummaries()

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

        try {
            val putDataReq = PutDataMapRequest.create("/gemini_theme_config").apply {
                dataMap.putString("style", styleId)
                dataMap.putString("mode", modeId)
                dataMap.putString("combined", combined)
                dataMap.putLong("timestamp", System.currentTimeMillis())
                setUrgent()
            }.asPutDataRequest().setUrgent()

            Wearable.getDataClient(this).putDataItem(putDataReq).addOnSuccessListener {
                Log.d("PhoneMainActivity", "Đã lưu theme vào DataClient: $combined")
            }.addOnFailureListener { e ->
                Log.e("PhoneMainActivity", "Lỗi lưu theme DataClient: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e("PhoneMainActivity", "Exception PutDataMapRequest: ${e.message}")
        }

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
            updateHubSummaries()
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
                updateHubSummaries()
                return@setOnClickListener
            }

            prefs.edit().putString("custom_api_key", key).apply()
            tvApiKeyStatus.text = "✓ Đang đồng bộ sang đồng hồ qua Bluetooth..."
            tvApiKeyStatus.setTextColor(0xFFF59E0B.toInt())
            updateHubSummaries()

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
                            updateHubSummaries()
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
                            updateHubSummaries()
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
                        updateHubSummaries()
                        Toast.makeText(this@PhoneMainActivity, "Lỗi kết nối: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun setupBluetoothSection() {
        btnReloadBluetooth.setOnClickListener {
            checkPermissionsAndLoadDevices(userInitiated = true)
        }

        btnTestTts.setOnClickListener {
            TtsSpeaker.speak(this, "Đây là âm thanh thử nghiệm từ trợ lý Gemini trên đồng hồ OPPO Watch.")
            Toast.makeText(this, "Đang phát âm thanh mẫu...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupQaHistorySection() {
        btnClearHistory.setOnClickListener {
            qaHistoryManager.clearHistory()
            loadQaHistory()
            updateHubSummaries()
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
        val isLight = (currentColorMode == ThemeManager.ColorMode.LIGHT)
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
            val tvLabelQuestion = itemView.findViewById<TextView>(R.id.tv_label_question)
            val tvLabelAnswer = itemView.findViewById<TextView>(R.id.tv_label_answer)
            val layoutQuestion = itemView.findViewById<LinearLayout>(R.id.layout_qa_question)
            val layoutAnswer = itemView.findViewById<LinearLayout>(R.id.layout_qa_answer)
            val btnReplay = itemView.findViewById<Button>(R.id.btn_replay_tts)

            btnReplay.setBackgroundResource(config.btnEmeraldDrawable)

            if (isLight) {
                layoutQuestion?.setBackgroundResource(R.drawable.bg_qa_question_light)
                layoutAnswer?.setBackgroundResource(R.drawable.bg_qa_answer_light)
                tvTime.setTextColor(Color.parseColor("#475569"))
                tvLabelQuestion?.setTextColor(Color.parseColor("#0284C7"))
                tvQuestion.setTextColor(Color.parseColor("#0F172A"))
                tvLabelAnswer?.setTextColor(Color.parseColor("#059669"))
                tvAnswer.setTextColor(Color.parseColor("#064E3B"))
            } else {
                layoutQuestion?.setBackgroundResource(R.drawable.bg_qa_question)
                layoutAnswer?.setBackgroundResource(R.drawable.bg_qa_answer)
                tvTime.setTextColor(Color.parseColor("#94A3B8"))
                tvLabelQuestion?.setTextColor(Color.parseColor("#38BDF8"))
                tvQuestion.setTextColor(Color.parseColor("#F1F5F9"))
                tvLabelAnswer?.setTextColor(Color.parseColor("#34D399"))
                tvAnswer.setTextColor(Color.parseColor("#ECFDF5"))
            }

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
            updateHubSummaries()
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
        val isLight = (currentColorMode == ThemeManager.ColorMode.LIGHT)
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
            btnCopy.setTextColor(if (isLight) Color.parseColor("#0F172A") else Color.parseColor("#FFFFFF"))

            tvTime.setTextColor(if (isLight) Color.parseColor("#475569") else Color.parseColor("#94A3B8"))
            tvModelKey.setTextColor(if (isLight) Color.parseColor("#334155") else Color.parseColor("#CBD5E1"))
            tvMessage.setTextColor(if (isLight) Color.parseColor("#DC2626") else Color.parseColor("#FCA5A5"))
            layoutSuggestion.setBackgroundColor(if (isLight) Color.parseColor("#15059669") else Color.parseColor("#1510B981"))
            tvSuggestion.setTextColor(if (isLight) Color.parseColor("#065F46") else Color.parseColor("#6EE7B7"))
            btnToggleRaw.setTextColor(if (isLight) Color.parseColor("#7C3AED") else Color.parseColor("#A78BFA"))
            tvRaw.setTextColor(if (isLight) Color.parseColor("#1E293B") else Color.parseColor("#E2E8F0"))
            tvRaw.setBackgroundColor(if (isLight) Color.parseColor("#10000000") else Color.parseColor("#20000000"))

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
        val currentMobileVersion = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.0"
        } catch (_: Exception) { "1.0.0" }

        tvAppVersion.text = "📱 Phiên bản Mobile: v$currentMobileVersion"
        tvAndroidVersion.text = "🤖 Hệ điều hành Android: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

        btnCheckUpdate.setOnClickListener {
            btnCheckUpdate.isEnabled = false
            tvUpdateStatus.text = "Đang kiểm tra GitHub Releases..."
            pbUpdateProgress.visibility = View.VISIBLE
            pbUpdateProgress.isIndeterminate = true

            GitHubUpdateManager.checkUpdate(this) { result ->
                pbUpdateProgress.isIndeterminate = false
                pbUpdateProgress.visibility = View.GONE
                btnCheckUpdate.isEnabled = true

                result.onSuccess { info ->
                    latestUpdateInfo = info
                    val isMobileNewer = GitHubUpdateManager.isVersionNewer(info.tagName, currentMobileVersion)

                    if (info.hasUpdate && isMobileNewer) {
                        tvUpdateStatus.text = "Phát hiện bản mới: ${info.tagName} (Hiện tại: v$currentMobileVersion)!\n👉 Hãy thực hiện Bước 1 (Cập nhật Mobile), sau đó thực hiện Bước 2 (Cập nhật Wear qua ADB)."
                        tvStep1Desc.text = "Có bản mới: ${info.tagName}. Bấm nút bên dưới để tải và cài đặt bản Mobile trước."
                        btnUpdateStep1.text = "📲 BƯỚC 1: CẬP NHẬT MOBILE LÊN ${info.tagName}"
                        tvAdbInstructions.text = "⚠️ Hãy hoàn thành Bước 1 (Cập nhật Mobile) trước. Sau đó bật 'Gỡ lỗi qua Wi-Fi' trên đồng hồ và bấm cài đặt Bước 2 bên dưới (không qua Bluetooth):"
                    } else {
                        tvUpdateStatus.text = "Mobile đang ở bản mới nhất (${info.tagName}).\n👉 Bạn có thể tiến hành Bước 2 để cập nhật đồng hồ qua Wireless ADB."
                        tvStep1Desc.text = "✓ Ứng dụng Mobile đã ở phiên bản mới nhất (${info.tagName})."
                        btnUpdateStep1.text = "✓ CÀI LẠI MOBILE (${info.tagName})"
                        tvAdbInstructions.text = "Bật 'Gỡ lỗi qua Wi-Fi' trên OPPO Watch và bấm nút Bước 2 bên dưới để cập nhật đồng hồ qua Wireless ADB (không qua Bluetooth):"
                    }
                }.onFailure { err ->
                    tvUpdateStatus.text = "Lỗi kiểm tra bản mới: ${err.message}"
                }
            }
        }

        btnUpdateStep1.setOnClickListener {
            val info = latestUpdateInfo
            if (info != null) {
                performStep1MobileUpdate(info)
            } else {
                tvStep1Desc.text = "Đang kiểm tra thông tin bản phát hành..."
                pbStep1Progress.visibility = View.VISIBLE
                pbStep1Progress.isIndeterminate = true
                GitHubUpdateManager.checkUpdate(this) { result ->
                    pbStep1Progress.isIndeterminate = false
                    pbStep1Progress.visibility = View.GONE
                    result.onSuccess { checkedInfo ->
                        latestUpdateInfo = checkedInfo
                        performStep1MobileUpdate(checkedInfo)
                    }.onFailure { err ->
                        tvStep1Desc.text = "Lỗi kiểm tra bản phát hành: ${err.message}"
                    }
                }
            }
        }
    }

    private fun performStep1MobileUpdate(info: GitHubUpdateManager.UpdateInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!packageManager.canRequestPackageInstalls()) {
                Toast.makeText(this, "Vui lòng bật quyền 'Cài đặt ứng dụng không rõ nguồn' để cập nhật Mobile", Toast.LENGTH_LONG).show()
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = android.net.Uri.parse("package:$packageName")
                }
                startActivity(intent)
                return
            }
        }

        if (info.phoneDownloadUrl.isNullOrEmpty()) {
            Toast.makeText(this, "Không tìm thấy link tải Phone APK trên GitHub Release!", Toast.LENGTH_LONG).show()
            tvStep1Desc.text = "Lỗi: Bản phát hành ${info.tagName} không chứa Phone APK."
            return
        }

        btnUpdateStep1.isEnabled = false
        pbStep1Progress.visibility = View.VISIBLE
        pbStep1Progress.progress = 0
        tvStep1Desc.text = "Đang tải bản cập nhật Mobile ${info.tagName} từ GitHub..."

        val phoneApkFile = File(cacheDir, "Gemini_Phone_Companion_Update.apk")
        GitHubUpdateManager.downloadPhoneApk(this, info.phoneDownloadUrl, phoneApkFile, object : GitHubUpdateManager.UpdateProgressListener {
            override fun onStatus(message: String) {
                tvStep1Desc.text = message
            }

            override fun onProgress(stage: String, percent: Int) {
                pbStep1Progress.progress = percent
                tvStep1Desc.text = "$stage: $percent%"
            }

            override fun onComplete() {
                pbStep1Progress.visibility = View.GONE
                btnUpdateStep1.isEnabled = true
                tvStep1Desc.text = "✓ Đã tải xong Mobile APK! Đang mở trình cài đặt Android..."
                GitHubUpdateManager.installPhoneApk(this@PhoneMainActivity, phoneApkFile)
                Toast.makeText(this@PhoneMainActivity, "Hãy bấm 'Cập nhật' trên màn hình để hoàn tất Bước 1!", Toast.LENGTH_LONG).show()
            }

            override fun onError(error: String) {
                pbStep1Progress.visibility = View.GONE
                btnUpdateStep1.isEnabled = true
                tvStep1Desc.text = "Lỗi tải Mobile: $error"
            }
        })
    }

    private fun setupAdbSection() {
        val prefs = getSharedPreferences("gemini_companion_prefs", Context.MODE_PRIVATE)
        val savedIp = prefs.getString("saved_watch_adb_ip", "")
        if (!savedIp.isNullOrEmpty()) {
            etWatchAdbIp.setText(savedIp)
        }

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
            tvAdbStatus.text = "🔍 Đang chuẩn bị bản APK Wear OS cho đồng hồ..."

            val watchApkFile = File(cacheDir, "Gemini_Watch_App_Update.apk")

            val info = latestUpdateInfo
            if (info != null) {
                proceedAdbWatchInstall(ip, port, watchApkFile, info)
            } else {
                GitHubUpdateManager.checkUpdate(this) { result ->
                    result.onSuccess { checkedInfo ->
                        latestUpdateInfo = checkedInfo
                        proceedAdbWatchInstall(ip, port, watchApkFile, checkedInfo)
                    }.onFailure { err ->
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
    }

    private fun proceedAdbWatchInstall(
        ip: String,
        port: Int,
        watchApkFile: File,
        info: GitHubUpdateManager.UpdateInfo
    ) {
        var needDownload = !watchApkFile.exists() || watchApkFile.length() == 0L
        if (!needDownload) {
            try {
                val archiveInfo = packageManager.getPackageArchiveInfo(watchApkFile.absolutePath, 0)
                val cachedVer = archiveInfo?.versionName ?: ""
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
                tvAdbStatus.text = "Đang tải APK Wear OS bản ${info.tagName} từ GitHub..."
                GitHubUpdateManager.downloadWatchApk(this, info.watchDownloadUrl, watchApkFile, object : GitHubUpdateManager.UpdateProgressListener {
                    override fun onStatus(message: String) {
                        tvAdbStatus.text = message
                    }

                    override fun onProgress(stage: String, percent: Int) {
                        tvAdbStatus.text = "$stage: $percent%"
                    }

                    override fun onComplete() {
                        tvAdbStatus.text = "✓ Tải APK ${info.tagName} xong (${watchApkFile.length() / 1024} KB). Đang kết nối Wireless ADB tới $ip..."
                        executeAdbInstall(ip, port, watchApkFile)
                    }

                    override fun onError(error: String) {
                        btnAdbInstall.isEnabled = true
                        pbAdbProgress.visibility = View.GONE
                        tvAdbStatus.text = "Lỗi tải APK đồng hồ: $error"
                    }
                }, onComplete = {})
            } else {
                btnAdbInstall.isEnabled = true
                pbAdbProgress.visibility = View.GONE
                tvAdbStatus.text = "Không tìm thấy link tải APK đồng hồ trên GitHub Release!"
            }
        } else {
            tvAdbStatus.text = "✓ APK bản ${info.tagName} đã sẵn sàng. Đang kết nối Wireless ADB tới $ip..."
            executeAdbInstall(ip, port, watchApkFile)
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
                updateHubSummaries()
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
        val currentMobileVersion = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.0"
        } catch (_: Exception) { "1.0.0" }
        if (::tvAppVersion.isInitialized) {
            tvAppVersion.text = "📱 Phiên bản Mobile: v$currentMobileVersion"
        }
        if (::tvAndroidVersion.isInitialized) {
            tvAndroidVersion.text = "🤖 Hệ điều hành Android: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        }
        checkPermissionsAndLoadDevices(userInitiated = false)
        loadQaHistory()
        loadErrorLogs()
        autoSyncApiKeyToWatch()
        updateHubSummaries()
        updateNotificationAccessStatus()
        try {
            Wearable.getMessageClient(this).addListener(wearMessageListener)
        } catch (_: Exception) {}
    }

    private fun updateNotificationAccessStatus() {
        if (!::tvNotificationAccessBadge.isInitialized) return
        val isGranted = QuickReplyNotificationService.isNotificationAccessGranted(this)
        if (isGranted) {
            tvNotificationAccessBadge.text = "✓ ĐÃ BẬT"
            tvNotificationAccessBadge.setTextColor(Color.parseColor("#34D399"))
            btnGrantNotificationAccess.text = "✓ ĐÃ CẤP QUYỀN TRUY CẬP THÔNG BÁO"
        } else {
            tvNotificationAccessBadge.text = "CHƯA BẬT"
            tvNotificationAccessBadge.setTextColor(Color.parseColor("#EF4444"))
            btnGrantNotificationAccess.text = "⚙️ CẤP QUYỀN TRUY CẬP THÔNG BÁO"
        }
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
            Log.e("PhoneMainActivity", "Lỗi quyền đọc bondedDevices", e)
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
        val isLight = (currentColorMode == ThemeManager.ColorMode.LIGHT)
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
            tvName.setTextColor(if (isLight) Color.parseColor("#0F172A") else Color.parseColor("#FFFFFF"))
            tvMac.text = mac
            tvMac.setTextColor(if (isLight) Color.parseColor("#B45309") else Color.parseColor("#F59E0B"))

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
                updateHubSummaries()
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