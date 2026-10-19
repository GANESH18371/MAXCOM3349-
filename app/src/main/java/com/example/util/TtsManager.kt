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

    // 2 NATURAL VOICES (1 Male, 1 Female)
    val MALE = PresetVoice(
        id = "male",
        name = "Male",
        gender = "Male",
        subtitle = "Clear, Warm & Confident • Friendly & Pleasant",
        sampleText = "नमस्ते! मैं मैक्स हूँ। आपकी हर बात सुनने और सहायता करने के लिए हमेशा तैयार हूँ।",
        preferredKeywords = listOf("-hib-", "-hic-", "-hif-", "-enb-", "-enc-", "male", "hi-in-x-hic", "hi-in-x-hib", "en-in-x-enc"),
        pitchMultiplier = 0.88f
    )

    val FEMALE = PresetVoice(
        id = "female",
        name = "Female",
        gender = "Female",
        subtitle = "Articulate, Warm & Expressive • Friendly & Engaging",
        sampleText = "नमस्ते! मैं आपकी दोस्त और असिस्टेंट हूँ। कहिए, आज आपके लिए क्या करूँ?",
        preferredKeywords = listOf("-hie-", "-hid-", "-hia-", "-ene-", "-end-", "-ena-", "female", "hi-in-x-hie", "hi-in-x-hid", "hi-in-x-hia", "en-in-x-ene"),
        pitchMultiplier = 1.34f
    )

    // Backward-compatible aliases for legacy references
    val ARJUN = MALE
    val ROHAN = MALE
    val ANANYA = FEMALE
    val PRIYA = FEMALE

    val PRESET_VOICES = listOf(MALE, FEMALE)

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

    private val _selectedPreset = MutableStateFlow(MALE)
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
        val savedPresetId = prefs.getString(KEY_SELECTED_PRESET_ID, MALE.id) ?: MALE.id
        _selectedPreset.value = when (savedPresetId.lowercase()) {
            "female", "ananya", "priya" -> FEMALE
            else -> MALE
        }
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
        val preset = PRESET_VOICES.find { it.id.equals(presetId, ignoreCase = true) }
            ?: (if (presetId.equals("female", ignoreCase = true) || presetId.equals("ananya", ignoreCase = true) || presetId.equals("priya", ignoreCase = true)) FEMALE else MALE)
        _selectedPreset.value = preset
        savePreferences(context)

        val resolvedVoice = resolveSystemVoiceForPreset(preset)
        val voiceIdToLog = resolvedVoice?.name ?: "${preset.id}_natural_v1"

        // Exact Required Debug Log: "VOICE_SELECTED: gender=<Male/Female>, voice_id=<>"
        DebugLogger.logVoiceSelected(gender = preset.gender, voiceId = voiceIdToLog)
        DebugLogger.logInfo("Voice selected: ${preset.gender} (${preset.name}), voiceId=$voiceIdToLog")

        applyAudioSettings()
    }

    private fun applyAudioSettings() {
        try {
            val preset = _selectedPreset.value
            resolveSystemVoiceForPreset(preset)?.let {
                tts?.voice = it
            }
            val effectivePitch = (_pitch.value * preset.pitchMultiplier).coerceIn(0.6f, 1.8f)
            tts?.setPitch(effectivePitch)
            tts?.setSpeechRate(_speechRate.value.coerceIn(0.7f, 1.6f))
        } catch (e: Exception) {
            Log.w(TAG, "Error applying audio settings", e)
        }
    }

    fun isVoiceFemale(v: Voice): Boolean {
        val n = v.name.lowercase(Locale.ROOT)
        val features = v.features?.map { it.lowercase(Locale.ROOT) } ?: emptyList()

        if (n.contains("female") || features.any { it.contains("female") } || n.contains("#female")) {
            return true
        }
        if (n.contains("-hia-") || n.contains("-hid-") || n.contains("-hie-") ||
            n.contains("-ena-") || n.contains("-end-") || n.contains("-ene-") ||
            n.contains("hie-") || n.contains("hia-") || n.contains("hid-") ||
            n.contains("woman") || n.contains("girl")) {
            return true
        }
        return false
    }

    fun isVoiceMale(v: Voice): Boolean {
        if (isVoiceFemale(v)) return false
        val n = v.name.lowercase(Locale.ROOT)
        val features = v.features?.map { it.lowercase(Locale.ROOT) } ?: emptyList()

        if (n.contains("male") || features.any { it.contains("male") } || n.contains("#male")) {
            return true
        }
        if (n.contains("-hib-") || n.contains("-hic-") || n.contains("-hif-") ||
            n.contains("-enb-") || n.contains("-enc-") || n.contains("-enf-") ||
            n.contains("hib-") || n.contains("hic-") || n.contains("hif-") ||
            n.contains("man") || n.contains("boy")) {
            return true
        }
        return false
    }

    fun resolveSystemVoiceForPreset(preset: PresetVoice, preferredLocale: Locale? = null): Voice? {
        val ttsEngine = tts ?: return null
        val available = ttsEngine.voices ?: return null
        if (available.isEmpty()) return null

        val isMale = preset.gender.equals("Male", ignoreCase = true)
        val prefLang = preferredLocale?.language?.lowercase(Locale.ROOT) ?: "hi"

        // Step 1: Strictly isolate candidates of matching gender first
        val matchingGender = available.filter { v ->
            if (isMale) isVoiceMale(v) else isVoiceFemale(v)
        }

        // If gender-matched voices exist, NEVER select an opposite gender voice
        val candidatePool = if (matchingGender.isNotEmpty()) matchingGender else available.toList()

        return candidatePool.maxByOrNull { v ->
            var score = 0
            val n = v.name.lowercase(Locale.ROOT)
            val lang = v.locale.language.lowercase(Locale.ROOT)
            val country = v.locale.country.lowercase(Locale.ROOT)

            val isFem = isVoiceFemale(v)
            val isM = isVoiceMale(v)

            if (isMale) {
                if (isM) score += 3000
                if (isFem) score -= 5000
            } else {
                if (isFem) score += 3000
                if (isM) score -= 5000
            }

            // Language match
            if (lang == prefLang) {
                score += 1500
            } else if (lang == "hi" || (lang == "en" && (country == "in" || country == "gb" || country == "us"))) {
                score += 500
            }

            if (country == "in") score += 300

            // Keyword boost
            for (kw in preset.preferredKeywords) {
                if (n.contains(kw.lowercase(Locale.ROOT))) {
                    score += 600
                }
            }

            if (v.quality == Voice.QUALITY_VERY_HIGH) score += 400
            else if (v.quality == Voice.QUALITY_HIGH) score += 200
            if (n.contains("neural")) score += 400
            if (n.contains("wavenet")) score += 400
            if (n.contains("network")) score += 150
            if (!v.isNetworkConnectionRequired) score += 50

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

            resolveSystemVoiceForPreset(currentPreset, targetLocale)?.let {
                ttsEngine.voice = it
            }

            val effectivePitch = (_pitch.value * currentPreset.pitchMultiplier).coerceIn(0.6f, 1.8f)
            ttsEngine.setPitch(effectivePitch)
            ttsEngine.setSpeechRate(_speechRate.value.coerceIn(0.7f, 1.6f))

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
            val sampleLocale = Locale("hi", "IN")
            ttsEngine.setLanguage(sampleLocale)

            resolveSystemVoiceForPreset(preset, sampleLocale)?.let {
                ttsEngine.voice = it
            }

            val effectivePitch = (_pitch.value * preset.pitchMultiplier).coerceIn(0.6f, 1.8f)
            ttsEngine.setPitch(effectivePitch)
            ttsEngine.setSpeechRate(_speechRate.value.coerceIn(0.7f, 1.6f))

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
        _selectedPreset.value = MALE
        _languageMode.value = "auto"
        applyAudioSettings()
        savePreferences(context)
        DebugLogger.logVoiceSelected(gender = MALE.gender, voiceId = MALE.id)
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
