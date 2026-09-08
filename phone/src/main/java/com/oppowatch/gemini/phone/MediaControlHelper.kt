package com.oppowatch.gemini.phone

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.net.Uri
import android.os.PowerManager
import android.util.Log
import android.view.KeyEvent
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.regex.Pattern
import kotlin.concurrent.thread

object MediaControlHelper {

    private const val TAG = "MediaControlHelper"
    private const val MORPHE_PACKAGE = "app.morphe.android.youtube"
    private const val STOCK_YOUTUBE_PACKAGE = "com.google.android.youtube"

    fun handleMediaControl(
        context: Context,
        command: String,
        query: String = "",
        onResult: (Boolean, String) -> Unit
    ) {
        when (command.uppercase()) {
            "PAUSE" -> {
                val success = executeMediaAction(context, MediaAction.PAUSE)
                val msg = if (success) "Đã tạm dừng phát video" else "Đã gửi lệnh tạm dừng"
                onResult(true, msg)
            }
            "PLAY" -> {
                val success = executeMediaAction(context, MediaAction.PLAY)
                val msg = if (success) "Đã tiếp tục phát video" else "Đã gửi lệnh tiếp tục phát"
                onResult(true, msg)
            }
            "NEXT" -> {
                val success = executeMediaAction(context, MediaAction.NEXT)
                val msg = if (success) "Đã chuyển sang video tiếp theo" else "Đã gửi lệnh chuyển video"
                onResult(true, msg)
            }
            "PREV" -> {
                val success = executeMediaAction(context, MediaAction.PREV)
                val msg = if (success) "Đã quay lại video trước" else "Đã gửi lệnh quay lại bài trước"
                onResult(true, msg)
            }
            "OPEN_VIDEO" -> {
                if (query.isBlank()) {
                    onResult(false, "Tên video cần tìm không được để trống")
                    return
                }
                openFirstYouTubeVideo(context, query.trim(), onResult)
            }
            else -> {
                onResult(false, "Không rõ lệnh điều khiển: $command")
            }
        }
    }

    private enum class MediaAction {
        PLAY, PAUSE, NEXT, PREV
    }

