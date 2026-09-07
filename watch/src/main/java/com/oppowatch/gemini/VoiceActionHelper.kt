package com.oppowatch.gemini

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.util.Log
import org.json.JSONObject
import java.util.regex.Pattern

data class VoiceAction(
    val type: String, // "SET_ALARM", "SET_TIMER", "REPLY_MESSAGE", "CREATE_TASK", "SET_REMINDER", "COPY_CLIPBOARD", "MEDIA_CONTROL"
    val hour: Int = 0,
    val minute: Int = 0,
    val seconds: Int = 0,
    val message: String = "",
    val recipient: String = "", // Tên người nhận (rỗng nếu là tin nhắn gần nhất)
    val text: String = "",       // Văn bản chi tiết / ghi chú / clipboard
    val delaySeconds: Int = 0,  // Số giây delay cho reminder
    val command: String = "",   // "PAUSE", "PLAY", "NEXT", "PREV", "OPEN_VIDEO"
    val query: String = ""      // Tên video / bài hát cho OPEN_VIDEO
)

object VoiceActionHelper {

    private const val TAG = "VoiceActionHelper"

    /**
     * Phân tích đối tượng action từ JSON phản hồi của Gemini
     */
    fun parseFromJson(json: JSONObject): VoiceAction? {
        try {
            if (!json.has("action") || json.isNull("action")) return null
            val actObj = json.optJSONObject("action") ?: return null
            val type = actObj.optString("type", "").uppercase().trim()
            return when (type) {
                "SET_ALARM" -> {
                    val hour = actObj.optInt("hour", -1)
                    val minute = actObj.optInt("minute", 0)
                    val msg = actObj.optString("message", "Báo thức Gemini")
                    if (hour in 0..23 && minute in 0..59) {
                        VoiceAction(type = "SET_ALARM", hour = hour, minute = minute, message = msg)
                    } else null
                }
                "SET_TIMER" -> {
                    val seconds = actObj.optInt("seconds", 0)
                    val msg = actObj.optString("message", "Hẹn giờ Gemini")
                    if (seconds > 0) {
                        VoiceAction(type = "SET_TIMER", seconds = seconds, message = msg)
                    } else null
                }
                "REPLY_MESSAGE" -> {
                    val msg = actObj.optString("message", "").trim()
                    val recipient = actObj.optString("recipient", "").trim()
                    if (msg.isNotEmpty()) {
                        VoiceAction(type = "REPLY_MESSAGE", recipient = recipient, message = msg)
                    } else null
                }
                "CREATE_TASK" -> {
                    val title = actObj.optString("title", "").ifEmpty { actObj.optString("message", "") }.trim()
                    val notes = actObj.optString("notes", "").trim()
                    if (title.isNotEmpty()) {
                        VoiceAction(type = "CREATE_TASK", message = title, text = notes)
                    } else null
                }
                "SET_REMINDER" -> {
                    val msg = actObj.optString("message", "").ifEmpty { actObj.optString("content", "") }.trim()
                    var delay = actObj.optInt("delay_seconds", 0)
                    if (delay <= 0) delay = 300 // Mặc định 5 phút nếu không rõ
                    if (msg.isNotEmpty()) {
                        VoiceAction(type = "SET_REMINDER", message = msg, delaySeconds = delay)
                    } else null
                }
                "COPY_CLIPBOARD" -> {
                    val txt = actObj.optString("text", "").ifEmpty { actObj.optString("message", "") }.trim()
                    if (txt.isNotEmpty()) {
                        VoiceAction(type = "COPY_CLIPBOARD", text = txt, message = txt)
                    } else null
                }
                "MEDIA_CONTROL" -> {
                    val cmd = actObj.optString("command", "").uppercase().trim()
                    val q = actObj.optString("query", "").trim()
                    if (cmd.isNotEmpty()) {
                        VoiceAction(type = "MEDIA_CONTROL", command = cmd, query = q, message = q)
                    } else null
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi parse VoiceAction JSON: ${e.message}")
            return null
        }
    }

    /**
     * Bộ phân tích dự phòng cục bộ (Local Regex Fallback)
     * Nếu Gemini không trả action JSON, dùng regex quét câu hỏi
     */
    fun parseFallback(text: String): VoiceAction? {
        val lower = text.lowercase().trim()

        // 0. Nhận diện ĐIỀU KHIỂN MEDIA & MỞ VIDEO YOUTUBE
        if (lower.startsWith("mở video") || lower.startsWith("bật video") || lower.startsWith("phát video") ||
            lower.startsWith("bật bài hát") || lower.startsWith("mở bài hát") || lower.startsWith("bật bài") ||
            lower.startsWith("mở bài") || lower.startsWith("nghe bài") || lower.startsWith("play video") ||
            lower.contains("trên youtube")) {
            val cleanQuery = text.replace(
                Regex("^(?:mở video|bật video|phát video|bật bài hát|mở bài hát|bật bài|mở bài|nghe bài|play video)(?:\\s*[:là-]?\\s*)", RegexOption.IGNORE_CASE),
                ""
            ).replace(Regex("(?:\\s*trên youtube\\s*)$", RegexOption.IGNORE_CASE), "").trim()
            if (cleanQuery.isNotEmpty()) {
                return VoiceAction(type = "MEDIA_CONTROL", command = "OPEN_VIDEO", query = cleanQuery, message = cleanQuery)
            }
        }
        if (lower == "tạm dừng" || lower == "dừng lại" || lower == "dừng" || lower == "pause" ||
            lower.startsWith("tạm dừng") || lower.startsWith("dừng video") || lower.startsWith("dừng nhạc")) {
            return VoiceAction(type = "MEDIA_CONTROL", command = "PAUSE", message = "Tạm dừng phát video")
        }
        if (lower == "tiếp tục" || lower == "phát tiếp" || lower == "play" ||
            lower.startsWith("tiếp tục") || lower.startsWith("phát tiếp") || lower.startsWith("tiếp tục phát")) {
            return VoiceAction(type = "MEDIA_CONTROL", command = "PLAY", message = "Tiếp tục phát video")
        }
        if (lower == "chuyển bài" || lower == "bài tiếp" || lower == "bài tiếp theo" || lower == "video tiếp" ||
            lower == "video tiếp theo" || lower == "next" || lower == "next bài" || lower == "next video" || lower == "bỏ qua") {
            return VoiceAction(type = "MEDIA_CONTROL", command = "NEXT", message = "Chuyển sang video tiếp theo")
        }
        if (lower == "bài trước" || lower == "quay lại bài trước" || lower == "video trước" || lower == "previous" || lower == "lùi bài") {
            return VoiceAction(type = "MEDIA_CONTROL", command = "PREV", message = "Quay lại video trước")
        }

        // 1. Nhận diện CHÉP CHÍNH TẢ / SAO CHÉP VÀO CLIPBOARD ĐIỆN THOẠI
        if (lower.startsWith("chép chính tả") || lower.startsWith("chép văn bản") ||
            lower.startsWith("sao chép") || lower.startsWith("copy vào") || lower.startsWith("copy clipboard") ||
            lower.contains("chép vào clipboard") || lower.contains("copy vào điện thoại")) {
            val cleanText = text.replace(
                Regex("^(?:chép chính tả|chép văn bản|sao chép|copy vào điện thoại|copy vào máy|copy clipboard|chép vào clipboard)(?:\\s*[:là-]?\\s*)", RegexOption.IGNORE_CASE),
                ""
            ).trim()
            if (cleanText.isNotEmpty()) {
                return VoiceAction(type = "COPY_CLIPBOARD", text = cleanText, message = cleanText)
            }
        }

        // 2. Nhận diện ĐẶT BÁO THỨC
        if (lower.contains("báo thức") || lower.contains("nhắc tôi lúc") ||
            lower.contains("dậy lúc") || lower.contains("đánh thức")) {
            val alarmAction = parseAlarmFallback(lower)
            if (alarmAction != null) return alarmAction
        }

        // 3. Nhận diện NHẮC NHỞ THEO NGỮ CẢNH
        if (lower.startsWith("nhắc tôi") || lower.startsWith("nhắc nhở") || lower.startsWith("nhắc mình") ||
            lower.contains("nhắc tôi") || lower.contains("nhắc nhở")) {
            val reminderAction = parseReminderFallback(text)
            if (reminderAction != null) return reminderAction
        }

        // 4. Nhận diện HẸN GIỜ / ĐẾM NGƯỢC
        if (lower.contains("hẹn giờ") || lower.contains("đếm ngược") ||
            lower.contains("bấm giờ") || lower.contains("timer")) {
            val timerAction = parseTimerFallback(lower)
            if (timerAction != null) return timerAction
        }

        // 5. Nhận diện TRẢ LỜI TIN NHẮN (Messenger, Zalo, Telegram, SMS)
        if (lower.startsWith("rep") || lower.startsWith("trả lời") || lower.startsWith("nhắn lại") ||
            lower.contains("rep tin") || lower.contains("trả lời tin")) {
            val replyAction = parseReplyFallback(text)
            if (replyAction != null) return replyAction
        }

        // 6. Nhận diện THÊM VIỆC CẦN LÀM / OPPO TASK
        if (lower.startsWith("thêm việc") || lower.startsWith("tạo việc") || lower.startsWith("ghi việc cần làm") ||
            lower.startsWith("lưu task") || lower.startsWith("thêm task") || lower.startsWith("việc cần làm")) {
            val cleanTask = text.replace(
                Regex("^(?:thêm việc(?: cần làm)?|tạo việc(?: cần làm)?|ghi việc cần làm|lưu task|thêm task|việc cần làm)(?:\\s*[:là-]?\\s*)", RegexOption.IGNORE_CASE),
                ""
            ).trim()
            if (cleanTask.isNotEmpty()) {
                return VoiceAction(type = "CREATE_TASK", message = cleanTask)
            }
        }

        return null
    }

    private fun parseAlarmFallback(lower: String): VoiceAction? {
        try {
            val isPm = lower.contains("chiều") || lower.contains("tối") || lower.contains("pm")
            val isNight = lower.contains("đêm")
            val isMorning = lower.contains("sáng") || lower.contains("am")

            // "7 giờ kém 15" hoặc "7h kém 10"
            val kemMatcher = Pattern.compile("(\\d{1,2})\\s*(?:h|:| giờ)\\s*kém\\s*(\\d{1,2})").matcher(lower)
            if (kemMatcher.find()) {
                var h = kemMatcher.group(1)?.toIntOrNull() ?: return null
                val kem = kemMatcher.group(2)?.toIntOrNull() ?: 15
                val m = 60 - kem
                h -= 1
                if (h < 0) h = 23
                if (isPm && h < 12) h += 12
                if (isNight && h == 12) h = 0
                return VoiceAction("SET_ALARM", hour = h, minute = m, message = "Báo thức Gemini")
            }

            // "6 rưỡi" hoặc "6 giờ rưỡi"
            val ruoiMatcher = Pattern.compile("(\\d{1,2})\\s*(?:h|:| giờ)?\\s*rưỡi").matcher(lower)
            if (ruoiMatcher.find()) {
                var h = ruoiMatcher.group(1)?.toIntOrNull() ?: return null
                if (isPm && h < 12) h += 12
                if (isNight && h == 12) h = 0
                return VoiceAction("SET_ALARM", hour = h, minute = 30, message = "Báo thức Gemini")
            }

            // "6 giờ 30", "6h30", "06:30", "7 giờ", "7h"
            val stdMatcher = Pattern.compile("(\\d{1,2})(?:\\s*(?:h|:| giờ)\\s*(\\d{1,2})?|\\s*giờ)").matcher(lower)
            if (stdMatcher.find()) {
                var h = stdMatcher.group(1)?.toIntOrNull() ?: return null
                val m = stdMatcher.group(2)?.toIntOrNull() ?: 0
                if (isPm && h < 12) h += 12
                if (isNight && h == 12) h = 0
                if (isMorning && h == 12) h = 0
                if (h in 0..23 && m in 0..59) {
                    return VoiceAction("SET_ALARM", hour = h, minute = m, message = "Báo thức Gemini")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi parseAlarmFallback: ${e.message}")
        }
        return null
    }

    private fun parseTimerFallback(lower: String): VoiceAction? {
        try {
            var totalSeconds = 0

            // Giờ / Tiếng
            val hourMatcher = Pattern.compile("(\\d+)\\s*(?:tiếng|giờ)").matcher(lower)
            if (hourMatcher.find()) {
                val h = hourMatcher.group(1)?.toIntOrNull() ?: 0
                totalSeconds += h * 3600
            } else if (lower.contains("nửa tiếng") || lower.contains("nửa giờ")) {
                totalSeconds += 1800
            }

            // Phút
            val minMatcher = Pattern.compile("(\\d+)\\s*(?:phút)").matcher(lower)
            if (minMatcher.find()) {
                val m = minMatcher.group(1)?.toIntOrNull() ?: 0
                totalSeconds += m * 60
            } else if (lower.contains("rưỡi") && totalSeconds > 0) {
                totalSeconds += 1800
            }

            // Giây
            val secMatcher = Pattern.compile("(\\d+)\\s*(?:giây|s)").matcher(lower)
            if (secMatcher.find()) {
                val s = secMatcher.group(1)?.toIntOrNull() ?: 0
                totalSeconds += s
            }

            if (totalSeconds > 0) {
                return VoiceAction("SET_TIMER", seconds = totalSeconds, message = "Hẹn giờ Gemini")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi parseTimerFallback: ${e.message}")
        }
        return null
    }

    private fun parseReplyFallback(text: String): VoiceAction? {
        try {
            val trimmed = text.trim()
            // Pattern 1: Nhận diện câu có từ dẫn "là / bảo / rằng / nói"
            // VD: "rep là đang đi xe", "trả lời của Tuấn Anh bảo ok em", "nhắn lại cho mẹ là con sắp về", "rep sếp bảo em gửi rồi"
            val pattern = Pattern.compile(
                "^(?:trả lời|rep|nhắn lại)(?:\\s+tin(?:\\s+nhắn)?)?(?:\\s+(?:(?:của|cho)\\s+)?([a-zA-ZÀ-ỹ0-9]+(?:\\s+[a-zA-ZÀ-ỹ0-9]+)*))?\\s+(?:là|bảo|rằng|nói)\\s+(.+)$",
                Pattern.CASE_INSENSITIVE
            )
            val matcher = pattern.matcher(trimmed)
            if (matcher.find()) {
                val recipient = matcher.group(1)?.trim() ?: ""
                val message = matcher.group(2)?.trim() ?: ""
                if (message.isNotEmpty()) {
                    return VoiceAction(type = "REPLY_MESSAGE", recipient = recipient, message = message)
                }
            }

            // Pattern 2: Nhận diện dạng trực tiếp không có từ nối "là / bảo"
            // VD: "rep đang đi xe lát gọi lại", "trả lời tin nhắn ok bạn"
            val directPattern = Pattern.compile(
                "^(?:trả lời|rep|nhắn lại)(?:\\s+tin(?:\\s+nhắn)?)?\\s+(.+)$",
                Pattern.CASE_INSENSITIVE
            )
            val directMatcher = directPattern.matcher(trimmed)
            if (directMatcher.find()) {
                val message = directMatcher.group(1)?.trim() ?: ""
                if (message.isNotEmpty() && !message.startsWith("câu hỏi", ignoreCase = true)) {
                    return VoiceAction(type = "REPLY_MESSAGE", recipient = "", message = message)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi parseReplyFallback: ${e.message}")
        }
        return null
    }

    private fun parseReminderFallback(text: String): VoiceAction? {
        try {
            val lower = text.lowercase().trim()
            var delaySeconds = 0

            // 1. Nhận diện dạng khoảng thời gian tương đối: "sau 15 phút", "sau 1 tiếng", "sau 30 giây"
            val minMatcher = Pattern.compile("sau\\s*(\\d+)\\s*(?:phút|p)").matcher(lower)
            if (minMatcher.find()) {
                val m = minMatcher.group(1)?.toIntOrNull() ?: 5
                delaySeconds += m * 60
            }
            val hourMatcher = Pattern.compile("sau\\s*(\\d+)\\s*(?:tiếng|giờ|h)").matcher(lower)
            if (hourMatcher.find()) {
                val h = hourMatcher.group(1)?.toIntOrNull() ?: 1
                delaySeconds += h * 3600
            }
            val secMatcher = Pattern.compile("sau\\s*(\\d+)\\s*(?:giây|s)").matcher(lower)
            if (secMatcher.find()) {
                val s = secMatcher.group(1)?.toIntOrNull() ?: 0
                delaySeconds += s
            }

            // 2. Nếu không có "sau X", nhận diện mốc giờ tuyệt đối: "lúc 8h tối", "lúc 14 giờ"
            if (delaySeconds == 0) {
                val timeMatcher = Pattern.compile("(?:lúc|vào)\\s*(\\d{1,2})(?:\\s*(?:h|:| giờ)\\s*(\\d{1,2})?|\\s*giờ)").matcher(lower)
                if (timeMatcher.find()) {
                    var targetH = timeMatcher.group(1)?.toIntOrNull() ?: 0
                    val targetM = timeMatcher.group(2)?.toIntOrNull() ?: 0
                    val isPm = lower.contains("chiều") || lower.contains("tối") || lower.contains("pm")
                    val isNight = lower.contains("đêm")
                    if (isPm && targetH < 12) targetH += 12
                    if (isNight && targetH == 12) targetH = 0

                    val now = java.util.Calendar.getInstance()
                    val targetCal = java.util.Calendar.getInstance().apply {
                        set(java.util.Calendar.HOUR_OF_DAY, targetH)
                        set(java.util.Calendar.MINUTE, targetM)
                        set(java.util.Calendar.SECOND, 0)
                        if (before(now)) {
                            add(java.util.Calendar.DAY_OF_YEAR, 1) // Chuyển sang ngày hôm sau
                        }
                    }
                    delaySeconds = ((targetCal.timeInMillis - now.timeInMillis) / 1000).toInt()
                }
            }

            if (delaySeconds <= 0) delaySeconds = 300 // Mặc định 5 phút nếu người dùng chỉ nói "nhắc tôi làm việc gì đó"

            // Làm sạch phần dẫn để trích xuất nội dung nhắc
            val cleanContent = text.replace(
                Regex("^(?:nhắc tôi|nhắc nhở|nhắc mình)(?:\\s+(?:sau|lúc|vào)[^:]+)?(?:\\s*[:là-]?\\s*)", RegexOption.IGNORE_CASE),
                ""
            ).trim()
            val msg = if (cleanContent.isNotEmpty()) cleanContent else "Nhắc nhở công việc"
            return VoiceAction(type = "SET_REMINDER", message = msg, delaySeconds = delaySeconds)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi parseReminderFallback: ${e.message}")
        }
        return null
    }

    /**
     * Kích hoạt gọi Intent hệ thống HeyClock hoặc gửi lệnh sang điện thoại
     */
    fun execute(context: Context, action: VoiceAction): Boolean {
        return try {
            when (action.type) {
                "SET_ALARM" -> {
                    val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                        putExtra(AlarmClock.EXTRA_HOUR, action.hour)
                        putExtra(AlarmClock.EXTRA_MINUTES, action.minute)
                        putExtra(AlarmClock.EXTRA_MESSAGE, action.message.ifEmpty { "Báo thức Gemini" })
                        putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                        addCategory(Intent.CATEGORY_VOICE)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    Log.i(TAG, "SET_ALARM: ${action.hour}:${String.format("%02d", action.minute)}")
                    true
                }
                "SET_TIMER" -> {
                    val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                        putExtra(AlarmClock.EXTRA_LENGTH, action.seconds)
                        putExtra(AlarmClock.EXTRA_MESSAGE, action.message.ifEmpty { "Hẹn giờ Gemini" })
                        putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                        addCategory(Intent.CATEGORY_VOICE)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    Log.i(TAG, "SET_TIMER: ${action.seconds}s")
                    true
                }
                "REPLY_MESSAGE" -> {
                    PhoneCommunicator.sendReplyMessageToPhone(context, action.recipient, action.message)
                    Log.i(TAG, "REPLY_MESSAGE: recipient='${action.recipient}', message='${action.message}'")
                    true
                }
                "CREATE_TASK" -> {
                    PhoneCommunicator.sendTaskToPhone(context, action.message, action.text)
                    Log.i(TAG, "CREATE_TASK: title='${action.message}'")
                    true
                }
                "SET_REMINDER" -> {
                    PhoneCommunicator.sendReminderToPhone(context, action.message, action.delaySeconds)
                    Log.i(TAG, "SET_REMINDER: msg='${action.message}', delay=${action.delaySeconds}s")
                    true
                }
                "COPY_CLIPBOARD" -> {
                    PhoneCommunicator.sendClipboardToPhone(context, action.text)
                    Log.i(TAG, "COPY_CLIPBOARD: text='${action.text}'")
                    true
                }
                "MEDIA_CONTROL" -> {
                    PhoneCommunicator.sendMediaControlToPhone(context, action.command, action.query)
                    Log.i(TAG, "MEDIA_CONTROL: cmd='${action.command}', query='${action.query}'")
                    true
                }
                else -> false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi thực thi VoiceAction: ${e.message}", e)
            false
        }
    }
}
