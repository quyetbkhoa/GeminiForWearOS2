package com.oppowatch.gemini.phone

import android.content.Intent
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class PhoneWearableListenerService : WearableListenerService() {

    private val filterManager by lazy { BluetoothFilterManager(this) }
    private val historyManager by lazy { QaHistoryManager(this) }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)

        if (messageEvent.path == "/gemini_tts_payload") {
            val rawPayload = String(messageEvent.data, Charsets.UTF_8)
            Log.d("PhoneListener", "Received TTS payload from watch: $rawPayload")

            var question = "Câu hỏi từ đồng hồ"
            var answer = rawPayload
            var timestamp = System.currentTimeMillis()

            try {
                val json = org.json.JSONObject(rawPayload)
                if (json.has("answer")) {
                    answer = json.optString("answer", rawPayload)
                    question = json.optString("question", "Câu hỏi bằng giọng nói")
                    timestamp = json.optLong("timestamp", System.currentTimeMillis())
                }
            } catch (_: Exception) {}

            // Save to Q&A History
            historyManager.addEntry(question, answer, timestamp)

            // Broadcast to UI to update both last message and history list
            val broadcastIntent = Intent("com.oppowatch.gemini.TTS_RECEIVED").apply {
                putExtra("question", question)
                putExtra("answer", answer)
                putExtra("timestamp", timestamp)
            }
            sendBroadcast(broadcastIntent)

            // Check if active connected Bluetooth device is in ticked list
            filterManager.checkAndPlayTtsIfAllowed(answer) { allowed, reason ->
                Log.d("PhoneListener", "Bluetooth check decision: $allowed ($reason)")
                if (allowed) {
                    TtsSpeaker.speak(this, answer)
                }
            }
        }
    }
}