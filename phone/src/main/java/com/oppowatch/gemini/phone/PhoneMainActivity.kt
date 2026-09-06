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
    private lateinit var tvLastMessage: TextView
    private lateinit var btnTestTts: Button

    // Các thành phần Cập nhật GitHub
    private lateinit var tvAppVersion: TextView
    private lateinit var tvUpdateStatus: TextView
    private lateinit var pbUpdateProgress: android.widget.ProgressBar
    private lateinit var btnCheckUpdate: Button

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val payload = intent?.getStringExtra("payload") ?: return
            tvLastMessage.text = "Tin nhắn từ đồng hồ:\n$payload"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_phone_main)

        filterManager = BluetoothFilterManager(this)
        TtsSpeaker.init(this)

        lvDevices = findViewById(R.id.lv_bluetooth_devices)
        tvLastMessage = findViewById(R.id.tv_last_message)
        btnTestTts = findViewById(R.id.btn_test_tts)

        tvAppVersion = findViewById(R.id.tv_app_version)
        tvUpdateStatus = findViewById(R.id.tv_update_status)
        pbUpdateProgress = findViewById(R.id.pb_update_progress)
        btnCheckUpdate = findViewById(R.id.btn_check_update)

        setupUpdateSection()
        checkPermissions()
        loadPairedBluetoothDevices()

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

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                    201
                )
            }
        }
    }

    private fun loadPairedBluetoothDevices() {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        val paired = adapter.bondedDevices.toList()

        val listAdapter = object : ArrayAdapter<BluetoothDevice>(this, 0, paired) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: LayoutInflater.from(context).inflate(
                    android.R.layout.simple_list_item_multiple_choice,
                    parent,
                    false
                )
                val device = getItem(position) ?: return view
                val name = device.name ?: "Thiết bị không tên"
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
            Toast.makeText(
                this,
                "${if (currentlyChecked) "Đã chọn" else "Đã bỏ"}: ${device.name}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}