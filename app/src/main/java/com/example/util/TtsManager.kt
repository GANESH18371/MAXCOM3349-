package com.example.util

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
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

data class PresetVoice(
    val id: String,
    val name: String,
    val gender: String, // "Male" or "Female"
    val subtitle: String,
    val sampleText: String,
    val preferredKeywords: List<String>,
    val pitchMultiplier: Float = 1.0f
)

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
    private const val KEY_SELECTED_PRESET_ID = "selected_preset_voice_id"
    private const val KEY_SELECTED_VOICE_NAME = "selected_preset_voice_name"
    private const val KEY_LANG_MODE = "tts_lang_mode"

    // 4 NATURAL PRESET VOICES (2 Male, 2 Female)
    val ARJUN = PresetVoice(
        id = "arjun",
        name = "Arjun",
        gender = "Male",
        subtitle = "Male • Confident & Deep (Hindi/English)",
        sampleText = "नमस्ते! मैं मैक्स हूँ। आपकी हर कमांड के लिए मैं हमेशा तैयार हूँ।",
        preferredKeywords = listOf("-hid-", "male", "-end-", "-enc-", "hi-in-x-hid", "en-in-x-end"),
        pitchMultiplier = 0.92f
    )

    val ROHAN = PresetVoice(
        id = "rohan",
        name = "Rohan",
        gender = "Male",
        subtitle = "Male • Energetic & Casual (Hindi/English)",
        sampleText = "हे दोस्त! मैं मैक्स हूँ, बताइए आज क्या काम करना है?",
        preferredKeywords = listOf("-hic-", "-cfa-", "male", "-enc-", "hi-in-x-hic", "en-in-x-enc"),
        pitchMultiplier = 1.05f
    )

    val ANANYA = PresetVoice(
        id = "ananya",
        name = "Ananya",
        gender = "Female",
        subtitle = "Female • Sweet & Natural (Hindi/English)",
        sampleText = "नमस्ते! मैं मैक्स हूँ। मैं आपके सारे काम चुटकी में कर सकती हूँ।",
        preferredKeywords = listOf("-hie-", "-hia-", "female", "-ene-", "hi-in-x-hie", "hi-in-x-hia", "en-in-x-ene"),
        pitchMultiplier = 1.12f
    )

    val PRIYA = PresetVoice(
        id = "priya",
        name = "Priya",
        gender = "Female",
        subtitle = "Female • Calm & Professional (Hindi/English)",
        sampleText = "नमस्ते, मैं मैक्स हूँ। आपका पर्सनल एआई असिस्टेंट।",
        preferredKeywords = listOf("-hib-", "female", "-enf-", "-ena-", "hi-in-x-hib", "en-in-x-enf", "en-in-x-ena"),
        pitchMultiplier = 1.00f
    )

    val PRESET_VOICES = listOf(ARJUN, ROHAN, ANANYA, PRIYA)

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var pendingSpeech: String? = null
    private var pendingCallback: (() -> Unit)? = null
    private val callbacks = ConcurrentHashMap<String, () -> Unit>()

    private fun postOnMain(action: () -> Unit) {
        try {
            val looper = Looper.getMainLooper()
            if (looper != null) {
                Handler(looper).post(action)
                return
            }
        } catch (_: Throwable) {
            // In unit test environments where Looper.getMainLooper() is unmocked
        }
        action.invoke()
    }

    private var appContext: Context? = null

    // Reactive StateFlows for UI and Settings
    private val _pitch = MutableStateFlow(1.0f)
    val pitch: StateFlow<Float> = _pitch.asStateFlow()

    private val _speechRate = MutableStateFlow(1.02f)
    val speechRate: StateFlow<Float> = _speechRate.asStateFlow()

    private val _selectedPreset = MutableStateFlow(ARJUN)
    val selectedPreset: StateFlow<PresetVoice> = _selectedPreset.asStateFlow()

    private val _languageMode = MutableStateFlow("auto") // "auto", "hi_IN", "en_IN"
    val languageMode: StateFlow<String> = _languageMode.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    fun init(context: Context) {
        if (tts != null) return
        appContext = context.applicationContext

        loadPreferences(context)

        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isInitialized = true
                    Log.i(TAG, "Central Unified TTS Engine initialized with preset: ${_selectedPreset.value.name}")

                    try {
                        val audioAttributes = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                        tts?.setAudioAttributes(audioAttributes)
                    } catch (e: Exception) {
                        Log.w(TAG, "AudioAttributes setup: ${e.message}")
                    }

                    // Configure default natural language
                    val hiResult = tts?.setLanguage(Locale("hi", "IN"))
                    if (hiResult == TextToSpeech.LANG_MISSING_DATA || hiResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts?.setLanguage(Locale.ENGLISH)
                    }

                    applyAudioSettings()

                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _isSpeaking.value = true
                        }

                        override fun onDone(utteranceId: String?) {
                            _isSpeaking.value = false
                            utteranceId?.let { id ->
                                callbacks.remove(id)?.let { cb ->
                                    postOnMain { cb.invoke() }
                                }
                            }
                        }

                        override fun onError(utteranceId: String?) {
                            _isSpeaking.value = false
                            utteranceId?.let { id ->
                                callbacks.remove(id)?.let { cb ->
                                    postOnMain { cb.invoke() }
                                }
                            }
                        }
                    })

                    pendingSpeech?.let { text ->
                        val cb = pendingCallback
                        pendingSpeech = null
                        pendingCallback = null
                        speakWithSelectedVoice(text, onDone = cb)
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
        _speechRate.value = prefs.getFloat(KEY_RATE, 1.02f).coerceIn(0.7f, 1.5f)
        val savedPresetId = prefs.getString(KEY_SELECTED_PRESET_ID, ARJUN.id) ?: ARJUN.id
        _selectedPreset.value = PRESET_VOICES.find { it.id == savedPresetId } ?: ARJUN
        _languageMode.value = prefs.getString(KEY_LANG_MODE, "auto") ?: "auto"
    }

    private fun savePreferences(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat(KEY_PITCH, _pitch.value)
            .putFloat(KEY_RATE, _speechRate.value)
            .putString(KEY_SELECTED_PRESET_ID, _selectedPreset.value.id)
            .putString(KEY_SELECTED_VOICE_NAME, _selectedPreset.value.name)
            .putString(KEY_LANG_MODE, _languageMode.value)
            .apply()
    }

    fun selectPresetVoice(context: Context, presetId: String) {
        val preset = PRESET_VOICES.find { it.id == presetId } ?: ARJUN
        _selectedPreset.value = preset
        savePreferences(context)

        val resolvedVoice = resolveSystemVoiceForPreset(preset)
        val voiceIdToLog = resolvedVoice?.name ?: preset.id

        // Exact Required Debug Log: "VOICE_SELECTED: name=<>, voice_id=<>"
        DebugLogger.logVoiceSelected(name = preset.name, voiceId = voiceIdToLog)
        DebugLogger.logInfo("Preset voice selected: ${preset.name} (${preset.gender}), voiceId=$voiceIdToLog")

        applyAudioSettings()
    }

    private fun applyAudioSettings() {
        try {
            val preset = _selectedPreset.value
            val effectivePitch = (_pitch.value * preset.pitchMultiplier).coerceIn(0.6f, 1.8f)
            tts?.setPitch(effectivePitch)
            tts?.setSpeechRate(_speechRate.value.coerceIn(0.7f, 1.6f))
            resolveSystemVoiceForPreset(preset)?.let {
                tts?.voice = it
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error applying audio settings", e)
        }
    }

    fun resolveSystemVoiceForPreset(preset: PresetVoice): Voice? {
        val ttsEngine = tts ?: return null
        val available = ttsEngine.voices ?: return null
        if (available.isEmpty()) return null

        val isMale = preset.gender.equals("Male", ignoreCase = true)

        val langFiltered = available.filter { v ->
            val l = v.locale.language.lowercase(Locale.ROOT)
            val c = v.locale.country.lowercase(Locale.ROOT)
            l == "hi" || (l == "en" && (c == "in" || c == "gb" || c == "us"))
        }.ifEmpty { available.toList() }

        return langFiltered.maxByOrNull { v ->
            var score = 0
            val n = v.name.lowercase(Locale.ROOT)

            val voiceIsMale = n.contains("male") || n.contains("-hid-") || n.contains("-hic-") || n.contains("-cfc-") || n.contains("-end-") || n.contains("-enc-") || n.contains("-cfa-") || n.contains("d-local") || n.contains("c-local") || n.contains("-m-")
            val voiceIsFemale = n.contains("female") || n.contains("-hie-") || n.contains("-hia-") || n.contains("-hib-") || n.contains("-ena-") || n.contains("-ene-") || n.contains("-enf-")

            if (isMale) {
                if (voiceIsMale) score += 2000
                if (voiceIsFemale) score -= 2000
            } else {
                if (voiceIsFemale) score += 2000
                if (voiceIsMale) score -= 2000
            }

            for (kw in preset.preferredKeywords) {
                if (n.contains(kw.lowercase(Locale.ROOT))) {
                    score += 500
                }
            }

            if (v.quality == Voice.QUALITY_VERY_HIGH) score += 400
            else if (v.quality == Voice.QUALITY_HIGH) score += 200
            if (n.contains("neural")) score += 300
            if (n.contains("wavenet")) score += 300
            if (n.contains("network")) score += 100
            if (!v.isNetworkConnectionRequired) score += 50

            if (v.locale.language == "hi") score += 150
            if (v.locale.country.equals("in", ignoreCase = true)) score += 100

            score
        }
    }

    /**
     * Primary Central TTS Function for the entire application.
     * Guaranteed to consistently use the selected preset voice with unified pitch & rate.
     */
    fun speakWithSelectedVoice(
        text: String,
        caller: String = "general",
        queueMode: Int = TextToSpeech.QUEUE_FLUSH,
        onDone: (() -> Unit)? = null
    ): Boolean {
        val cleanText = sanitizeForSpeech(text)
        if (cleanText.isBlank()) {
            onDone?.let { postOnMain { it.invoke() } }
            return false
        }

        val currentPreset = _selectedPreset.value

        // Exact Required Debug Log: "TTS_CALL: using_voice=<naam>, text=<>"
        DebugLogger.logTtsCall(usingVoice = currentPreset.name, text = cleanText)
        DebugLogger.logTtsCallSource(source = caller, voiceUsed = currentPreset.name)

        val ttsEngine = tts
        if (ttsEngine == null || !isInitialized) {
            pendingSpeech = cleanText
            pendingCallback = onDone
            return false
        }

        try {
            val effectivePitch = (_pitch.value * currentPreset.pitchMultiplier).coerceIn(0.6f, 1.8f)
            ttsEngine.setPitch(effectivePitch)
            ttsEngine.setSpeechRate(_speechRate.value.coerceIn(0.7f, 1.6f))

            val hasDevanagari = cleanText.any { it in '\u0900'..'\u097F' }
            val lower = cleanText.lowercase()
            val isHinglish = listOf(
                "ji", "sir", "ho gaya", "kar diya", "khol diya", "khol", "karo", "chalao", "dekho",
                "samajh", "raha", "hoon", "nahi", "theek", "hai", "batao", "sunao", "badha", "ghata",
                "namaste", "shukriya", "dost", "dhoondo", "lagao", "bajao"
            ).any { lower.contains(it) }

            val targetLocale = if (hasDevanagari || isHinglish || _languageMode.value == "hi_IN") {
                Locale("hi", "IN")
            } else {
                Locale("en", "IN")
            }

            val langResult = ttsEngine.setLanguage(targetLocale)
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                ttsEngine.setLanguage(Locale.ENGLISH)
            }

            resolveSystemVoiceForPreset(currentPreset)?.let {
                ttsEngine.voice = it
            }

            val utteranceId = "tts_${currentPreset.id}_${System.currentTimeMillis()}_${cleanText.hashCode()}"
            if (onDone != null) {
                callbacks[utteranceId] = onDone
            }

            _isSpeaking.value = true
            ttsEngine.speak(cleanText, queueMode, null, utteranceId)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error in speakWithSelectedVoice", e)
            _isSpeaking.value = false
            onDone?.let { postOnMain { it.invoke() } }
            return false
        }
    }

    /**
     * Backward-compatible alias routing directly to speakWithSelectedVoice.
     */
    fun speakIfVoiceReady(
        text: String,
        caller: String = "general",
        queueMode: Int = TextToSpeech.QUEUE_FLUSH,
        onDone: (() -> Unit)? = null
    ): Boolean {
        return speakWithSelectedVoice(text, caller = caller, queueMode = queueMode, onDone = onDone)
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        speakWithSelectedVoice(text, caller = "general", onDone = onDone)
    }

    fun speak(text: String, queueMode: Int, onDone: (() -> Unit)? = null) {
        speakWithSelectedVoice(text, caller = "general", queueMode = queueMode, onDone = onDone)
    }

    /**
     * Speaks the sample phrase for a specific preset voice without changing the user's permanent selection.
     */
    fun speakSampleForPreset(preset: PresetVoice, onDone: (() -> Unit)? = null) {
        val ttsEngine = tts ?: return
        try {
            val effectivePitch = (_pitch.value * preset.pitchMultiplier).coerceIn(0.6f, 1.8f)
            ttsEngine.setPitch(effectivePitch)
            ttsEngine.setSpeechRate(_speechRate.value.coerceIn(0.7f, 1.6f))
            ttsEngine.setLanguage(Locale("hi", "IN"))

            resolveSystemVoiceForPreset(preset)?.let {
                ttsEngine.voice = it
            }

            DebugLogger.logTtsCall(usingVoice = preset.name, text = preset.sampleText)
            DebugLogger.logTtsCallSource(source = "SettingsSample", voiceUsed = preset.name)

            val utteranceId = "sample_${preset.id}_${System.currentTimeMillis()}"
            if (onDone != null) {
                callbacks[utteranceId] = onDone
            }
            _isSpeaking.value = true
            ttsEngine.speak(preset.sampleText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing preset sample", e)
            onDone?.invoke()
        }
    }

    fun setPitch(context: Context, newPitch: Float) {
        _pitch.value = newPitch.coerceIn(0.7f, 1.5f)
        applyAudioSettings()
        savePreferences(context)
    }

    fun setSpeechRate(context: Context, newRate: Float) {
        _speechRate.value = newRate.coerceIn(0.7f, 1.5f)
        applyAudioSettings()
        savePreferences(context)
    }

    fun setLanguageMode(context: Context, mode: String) {
        _languageMode.value = mode
        savePreferences(context)
    }

    fun resetToDefaults(context: Context) {
        _pitch.value = 1.0f
        _speechRate.value = 1.02f
        _selectedPreset.value = ARJUN
        _languageMode.value = "auto"
        applyAudioSettings()
        savePreferences(context)
        DebugLogger.logVoiceSelected(name = ARJUN.name, voiceId = ARJUN.id)
    }

    fun testSampleSpeech(context: Context, isHindi: Boolean = true) {
        val sample = if (isHindi) {
            "नमस्ते! मैं मैक्स हूँ. आपकी आवाज़ और सेटिंग्स बिल्कुल सही काम कर रही हैं."
        } else {
            "Hello! I am Max. Your unified speech engine and voice settings are working perfectly."
        }
        speakWithSelectedVoice(sample, caller = "SettingsTest")
    }

    fun testPhrases(context: Context, phraseIndex: Int) {
        val phrase = when (phraseIndex) {
            0 -> "YouTube khul gaya"
            1 -> "WiFi on kar diya"
            2 -> "आज तापमान 28 डिग्री सेल्सियस है और मौसम साफ है"
            3 -> "Auto-reply on ho gaya"
            4 -> "Alert: Phone moving without authorization!"
            5 -> "Maine aapki permanent memories me save kar liya hai"
            else -> "Max AI assistant taiyaar hai"
        }
        speakWithSelectedVoice(phrase, caller = "SettingsTest")
    }

    private fun sanitizeForSpeech(raw: String): String {
        return raw
            .replace(Regex("\\*\\*|\\*|_|#|`"), "")
            .replace(Regex("https?://\\S+"), "link")
            .replace("°C", " डिग्री सेल्सियस ")
            .replace(Regex("(?i)\\bwi-?fi\\b"), "WiFi")
            .replace(Regex("(?i)\\bdnd\\b"), "Do Not Disturb")
            .replace(Regex("\\s+"), " ")
            .trim()
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
