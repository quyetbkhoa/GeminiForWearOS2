package com.oppowatch.gemini.phone

import android.content.Intent
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class PhoneWearableListenerService : WearableListenerService() {

    private val filterManager by lazy { BluetoothFilterManager(this) }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)

        if (messageEvent.path == "/gemini_tts_payload") {
            val text = String(messageEvent.data, Charsets.UTF_8)
            Log.d("PhoneListener", "Received TTS payload from watch: $text")

            // Broadcast to UI to display
            val broadcastIntent = Intent("com.oppowatch.gemini.TTS_RECEIVED").apply {
                putExtra("payload", text)
            }
            sendBroadcast(broadcastIntent)

            // Check if active connected Bluetooth device is in ticked list
            filterManager.checkAndPlayTtsIfAllowed(text) { allowed, reason ->
                Log.d("PhoneListener", "Bluetooth check decision: $allowed ($reason)")
                if (allowed) {
                    TtsSpeaker.speak(this, text)
                }
            }
        }
    }
}