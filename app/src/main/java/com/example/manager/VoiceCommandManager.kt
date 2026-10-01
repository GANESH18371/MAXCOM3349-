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
import com.example.service.MaxAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
        PermanentMemoryManager.init(context)
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
                // Support multi-sentence continuous thought flow without aggressive cut-offs
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2500L)
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
        DebugLogger.logSttRawText(trimmed)
        if (trimmed.isBlank()) return

        // =========================================================================
        // WAKE-WORD DETECTION & OWNER VOICE BIOMETRIC VERIFICATION (2-STAGE GATE)
        // =========================================================================
        val detectedWake = WakeWordManager.detectWakePhrase(trimmed)
        var effectiveCommand = trimmed

        if (detectedWake != null && WakeWordManager.isEnabled.value) {
            val pcm = WakeWordManager.generatePcmFromSpeech(trimmed)
            val isVerified = WakeWordManager.verifyAndTrigger(context, detectedWake, pcm) {
                // Owner matched!
            }
            if (!isVerified) {
                // Non-owner voice! Max remains completely silent and ignores!
                _voiceState.value = VoiceState.Idle
                return
            }

            effectiveCommand = WakeWordManager.stripWakePhrase(trimmed, detectedWake)
            if (effectiveCommand.isBlank()) {
                // Just wake phrase spoken by owner (e.g. "Hey Max") -> acknowledge and wait for command
                _voiceState.value = VoiceState.Success("Aapka swagat hai! Boliye, main sun raha hoon.")
                TtsManager.speak("Haan boliye, main sun raha hoon.")
                startListening()
                return
            }
        }

        _lastRecognizedText.value = effectiveCommand
        _voiceState.value = VoiceState.Processing(effectiveCommand)
        DebugLogger.logInfo("Processing voice command: \"$effectiveCommand\"")

        val lower = effectiveCommand.lowercase(Locale.getDefault())

        // =========================================================================
        // STEP 0.0: PERMANENT LONG-TERM MEMORY LAYER
        // =========================================================================
        if (PermanentMemoryManager.isMemoryCommand(lower)) {
            DebugLogger.logCommandRouterClassification("OFFLINE_TASK")
            scope.launch {
                val memResult = PermanentMemoryManager.handleMemoryCommand(trimmed, context)
                when (memResult) {
                    is MemoryCommandResult.LaunchFavoriteApp -> {
                        val launched = AppOpenManager.processAndLaunch(context, memResult.appName)
                        if (launched) {
                            TtsManager.speak("Aapki favorite app ${memResult.appName} khol raha hoon.")
                            _voiceState.value = VoiceState.Success("Opened favorite app: ${memResult.appName}")
                        } else {
                            val msg = "Favorite app '${memResult.appName}' open nahi ho saki."
                            TtsManager.speak(msg)
                            _voiceState.value = VoiceState.Error(msg)
                        }
                    }
                    is MemoryCommandResult.Handled -> {
                        _voiceState.value = VoiceState.Success(memResult.message)
                    }
                    is MemoryCommandResult.NotMemoryCommand -> {
                        // proceed to context and hardware routing
                    }
                }
            }
            return
        }

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
                DebugLogger.logCommandRouterClassification("OFFLINE_TASK")
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
                DebugLogger.logCommandRouterClassification("OFFLINE_TASK")
                DebugLogger.logContextUsed(true, contextResult.description)
                executeResolvedHardwareToggle(contextResult.feature, contextResult.targetState)
                _voiceState.value = VoiceState.Success(contextResult.description)
                return
            }
            is ContextResolutionResult.ResolvedAppOpen -> {
                DebugLogger.logCommandRouterClassification("SCREEN_TASK")
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
                DebugLogger.logCommandRouterClassification("SCREEN_TASK")
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
                DebugLogger.logCommandRouterClassification("CONVERSATION")
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
            DebugLogger.logCommandRouterClassification("OFFLINE_TASK")
            val turnOffWords = listOf("off", "band", "disable", "stop", "बंद", "हटाओ", "rok", "bujhao")
            val isOff = turnOffWords.any { lower.contains(it) }
            val targetEnable = !isOff

            val success = WhatsAppAutoReplyManager.setAutoReplyEnabled(context, targetEnable, announceWithTts = false)
            if (targetEnable) {
                if (success) {
                    TtsManager.speak("Auto-reply on ho gaya")
                    _voiceState.value = VoiceState.Success("WhatsApp Auto-Reply is ON")
                } else {
                    TtsManager.speak("Auto-reply ke liye notification permission zaroori hai")
                    _voiceState.value = VoiceState.Error("Notification Access permission required for Auto-Reply")
                }
            } else {
                TtsManager.speak("Auto-reply off ho gaya")
                _voiceState.value = VoiceState.Success("WhatsApp Auto-Reply is OFF")
            }
            return
        }

        // =========================================================================
        // STEP 0.7: WEATHER COMMAND ("aaj ka mausam kaisa hai")
        // =========================================================================
        if (WeatherManager.isWeatherCommand(lower)) {
            DebugLogger.logCommandRouterClassification("CONVERSATION")
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
            DebugLogger.logCommandRouterClassification("OFFLINE_TASK")
            handleReminderVoiceCommand(trimmed, lower)
            return
        }

        // =========================================================================
        // STEP 0.9: CAMERA & SCENE ANALYSIS ("selfie lo", "photo lo", "saamne kya hai")
        // =========================================================================
        if (MaxCameraManager.isSceneAnalysisCommand(lower)) {
            DebugLogger.logCommandRouterClassification("CONVERSATION")
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
            DebugLogger.logCommandRouterClassification("SCREEN_TASK")
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
            DebugLogger.logCommandRouterClassification("SCREEN_TASK")
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
            DebugLogger.logCommandRouterClassification("OFFLINE_TASK")
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
        // STEP 0.96: GENERIC MULTI-APP MESSAGING & AUTO-SAVE ("Telegram par 9876543210 ko message karo", "Instagram par Ravi ko message bhejo", etc.)
        // =========================================================================
        if (GenericMessagingManager.isMessagingCommand(lower)) {
            DebugLogger.logCommandRouterClassification("SCREEN_TASK")
            _voiceState.value = VoiceState.Processing("मैसेज तैयार किया जा रहा है...")
            GenericMessagingManager.executeMessagingFlow(context, trimmed) { success, msg ->
                if (success) {
                    _voiceState.value = VoiceState.Success(msg)
                } else {
                    _voiceState.value = VoiceState.Error(msg)
                }
            }
            return
        }

        // =========================================================================
        // STEP 0.97: GENERIC APP-CONTROL FOR MEDIA COMMANDS ("agla wala chalao", "pause karo", "Arijit Singh chalao", etc.)
        // =========================================================================
        if (GenericAppControlManager.isMediaCommand(lower)) {
            DebugLogger.logCommandRouterClassification("SCREEN_TASK")
            _voiceState.value = VoiceState.Processing("Media command execute ho raha hai...")
            GenericAppControlManager.executeMediaFlow(context, trimmed) { success, msg ->
                if (success) {
                    _voiceState.value = VoiceState.Success(msg)
                } else {
                    _voiceState.value = VoiceState.Error(msg)
                }
            }
            return
        }

        // =========================================================================
        // STEP 0.98: COMPOUND LOCAL MULTI-INTENT ("torch on karo aur wifi band karo")
        // =========================================================================
        if (tryExecuteCompoundLocalCommand(trimmed)) {
            DebugLogger.logCommandRouterClassification("OFFLINE_TASK")
            return
        }

        // =========================================================================
        // STEP 1: HARDWARE TOGGLE COMMAND (VOLUME / TORCH / WIFI / etc.) [UNTOUCHED]
        // =========================================================================
        if (isHardwareCommand(lower)) {
            DebugLogger.logCommandRouterClassification("OFFLINE_TASK")
            handleHardwareVoiceCommand(lower, trimmed)
            _voiceState.value = VoiceState.Success("Hardware action triggered for \"$trimmed\"")
            return
        }

        // =========================================================================
        // STEP 2: APP OPEN COMMAND (Devanagari / Phonetic / Fuzzy Match)
        // =========================================================================
        val launched = AppOpenManager.processAndLaunch(context, trimmed)
        if (launched) {
            DebugLogger.logCommandRouterClassification("SCREEN_TASK")
            val apps = AppOpenManager.getFreshInstalledApps(context)
            val matchedApp = AppOpenManager.fuzzyMatchApp(trimmed, apps)
            val appLabel = matchedApp?.name ?: AppOpenManager.sanitizeCommand(trimmed).replaceFirstChar { it.uppercase() }
            if (matchedApp != null) {
                AppContextManager.recordAppOpen(matchedApp)
            }
            val replyMsg = "$appLabel khul gaya"
            AppContextManager.recordConversationExchange(trimmed, replyMsg)
            TtsManager.speak(replyMsg)
            _voiceState.value = VoiceState.Success("App opened: $appLabel")
            return
        }

        // =========================================================================
        // STEP 3: DEEP HUMAN-LIKE COMPREHENSION (Gemini 2.5 Flash Model)
        // Understands casual, colloquial, indirect phrasing, idioms, and multi-sentence thought flows.
        // Executes multiple intents sequentially and speaks natural, warm Hindi/Hinglish.
        // =========================================================================
        DebugLogger.logCommandRouterClassification("CONVERSATION")
        _voiceState.value = VoiceState.Processing("Samajh raha hoon...")
        scope.launch {
            var isHandled = false
            // Natural filler only if network/processing takes > 1.15 seconds
            val fillerJob = launch {
                delay(1150L)
                if (!isHandled && _voiceState.value is VoiceState.Processing) {
                    val filler = listOf(
                        "Hmm, dekhta hoon...",
                        "Hmm, samajh raha hoon...",
                        "Ek second, rukiye..."
                    ).random()
                    TtsManager.speak(filler)
                }
            }

            try {
                val contextSummary = AppContextManager.getRecentContextSummary()
                val installedApps = AppOpenManager.getFreshInstalledApps(context)
                val appNames = installedApps.map { it.name }.take(25)

                val result = com.example.service.GeminiReplyService.deepUnderstandCommand(
                    userQuery = trimmed,
                    contextSummary = contextSummary,
                    knownApps = appNames
                )

                isHandled = true
                fillerJob.cancel()

                DebugLogger.logInfo("Deep Comprehension: intent='${result.understoodIntent}', actionsCount=${result.actions.size}")

                // Execute all recognized actions in sequential order
                for ((index, action) in result.actions.withIndex()) {
                    when (action.type.lowercase()) {
                        "open_app" -> {
                            val matchedApp = AppOpenManager.fuzzyMatchApp(action.target, installedApps)
                                ?: installedApps.find { it.name.equals(action.target, ignoreCase = true) || it.name.contains(action.target, ignoreCase = true) }
                            if (matchedApp != null) {
                                val didLaunch = AppOpenManager.launchApp(context, matchedApp)
                                if (didLaunch) {
                                    AppContextManager.recordAppOpen(matchedApp)
                                }
                            }
                        }
                        "toggle" -> {
                            executeDeepComprehensionToggle(action.target)
                        }
                    }
                    if (index < result.actions.size - 1) {
                        delay(350L) // Smooth gap between multiple actions
                    }
                }

                // Deliver warm, friendly response via TTS
                val replyText = if (result.replyText.isNotBlank()) result.replyText else "Main aapke liye kaam kar raha hoon."
                AppContextManager.recordConversationExchange(trimmed, replyText)
                TtsManager.speak(replyText)
                _voiceState.value = VoiceState.Success(replyText)

            } catch (e: Exception) {
                isHandled = true
                fillerJob.cancel()
                val err = "Kshama karein, main theek se samajh nahi saka. Ek baar dobara batayiye na!"
                AppContextManager.recordConversationExchange(trimmed, err)
                _voiceState.value = VoiceState.Error(err)
                TtsManager.speak(err)
            }
        }
    }

    private fun tryExecuteCompoundLocalCommand(raw: String): Boolean {
        val lower = raw.lowercase()
        val regex = Regex(" aur | and | phir | tatha ")
        if (!regex.containsMatchIn(lower)) return false

        val parts = raw.split(regex, limit = 2)
        if (parts.size != 2) return false

        val part1 = parts[0].trim()
        val part2 = parts[1].trim()

        val isHw1 = isHardwareCommand(part1.lowercase())
        val isHw2 = isHardwareCommand(part2.lowercase())

        if (isHw1 && isHw2) {
            handleHardwareVoiceCommand(part1.lowercase(), part1)
            scope.launch {
                delay(300L)
                handleHardwareVoiceCommand(part2.lowercase(), part2)
                val msg = "Dono hardware settings adjust ho gayi"
                AppContextManager.recordConversationExchange(raw, msg)
                TtsManager.speak(msg)
                _voiceState.value = VoiceState.Success(msg)
            }
            return true
        }

        val apps = AppOpenManager.getFreshInstalledApps(context)
        val app1 = AppOpenManager.fuzzyMatchApp(part1, apps)
        if (app1 != null && isHw2) {
            val launched = AppOpenManager.launchApp(context, app1)
            if (launched) {
                AppContextManager.recordAppOpen(app1)
            }
            scope.launch {
                delay(300L)
                handleHardwareVoiceCommand(part2.lowercase(), part2)
                val msg = "${app1.name} khol diya aur hardware setting adjust kar di"
                AppContextManager.recordConversationExchange(raw, msg)
                TtsManager.speak(msg)
                _voiceState.value = VoiceState.Success(msg)
            }
            return true
        }

        return false
    }

    private fun executeDeepComprehensionToggle(target: String) {
        val lower = target.lowercase()
        when {
            lower.contains("torch_on") -> executeResolvedHardwareToggle(HardwareFeature.TORCH, true)
            lower.contains("torch_off") -> executeResolvedHardwareToggle(HardwareFeature.TORCH, false)
            lower.contains("wifi_on") -> executeResolvedHardwareToggle(HardwareFeature.WIFI, true)
            lower.contains("wifi_off") -> executeResolvedHardwareToggle(HardwareFeature.WIFI, false)
            lower.contains("bluetooth_on") -> executeResolvedHardwareToggle(HardwareFeature.BLUETOOTH, true)
            lower.contains("bluetooth_off") -> executeResolvedHardwareToggle(HardwareFeature.BLUETOOTH, false)
            lower.contains("volume_up") -> {
                HardwareToggleManager.adjustVolume(context, VolumeAction.UP)
                AppContextManager.recordHardwareToggle(HardwareFeature.VOLUME, "Volume UP", null)
            }
            lower.contains("volume_down") -> {
                HardwareToggleManager.adjustVolume(context, VolumeAction.DOWN)
                AppContextManager.recordHardwareToggle(HardwareFeature.VOLUME, "Volume DOWN", null)
            }
            lower.contains("volume_mute") -> {
                HardwareToggleManager.adjustVolume(context, VolumeAction.MUTE)
                AppContextManager.recordHardwareToggle(HardwareFeature.VOLUME, "Muted", false)
            }
            lower.contains("brightness") -> {
                HardwareToggleManager.toggleBrightness(context)
                AppContextManager.recordHardwareToggle(HardwareFeature.BRIGHTNESS, "Toggled", null)
            }
            lower.contains("dnd_on") -> executeResolvedHardwareToggle(HardwareFeature.DND, true)
            lower.contains("dnd_off") -> executeResolvedHardwareToggle(HardwareFeature.DND, false)
            lower.contains("hotspot_on") -> executeResolvedHardwareToggle(HardwareFeature.HOTSPOT, true)
            lower.contains("hotspot_off") -> executeResolvedHardwareToggle(HardwareFeature.HOTSPOT, false)
            lower.contains("mobile_data") -> executeResolvedHardwareToggle(HardwareFeature.MOBILE_DATA, null)
            lower.contains("airplane") -> executeResolvedHardwareToggle(HardwareFeature.AIRPLANE_MODE, null)
            else -> {
                if (lower.contains("torch")) executeResolvedHardwareToggle(HardwareFeature.TORCH, true)
                else if (lower.contains("wifi")) executeResolvedHardwareToggle(HardwareFeature.WIFI, true)
            }
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
        // Priority 1: Volume Commands (In-App first if available, else System API)
        if (isVolumeCommand(lower)) {
            val parsed = HardwareToggleManager.parseVolumeCommand(lower)
            if (MaxAccessibilityService.isRunning()) {
                val action = if (lower.contains("down") || lower.contains("kam") || lower.contains("dheere") || lower.contains("dheeme")) "volume_down" else "volume_up"
                MaxAccessibilityService.instance?.executeGenericMediaAction(action) { inAppSuccess: Boolean, _: String ->
                    if (!inAppSuccess) {
                        HardwareToggleManager.adjustVolume(context, parsed.action, parsed.explicitPercent)
                    }
                }
            } else {
                HardwareToggleManager.adjustVolume(context, parsed.action, parsed.explicitPercent)
            }
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
                TtsManager.speak(if (targetState == false) "Torch band kar di" else "Torch on kar di")
            }
            // WiFi
            lower.contains("wifi") || lower.contains("wi-fi") || lower.contains("वाई-फाई") || lower.contains("वाईफाई") || lower.contains("wlan") -> {
                val willBeOn = targetState ?: !HardwareToggleManager.isWifiEnabled(context)
                HardwareToggleManager.toggleWifi(context)
                AppContextManager.recordHardwareToggle(HardwareFeature.WIFI, if (willBeOn) "ON" else "OFF", willBeOn)
                TtsManager.speak(if (willBeOn) "WiFi on kar diya" else "WiFi band kar diya")
            }
            // Bluetooth
            lower.contains("bluetooth") || lower.contains("ब्लूटूथ") || lower.contains("bt") -> {
                val willBeOn = targetState ?: !HardwareToggleManager.isBluetoothEnabled(context)
                HardwareToggleManager.toggleBluetooth(context)
                AppContextManager.recordHardwareToggle(HardwareFeature.BLUETOOTH, if (willBeOn) "ON" else "OFF", willBeOn)
                TtsManager.speak(if (willBeOn) "Bluetooth on kar diya" else "Bluetooth band kar diya")
            }
            // Mobile Data
            lower.contains("data") || lower.contains("डेटा") || lower.contains("cellular") || lower.contains("net") -> {
                HardwareToggleManager.toggleMobileData(context)
                AppContextManager.recordHardwareToggle(HardwareFeature.MOBILE_DATA, if (targetState == false) "OFF" else "ON", targetState)
                TtsManager.speak("Mobile Data settings khol di hai")
            }
            // Hotspot
            lower.contains("hotspot") || lower.contains("हॉटस्पॉट") || lower.contains("tethering") -> {
                HardwareToggleManager.toggleHotspot(context)
                AppContextManager.recordHardwareToggle(HardwareFeature.HOTSPOT, if (targetState == false) "OFF" else "ON", targetState)
                TtsManager.speak("Hotspot settings khol di hai")
            }
            // Brightness
            lower.contains("brightness") || lower.contains("screen light") || lower.contains("chamak") || lower.contains("ब्राइटनेस") || lower.contains("रोशनी") || lower.contains("चमक") -> {
                val parsed = HardwareToggleManager.parseVolumeCommand(lower) // extracts percentage if any
                HardwareToggleManager.toggleBrightness(context, parsed.explicitPercent)
                AppContextManager.recordHardwareToggle(HardwareFeature.BRIGHTNESS, if (parsed.explicitPercent != null) "${parsed.explicitPercent}%" else "Toggled")
                TtsManager.speak("Brightness adjust kar di hai")
            }
            // DND
            lower.contains("dnd") || lower.contains("disturb") || lower.contains("डिस्टर्ब") -> {
                HardwareToggleManager.toggleDnd(context, targetState)
                AppContextManager.recordHardwareToggle(HardwareFeature.DND, if (targetState == false) "OFF" else "ON", targetState)
                TtsManager.speak(if (targetState == false) "Do Not Disturb band kar diya" else "Do Not Disturb on kar diya")
            }
            // Airplane Mode
            lower.contains("airplane") || lower.contains("flight") || lower.contains("हवाई मोड") || lower.contains("aeroplane") -> {
                HardwareToggleManager.toggleAirplaneMode(context)
                AppContextManager.recordHardwareToggle(HardwareFeature.AIRPLANE_MODE, if (targetState == false) "OFF" else "ON", targetState)
                TtsManager.speak("Airplane Mode settings khol di hai")
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
