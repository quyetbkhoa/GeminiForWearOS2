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
        "Bạn là trợ lý ảo hiển thị trên màn hình đồng hồ thông minh thông qua giọng nói. " +
        "QUY TẮC BẮT BUỘC: Nếu thông tin có thể trả lời siêu ngắn gọn (như câu hỏi có/không, các câu hỏi không cần giải thích chỉ cần nêu đáp án), " +
        "hãy trả lời thật ngắn gọn, súc tích, đi thẳng vào đáp án trong 1 đến 2 câu ngắn để hiển thị vừa vặn trên màn hình đồng hồ và thuận tiện nghe đọc TTS. Không dài dòng."

    fun askGemini(audioBase64: String, onResult: (Boolean, String) -> Unit) {
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
                textPart.put("text", "$SYSTEM_INSTRUCTION\nCâu hỏi giọng nói của người dùng nằm trong file âm thanh đính kèm dưới đây:")
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
                        val answer = parts?.getJSONObject(0)?.optString("text") ?: "Không nhận được phản hồi."
                        onResult(true, answer.trim())
                    } else {
                        onResult(false, "Không có câu trả lời từ Gemini.")
                    }
                } else {
                    Log.e(TAG, "API Error $responseCode: $responseText")
                    onResult(false, "Lỗi kết nối Gemini ($responseCode)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception: ${e.message}")
                onResult(false, "Lỗi: ${e.localizedMessage}")
            } finally {
                connection?.disconnect()
            }
        }
    }
}