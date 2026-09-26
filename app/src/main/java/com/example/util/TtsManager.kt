package com.example.util

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class VoiceProfileInfo(
    val id: String,
    val displayName: String,
    val languageCode: String,
    val isDefault: Boolean = false
)

object TtsManager {
    private const val TAG = "TtsManager"
    private const val PREFS_NAME = "max_tts_unified_prefs"
    private const val KEY_PITCH = "tts_pitch"
    private const val KEY_RATE = "tts_rate"
    private const val KEY_VOICE_ID = "tts_voice_id"
    private const val KEY_LANG_MODE = "tts_lang_mode"

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var pendingSpeech: String? = null
    private var pendingCallback: (() -> Unit)? = null
    private val callbacks = ConcurrentHashMap<String, () -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var appContext: Context? = null

    // Reactive StateFlows for UI and Settings
    private val _pitch = MutableStateFlow(1.0f)
    val pitch: StateFlow<Float> = _pitch.asStateFlow()

    private val _speechRate = MutableStateFlow(1.0f)
    val speechRate: StateFlow<Float> = _speechRate.asStateFlow()

    private val _selectedVoiceId = MutableStateFlow("auto_natural")
    val selectedVoiceId: StateFlow<String> = _selectedVoiceId.asStateFlow()

    private val _languageMode = MutableStateFlow("auto") // "auto", "hi_IN", "en_IN"
    val languageMode: StateFlow<String> = _languageMode.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<VoiceProfileInfo>>(emptyList())
    val availableVoices: StateFlow<List<VoiceProfileInfo>> = _availableVoices.asStateFlow()

    fun init(context: Context) {
        if (tts != null) return
        appContext = context.applicationContext

        // Load saved preferences
        loadPreferences(context)

        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isInitialized = true
                    Log.i(TAG, "Central Unified TTS Engine initialized successfully")

                    // Configure default natural language fallback
                    val hiResult = tts?.setLanguage(Locale("hi", "IN"))
                    if (hiResult == TextToSpeech.LANG_MISSING_DATA || hiResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts?.setLanguage(Locale.ENGLISH)
                    }

                    // Apply configured pitch and speech rate
                    applyAudioSettings()

                    // Discover and index high-quality available voices
                    refreshAvailableVoices()

                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _isSpeaking.value = true
                        }

                        override fun onDone(utteranceId: String?) {
                            _isSpeaking.value = false
                            utteranceId?.let { id ->
                                callbacks.remove(id)?.let { cb ->
                                    mainHandler.post { cb.invoke() }
                                }
                            }
                        }

