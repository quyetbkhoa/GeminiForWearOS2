package com.oppowatch.gemini.phone

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.util.Log

class BluetoothFilterManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("gemini_bt_filter_prefs", Context.MODE_PRIVATE)

    fun getSelectedMacAddresses(): Set<String> {
        return prefs.getStringSet("selected_macs", emptySet()) ?: emptySet()
    }

    fun setDeviceSelected(macAddress: String, selected: Boolean) {
        val current = getSelectedMacAddresses().toMutableSet()
        if (selected) {
            current.add(macAddress)
        } else {
            current.remove(macAddress)
        }
        prefs.edit().putStringSet("selected_macs", current).apply()
    }

    fun isDeviceSelected(macAddress: String): Boolean {
        return getSelectedMacAddresses().contains(macAddress)
    }

    fun checkAndPlayTtsIfAllowed(text: String, onDecision: (Boolean, String) -> Unit) {
        val selectedMacs = getSelectedMacAddresses()
        if (selectedMacs.isEmpty()) {
            onDecision(false, "Không có thiết bị Bluetooth nào được tick chọn.")
            return
        }

        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || !adapter.isEnabled) {
            onDecision(false, "Bluetooth trên điện thoại đang tắt.")
            return
        }

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val isA2dpOn = audioManager?.isBluetoothA2dpOn == true

        if (!isA2dpOn) {
            onDecision(false, "Không có tai nghe/loa Bluetooth A2DP nào đang kết nối.")
            return
        }

        // Check connected Bluetooth profile devices
        adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                if (profile == BluetoothProfile.A2DP && proxy != null) {
                    val connectedDevices = proxy.connectedDevices
                    adapter.closeProfileProxy(profile, proxy)

                    val matchedDevice = connectedDevices.firstOrNull { device ->
                        selectedMacs.contains(device.address)
                    }

                    if (matchedDevice != null) {
                        onDecision(true, "Kết nối đúng thiết bị được tick: ${matchedDevice.name} (${matchedDevice.address})")
                    } else {
                        val names = connectedDevices.joinToString { it.name ?: it.address }
                        onDecision(false, "Thiết bị đang kết nối ($names) không nằm trong danh sách tick chọn.")
                    }
                } else {
                    onDecision(false, "Không lấy được A2DP proxy.")
                }
            }

            override fun onServiceDisconnected(profile: Int) {}
        }, BluetoothProfile.A2DP)
    }
}