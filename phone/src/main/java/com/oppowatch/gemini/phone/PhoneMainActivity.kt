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
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PhoneMainActivity : AppCompatActivity() {

    private lateinit var filterManager: BluetoothFilterManager
    private lateinit var lvDevices: ListView
    private lateinit var tvEmptyDevices: TextView
    private lateinit var btnReloadBluetooth: Button
    private lateinit var btnTestTts: Button

    // Q&A History
    private lateinit var qaHistoryManager: QaHistoryManager
    private lateinit var lvQaHistory: ListView
    private lateinit var tvEmptyHistory: TextView
    private lateinit var btnClearHistory: Button

    // Các thành phần Cập nhật GitHub
    private lateinit var tvAppVersion: TextView
    private lateinit var tvUpdateStatus: TextView
    private lateinit var pbUpdateProgress: android.widget.ProgressBar
    private lateinit var btnCheckUpdate: Button

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadQaHistory()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_phone_main)

        filterManager = BluetoothFilterManager(this)
        qaHistoryManager = QaHistoryManager(this)
        TtsSpeaker.init(this)

        lvDevices = findViewById(R.id.lv_bluetooth_devices)
        tvEmptyDevices = findViewById(R.id.tv_empty_devices)
        btnReloadBluetooth = findViewById(R.id.btn_reload_bluetooth)
        btnTestTts = findViewById(R.id.btn_test_tts)

        lvQaHistory = findViewById(R.id.lv_qa_history)
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
        if (history.isEmpty()) {
            tvEmptyHistory.visibility = View.VISIBLE
            lvQaHistory.adapter = null
            return
        }

        tvEmptyHistory.visibility = View.GONE

        val adapter = object : ArrayAdapter<QaItem>(this, 0, history) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: LayoutInflater.from(context).inflate(
                    R.layout.item_qa_history,
                    parent,
                    false
                )
                val item = getItem(position) ?: return view

                val tvTime = view.findViewById<TextView>(R.id.tv_qa_time)
                val tvQuestion = view.findViewById<TextView>(R.id.tv_qa_question)
                val tvAnswer = view.findViewById<TextView>(R.id.tv_qa_answer)
                val btnReplay = view.findViewById<Button>(R.id.btn_replay_tts)

                tvTime.text = "🕒 ${item.getFormattedTime()}"
                tvQuestion.text = item.question
                tvAnswer.text = item.answer

                btnReplay.setOnClickListener {
                    TtsSpeaker.speak(context, item.answer)
                    Toast.makeText(context, "Đang phát lại câu trả lời...", Toast.LENGTH_SHORT).show()
                }

                return view
            }
        }

        lvQaHistory.adapter = adapter
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
        if (adapter == null) {
            tvEmptyDevices.text = "Thiết bị này không hỗ trợ Bluetooth."
            tvEmptyDevices.visibility = View.VISIBLE
            return
        }

        if (!adapter.isEnabled) {
            tvEmptyDevices.text = "Bluetooth trên điện thoại đang TẮT.\nVui lòng bật Bluetooth và bấm '🔄 LÀM MỚI'."
            tvEmptyDevices.visibility = View.VISIBLE
            lvDevices.adapter = null
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
            lvDevices.adapter = null
            if (userInitiated) {
                Toast.makeText(this, "Chưa tìm thấy thiết bị Bluetooth nào!", Toast.LENGTH_SHORT).show()
            }
            return
        }

        tvEmptyDevices.visibility = View.GONE

        val listAdapter = object : ArrayAdapter<BluetoothDevice>(this, 0, paired) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: LayoutInflater.from(context).inflate(
                    android.R.layout.simple_list_item_multiple_choice,
                    parent,
                    false
                )
                val device = getItem(position) ?: return view
                val name = try { device.name ?: "Thiết bị không tên" } catch (_: SecurityException) { "Thiết bị Bluetooth" }
                val mac = device.address

                val checkedTextView = view.findViewById<TextView>(android.R.id.text1)
                checkedTextView.text = "$name\n$mac"
                checkedTextView.textSize = 14f

                val isChecked = filterManager.isDeviceSelected(mac)
                (parent as? ListView)?.setItemChecked(position, isChecked)

                return view
            }
        }

        lvDevices.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        lvDevices.adapter = listAdapter

        lvDevices.setOnItemClickListener { _, _, position, _ ->
            val device = paired[position]
            val currentlyChecked = lvDevices.isItemChecked(position)
            filterManager.setDeviceSelected(device.address, currentlyChecked)
            val name = try { device.name ?: "Thiết bị" } catch (_: SecurityException) { "Thiết bị" }
            Toast.makeText(
                this,
                "${if (currentlyChecked) "Đã chọn" else "Đã bỏ"}: $name",
                Toast.LENGTH_SHORT
            ).show()
        }

        if (userInitiated) {
            Toast.makeText(this, "Đã làm mới: ${paired.size} thiết bị", Toast.LENGTH_SHORT).show()
        }
    }
}