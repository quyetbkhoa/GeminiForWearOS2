package com.oppowatch.gemini.phone

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PhoneMainActivity : AppCompatActivity() {

    private lateinit var filterManager: BluetoothFilterManager
    private lateinit var llBluetoothDevices: LinearLayout
    private lateinit var tvEmptyDevices: TextView
    private lateinit var btnReloadBluetooth: Button
    private lateinit var btnTestTts: Button

    // Q&A History
    private lateinit var qaHistoryManager: QaHistoryManager
    private lateinit var llQaHistory: LinearLayout
    private lateinit var tvEmptyHistory: TextView
    private lateinit var btnClearHistory: Button

    // Các thành phần Cập nhật GitHub
    private lateinit var tvAppVersion: TextView
    private lateinit var tvUpdateStatus: TextView
    private lateinit var pbUpdateProgress: ProgressBar
    private lateinit var btnCheckUpdate: Button

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

        llBluetoothDevices = findViewById(R.id.ll_bluetooth_devices_list)
        tvEmptyDevices = findViewById(R.id.tv_empty_devices)
        btnReloadBluetooth = findViewById(R.id.btn_reload_bluetooth)
        btnTestTts = findViewById(R.id.btn_test_tts)

        llQaHistory = findViewById(R.id.ll_qa_history_list)
        tvEmptyHistory = findViewById(R.id.tv_empty_history)
        btnClearHistory = findViewById(R.id.btn_clear_history)

        tvAppVersion = findViewById(R.id.tv_app_version)
        tvUpdateStatus = findViewById(R.id.tv_update_status)
        pbUpdateProgress = findViewById(R.id.pb_update_progress)
        btnCheckUpdate = findViewById(R.id.btn_check_update)

        setupUpdateSection()
        setupQaHistorySection()
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

        for (item in history) {
            val itemView = LayoutInflater.from(this).inflate(
                R.layout.item_qa_history,
                llQaHistory,
                false
            )

            val tvTime = itemView.findViewById<TextView>(R.id.tv_qa_time)
            val tvQuestion = itemView.findViewById<TextView>(R.id.tv_qa_question)
            val tvAnswer = itemView.findViewById<TextView>(R.id.tv_qa_answer)
            val btnReplay = itemView.findViewById<Button>(R.id.btn_replay_tts)

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
                tvUpdateStatus.text = "Đã hoàn tất truyền cập nhật!"
                pbUpdateProgress.visibility = View.GONE
                btnCheckUpdate.isEnabled = true
                btnCheckUpdate.text = "KIỂM TRA CẬP NHẬT KHÁC"
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
            com.google.android.gms.wearable.Wearable.getMessageClient(this).addListener(wearMessageListener)
        } catch (_: Exception) {}
    }

    override fun onPause() {
        super.onPause()
        try {
            com.google.android.gms.wearable.Wearable.getMessageClient(this).removeListener(wearMessageListener)
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

        for (device in paired) {
            val itemView = LayoutInflater.from(this).inflate(
                R.layout.item_bluetooth_device,
                llBluetoothDevices,
                false
            )

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
                    tvSwitch.setTextColor(0xFFFFFFFF.toInt())
                    tvSwitch.setBackgroundResource(R.drawable.bg_switch_on)
                } else {
                    tvSwitch.text = "TẮT"
                    tvSwitch.setTextColor(0xFF94A3B8.toInt())
                    tvSwitch.setBackgroundResource(R.drawable.bg_switch_off)
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