    private fun executeMediaAction(context: Context, action: MediaAction): Boolean {
        // 1. Thử điều khiển qua MediaController (yêu cầu NotificationListenerService)
        try {
            val sessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            val notificationListener = ComponentName(context, QuickReplyNotificationService::class.java)
            val controllers = sessionManager?.getActiveSessions(notificationListener)

            if (!controllers.isNullOrEmpty()) {
                // Ưu tiên session của Morphe YouTube hoặc YouTube chuẩn
                val targetController = controllers.firstOrNull {
                    it.packageName == MORPHE_PACKAGE || it.packageName == STOCK_YOUTUBE_PACKAGE
                } ?: controllers.firstOrNull()

                if (targetController != null) {
                    Log.i(TAG, "Controlling MediaSession of: ${targetController.packageName} for action: $action")
                    when (action) {
                        MediaAction.PLAY -> targetController.transportControls.play()
                        MediaAction.PAUSE -> targetController.transportControls.pause()
                        MediaAction.NEXT -> targetController.transportControls.skipToNext()
                        MediaAction.PREV -> targetController.transportControls.skipToPrevious()
                    }
                    return true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaController dispatch failed: ${e.message}. Falling back to AudioManager.")
        }

        // 2. Dự phòng: Gửi Media KeyEvent qua AudioManager
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val keyCode = when (action) {
                MediaAction.PLAY -> KeyEvent.KEYCODE_MEDIA_PLAY
                MediaAction.PAUSE -> KeyEvent.KEYCODE_MEDIA_PAUSE
                MediaAction.NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
                MediaAction.PREV -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            }
            audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            true
        } catch (e: Exception) {
            Log.e(TAG, "KeyEvent dispatch failed: ${e.message}")
            false
        }
    }

    private fun getTargetYouTubePackage(context: Context): String? {
        val pm = context.packageManager
        // 1. Kiểm tra Morphe YouTube trước
        try {
            pm.getPackageInfo(MORPHE_PACKAGE, 0)
            return MORPHE_PACKAGE
        } catch (_: Exception) {}

        // 2. Kiểm tra YouTube gốc
        try {
            pm.getPackageInfo(STOCK_YOUTUBE_PACKAGE, 0)
            return STOCK_YOUTUBE_PACKAGE
        } catch (_: Exception) {}

        return null
    }

    // Cache ID video để không phải gọi mạng lại khi tìm lại câu tương tự
    private val videoCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * Tự động tìm kiếm video trên YouTube, trích xuất ID video đầu tiên
     * và gọi Intent phát trực tiếp video đó mà không cần người dùng chọn
     */
    private fun openFirstYouTubeVideo(
        context: Context,
        query: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val cacheKey = query.lowercase().trim()
        val cachedId = videoCache[cacheKey]
        if (!cachedId.isNullOrEmpty()) {
            Log.i(TAG, "Using cached YouTube videoId for '$query': $cachedId")
            launchYouTubePlayer(context, query, cachedId, onResult)
            return
        }

        thread(name = "YouTubeSearchLauncherThread") {
            // Đánh thức màn hình tạm thời nếu điện thoại đang tắt màn hình trong túi
            var wakeLock: PowerManager.WakeLock? = null
            try {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                @Suppress("DEPRECATION")
                wakeLock = pm?.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "Gemini:OpenYouTubeVideo"
                )
                wakeLock?.acquire(10000L)
            } catch (_: Exception) {}

            var foundVideoId: String? = null
            try {
                val searchUrlStr = "https://www.youtube.com/results?search_query=" + URLEncoder.encode(query, "UTF-8")
                val url = URL(searchUrlStr)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    // Dùng Desktop Chrome User-Agent kèm Cookie Consent để YouTube luôn trả về kết quả desktop đầy đủ, không bị chặn hay redirect sang consent wall
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
                    setRequestProperty("Accept-Language", "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7")
                    setRequestProperty("Cookie", "CONSENT=YES+cb.20210328-17-p0.en+FX+999; SOCS=CAISNQgDEitib3FfaWRlbnRpdHlmcm9udGVuZHVpc2VydmVyXzIwMjMwODI5LjA4X3AwGgJ2aSACGgYIgLCFpwY")
                    connectTimeout = 7000
                    readTimeout = 8000
                }

                val patternVideoId = Pattern.compile("\"videoId\":\"([a-zA-Z0-9_-]{11})\"")
                val patternWatchUrl = Pattern.compile("/watch\\?v=([a-zA-Z0-9_-]{11})")

                // Đọc theo chunk 16KB và quét Regex dần, dừng ngay khi tìm thấy ID đầu tiên
                conn.inputStream.use { input ->
                    val buffer = ByteArray(16384)
                    val textBuilder = StringBuilder()
                    var bytesRead: Int
                    var totalRead = 0
                    val maxBytesToRead = 1500000 // Tối đa 1.5MB

                    while (input.read(buffer).also { bytesRead = it } != -1 && totalRead < maxBytesToRead) {
                        totalRead += bytesRead
                        textBuilder.append(String(buffer, 0, bytesRead, Charsets.UTF_8))

                        val matcher1 = patternVideoId.matcher(textBuilder)
                        if (matcher1.find()) {
                            foundVideoId = matcher1.group(1)
                            break
                        }
                        val matcher2 = patternWatchUrl.matcher(textBuilder)
                        if (matcher2.find()) {
                            foundVideoId = matcher2.group(1)
                            break
                        }
                    }
                }
                conn.disconnect()

                if (!foundVideoId.isNullOrEmpty()) {
                    Log.i(TAG, "Found first YouTube videoId for '$query': $foundVideoId")
                    videoCache[cacheKey] = foundVideoId!!
                }
            } catch (e: Exception) {
                Log.w(TAG, "Search query scrape failed: ${e.message}")
            }

            try {
                launchYouTubePlayer(context, query, foundVideoId, onResult)
            } finally {
                try {
                    if (wakeLock?.isHeld == true) {
                        wakeLock.release()
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun launchYouTubePlayer(
        context: Context,
        query: String,
        videoId: String?,
        onResult: (Boolean, String) -> Unit
    ) {
        val targetPackage = getTargetYouTubePackage(context)
        try {
            if (!videoId.isNullOrEmpty()) {
                // Mở trực tiếp video để Morphe YouTube / YouTube autoplay ngay lập tức
                val videoUri = Uri.parse("vnd.youtube:$videoId")
                val intent = Intent(Intent.ACTION_VIEW, videoUri).apply {
                    targetPackage?.let { setPackage(it) }
                    putExtra("autoplay", true)
                    putExtra("play", true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                context.startActivity(intent)
                onResult(true, "Đang phát video $query trên YouTube")
            } else {
                // Dự phòng nếu không parse được videoId: Mở trang kết quả tìm kiếm của YouTube
                val searchUri = Uri.parse("vnd.youtube:results?q=" + URLEncoder.encode(query, "UTF-8"))
                val intent = Intent(Intent.ACTION_VIEW, searchUri).apply {
                    targetPackage?.let { setPackage(it) }
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                context.startActivity(intent)
                onResult(true, "Đang mở tìm kiếm $query trên YouTube")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cannot launch YouTube activity: ${e.message}")
            onResult(false, "Không thể mở ứng dụng YouTube: ${e.message}")
        }
    }
}
