package com.oppowatch.gemini.phone

import android.content.Intent
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class PhoneWearableListenerService : WearableListenerService() {

    private val filterManager by lazy { BluetoothFilterManager(this) }
    private val historyManager by lazy { QaHistoryManager(this) }
    private val errorLogManager by lazy { ApiErrorLogManager(this) }

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
                setPackage(packageName)
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
        } else if (messageEvent.path == "/gemini_error_log") {
            val rawJson = String(messageEvent.data, Charsets.UTF_8)
            Log.e("PhoneListener", "Nhận được log lỗi API từ đồng hồ: $rawJson")
            try {
                val json = org.json.JSONObject(rawJson)
                val item = ApiErrorItem(
                    id = java.util.UUID.randomUUID().toString(),
                    timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                    statusCode = json.optInt("statusCode", 0),
                    errorType = json.optString("errorType", "HTTP_ERROR"),
                    model = json.optString("model", ""),
                    apiKeyMasked = json.optString("apiKeyMasked", ""),
                    errorMessage = json.optString("errorMessage", ""),
                    errorStatus = json.optString("errorStatus", ""),
                    rawResponse = json.optString("rawResponse", ""),
                    suggestion = json.optString("suggestion", ""),
                    source = json.optString("source", "WATCH")
                )
                errorLogManager.addError(item)

                val broadcastIntent = Intent("com.oppowatch.gemini.ERROR_LOG_RECEIVED").apply {
                    setPackage(packageName)
                    putExtra("raw_json", rawJson)
                }
                sendBroadcast(broadcastIntent)
            } catch (e: Exception) {
                Log.e("PhoneListener", "Lỗi xử lý error log từ đồng hồ: ${e.message}")
            }
        } else if (messageEvent.path == "/gemini_reply_message") {
            val rawPayload = String(messageEvent.data, Charsets.UTF_8)
            Log.d("PhoneListener", "Received reply message command from watch: $rawPayload")

            var recipient = ""
            var message = ""
            try {
                val json = org.json.JSONObject(rawPayload)
                recipient = json.optString("recipient", "").trim()
                message = json.optString("message", "").trim()
            } catch (_: Exception) {
                message = rawPayload
            }

            if (message.isEmpty()) {
                Log.w("PhoneListener", "Nội dung tin nhắn trả lời rỗng.")
                return
            }

            val replyResult = QuickReplyNotificationService.sendReply(this, recipient, message)

            val ttsResponse = if (replyResult.success) {
                val who = replyResult.senderName.ifEmpty { recipient }
                if (who.isNotEmpty() && who != replyResult.appName) {
                    "Đã trả lời $who qua ${replyResult.appName}: ${replyResult.replyText}"
                } else {
                    "Đã gửi tin nhắn qua ${replyResult.appName}: ${replyResult.replyText}"
                }
            } else {
                replyResult.errorMessage
            }

            // Lưu vào lịch sử tác vụ
            historyManager.addEntry(
                "💬 Trả lời tin nhắn ${if (recipient.isNotEmpty()) recipient else "(gần nhất)"}",
                ttsResponse,
                System.currentTimeMillis()
            )

            // Đọc phản hồi TTS qua tai nghe / loa ngoài điện thoại
            filterManager.checkAndPlayTtsIfAllowed(ttsResponse) { allowed, _ ->
                if (allowed) {
                    TtsSpeaker.speak(this, ttsResponse)
                }
            }
        } else if (messageEvent.path == "/gemini_task") {
            val rawPayload = String(messageEvent.data, Charsets.UTF_8)
            Log.d("PhoneListener", "Received task command from watch: $rawPayload")

            var title = ""
            var notes = ""
            try {
                val json = org.json.JSONObject(rawPayload)
                title = json.optString("title", "").trim()
                notes = json.optString("notes", "").trim()
            } catch (_: Exception) {
                title = rawPayload
            }

            if (title.isNotEmpty()) {
                GoogleTasksManager.addTask(this, title, notes)
                val ttsResponse = "Đã thêm vào Google Tasks: $title"

                historyManager.addEntry(
                    "📝 Google Tasks",
                    title,
                    System.currentTimeMillis()
                )

                filterManager.checkAndPlayTtsIfAllowed(ttsResponse) { allowed, _ ->
                    if (allowed) {
                        TtsSpeaker.speak(this, ttsResponse)
                    }
                }
            }
        } else if (messageEvent.path == "/gemini_reminder") {
            val rawPayload = String(messageEvent.data, Charsets.UTF_8)
            Log.d("PhoneListener", "Received reminder command from watch: $rawPayload")

            var message = ""
            var delaySeconds = 300
            try {
                val json = org.json.JSONObject(rawPayload)
                message = json.optString("message", "").trim()
                delaySeconds = json.optInt("delay_seconds", 300)
            } catch (_: Exception) {
                message = rawPayload
            }

            if (message.isNotEmpty()) {
                ReminderManager.scheduleReminder(this, message, delaySeconds)
                val m = delaySeconds / 60
                val ttsResponse = if (m > 0) "Đã hẹn nhắc nhở sau $m phút: $message" else "Đã hẹn nhắc nhở: $message"

                historyManager.addEntry(
                    "⏰ Nhắc nhở theo ngữ cảnh",
                    ttsResponse,
                    System.currentTimeMillis()
                )

                filterManager.checkAndPlayTtsIfAllowed(ttsResponse) { allowed, _ ->
                    if (allowed) {
                        TtsSpeaker.speak(this, ttsResponse)
                    }
                }
            }
        } else if (messageEvent.path == "/gemini_clipboard") {
            val rawPayload = String(messageEvent.data, Charsets.UTF_8)
            Log.d("PhoneListener", "Received clipboard/dictation command from watch: $rawPayload")

            var text = ""
            try {
                val json = org.json.JSONObject(rawPayload)
                text = json.optString("text", "").trim()
            } catch (_: Exception) {
                text = rawPayload
            }

            if (text.isNotEmpty()) {
                val trampolineIntent = Intent(this, ClipboardTrampolineActivity::class.java).apply {
                    putExtra("text", text)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                }
                startActivity(trampolineIntent)

                val ttsResponse = "Đã sao chép vào bộ nhớ tạm"
                historyManager.addEntry(
                    "📋 Chép chính tả vào Clipboard",
                    text,
                    System.currentTimeMillis()
                )

                filterManager.checkAndPlayTtsIfAllowed(ttsResponse) { allowed, _ ->
                    if (allowed) {
                        TtsSpeaker.speak(this, ttsResponse)
                    }
                }
            }
        }
    }
}