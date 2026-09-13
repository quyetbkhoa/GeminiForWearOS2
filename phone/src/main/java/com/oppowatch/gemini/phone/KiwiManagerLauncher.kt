package com.oppowatch.gemini.phone

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Điều hướng và kết nối với Kiwi Manager để quản lý cập nhật tập trung cho cả Phone và Watch
 */
object KiwiManagerLauncher {
    const val KIWI_PACKAGE = "com.kiwi.manager"
    const val KIWI_GITHUB_URL = "https://github.com/quyetbkhoa/KiwiManager/releases/latest"

    fun isInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(KIWI_PACKAGE, 0) != null
        } catch (_: Exception) {
            false
        }
    }

    fun openOrPrompt(context: Context, appName: String = "Gemini for Wear OS") {
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(KIWI_PACKAGE)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        } else {
            AlertDialog.Builder(context)
                .setTitle("🥝 Kiwi Manager")
                .setMessage("Để cập nhật $appName tự động cho cả Điện thoại và Đồng hồ Wear OS qua Wireless ADB một chạm, vui lòng cài đặt Kiwi Manager.\n\nBạn có muốn tải Kiwi Manager ngay bây giờ?")
                .setPositiveButton("Tải Kiwi Manager") { _, _ ->
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(KIWI_GITHUB_URL)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
                .setNegativeButton("Đóng", null)
                .show()
        }
    }
}
