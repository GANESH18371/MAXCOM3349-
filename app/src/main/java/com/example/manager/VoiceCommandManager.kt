package com.example.manager

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.example.util.DebugLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

sealed interface VoiceState {
    object Idle : VoiceState
    object Listening : VoiceState
    data class Processing(val recognizedText: String) : VoiceState
    data class Success(val message: String) : VoiceState
    data class Error(val message: String) : VoiceState
}

class VoiceCommandManager(private val context: Context) {

    private val _voiceState = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _lastRecognizedText = MutableStateFlow("")
    val lastRecognizedText: StateFlow<String> = _lastRecognizedText.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null

    init {
        initRecognizer()
    }

    private fun initRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(createRecognitionListener())
            }
        } else {
            Log.w(TAG, "Speech recognition not available on this device")
        }
    }

    fun startListening() {
        if (speechRecognizer == null) {
            initRecognizer()
        }

        val recognizer = speechRecognizer ?: run {
            _voiceState.value = VoiceState.Error("SpeechRecognizer unavailable on device")
            return
        }

        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                // Support both Hindi and English
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
                putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-IN", "en-US", "hi-IN"))
            }

            _voiceState.value = VoiceState.Listening
            recognizer.startListening(intent)
            DebugLogger.logInfo("Voice listening started (Hindi + English)...")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening", e)
            _voiceState.value = VoiceState.Error("Could not start listening: ${e.message}")
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping listener", e)
        }
        _voiceState.value = VoiceState.Idle
    }

    fun cancelListening() {
        try {
            speechRecognizer?.cancel()
        } catch (e: Exception) {
            Log.w(TAG, "Error cancelling listener", e)
        }
        _voiceState.value = VoiceState.Idle
    }

    fun processCommand(commandText: String) {
        val trimmed = commandText.trim()
        if (trimmed.isBlank()) return

        _lastRecognizedText.value = trimmed
        _voiceState.value = VoiceState.Processing(trimmed)
        DebugLogger.logInfo("Processing voice command: \"$trimmed\"")

        val lower = trimmed.lowercase(Locale.getDefault())

        // 1. Check if it's a Hardware Toggle Command
        if (isHardwareCommand(lower)) {
            handleHardwareVoiceCommand(lower)
            _voiceState.value = VoiceState.Success("Hardware action triggered for \"$trimmed\"")
            return
        }

        // 2. Otherwise it's an App Open Command!
        val launched = AppOpenManager.processAndLaunch(context, trimmed)
        if (launched) {
            _voiceState.value = VoiceState.Success("App opened for \"$trimmed\"")
        } else {
            _voiceState.value = VoiceState.Error("No matching app found for \"$trimmed\"")
        }
    }

    private fun isHardwareCommand(lower: String): Boolean {
        val hardwareKeywords = listOf(
            "wifi", "wi-fi", "वाई-फाई", "वाईफाई",
            "bluetooth", "ब्लूटूथ",
            "mobile data", "data on", "data off", "data band", "data chalu", "डेटा",
            "hotspot", "हॉटस्पॉट", "tethering",
            "torch", "flashlight", "टॉर्च", "फ्लैशलाइट",
            "volume", "sound", "awaz", "आवाज", "आवाज़", "mute",
            "brightness", "screen light", "chamak", "ब्राइटनेस", "रोशनी",
            "dnd", "do not disturb", "silent",
            "airplane", "flight mode", "हवाई मोड"
        )
        return hardwareKeywords.any { lower.contains(it) }
    }

    private fun handleHardwareVoiceCommand(lower: String) {
        val turnOffWords = listOf("off", "band", "close", "disable", "stop", "बंद", "हटाओ", "rok")
        val isExplicitOff = turnOffWords.any { lower.contains(it) }
        val turnOnWords = listOf("on", "chalu", "open", "enable", "start", "चालू", "जलाओ", "on karo")
        val isExplicitOn = turnOnWords.any { lower.contains(it) }

        val targetState = when {
            isExplicitOff -> false
            isExplicitOn -> true
            else -> null
        }

        when {
            // Torch
            lower.contains("torch") || lower.contains("flashlight") || lower.contains("टॉर्च") || lower.contains("फ्लैशलाइट") -> {
                HardwareToggleManager.toggleTorch(context, targetState)
            }
            // WiFi
            lower.contains("wifi") || lower.contains("wi-fi") || lower.contains("वाई-फाई") || lower.contains("वाईफाई") -> {
                HardwareToggleManager.toggleWifi(context)
            }
            // Bluetooth
            lower.contains("bluetooth") || lower.contains("ब्लूटूथ") -> {
                HardwareToggleManager.toggleBluetooth(context)
            }
            // Mobile Data
            lower.contains("data") || lower.contains("डेटा") || lower.contains("net") -> {
                HardwareToggleManager.toggleMobileData(context)
            }
            // Hotspot
            lower.contains("hotspot") || lower.contains("हॉटस्पॉट") -> {
                HardwareToggleManager.toggleHotspot(context)
            }
            // Volume
            lower.contains("volume") || lower.contains("sound") || lower.contains("awaz") || lower.contains("आवाज") -> {
                when {
                    lower.contains("up") || lower.contains("badhao") || lower.contains("badao") || lower.contains("jyada") || lower.contains("बढ़ाओ") -> {
                        HardwareToggleManager.adjustVolume(context, VolumeAction.UP)
                    }
                    lower.contains("down") || lower.contains("kam") || lower.contains("ghatao") || lower.contains("कम") -> {
                        HardwareToggleManager.adjustVolume(context, VolumeAction.DOWN)
                    }
                    lower.contains("mute") || lower.contains("silent") || lower.contains("band") -> {
                        HardwareToggleManager.adjustVolume(context, VolumeAction.MUTE)
                    }
                    lower.contains("max") || lower.contains("full") || lower.contains("100") -> {
                        HardwareToggleManager.adjustVolume(context, VolumeAction.MAX)
                    }
                    else -> {
                        HardwareToggleManager.adjustVolume(context, VolumeAction.UP)
                    }
                }
            }
            // Brightness
            lower.contains("brightness") || lower.contains("screen light") || lower.contains("chamak") || lower.contains("ब्राइटनेस") -> {
                HardwareToggleManager.toggleBrightness(context)
            }
            // DND
            lower.contains("dnd") || lower.contains("disturb") || lower.contains("silent") -> {
                HardwareToggleManager.toggleDnd(context, targetState)
            }
            // Airplane Mode
            lower.contains("airplane") || lower.contains("flight") || lower.contains("हवाई मोड") -> {
                HardwareToggleManager.toggleAirplaneMode(context)
            }
        }
    }

    private fun createRecognitionListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _voiceState.value = VoiceState.Listening
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            _voiceState.value = VoiceState.Processing("Processing audio...")
        }

        override fun onError(error: Int) {
            val errorMsg = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Audio permission required"
                SpeechRecognizer.ERROR_NETWORK -> "Network required for voice pack"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Voice network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                SpeechRecognizer.ERROR_SERVER -> "Server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                else -> "Speech recognition error ($error)"
            }
            Log.w(TAG, "SpeechRecognizer error: $errorMsg")
            _voiceState.value = VoiceState.Error(errorMsg)
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val command = matches[0]
                processCommand(command)
            } else {
                _voiceState.value = VoiceState.Error("No match found")
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                _lastRecognizedText.value = matches[0]
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun destroy() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {
            Log.w(TAG, "Error destroying speech recognizer", e)
        }
    }

    companion object {
        private const val TAG = "VoiceCommandManager"
    }
}
