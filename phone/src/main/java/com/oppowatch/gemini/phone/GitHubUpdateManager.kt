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
    private const val RELEASES_LIST_URL = "https://api.github.com/repos/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases"

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

    fun isVersionNewer(newVer: String, currentVer: String): Boolean {
        val p1 = newVer.replace("v", "").trim().split(".").mapNotNull { it.toIntOrNull() }
        val p2 = currentVer.replace("v", "").trim().split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(p1.size, p2.size)
        for (i in 0 until maxLen) {
            val v1 = p1.getOrElse(i) { 0 }
            val v2 = p2.getOrElse(i) { 0 }
            if (v1 > v2) return true
            if (v1 < v2) return false
        }
        return false
    }

    fun checkUpdate(context: Context, onResult: (Result<UpdateInfo>) -> Unit) {
        thread(name = "CheckUpdateThread") {
            try {
                val currentVersion = try {
                    val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                    pInfo.versionName ?: "1.0.0"
                } catch (_: Exception) { "1.0.0" }

                // Gọi trực tiếp API danh sách releases kèm nocache để chống CDN Fastly cache trễ
                val timestamp = System.currentTimeMillis()
                val url = URL("$RELEASES_LIST_URL?per_page=10&nocache=$timestamp")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    setRequestProperty("User-Agent", "Gemini-WearOS-Companion")
                    setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate")
                    setRequestProperty("Pragma", "no-cache")
                    connectTimeout = 10000
                    readTimeout = 10000
                }

                val responseCode = conn.responseCode
                if (responseCode != 200) {
                    mainHandler.post {
                        onResult(Result.failure(Exception("GitHub API phản hồi mã: $responseCode")))
                    }
                    return@thread
                }

                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val releasesArray = org.json.JSONArray(jsonStr)

                if (releasesArray.length() == 0) {
                    mainHandler.post {
                        onResult(Result.failure(Exception("Chưa tìm thấy bản phát hành nào trên repository!")))
                    }
                    return@thread
                }

                // Duyệt tìm release công khai mới nhất (bỏ qua draft và prerelease)
                var newestRelease: JSONObject? = null
                var highestTag = ""

                for (i in 0 until releasesArray.length()) {
                    val rel = releasesArray.getJSONObject(i)
                    if (rel.optBoolean("draft", false) || rel.optBoolean("prerelease", false)) continue
                    val tag = rel.optString("tag_name", "").trim()
                    if (tag.isEmpty()) continue

                    if (newestRelease == null || isVersionNewer(tag, highestTag)) {
                        newestRelease = rel
                        highestTag = tag
                    }
                }

                if (newestRelease == null) {
                    newestRelease = releasesArray.getJSONObject(0)
                    highestTag = newestRelease.optString("tag_name", "v1.0.0")
                }

                val tagName = highestTag
                val releaseName = newestRelease.optString("name", tagName)
                val body = newestRelease.optString("body", "Không có ghi chú phát hành.")

                var phoneUrl: String? = null
                var watchUrl: String? = null

                val assets = newestRelease.optJSONArray("assets")
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

                val cleanTag = tagName.replace("v", "").trim()
                val cleanCurrent = currentVersion.replace("v", "").trim()
                val hasUpdate = isVersionNewer(cleanTag, cleanCurrent) || (cleanTag != cleanCurrent)

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

    fun downloadPhoneApk(
        context: Context,
        downloadUrl: String,
        destinationFile: File,
        listener: UpdateProgressListener,
        onComplete: ((File) -> Unit)? = null
    ) {
        thread(name = "DownloadPhoneApkThread") {
            try {
                mainHandler.post { listener.onStatus("Đang tải bản cập nhật Mobile từ GitHub...") }
                downloadWithRedirect(downloadUrl, destinationFile) { percent ->
                    mainHandler.post { listener.onProgress("Tải Phone APK", percent) }
                }
                mainHandler.post {
                    listener.onStatus("✓ Đã tải xong Mobile APK (${destinationFile.length() / 1024} KB)!")
                    listener.onComplete()
                    onComplete?.invoke(destinationFile)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi tải Mobile APK", e)
                mainHandler.post { listener.onError("Lỗi tải Phone APK: ${e.localizedMessage}") }
            }
        }
    }

    fun downloadWatchApk(
        context: Context,
        downloadUrl: String,
        destinationFile: File,
        listener: UpdateProgressListener,
        onComplete: ((File) -> Unit)? = null
    ) {
        thread(name = "DownloadWatchApkThread") {
            try {
                mainHandler.post { listener.onStatus("Đang tải bản cập nhật Wear OS từ GitHub...") }
                downloadWithRedirect(downloadUrl, destinationFile) { percent ->
                    mainHandler.post { listener.onProgress("Tải Watch APK", percent) }
                }
                mainHandler.post {
                    listener.onStatus("✓ Đã tải xong Wear APK (${destinationFile.length() / 1024} KB)!")
                    listener.onComplete()
                    onComplete?.invoke(destinationFile)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi tải Watch APK", e)
                mainHandler.post { listener.onError("Lỗi tải Watch APK: ${e.localizedMessage}") }
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

    fun installPhoneApk(context: Context, apkFile: File) {
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
