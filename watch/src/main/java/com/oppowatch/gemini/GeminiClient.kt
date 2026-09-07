package com.oppowatch.gemini

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.concurrent.thread

object GeminiClient {

    private const val TAG = "GeminiClient"
    private const val MODEL = "gemini-3.5-flash-lite"

    @Volatile
    private var activeConnection: HttpURLConnection? = null
    private val connectionLock = Any()

    /**
     * Hủy ngay kết nối HTTP đang gọi Gemini nếu người dùng vuốt back thoát app
     */
    fun cancelCurrentRequest() {
        synchronized(connectionLock) {
            try {
                activeConnection?.disconnect()
                Log.d(TAG, "Đã ngắt kết nối Gemini request.")
            } catch (_: Exception) {}
            activeConnection = null
        }
    }

    fun askGemini(context: Context, audioBase64: String, onResult: (Boolean, String, String, VoiceAction?) -> Unit) {
        thread {
            var connection: HttpURLConnection? = null
            try {
                // Ưu tiên đọc API Key cá nhân do người dùng cấu hình từ điện thoại đồng bộ sang
                val prefs = context.getSharedPreferences("gemini_prefs", Context.MODE_PRIVATE)
                val customKey = prefs.getString("custom_api_key", null)?.trim()
                val activeApiKey = if (!customKey.isNullOrEmpty()) customKey else GeminiConfig.GEMINI_API_KEY
                // Đọc mô hình do người dùng chọn (mặc định Gemini 3.8 Flash mới nhất)
                val model = prefs.getString("selected_model", "gemini-3.8-flash") ?: "gemini-3.8-flash"
                val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$activeApiKey"
                val url = URL(endpoint)

                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = 15000
                    readTimeout = 20000
                    doOutput = true
                }

                synchronized(connectionLock) {
                    activeConnection = connection
                }

                // Lấy thời gian thực tế hiện tại của hệ thống theo múi giờ Việt Nam
                val sdf = SimpleDateFormat("EEEE, dd/MM/yyyy HH:mm:ss", Locale("vi", "VN")).apply {
                    timeZone = TimeZone.getTimeZone("GMT+7")
                }
                val nowStr = sdf.format(Date())

                val systemInstructionText =
                    "Bạn là trợ lý AI thông minh tích hợp trên đồng hồ OPPO Watch Wear OS. " +
                    "Mốc thời gian thực hiện tại của hệ thống: $nowStr (Múi giờ Việt Nam GMT+7). " +
                    "Hãy luôn căn cứ vào mốc thời gian này để trả lời chuẩn xác ngày, tháng, năm hôm nay, hôm qua, ngày mai khi người dùng hỏi. " +
                    "QUY TẮC BẮT BUỘC: " +
                    "1. Hãy nghe file âm thanh giọng nói của người dùng và nhận diện chính xác câu hỏi. " +
                    "2. Trả lời theo đúng định dạng JSON chuẩn gồm các trường: " +
                    "\"question\": câu hỏi hoặc yêu cầu của người dùng được viết lại chuẩn tiếng Việt; " +
                    "\"answer\": câu trả lời siêu ngắn gọn, súc tích, đi thẳng vào đáp án trong 1 đến 2 câu ngắn. " +
                    "3. NẾU người dùng yêu cầu ĐẶT BÁO THỨC hoặc HẸN GIỜ/TIMER, hãy thêm trường \"action\" vào JSON: " +
                    "- Đặt báo thức: {\"type\":\"SET_ALARM\",\"hour\":<0-23>,\"minute\":<0-59>,\"message\":\"<nhãn>\"} " +
                    "- Hẹn giờ đếm ngược: {\"type\":\"SET_TIMER\",\"seconds\":<tổng giây>,\"message\":\"<nhãn>\"} " +
                    "Ví dụ đặt báo thức 6h30 sáng: {\"question\":\"Đặt báo thức 6 giờ 30 sáng\",\"answer\":\"Đã đặt báo thức lúc 06:30 cho bạn.\",\"action\":{\"type\":\"SET_ALARM\",\"hour\":6,\"minute\":30,\"message\":\"Báo thức sáng\"}} " +
                    "Ví dụ hẹn giờ 10 phút: {\"question\":\"Hẹn giờ 10 phút\",\"answer\":\"Đã bắt đầu hẹn giờ 10 phút.\",\"action\":{\"type\":\"SET_TIMER\",\"seconds\":600,\"message\":\"Hẹn giờ\"}} " +
                    "4. Nếu KHÔNG phải yêu cầu báo thức/hẹn giờ, KHÔNG cần trường action. " +
                    "5. Tuyệt đối chỉ trả về chuỗi JSON thuần túy, không dùng markdown code block ```json."

                // Build Request JSON
                val rootJson = JSONObject()

                // System instruction
                val sysObj = JSONObject()
                val sysParts = JSONArray()
                sysParts.put(JSONObject().put("text", systemInstructionText))
                sysObj.put("parts", sysParts)
                rootJson.put("system_instruction", sysObj)

                // Contents
                val contentsArray = JSONArray()
                val contentObj = JSONObject()
                val partsArray = JSONArray()

                // 1. Text prompt
                val textPart = JSONObject()
                textPart.put("text", "Hãy nghe file âm thanh sau và trả lời:")
                partsArray.put(textPart)

                // 2. Audio part (inline_data)
                val audioPart = JSONObject()
                val inlineData = JSONObject()
                inlineData.put("mime_type", "audio/mp4")
                inlineData.put("data", audioBase64)
                audioPart.put("inline_data", inlineData)
                partsArray.put(audioPart)

                contentObj.put("parts", partsArray)
                contentsArray.put(contentObj)
                rootJson.put("contents", contentsArray)

                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(rootJson.toString())
                    writer.flush()
                }

                val responseCode = connection.responseCode
                val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
                val responseText = BufferedReader(InputStreamReader(stream)).use { it.readText() }

                // Kiểm tra lại nếu request đã bị hủy trong quá trình đọc response
                synchronized(connectionLock) {
                    if (activeConnection == null) {
                        Log.d(TAG, "Request đã bị hủy bỏ trước khi trả về.")
                        return@thread
                    }
                }

                if (responseCode in 200..299) {
                    val respJson = JSONObject(responseText)
                    val candidates = respJson.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val content = candidates.getJSONObject(0).optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        val rawText = parts?.getJSONObject(0)?.optString("text")?.trim() ?: ""

                        var question = "Câu hỏi từ đồng hồ"
                        var answer = rawText
                        var voiceAction: VoiceAction? = null

                        try {
                            val clean = rawText.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                            val parsed = JSONObject(clean)
                            if (parsed.has("answer")) {
                                answer = parsed.optString("answer", rawText)
                                question = parsed.optString("question", "Câu hỏi bằng giọng nói")
                            } else if (parsed.has("a")) {
                                answer = parsed.optString("a", rawText)
                                question = parsed.optString("q", "Câu hỏi bằng giọng nói")
                            }
                            // Trích xuất action từ JSON trả về của Gemini
                            voiceAction = VoiceActionHelper.parseFromJson(parsed)
                        } catch (_: Exception) {}

                        // Fallback regex: nếu Gemini không trả action JSON, quét câu hỏi bằng regex
                        if (voiceAction == null) {
                            voiceAction = VoiceActionHelper.parseFallback(question)
                        }

                        onResult(true, question, answer, voiceAction)
                    } else {
                        onResult(false, "Không rõ câu hỏi", "Không có câu trả lời từ Gemini.", null)
                    }
                } else {
                    Log.e(TAG, "API Error $responseCode: $responseText")

                    var errorMsg = "Lỗi kết nối Gemini ($responseCode)"
                    var errorStatus = ""
                    try {
                        val errObj = JSONObject(responseText).optJSONObject("error")
                        if (errObj != null) {
                            errorMsg = errObj.optString("message", errorMsg)
                            errorStatus = errObj.optString("status", "")
                        }
                    } catch (_: Exception) {}

                    val watchDisplayText = when (responseCode) {
                        403 -> "Lỗi 403: Thiếu hoặc sai API Key. Hãy mở app điện thoại để đồng bộ lại Key!"
                        400 -> "Lỗi 400: Yêu cầu không hợp lệ ($errorMsg)."
                        429 -> "Lỗi 429: Đạt giới hạn gọi AI (Quota). Thử lại sau 30s."
                        500, 502, 503 -> "Lỗi máy chủ Google Gemini ($responseCode). Thử lại sau."
                        else -> "Lỗi kết nối Gemini ($responseCode)"
                    }

                    val maskedKey = if (activeApiKey.isNullOrEmpty()) {
                        "TRỐNG (Chưa cài đặt API Key trên đồng hồ)"
                    } else if (activeApiKey.length > 10) {
                        "${activeApiKey.take(6)}...${activeApiKey.takeLast(4)}"
                    } else {
                        "***"
                    }

                    val suggestion = when (responseCode) {
                        403 -> "Lỗi 403 (Permission Denied / Invalid Key): 1) Mở app Gemini trên điện thoại, nhập API Key từ aistudio.google.com và bấm 'LƯU & ĐỒNG BỘ SANG ĐỒNG HỒ' (khi gỡ cài đặt trên đồng hồ, dữ liệu key cũ đã bị xóa sạch). 2) Kiểm tra hạn mức/tài khoản Google AI Studio."
                        400 -> "Lỗi 400: Mô hình '$model' hoặc định dạng không hỗ trợ. Thử đổi sang Gemini 3.8 Flash."
                        429 -> "Lỗi 429: Vượt hạn mức gọi API miễn phí mỗi phút (RPM). Vui lòng chờ 30 giây rồi thử lại."
                        else -> "Kiểm tra kết nối mạng của đồng hồ hoặc thử lại sau."
                    }

                    val errorLogJson = JSONObject().apply {
                        put("timestamp", System.currentTimeMillis())
                        put("statusCode", responseCode)
                        put("errorType", "HTTP_ERROR")
                        put("model", model)
                        put("apiKeyMasked", maskedKey)
                        put("errorMessage", errorMsg)
                        put("errorStatus", errorStatus)
                        put("rawResponse", responseText)
                        put("suggestion", suggestion)
                        put("source", "WATCH")
                    }.toString()

                    PhoneCommunicator.sendErrorLogToPhone(context, errorLogJson)

                    onResult(false, "Lỗi kết nối", watchDisplayText, null)
                }
            } catch (e: Exception) {
                // Nếu bị cancel thì ngắt êm thấm, không báo lỗi ra màn hình
                synchronized(connectionLock) {
                    if (activeConnection == null) {
                        Log.d(TAG, "Request đã bị hủy, không trả kết quả.")
                        return@thread
                    }
                }
                Log.e(TAG, "Exception: ${e.message}")

                val errorLogJson = JSONObject().apply {
                    put("timestamp", System.currentTimeMillis())
                    put("statusCode", -1)
                    put("errorType", "NETWORK_EXCEPTION")
                    put("model", "N/A")
                    put("apiKeyMasked", "N/A")
                    put("errorMessage", e.localizedMessage ?: e.message ?: "Unknown Exception")
                    put("errorStatus", "EXCEPTION")
                    put("rawResponse", e.stackTraceToString())
                    put("suggestion", "Kiểm tra kết nối Wi-Fi hoặc Bluetooth giữa đồng hồ và điện thoại.")
                    put("source", "WATCH")
                }.toString()

                PhoneCommunicator.sendErrorLogToPhone(context, errorLogJson)

                onResult(false, "Lỗi ngoại lệ", "Lỗi: ${e.localizedMessage}", null)
            } finally {
                synchronized(connectionLock) {
                    if (activeConnection == connection) {
                        activeConnection = null
                    }
                }
                connection?.disconnect()
            }
        }
    }
}