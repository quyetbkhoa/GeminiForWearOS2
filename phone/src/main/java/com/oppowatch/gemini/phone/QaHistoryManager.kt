package com.oppowatch.gemini.phone

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class QaItem(
    val question: String,
    val answer: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun getFormattedTime(): String {
        val sdf = SimpleDateFormat("HH:mm:ss - dd/MM", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}

class QaHistoryManager(context: Context) {

    private val prefs = context.getSharedPreferences("gemini_qa_history", Context.MODE_PRIVATE)

    fun addEntry(question: String, answer: String, timestamp: Long = System.currentTimeMillis()) {
        val list = getHistory().toMutableList()
        list.add(0, QaItem(question, answer, timestamp)) // Newest first

        if (list.size > 50) {
            list.removeAt(list.size - 1)
        }

        saveHistory(list)
    }

    fun getHistory(): List<QaItem> {
        val jsonStr = prefs.getString("history_json", null) ?: return emptyList()
        val result = mutableListOf<QaItem>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                result.add(
                    QaItem(
                        question = obj.optString("question", "Câu hỏi bằng giọng nói"),
                        answer = obj.optString("answer", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
        } catch (_: Exception) {}
        return result
    }

    fun clearHistory() {
        prefs.edit().remove("history_json").apply()
    }

    private fun saveHistory(list: List<QaItem>) {
        val array = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("question", item.question)
                put("answer", item.answer)
                put("timestamp", item.timestamp)
            }
            array.put(obj)
        }
        prefs.edit().putString("history_json", array.toString()).apply()
    }
}
