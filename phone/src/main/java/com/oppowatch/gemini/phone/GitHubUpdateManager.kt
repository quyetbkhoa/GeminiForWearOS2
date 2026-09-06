package com.oppowatch.gemini.phone

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

object GitHubUpdateManager {
    private const val TAG = "GitHubUpdateManager"
    const val GITHUB_REPO_OWNER = "quyetbkhoa"
    const val GITHUB_REPO_NAME = "GeminiForWearOS2"
    private const val API_URL = "https://api.github.com/repos/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases/latest"

    data class UpdateInfo(
        val hasUpdate: Boolean,
        val tagName: String,
        val releaseName: String,
        val releaseNotes: String,
        val phoneDownloadUrl: String?,
        val watchDownloadUrl: String?,
        val currentVersion: String
    )

    interface UpdateProgressListener {
        fun onStatus(message: String)
        fun onProgress(stage: String, percent: Int)
        fun onComplete()
        fun onError(error: String)
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    fun checkUpdate(context: Context, onResult: (Result<UpdateInfo>) -> Unit) {
        thread(name = "CheckUpdateThread") {
            try {
                val currentVersion = try {
                    val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                    pInfo.versionName ?: "1.0.0"
                } catch (_: Exception) { "1.0.0" }

                val url = URL(API_URL)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    setRequestProperty("User-Agent", "Gemini-WearOS-Companion")
                    connectTimeout = 10000
                    readTimeout = 10000
                }

                val responseCode = conn.responseCode
                if (responseCode != 200) {
                    mainHandler.post {
                        onResult(Result.failure(Exception("GitHub API phản hồi mã: $responseCode (Chưa có Release hoặc vượt giới hạn request)")))
                    }
                    return@thread
                }

                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(jsonStr)

                val tagName = root.optString("tag_name", "v1.0.0")
                val releaseName = root.optString("name", tagName)
                val body = root.optString("body", "Không có ghi chú phát hành.")

                var phoneUrl: String? = null
                var watchUrl: String? = null

                val assets = root.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "").lowercase()
                        val downloadUrl = asset.optString("browser_download_url", "")

                        if (name.contains("phone") && name.endsWith(".apk")) {
                            phoneUrl = downloadUrl
                        } else if (name.contains("watch") && name.endsWith(".apk")) {
                            watchUrl = downloadUrl
                        }
                    }
                }

                // So sánh version đơn giản (nếu tag khác version hiện tại thì có update)
                val cleanTag = tagName.replace("v", "").trim()
                val cleanCurrent = currentVersion.replace("v", "").trim()
                val hasUpdate = cleanTag != cleanCurrent

                val info = UpdateInfo(
                    hasUpdate = hasUpdate,
                    tagName = tagName,
                    releaseName = releaseName,
                    releaseNotes = body,
                    phoneDownloadUrl = phoneUrl,
                    watchDownloadUrl = watchUrl,
                    currentVersion = currentVersion
                )

