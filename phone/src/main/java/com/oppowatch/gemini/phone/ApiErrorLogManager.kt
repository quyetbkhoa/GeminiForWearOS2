package com.oppowatch.gemini.phone

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ApiErrorItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val statusCode: Int = 0,
    val errorType: String = "HTTP_ERROR",
    val model: String = "",
    val apiKeyMasked: String = "",
    val errorMessage: String = "",
    val errorStatus: String = "",
    val rawResponse: String = "",
    val suggestion: String = "",
    val source: String = "WATCH" // "WATCH" hoặc "PHONE_TEST"
) {
    fun getFormattedTime(): String {
        val sdf = SimpleDateFormat("HH:mm:ss - dd/MM/yyyy", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun getStatusBadgeText(): String {
        return if (statusCode > 0) {
            "HTTP $statusCode ${if (errorStatus.isNotEmpty()) "($errorStatus)" else ""}".trim()
        } else {
            errorType
        }
    }
}

class ApiErrorLogManager(context: Context) {
    private val prefs = context.getSharedPreferences("gemini_api_error_logs", Context.MODE_PRIVATE)

    fun addError(item: ApiErrorItem) {
        val list = getErrorLogs().toMutableList()
        // Không trùng lặp nếu cùng statusCode và trong vòng 2 giây
        if (list.isNotEmpty() && list[0].statusCode == item.statusCode &&
            Math.abs(list[0].timestamp - item.timestamp) < 2000
        ) {
            return
        }
        list.add(0, item) // Mới nhất lên đầu
        if (list.size > 50) {
            list.removeAt(list.size - 1)
        }
        saveLogs(list)
    }

    fun getErrorLogs(): List<ApiErrorItem> {
        val jsonStr = prefs.getString("error_logs_json", null) ?: return emptyList()
        val result = mutableListOf<ApiErrorItem>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                result.add(
                    ApiErrorItem(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        statusCode = obj.optInt("statusCode", 0),
                        errorType = obj.optString("errorType", "HTTP_ERROR"),
                        model = obj.optString("model", ""),
                        apiKeyMasked = obj.optString("apiKeyMasked", ""),
                        errorMessage = obj.optString("errorMessage", ""),
                        errorStatus = obj.optString("errorStatus", ""),
                        rawResponse = obj.optString("rawResponse", ""),
                        suggestion = obj.optString("suggestion", ""),
                        source = obj.optString("source", "WATCH")
                    )
                )
            }
        } catch (_: Exception) {}
        return result
    }

    fun clearLogs() {
        prefs.edit().remove("error_logs_json").apply()
    }

    private fun saveLogs(list: List<ApiErrorItem>) {
        val array = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("timestamp", item.timestamp)
                put("statusCode", item.statusCode)
                put("errorType", item.errorType)
                put("model", item.model)
                put("apiKeyMasked", item.apiKeyMasked)
                put("errorMessage", item.errorMessage)
                put("errorStatus", item.errorStatus)
                put("rawResponse", item.rawResponse)
                put("suggestion", item.suggestion)
                put("source", item.source)
            }
            array.put(obj)
        }
        prefs.edit().putString("error_logs_json", array.toString()).apply()
    }
}
