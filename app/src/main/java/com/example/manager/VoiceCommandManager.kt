package com.example.manager

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.ReminderRepository
import com.example.util.DebugLogger
import com.example.util.ReminderParser
import com.example.util.ReminderVoiceAction
import com.example.util.TtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

sealed interface VoiceState {
    object Idle : VoiceState
    object Listening : VoiceState
    data class Processing(val recognizedText: String) : VoiceState
    data class Success(val message: String) : VoiceState
    data class Error(val message: String) : VoiceState
}

class VoiceCommandManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var silenceTimeoutRunnable: Runnable? = null

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
            BatteryOptimizationManager.updateSubsystemState(voiceState = "IDLE (Sleep Mode)")
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
            BatteryOptimizationManager.updateSubsystemState(voiceState = "ACTIVE (Listening)")
            recognizer.startListening(intent)
            DebugLogger.logInfo("Voice listening started (Hindi + English)...")

            // Smart Inactivity Timeout: 6 seconds auto-sleep if no speech detected
            clearSilenceTimer()
            silenceTimeoutRunnable = Runnable {
                if (_voiceState.value == VoiceState.Listening) {
                    Log.d(TAG, "Smart Listening: Inactivity timeout reached, auto-closing mic to save battery")
                    DebugLogger.logInfo("Smart Listening: Inactivity timeout, sleeping mic to conserve battery")
                    cancelListening()
                }
            }
            mainHandler.postDelayed(silenceTimeoutRunnable!!, 6000L)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening", e)
            _voiceState.value = VoiceState.Error("Could not start listening: ${e.message}")
            BatteryOptimizationManager.updateSubsystemState(voiceState = "IDLE (Sleep Mode)")
        }
    }

    fun stopListening() {
        clearSilenceTimer()
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping listener", e)
        }
        _voiceState.value = VoiceState.Idle
        BatteryOptimizationManager.updateSubsystemState(voiceState = "IDLE (Sleep Mode)")
    }

    fun cancelListening() {
        clearSilenceTimer()
        try {
            speechRecognizer?.cancel()
        } catch (e: Exception) {
            Log.w(TAG, "Error cancelling listener", e)
        }
        _voiceState.value = VoiceState.Idle
        BatteryOptimizationManager.updateSubsystemState(voiceState = "IDLE (Sleep Mode)")
    }

    private fun clearSilenceTimer() {
        silenceTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        silenceTimeoutRunnable = null
    }

    fun processCommand(commandText: String) {
        val trimmed = commandText.trim()
        if (trimmed.isBlank()) return

        _lastRecognizedText.value = trimmed
        _voiceState.value = VoiceState.Processing(trimmed)
        DebugLogger.logInfo("Processing voice command: \"$trimmed\"")

        val lower = trimmed.lowercase(Locale.getDefault())

        // =========================================================================
        // STEP 0: CONTEXT AWARENESS LAYER (Added on top of existing working logic)
        // =========================================================================
        val currentApp = AppContextManager.getCurrentApp()
        DebugLogger.logContextCurrentApp(currentApp?.name)

        val isVol = isVolumeCommand(lower)
        val isHw = isHardwareCommand(lower) && !isVol

        val contextResult = AppContextManager.resolveContext(lower, isVolume = isVol, hasHardwareName = isHw)

        when (contextResult) {
            is ContextResolutionResult.ResolvedVolume -> {
                DebugLogger.logContextUsed(true, contextResult.description)
                val parsed = contextResult.parsed
                HardwareToggleManager.adjustVolume(context, parsed.action, parsed.explicitPercent)
                AppContextManager.recordHardwareToggle(
                    HardwareFeature.VOLUME,
                    if (parsed.explicitPercent != null) "Set to ${parsed.explicitPercent}%" else parsed.action.name
                )
                _voiceState.value = VoiceState.Success("Volume adjusted (${contextResult.description})")
                return
            }
            is ContextResolutionResult.ResolvedHardware -> {
                DebugLogger.logContextUsed(true, contextResult.description)
                executeResolvedHardwareToggle(contextResult.feature, contextResult.targetState)
                _voiceState.value = VoiceState.Success(contextResult.description)
                return
            }
            is ContextResolutionResult.ResolvedAppOpen -> {
                DebugLogger.logContextUsed(true, contextResult.description)
                val launched = AppOpenManager.launchApp(context, contextResult.app)
                if (launched) {
                    AppContextManager.recordAppOpen(contextResult.app)
                    DebugLogger.logLaunch(true, contextResult.app.name)
                    _voiceState.value = VoiceState.Success("App opened: ${contextResult.app.name}")
                } else {
                    DebugLogger.logLaunch(false, "Could not open ${contextResult.app.name}")
                    _voiceState.value = VoiceState.Error("Could not open ${contextResult.app.name}")
                }
                return
            }
            is ContextResolutionResult.ResolvedAppClose -> {
                DebugLogger.logContextUsed(true, contextResult.description)
                try {
                    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(homeIntent)
                    DebugLogger.logInfo("Navigated to Home to close ${contextResult.app.name}")
                } catch (e: Exception) {
                    Log.w(TAG, "Error closing app via home intent", e)
                }
                _voiceState.value = VoiceState.Success("Closed ${contextResult.app.name}")
                return
            }
            is ContextResolutionResult.Ambiguous -> {
                DebugLogger.logContextUsed(false, "Ambiguous: No active context")
                _voiceState.value = VoiceState.Error(contextResult.message)
                return
            }
            is ContextResolutionResult.NoReference -> {
                // Command contains no referring pronouns / ambiguous action; proceed to standard handlers
                DebugLogger.logContextUsed(false)
            }
        }

        // =========================================================================
        // STEP 0.5: WHATSAPP AUTO-REPLY VOICE CONTROL ("auto-reply on/off karo")
        // =========================================================================
        if (isAutoReplyCommand(lower)) {
            val turnOffWords = listOf("off", "band", "disable", "stop", "बंद", "हटाओ", "rok", "bujhao")
            val isOff = turnOffWords.any { lower.contains(it) }
            val targetEnable = !isOff

            val success = WhatsAppAutoReplyManager.setAutoReplyEnabled(context, targetEnable, announceWithTts = true)
            if (targetEnable) {
                if (success) {
                    _voiceState.value = VoiceState.Success("WhatsApp Auto-Reply is ON")
                } else {
                    _voiceState.value = VoiceState.Error("Notification Access permission required for Auto-Reply")
                }
            } else {
                _voiceState.value = VoiceState.Success("WhatsApp Auto-Reply is OFF")
            }
            return
        }

        // =========================================================================
        // STEP 0.7: WEATHER COMMAND ("aaj ka mausam kaisa hai")
        // =========================================================================
        if (WeatherManager.isWeatherCommand(lower)) {
            _voiceState.value = VoiceState.Processing("मौसम की जानकारी ली जा रही है...")
            WeatherManager.fetchAndAnnounceWeather(context) { success, msg ->
                if (success) {
                    _voiceState.value = VoiceState.Success(msg)
                } else {
                    _voiceState.value = VoiceState.Error(msg)
                }
            }
            return
        }

        // =========================================================================
        // STEP 0.8: REMINDERS & ALARMS ("mujhe [samay] par [kaam] yaad dilana")
        // =========================================================================
        if (ReminderParser.isReminderOrAlarmCommand(lower)) {
            handleReminderVoiceCommand(trimmed, lower)
            return
        }

        // =========================================================================
        // STEP 0.9: CAMERA & SCENE ANALYSIS ("selfie lo", "photo lo", "saamne kya hai")
        // =========================================================================
        if (MaxCameraManager.isSceneAnalysisCommand(lower)) {
            _voiceState.value = VoiceState.Processing("सामने का दृश्य देखा जा रहा है...")
            MaxCameraManager.analyzeScene(context) { success, result ->
                if (success) {
                    _voiceState.value = VoiceState.Success(result)
                } else {
                    _voiceState.value = VoiceState.Error(result)
                }
            }
            return
        }

        if (MaxCameraManager.isSelfieCommand(lower)) {
            _voiceState.value = VoiceState.Processing("सेल्फी ली जा रही है...")
            MaxCameraManager.capturePhoto(context, isFrontCamera = true) { success, result ->
                if (success) {
                    _voiceState.value = VoiceState.Success("Selfie captured ($result)")
                } else {
                    _voiceState.value = VoiceState.Error(result)
                }
            }
            return
        }

        if (MaxCameraManager.isBackPhotoCommand(lower)) {
            _voiceState.value = VoiceState.Processing("फोटो ली जा रही है...")
            MaxCameraManager.capturePhoto(context, isFrontCamera = false) { success, result ->
                if (success) {
                    _voiceState.value = VoiceState.Success("Photo captured ($result)")
                } else {
                    _voiceState.value = VoiceState.Error(result)
                }
            }
            return
        }

        // =========================================================================
        // STEP 0.95: ANTI-THEFT GUARD EMERGENCY CONTACT ("mera emergency contact 9876543210 hai")
        // =========================================================================
        if (AntiTheftManager.isTrustedContactCommand(lower)) {
            _voiceState.value = VoiceState.Processing("इमरजेंसी कॉन्टैक्ट सेट किया जा रहा है...")
            val (saved, message) = AntiTheftManager.parseAndSaveContactFromVoice(context, trimmed)
            if (saved) {
                _voiceState.value = VoiceState.Success(message)
            } else {
                _voiceState.value = VoiceState.Error(message)
            }
            return
        }

        // =========================================================================
        // STEP 1: HARDWARE TOGGLE COMMAND (VOLUME / TORCH / WIFI / etc.) [UNTOUCHED]
        // =========================================================================
        if (isHardwareCommand(lower)) {
            handleHardwareVoiceCommand(lower, trimmed)
            _voiceState.value = VoiceState.Success("Hardware action triggered for \"$trimmed\"")
            return
        }

        // =========================================================================
        // STEP 2: APP OPEN COMMAND (Devanagari / Phonetic / Fuzzy Match) [UNTOUCHED]
        // =========================================================================
        val launched = AppOpenManager.processAndLaunch(context, trimmed)
        if (launched) {
            _voiceState.value = VoiceState.Success("App opened for \"$trimmed\"")
        } else {
            _voiceState.value = VoiceState.Error("No matching app found for \"$trimmed\"")
        }
    }

    /**
     * Executes resolved hardware toggle and records into ContextManager
     */
    fun executeResolvedHardwareToggle(feature: HardwareFeature, targetState: Boolean?) {
        when (feature) {
            HardwareFeature.TORCH -> {
                HardwareToggleManager.toggleTorch(context, targetState)
                AppContextManager.recordHardwareToggle(HardwareFeature.TORCH, if (targetState == true) "ON" else "OFF", targetState)
            }
            HardwareFeature.WIFI -> {
                HardwareToggleManager.toggleWifi(context)
                AppContextManager.recordHardwareToggle(HardwareFeature.WIFI, if (targetState == true) "ON" else "OFF", targetState)
            }
            HardwareFeature.BLUETOOTH -> {
                HardwareToggleManager.toggleBluetooth(context)
                AppContextManager.recordHardwareToggle(HardwareFeature.BLUETOOTH, if (targetState == true) "ON" else "OFF", targetState)
            }
            HardwareFeature.MOBILE_DATA -> {
                HardwareToggleManager.toggleMobileData(context)
                AppContextManager.recordHardwareToggle(HardwareFeature.MOBILE_DATA, if (targetState == true) "ON" else "OFF", targetState)
            }
            HardwareFeature.HOTSPOT -> {
                HardwareToggleManager.toggleHotspot(context)
                AppContextManager.recordHardwareToggle(HardwareFeature.HOTSPOT, if (targetState == true) "ON" else "OFF", targetState)
            }
            HardwareFeature.BRIGHTNESS -> {
                HardwareToggleManager.toggleBrightness(context)
                AppContextManager.recordHardwareToggle(HardwareFeature.BRIGHTNESS, "Toggled")
            }
            HardwareFeature.DND -> {
                HardwareToggleManager.toggleDnd(context, targetState)
                AppContextManager.recordHardwareToggle(HardwareFeature.DND, if (targetState == true) "ON" else "OFF", targetState)
            }
            HardwareFeature.AIRPLANE_MODE -> {
                HardwareToggleManager.toggleAirplaneMode(context)
                AppContextManager.recordHardwareToggle(HardwareFeature.AIRPLANE_MODE, if (targetState == true) "ON" else "OFF", targetState)
            }
            HardwareFeature.VOLUME -> {
                HardwareToggleManager.adjustVolume(context, if (targetState == false) VolumeAction.MUTE else VolumeAction.UP)
                AppContextManager.recordHardwareToggle(HardwareFeature.VOLUME, if (targetState == false) "Muted" else "UP")
            }
        }
    }

    /**
     * Checks if the voice command is related to WhatsApp Auto-Reply
     */
    fun isAutoReplyCommand(lower: String): Boolean {
        val keywords = listOf(
            "auto reply", "auto-reply", "autoreply", "auto rply",
            "ऑटो रिप्लाई", "ऑटो-रिप्लाई", "ऑटोरिप्लाई", "ऑटो रिप्लाय",
            "whatsapp reply", "whatsapp auto", "व्हाट्सएप रिप्लाई", "व्हाट्सएप ऑटो"
        )
        return keywords.any { lower.contains(it) }
    }

    /**
     * Handles setting, cancelling, or listing reminders and alarms
     */
    private fun handleReminderVoiceCommand(trimmed: String, lower: String) {
        val parsedAction = ReminderParser.parseCommand(trimmed)
        if (parsedAction == null) {
            val fallbackMsg = "रिमाइंडर समझ नहीं आया. कृपया समय और काम स्पष्ट बोलें."
            TtsManager.speak(fallbackMsg)
            _voiceState.value = VoiceState.Error(fallbackMsg)
            return
        }

        when (parsedAction) {
            is ReminderVoiceAction.SetReminder -> {
                ReminderScheduler.scheduleReminder(
                    context = context,
                    task = parsedAction.task,
                    triggerTimeMillis = parsedAction.triggerTimeMillis,
                    isAlarm = parsedAction.isAlarm
                ) { savedItem ->
                    val typeStr = if (parsedAction.isAlarm) "अलार्म" else "रिमाइंडर"
                    val confirmMsg = if (parsedAction.isAlarm) {
                        "अलार्म ${parsedAction.humanTimeDescription} के लिए सेट कर दिया गया है."
                    } else {
                        "ठीक है, ${parsedAction.humanTimeDescription} पर ${parsedAction.task} याद दिला दूँगा."
                    }
                    TtsManager.speak(confirmMsg)
                    _voiceState.value = VoiceState.Success("$typeStr: ${parsedAction.task} at ${savedItem.formattedTime}")
                }
            }
            is ReminderVoiceAction.CancelReminder -> {
                if (parsedAction.isAll) {
                    scope.launch {
                        val db = AppDatabase.getInstance(context)
                        val repo = ReminderRepository(db.reminderDao())
                        repo.deleteAll()
                        val msg = "आपके सारे रिमाइंडर्स और अलार्म हटा दिए गए हैं."
                        TtsManager.speak(msg)
                        _voiceState.value = VoiceState.Success(msg)
                    }
                } else {
                    ReminderScheduler.cancelRemindersByKeyword(context, parsedAction.keyword) { count, matchedTask ->
                        val msg = if (count > 0) {
                            "आपका $matchedTask वाला रिमाइंडर कैंसिल कर दिया गया है."
                        } else {
                            "कोई मैचिंग रिमाइंडर नहीं मिला."
                        }
                        TtsManager.speak(msg)
                        _voiceState.value = if (count > 0) VoiceState.Success(msg) else VoiceState.Error(msg)
                    }
                }
            }
            is ReminderVoiceAction.ListReminders -> {
                scope.launch {
                    val db = AppDatabase.getInstance(context)
                    val repo = ReminderRepository(db.reminderDao())
                    val activeList = repo.getActiveReminders()
                    if (activeList.isEmpty()) {
                        val msg = "आपका कोई एक्टिव रिमाइंडर या अलार्म नहीं है."
                        TtsManager.speak(msg)
                        _voiceState.value = VoiceState.Success(msg)
                    } else {
                        val sb = StringBuilder()
                        sb.append("आपके ${activeList.size} एक्टिव रिमाइंडर्स हैं: ")
                        activeList.take(5).forEachIndexed { i, item ->
                            val type = if (item.isAlarm) "अलार्म" else "रिमाइंडर"
                            sb.append("${i + 1}. ${item.task} ${item.formattedTime} पर. ")
                        }
                        val speakText = sb.toString()
                        TtsManager.speak(speakText)
                        _voiceState.value = VoiceState.Success("Active Reminders: ${activeList.size}")
                    }
                }
            }
        }
    }

    /**
     * Checks if the voice command is related to Volume / Audio
     */
    fun isVolumeCommand(lower: String): Boolean {
        val volumeIndicators = listOf(
            // English
            "volume", "sound", "audio", "mute", "unmute", "louder", "softer", "quieter",
            // Hinglish / Roman Hindi
            "awaz", "aawaz", "awaaz", "awaj", "aawaaj", "chup", "shant",
            // Devanagari Hindi
            "वॉल्यूम", "वोल्यूम", "वॉल्युम", "वॉलयूम", "बोल्यूम",
            "आवाज", "आवाज़", "साउंड", "ऑडियो",
            "म्यूट", "अनम्यूट", "चुप करो", "चुप", "शांत"
        )
        return volumeIndicators.any { lower.contains(it) }
    }

    /**
     * Checks if the command matches any hardware toggle keyword
     */
    fun isHardwareCommand(lower: String): Boolean {
        if (isVolumeCommand(lower)) return true

        val hardwareKeywords = listOf(
            // WiFi
            "wifi", "wi-fi", "वाई-फाई", "वाईफाई", "wlan", "इंटरनेट", "internet",
            // Bluetooth
            "bluetooth", "ब्लूटूथ", "bt",
            // Mobile Data
            "mobile data", "data on", "data off", "data band", "data chalu", "डेटा", "cellular", "net on", "net off",
            // Hotspot
            "hotspot", "हॉटस्पॉट", "tethering", "पर्सनल हॉटस्पॉट",
            // Torch / Flashlight
            "torch", "flashlight", "टॉर्च", "फ्लैशलाइट", "flash", "light on", "light off", "लाइट",
            // Brightness
            "brightness", "screen light", "chamak", "ब्राइटनेस", "रोशनी", "स्क्रीन लाइट", "चमक",
            // DND
            "dnd", "do not disturb", "डू नॉट डिस्टर्ब",
            // Airplane Mode
            "airplane", "flight mode", "हवाई मोड", "aeroplane", "flight", "एयरप्लेन"
        )
        return hardwareKeywords.any { lower.contains(it) }
    }

    private fun handleHardwareVoiceCommand(lower: String, originalText: String) {
        // Priority 1: Volume Commands (DIRECT API)
        if (isVolumeCommand(lower)) {
            val parsed = HardwareToggleManager.parseVolumeCommand(lower)
            HardwareToggleManager.adjustVolume(context, parsed.action, parsed.explicitPercent)
            AppContextManager.recordHardwareToggle(
                HardwareFeature.VOLUME,
                if (parsed.explicitPercent != null) "Set to ${parsed.explicitPercent}%" else parsed.action.name
            )
            return
        }

        val turnOffWords = listOf("off", "band", "close", "disable", "stop", "बंद", "हटाओ", "rok", "bujhao", "बुझाओ")
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
            lower.contains("torch") || lower.contains("flashlight") || lower.contains("टॉर्च") || lower.contains("फ्लैशलाइट") || lower.contains("flash") || lower.contains("लाइट") -> {
                HardwareToggleManager.toggleTorch(context, targetState)
                AppContextManager.recordHardwareToggle(HardwareFeature.TORCH, if (targetState == false) "OFF" else "ON", targetState)
            }
            // WiFi
            lower.contains("wifi") || lower.contains("wi-fi") || lower.contains("वाई-फाई") || lower.contains("वाईफाई") || lower.contains("wlan") -> {
                HardwareToggleManager.toggleWifi(context)
                AppContextManager.recordHardwareToggle(HardwareFeature.WIFI, if (targetState == false) "OFF" else "ON", targetState)
            }
            // Bluetooth
            lower.contains("bluetooth") || lower.contains("ब्लूटूथ") || lower.contains("bt") -> {
                HardwareToggleManager.toggleBluetooth(context)
                AppContextManager.recordHardwareToggle(HardwareFeature.BLUETOOTH, if (targetState == false) "OFF" else "ON", targetState)
            }
            // Mobile Data
            lower.contains("data") || lower.contains("डेटा") || lower.contains("cellular") || lower.contains("net") -> {
                HardwareToggleManager.toggleMobileData(context)
                AppContextManager.recordHardwareToggle(HardwareFeature.MOBILE_DATA, if (targetState == false) "OFF" else "ON", targetState)
            }
            // Hotspot
            lower.contains("hotspot") || lower.contains("हॉटस्पॉट") || lower.contains("tethering") -> {
                HardwareToggleManager.toggleHotspot(context)
                AppContextManager.recordHardwareToggle(HardwareFeature.HOTSPOT, if (targetState == false) "OFF" else "ON", targetState)
            }
            // Brightness
            lower.contains("brightness") || lower.contains("screen light") || lower.contains("chamak") || lower.contains("ब्राइटनेस") || lower.contains("रोशनी") || lower.contains("चमक") -> {
                val parsed = HardwareToggleManager.parseVolumeCommand(lower) // extracts percentage if any
                HardwareToggleManager.toggleBrightness(context, parsed.explicitPercent)
                AppContextManager.recordHardwareToggle(HardwareFeature.BRIGHTNESS, if (parsed.explicitPercent != null) "${parsed.explicitPercent}%" else "Toggled")
            }
            // DND
            lower.contains("dnd") || lower.contains("disturb") || lower.contains("डिस्टर्ब") -> {
                HardwareToggleManager.toggleDnd(context, targetState)
                AppContextManager.recordHardwareToggle(HardwareFeature.DND, if (targetState == false) "OFF" else "ON", targetState)
            }
            // Airplane Mode
            lower.contains("airplane") || lower.contains("flight") || lower.contains("हवाई मोड") || lower.contains("aeroplane") -> {
                HardwareToggleManager.toggleAirplaneMode(context)
                AppContextManager.recordHardwareToggle(HardwareFeature.AIRPLANE_MODE, if (targetState == false) "OFF" else "ON", targetState)
            }
        }
    }

    private fun createRecognitionListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _voiceState.value = VoiceState.Listening
            BatteryOptimizationManager.updateSubsystemState(voiceState = "ACTIVE (Listening)")
        }

        override fun onBeginningOfSpeech() {
            clearSilenceTimer()
            BatteryOptimizationManager.updateSubsystemState(voiceState = "PROCESSING (Speech Detected)")
        }

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            clearSilenceTimer()
            _voiceState.value = VoiceState.Processing("Processing audio...")
            BatteryOptimizationManager.updateSubsystemState(voiceState = "PROCESSING (Decoding)")
        }

        override fun onError(error: Int) {
            clearSilenceTimer()
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
            BatteryOptimizationManager.updateSubsystemState(voiceState = "IDLE (Sleep Mode)")
        }

        override fun onResults(results: Bundle?) {
            clearSilenceTimer()
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val command = matches[0]
                processCommand(command)
            } else {
                _voiceState.value = VoiceState.Error("No match found")
            }
            BatteryOptimizationManager.updateSubsystemState(voiceState = "IDLE (Sleep Mode)")
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
        clearSilenceTimer()
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {
            Log.w(TAG, "Error destroying speech recognizer", e)
        }
        BatteryOptimizationManager.updateSubsystemState(voiceState = "IDLE (Sleep Mode)")
    }

    companion object {
        private const val TAG = "VoiceCommandManager"
    }
}