                mainHandler.post { onResult(Result.success(info)) }

            } catch (e: Exception) {
                Log.e(TAG, "Lỗi kiểm tra cập nhật", e)
                mainHandler.post { onResult(Result.failure(e)) }
            }
        }
    }

    fun startFullUpdate(activity: Activity, updateInfo: UpdateInfo, listener: UpdateProgressListener) {
        thread(name = "FullUpdateThread") {
            try {
                val cacheDir = activity.cacheDir

                // 1. Tải bản cho điện thoại nếu có URL
                val phoneApkFile = File(cacheDir, "Gemini_Phone_Companion_Update.apk")
                if (!updateInfo.phoneDownloadUrl.isNullOrEmpty()) {
                    mainHandler.post {
                        listener.onStatus("Đang tải bản cập nhật Điện thoại từ GitHub...")
                    }
                    downloadWithRedirect(updateInfo.phoneDownloadUrl, phoneApkFile) { percent ->
                        mainHandler.post { listener.onProgress("Tải Phone APK", percent) }
                    }
                }

                // 2. Tải bản cho đồng hồ nếu có URL
                val watchApkFile = File(cacheDir, "Gemini_Watch_App_Update.apk")
                if (!updateInfo.watchDownloadUrl.isNullOrEmpty()) {
                    mainHandler.post {
                        listener.onStatus("Đang tải bản cập nhật Đồng hồ từ GitHub...")
                    }
                    downloadWithRedirect(updateInfo.watchDownloadUrl, watchApkFile) { percent ->
                        mainHandler.post { listener.onProgress("Tải Watch APK", percent) }
                    }

                    // 3. Đẩy sang đồng hồ qua Bluetooth ChannelClient
                    mainHandler.post {
                        listener.onStatus("Đang đẩy bản cập nhật sang OPPO Watch qua Bluetooth...")
                    }

                    var pushFinished = false
                    var pushError: String? = null

                    WatchApkPusher.pushApkToWatch(activity, watchApkFile, object : WatchApkPusher.PushCallback {
                        override fun onProgress(percentage: Int) {
                            mainHandler.post { listener.onProgress("Gửi sang Đồng hồ", percentage) }
                        }

                        override fun onSuccess() {
                            pushFinished = true
                        }

                        override fun onError(error: String) {
                            pushError = error
                            pushFinished = true
                        }
                    })

                    while (!pushFinished) {
                        Thread.sleep(200)
                    }

                    if (pushError != null) {
                        mainHandler.post {
                            listener.onStatus("Cảnh báo đẩy đồng hồ: $pushError")
                        }
                    } else {
                        mainHandler.post {
                            listener.onStatus("Đã gửi xong APK sang đồng hồ! Màn hình đồng hồ sẽ hiện thông báo cài đặt.")
                        }
                    }
                }

                // 4. Mở trình cài đặt cho điện thoại
                if (phoneApkFile.exists() && phoneApkFile.length() > 0) {
                    mainHandler.post {
                        listener.onStatus("Đang mở trình cài đặt trên điện thoại...")
                        installPhoneApk(activity, phoneApkFile)
                        listener.onComplete()
                    }
                } else {
                    mainHandler.post {
                        listener.onComplete()
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Lỗi trong tiến trình cập nhật", e)
                mainHandler.post { listener.onError("Lỗi cập nhật: ${e.localizedMessage}") }
            }
        }
    }

    private fun downloadWithRedirect(fileUrl: String, destinationFile: File, onProgress: (Int) -> Unit) {
        var currentUrl = fileUrl
        var connection: HttpURLConnection
        var redirects = 0

        while (true) {
            val url = URL(currentUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "Gemini-WearOS-Companion")
                connectTimeout = 15000
                readTimeout = 15000
            }

            val status = connection.responseCode
            if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                status == HttpURLConnection.HTTP_MOVED_PERM ||
                status == 307 || status == 308) {
                currentUrl = connection.getHeaderField("Location") ?: break
                redirects++
                if (redirects > 8) throw Exception("Quá nhiều lần chuyển hướng tải file")
                continue
            }
            break
        }

        val totalLength = connection.contentLengthLong
        if (destinationFile.exists()) destinationFile.delete()

        var downloaded = 0L
        val buffer = ByteArray(8192)

        connection.inputStream.use { input ->
            FileOutputStream(destinationFile).use { output ->
                var read: Int
                var lastReport = -1
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    downloaded += read
                    if (totalLength > 0) {
                        val percent = ((downloaded * 100) / totalLength).toInt()
                        if (percent != lastReport) {
                            lastReport = percent
                            onProgress(percent)
                        }
                    }
                }
                output.flush()
            }
        }
    }

    private fun installPhoneApk(context: Context, apkFile: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Không thể mở trình cài đặt APK điện thoại", e)
        }
    }
}
