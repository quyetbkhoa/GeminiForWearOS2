package com.oppowatch.gemini

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.util.Log
import org.json.JSONObject
import java.util.regex.Pattern

data class VoiceAction(
    val type: String, // "SET_ALARM" hoặc "SET_TIMER"
    val hour: Int = 0,
    val minute: Int = 0,
    val seconds: Int = 0,
    val message: String = ""
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

        // 1. Nhận diện ĐẶT BÁO THỨC
        if (lower.contains("báo thức") || lower.contains("nhắc tôi lúc") ||
            lower.contains("dậy lúc") || lower.contains("đánh thức")) {
            val alarmAction = parseAlarmFallback(lower)
            if (alarmAction != null) return alarmAction
        }

        // 2. Nhận diện HẸN GIỜ / ĐẾM NGƯỢC
        if (lower.contains("hẹn giờ") || lower.contains("đếm ngược") ||
            lower.contains("bấm giờ") || lower.contains("timer")) {
            val timerAction = parseTimerFallback(lower)
            if (timerAction != null) return timerAction
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

    /**
     * Kích hoạt gọi Intent hệ thống HeyClock của OPPO Watch
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
                else -> false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi thực thi VoiceAction: ${e.message}", e)
            false
        }
    }
}
