package com.oppowatch.gemini

import android.util.Log

/**
 * Quản lý bộ nhớ đệm ngữ cảnh hội thoại đa lượt (Multi-turn Conversation Context).
 * - Lưu trữ tối đa 5 lượt hỏi - đáp gần nhất.
 * - Thời gian sống của cache (TTL): 5 phút (300.000 ms).
 * - Tự động xóa cache khi quá thời gian không tương tác hoặc người dùng chủ động làm mới.
 */
object ConversationMemory {

    private const val TAG = "ConversationMemory"
    private const val MAX_TURNS = 5
    private const val CACHE_TTL_MS = 5 * 60 * 1000L // 5 phút

    data class Turn(
        val question: String,
        val answer: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val turns = mutableListOf<Turn>()
    private val lock = Any()

    /**
     * Thêm một lượt hỏi đáp mới vào bộ nhớ đệm
     */
    fun addTurn(question: String, answer: String) {
        val q = question.trim()
        val a = answer.trim()
        if (q.isEmpty() || a.isEmpty()) return

        synchronized(lock) {
            // Kiểm tra tính hợp lệ của cache hiện tại
            checkAndExpireIfNeeded()

            turns.add(Turn(q, a, System.currentTimeMillis()))
            while (turns.size > MAX_TURNS) {
                turns.removeAt(0)
            }
            Log.d(TAG, "Đã lưu lượt hội thoại mới. Tổng số lượt hiện tại: ${turns.size}/$MAX_TURNS")
        }
    }

    /**
     * Lấy danh sách các lượt hội thoại hợp lệ còn hạn trong 5 phút
     */
    fun getValidHistory(): List<Turn> {
        synchronized(lock) {
            checkAndExpireIfNeeded()
            return turns.toList()
        }
    }

    /**
     * Xóa toàn bộ bộ nhớ đệm
     */
    fun clear() {
        synchronized(lock) {
            turns.clear()
            Log.d(TAG, "Đã xóa toàn bộ bộ nhớ đệm hội thoại.")
        }
    }

    /**
     * Kiểm tra thời gian từ lượt cuối cùng, nếu > 5 phút thì reset
     */
    private fun checkAndExpireIfNeeded() {
        if (turns.isEmpty()) return
        val lastTimestamp = turns.last().timestamp
        val now = System.currentTimeMillis()
        if (now - lastTimestamp > CACHE_TTL_MS) {
            Log.d(TAG, "Bộ nhớ đệm hội thoại đã hết hạn (${(now - lastTimestamp) / 1000}s > 300s). Reset.")
            turns.clear()
        }
    }
}