                        override fun onError(utteranceId: String?) {
                            _isSpeaking.value = false
                            utteranceId?.let { id ->
                                callbacks.remove(id)?.let { cb ->
                                    mainHandler.post { cb.invoke() }
                                }
                            }
                        }
                    })

                    // Speak any queued pending announcement
                    pendingSpeech?.let { text ->
                        val cb = pendingCallback
                        pendingSpeech = null
                        pendingCallback = null
                        speak(text, cb)
                    }
                } else {
                    Log.w(TAG, "TTS Initialization failed with status: $status")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not initialize central TTS", e)
        }
    }

    private fun loadPreferences(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _pitch.value = prefs.getFloat(KEY_PITCH, 1.0f).coerceIn(0.7f, 1.5f)
        _speechRate.value = prefs.getFloat(KEY_RATE, 1.0f).coerceIn(0.7f, 1.5f)
        _selectedVoiceId.value = prefs.getString(KEY_VOICE_ID, "auto_natural") ?: "auto_natural"
        _languageMode.value = prefs.getString(KEY_LANG_MODE, "auto") ?: "auto"
    }

    private fun savePreferences(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat(KEY_PITCH, _pitch.value)
            .putFloat(KEY_RATE, _speechRate.value)
            .putString(KEY_VOICE_ID, _selectedVoiceId.value)
            .putString(KEY_LANG_MODE, _languageMode.value)
            .apply()
    }

    private fun applyAudioSettings() {
        try {
            tts?.setPitch(_pitch.value)
            tts?.setSpeechRate(_speechRate.value)
        } catch (e: Exception) {
            Log.w(TAG, "Error applying audio settings", e)
        }
    }

    private fun refreshAvailableVoices() {
        val ttsInstance = tts ?: return
        try {
            val systemVoices = ttsInstance.voices ?: emptySet()
            val list = mutableListOf<VoiceProfileInfo>()

            list.add(
                VoiceProfileInfo(
                    id = "auto_natural",
                    displayName = "Auto Natural (Hindi & Indian English Hybrid)",
                    languageCode = "hi-IN / en-IN",
                    isDefault = true
                )
            )

            for (v in systemVoices) {
                val locale = v.locale
                val lang = locale.language.lowercase(Locale.ROOT)
                val country = locale.country.lowercase(Locale.ROOT)

                if (lang == "hi" || (lang == "en" && (country == "in" || country == "gb" || country == "us"))) {
                    val qualityStr = if (v.quality >= Voice.QUALITY_VERY_HIGH) " (HD)" else ""
                    val isHindi = lang == "hi"
                    val label = "${if (isHindi) "Hindi" else "English ($country)"} Voice: ${v.name.substringAfterLast("-")}$qualityStr"
                    list.add(
                        VoiceProfileInfo(
                            id = v.name,
                            displayName = label,
                            languageCode = "${locale.language}-${locale.country}"
                        )
                    )
                }
            }
            _availableVoices.value = list
        } catch (e: Exception) {
            Log.w(TAG, "Error enumerating TTS voices", e)
        }
    }

    /**
     * Centralized unified speak function used across ALL app features:
     * - Weather reports
     * - Anti-theft alerts & confirmations
     * - Gemini camera scene analysis responses
     * - WhatsApp auto-reply toggles & responses
     * - Call announce & incoming caller speech
     * - Reminders & Alarms
     * - Hardware toggles & App launcher confirmations
     */
    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (text.isBlank()) {
            onDone?.let { mainHandler.post { it.invoke() } }
            return
        }

        DebugLogger.logInfo("TTS Speaking: \"$text\"")

        if (!isInitialized || tts == null) {
            pendingSpeech = text
            pendingCallback = onDone
            return
        }

        try {
            val ttsEngine = tts!!

            // 1. Always enforce unified user Pitch & Speech Rate
            ttsEngine.setPitch(_pitch.value)
            ttsEngine.setSpeechRate(_speechRate.value)

            // 2. Determine Smart Script / Language Target
            val hasDevanagari = text.any { it in '\u0900'..'\u097F' }
            val mode = _languageMode.value

            val targetLocale = when {
                mode == "hi_IN" -> Locale("hi", "IN")
                mode == "en_IN" -> Locale("en", "IN")
                hasDevanagari -> Locale("hi", "IN")
                else -> Locale("en", "IN")
            }

            // Set language matching the content to ensure zero phonetic distortions
            val langResult = ttsEngine.setLanguage(targetLocale)
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                if (targetLocale.language == "hi") {
                    ttsEngine.setLanguage(Locale.ENGLISH)
                }
            }

            // 3. Apply custom selected voice if explicitly chosen
            val selectedVoice = _selectedVoiceId.value
            if (selectedVoice != "auto_natural") {
                val matchingVoice = ttsEngine.voices?.find { it.name == selectedVoice }
                if (matchingVoice != null) {
                    ttsEngine.voice = matchingVoice
                }
            }

            val utteranceId = "max_tts_${System.currentTimeMillis()}"
            if (onDone != null) {
                callbacks[utteranceId] = onDone
            }

            ttsEngine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        } catch (e: Exception) {
            Log.e(TAG, "Error speaking text in unified TTS", e)
            _isSpeaking.value = false
            onDone?.let { mainHandler.post { it.invoke() } }
        }
    }

    fun setPitch(context: Context, value: Float) {
        _pitch.value = value.coerceIn(0.7f, 1.5f)
        applyAudioSettings()
        savePreferences(context)
    }

    fun setSpeechRate(context: Context, value: Float) {
        _speechRate.value = value.coerceIn(0.7f, 1.5f)
        applyAudioSettings()
        savePreferences(context)
    }

    fun setVoiceId(context: Context, voiceId: String) {
        _selectedVoiceId.value = voiceId
        savePreferences(context)
    }

    fun setLanguageMode(context: Context, mode: String) {
        _languageMode.value = mode
        savePreferences(context)
    }

    fun resetToDefaults(context: Context) {
        _pitch.value = 1.0f
        _speechRate.value = 1.0f
        _selectedVoiceId.value = "auto_natural"
        _languageMode.value = "auto"
        applyAudioSettings()
        savePreferences(context)
    }

    fun testSampleSpeech(context: Context, isHindi: Boolean = true) {
        val sample = if (isHindi) {
            "नमस्ते! मैं मैक्स हूँ. आपकी आवाज़ और सेटिंग्स बिल्कुल सही काम कर रही हैं."
        } else {
            "Hello! I am Max. Your unified speech engine and voice settings are working perfectly."
        }
        speak(sample)
    }

    fun stop() {
        try {
            tts?.stop()
            _isSpeaking.value = false
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping TTS", e)
        }
    }

    fun shutdown() {
        try {
            callbacks.clear()
            tts?.stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
            _isSpeaking.value = false
        } catch (e: Exception) {
            Log.w(TAG, "Error shutting down TTS", e)
        }
    }
}
