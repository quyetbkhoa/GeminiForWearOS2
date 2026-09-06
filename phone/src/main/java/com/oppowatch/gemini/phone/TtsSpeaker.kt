package com.oppowatch.gemini.phone

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

object TtsSpeaker {

    private const val TAG = "TtsSpeaker"
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    fun init(context: Context) {
        if (tts == null) {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = tts?.setLanguage(Locale("vi", "VN"))
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.w(TAG, "Vietnamese TTS not supported, falling back to default")
                        tts?.setLanguage(Locale.getDefault())
                    }
                    isInitialized = true
                    Log.d(TAG, "TTS initialized successfully")
                } else {
                    Log.e(TAG, "TTS initialization failed: $status")
                }
            }
        }
    }

    fun speak(context: Context, text: String) {
        if (!isInitialized) {
            init(context)
        }
        val params = Bundle()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "gemini_tts_utterance")
    }
}