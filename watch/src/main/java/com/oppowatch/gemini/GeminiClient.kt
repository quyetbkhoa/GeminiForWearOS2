package com.oppowatch.gemini

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

object GeminiClient {

    private const val TAG = "GeminiClient"
    private val API_KEY = GeminiConfig.GEMINI_API_KEY
    private const val MODEL = "gemini-3.5-flash-lite"
    private val ENDPOINT get() = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$API_KEY"

    private const val SYSTEM_INSTRUCTION =
        "Bạn là trợ lý AI thông minh tích hợp trên đồng hồ Wear OS. " +
        "QUY TẮC BẮT BUỘC: " +
        "1. Hãy nghe file âm thanh giọng nói của người dùng và nhận diện chính xác câu hỏi. " +
        "2. Trả lời theo đúng định dạng JSON chuẩn gồm 2 trường: " +
        "\"question\": câu hỏi hoặc yêu cầu của người dùng được viết lại chuẩn tiếng Việt; " +
        "\"answer\": câu trả lời siêu ngắn gọn, súc tích, đi thẳng vào đáp án trong 1 đến 2 câu ngắn. " +
        "3. Tuyệt đối chỉ trả về chuỗi JSON thuần túy, không dùng markdown code block ```json. " +
        "Ví dụ: {\"question\": \"Mấy giờ rồi\", \"answer\": \"Bây giờ là 6 giờ sáng.\"}"

    fun askGemini(audioBase64: String, onResult: (Boolean, String, String) -> Unit) {
        thread {
            var connection: HttpURLConnection? = null
            try {
                val url = URL(ENDPOINT)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = 15000
                    readTimeout = 20000
                    doOutput = true
                }

                // Build Request JSON
                val rootJson = JSONObject()
                val contentsArray = JSONArray()
                val contentObj = JSONObject()
                val partsArray = JSONArray()

                // 1. Text prompt with system instruction
                val textPart = JSONObject()
                textPart.put("text", "$SYSTEM_INSTRUCTION\nFile âm thanh của người dùng:")
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
                Log.e(TAG, "Exception: ${e.message}")
                onResult(false, "Lỗi ngoại lệ", "Lỗi: ${e.localizedMessage}")
            } finally {
                connection?.disconnect()
            }
        }
    }
}