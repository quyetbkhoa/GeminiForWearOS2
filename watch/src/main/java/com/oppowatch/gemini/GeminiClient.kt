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

    fun askGemini(context: Context, audioBase64: String, onResult: (Boolean, String, String) -> Unit) {
        thread {
            var connection: HttpURLConnection? = null
            try {
                // Ưu tiên đọc API Key cá nhân do người dùng cấu hình từ điện thoại đồng bộ sang
                val prefs = context.getSharedPreferences("gemini_prefs", Context.MODE_PRIVATE)
                val customKey = prefs.getString("custom_api_key", null)?.trim()
                val activeApiKey = if (!customKey.isNullOrEmpty()) customKey else GeminiConfig.GEMINI_API_KEY

                val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$activeApiKey"
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
                    "2. Trả lời theo đúng định dạng JSON chuẩn gồm 2 trường: " +
                    "\"question\": câu hỏi hoặc yêu cầu của người dùng được viết lại chuẩn tiếng Việt; " +
                    "\"answer\": câu trả lời siêu ngắn gọn, súc tích, đi thẳng vào đáp án trong 1 đến 2 câu ngắn. " +
                    "3. Tuyệt đối chỉ trả về chuỗi JSON thuần túy, không dùng markdown code block ```json."

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
                        } catch (_: Exception) {}

                        onResult(true, question, answer)
                    } else {
                        onResult(false, "Không rõ câu hỏi", "Không có câu trả lời từ Gemini.")
                    }
                } else {
                    Log.e(TAG, "API Error $responseCode: $responseText")
                    onResult(false, "Lỗi kết nối", "Lỗi kết nối Gemini ($responseCode)")
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
                onResult(false, "Lỗi ngoại lệ", "Lỗi: ${e.localizedMessage}")
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