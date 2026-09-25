package com.example.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

object TtsManager {
    private const val TAG = "TtsManager"
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var pendingSpeech: String? = null

    fun init(context: Context) {
        if (tts != null) return
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isInitialized = true
                    // Try Hindi or English
                    val result = tts?.setLanguage(Locale("hi", "IN"))
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts?.setLanguage(Locale.ENGLISH)
                    }
                    pendingSpeech?.let { text ->
                        speak(text)
                        pendingSpeech = null
                    }
                } else {
                    Log.w(TAG, "TTS Initialization failed: $status")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not initialize TTS", e)
        }
    }

    fun speak(text: String) {
        DebugLogger.logInfo("TTS Speaking: \"$text\"")
        if (!isInitialized || tts == null) {
            pendingSpeech = text
            return
        }
        try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "max_tts_${System.currentTimeMillis()}")
        } catch (e: Exception) {
            Log.w(TAG, "Error speaking text", e)
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
        } catch (e: Exception) {
            Log.w(TAG, "Error shutting down TTS", e)
        }
    }
}
