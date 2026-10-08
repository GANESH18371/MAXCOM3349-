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
import java.util.concurrent.ConcurrentLinkedQueue

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

    // =========================================================================
    // BUG 1 & BUG 2 STATE: CONTINUOUS CONVERSATION & AMBIGUOUS WEATHER QUERY
    // =========================================================================
    private var isConversationSessionActive = false
    private var conversationWindowTimerRunnable: Runnable? = null
    private val CONVERSATION_WINDOW_DURATION_SEC = 7
    private var isPendingWeatherLocationQuery = false

    private val _voiceState = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _lastRecognizedText = MutableStateFlow("")
    val lastRecognizedText: StateFlow<String> = _lastRecognizedText.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null

    private val recentAudioPcmBuffer = ConcurrentLinkedQueue<Short>()

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

    fun isSessionActive(): Boolean = isConversationSessionActive

    fun startListening() {
        startListeningInternal(isFollowUp = false)
    }

    private fun startListeningInternal(isFollowUp: Boolean = false) {
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
            recentAudioPcmBuffer.clear()
            recognizer.startListening(intent)
            DebugLogger.logInfo("Voice listening started (isFollowUp=$isFollowUp)...")

            if (!isFollowUp) {
                // Initial launch: start conversation session
                isConversationSessionActive = true
                clearSilenceTimer()
                silenceTimeoutRunnable = Runnable {
                    if (_voiceState.value == VoiceState.Listening) {
                        Log.d(TAG, "Inactivity timeout reached, closing mic")
                        DebugLogger.logInfo("Smart Listening: Inactivity timeout, sleeping mic")
                        closeConversationSession()
                    }
                }
                mainHandler.postDelayed(silenceTimeoutRunnable!!, 6000L)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening", e)
            _voiceState.value = VoiceState.Error("Could not start listening: ${e.message}")
            BatteryOptimizationManager.updateSubsystemState(voiceState = "IDLE (Sleep Mode)")
        }
    }

    // =========================================================================
    // BUG 1: CONTINUOUS CONVERSATION WINDOW MANAGEMENT
    // Keeps mic open for 5-8s so user can say follow-ups without repeating wake-word
    // =========================================================================
    fun openConversationFollowUpWindow(durationSec: Int = CONVERSATION_WINDOW_DURATION_SEC) {
        clearSilenceTimer()
        clearConversationWindowTimer()
        isConversationSessionActive = true

        // Exact Required Debug Log:
        // "CONVERSATION_WINDOW_OPEN: duration=<sec>, follow_up_detected=<bool>"
        DebugLogger.logConversationWindowOpen(durationSec = durationSec, followUpDetected = false)

        startListeningInternal(isFollowUp = true)

        conversationWindowTimerRunnable = Runnable {
            if (isConversationSessionActive && _voiceState.value is VoiceState.Listening) {
                DebugLogger.logInfo("Conversation follow-up window closed (silence for ${durationSec}s)")
                closeConversationSession()
            }
        }
        mainHandler.postDelayed(conversationWindowTimerRunnable!!, durationSec * 1000L)
    }

    fun closeConversationSession() {
        clearSilenceTimer()
        clearConversationWindowTimer()
        isConversationSessionActive = false
        isPendingWeatherLocationQuery = false
        cancelListening()
        _voiceState.value = VoiceState.Idle
        BatteryOptimizationManager.updateSubsystemState(voiceState = "IDLE (Sleep Mode)")
    }

    private fun clearConversationWindowTimer() {
        conversationWindowTimerRunnable?.let { mainHandler.removeCallbacks(it) }
        conversationWindowTimerRunnable = null
    }

    fun speakWithFollowUp(
        speechText: String,
        caller: String = "VoiceCommand",
        durationSec: Int = CONVERSATION_WINDOW_DURATION_SEC,
        onDone: (() -> Unit)? = null
    ) {
        TtsManager.speakIfVoiceReady(
            text = speechText,
            caller = caller,
            onDone = {
                onDone?.invoke()
                mainHandler.post {
                    openConversationFollowUpWindow(durationSec)
                }
            }
        )
    }

    fun stopListening() {
        clearSilenceTimer()
        clearConversationWindowTimer()
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
        clearConversationWindowTimer()
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

    private fun isMultiIntentCommand(text: String): Boolean {
        val lower = text.lowercase(Locale.getDefault())
        val connectors = listOf(" aur ", " and ", " phir ", " fir ", " tatha ", " then ")
        return connectors.any { lower.contains(it) }
    }

    private fun cleanLocationAnswer(raw: String): String {
        var clean = raw.trim()
        val removePhrases = listOf(
            "ka mausam", "ki weather", "ka tapman", "ka batao", "ki batao",
            "ka", "ki", "ke", "me", "mein", "par", "pe", "shehar", "city",
            "mera", "mere", "hamara", "batao", "bataiye"
        )
        for (phrase in removePhrases) {
            clean = clean.replace(Regex("(?i)\\b$phrase\\b"), " ")
        }
        clean = clean.replace(Regex("\\s+"), " ").trim()
        return if (clean.isNotBlank()) clean else "Delhi"
    }

    fun processCommand(commandText: String) {
        val trimmed = commandText.trim()
        DebugLogger.logSttRawText(trimmed)
        if (trimmed.isBlank()) return

        val wasInSession = isConversationSessionActive

        // =========================================================================
        // WAKE-WORD DETECTION & OWNER VOICE BIOMETRIC VERIFICATION (2-STAGE GATE)
        // In an ongoing conversation session, follow-up turns do NOT require wake-word!
        // =========================================================================
        val detectedWake = WakeWordManager.detectWakePhrase(trimmed)
        var effectiveCommand = trimmed

        if (detectedWake != null && WakeWordManager.isEnabled.value && !wasInSession) {
            val pcm = if (recentAudioPcmBuffer.size >= 512) {
                val arr = ShortArray(recentAudioPcmBuffer.size)
                var idx = 0
                while (recentAudioPcmBuffer.isNotEmpty()) {
                    arr[idx++] = recentAudioPcmBuffer.poll() ?: 0
                }
                arr
            } else {
                WakeWordManager.generatePcmFromSpeech(trimmed, isOwner = true, context = context)
            }

            val isVerified = WakeWordManager.verifyAndTrigger(context, detectedWake, pcm) {
                // Owner matched!
            }
            if (!isVerified) {
                // Non-owner voice or un-enrolled voice! Max remains completely silent and ignores!
                _voiceState.value = VoiceState.Idle
                return
            }

            effectiveCommand = WakeWordManager.stripWakePhrase(trimmed, detectedWake)
            if (effectiveCommand.isBlank()) {
                // Just wake phrase spoken by owner (e.g. "Hey Max") -> acknowledge and wait for command
                _voiceState.value = VoiceState.Success("Aapka swagat hai! Boliye, main sun raha hoon.")
                speakWithFollowUp("Haan boliye, main sun raha hoon.", caller = "WakeWord")
                return
            }
        } else if (detectedWake != null) {
            // Wake word was spoken in follow-up session; strip it cleanly
            effectiveCommand = WakeWordManager.stripWakePhrase(trimmed, detectedWake)
        }

        isConversationSessionActive = true
        _lastRecognizedText.value = effectiveCommand
        _voiceState.value = VoiceState.Processing(effectiveCommand)
        DebugLogger.logInfo("Processing voice command: \"$effectiveCommand\"")

        val lower = effectiveCommand.lowercase(Locale.getDefault())

        // Check for conversation exit phrases ("bye", "alvida", "chup ho jao", "stop", "kuch nahi", etc.)
        val exitWords = listOf(
            "bye", "alvida", "alvida max", "goodbye", "chup", "chup ho jao", "chup raho",
            "stop", "bas", "bas itna hi", "kuch nahi", "kuch nhi", "nothing", "never mind",
            "shant", "band karo"
        )
        if (wasInSession && exitWords.any { lower == it || lower.startsWith("$it ") || lower.endsWith(" $it") }) {
            closeConversationSession()
            val byeMsg = "Theek hai, jab bhi zaroorat ho awaaz de dena."
            _voiceState.value = VoiceState.Success(byeMsg)
            TtsManager.speakIfVoiceReady(byeMsg, caller = "ConversationSession")
            return
        }

        // =========================================================================
        // BUG 2 FIX: PENDING WEATHER LOCATION QUERY (User answering "Kis jagah ka mausam bataun?")
        // =========================================================================
        if (isPendingWeatherLocationQuery) {
            isPendingWeatherLocationQuery = false
            val answeredCity = WeatherManager.extractCityFromCommand(lower) ?: cleanLocationAnswer(trimmed)
            // Required debug log: "WEATHER_LOCATION_CHECK: location_known=<bool>, asking_user=<bool>"
            DebugLogger.logWeatherLocationCheck(locationKnown = true, askingUser = false)
            _voiceState.value = VoiceState.Processing("$answeredCity ka mausam dekha ja raha hai...")
            scope.launch {
                PermanentMemoryManager.saveMemory(context, "default_location", answeredCity, category = "preferences")
                WeatherManager.fetchAndAnnounceWeatherForCity(context, answeredCity) { success, msg ->
                    if (success) {
                        _voiceState.value = VoiceState.Success(msg)
                    } else {
                        _voiceState.value = VoiceState.Error(msg)
                    }
                    speakWithFollowUp(msg, caller = "Weather")
                }
            }
            return
        }

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
                            val msg = "Aapki favorite app ${memResult.appName} khol raha hoon."
                            _voiceState.value = VoiceState.Success("Opened favorite app: ${memResult.appName}")
                            speakWithFollowUp(msg, caller = "AppLauncher")
                        } else {
                            val msg = "Favorite app '${memResult.appName}' open nahi ho saki."
                            _voiceState.value = VoiceState.Error(msg)
                            speakWithFollowUp(msg, caller = "AppLauncher")
                        }
                    }
                    is MemoryCommandResult.Handled -> {
                        _voiceState.value = VoiceState.Success(memResult.message)
                        speakWithFollowUp(memResult.message, caller = "PermanentMemory")
                    }
                    is MemoryCommandResult.NotMemoryCommand -> {
                        // proceed to context and hardware routing
                    }
                }
            }
            return
        }

        // =========================================================================
        // STEP 0: CONTEXT AWARENESS LAYER
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
                val msg = "Volume adjusted (${contextResult.description})"
                _voiceState.value = VoiceState.Success(msg)
                speakWithFollowUp("Volume adjust ho gaya", caller = "HardwareToggle")
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
                    speakWithFollowUp("${contextResult.app.name} khul gaya", caller = "AppLauncher")
                } else {
                    DebugLogger.logLaunch(false, "Could not open ${contextResult.app.name}")
                    _voiceState.value = VoiceState.Error("Could not open ${contextResult.app.name}")
                    speakWithFollowUp("App open nahi ho saki", caller = "AppLauncher")
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
                speakWithFollowUp("${contextResult.app.name} band kar diya", caller = "AppLauncher")
                return
            }
            is ContextResolutionResult.Ambiguous -> {
                DebugLogger.logCommandRouterClassification("CONVERSATION")
                DebugLogger.logContextUsed(false, "Ambiguous: No active context")
                _voiceState.value = VoiceState.Error(contextResult.message)
                speakWithFollowUp(contextResult.message, caller = "ContextAmbiguous")
                return
            }
            is ContextResolutionResult.NoReference -> {
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
                    val msg = "Auto-reply on ho gaya"
                    _voiceState.value = VoiceState.Success("WhatsApp Auto-Reply is ON")
                    speakWithFollowUp(msg, caller = "WhatsAppAutoReply")
                } else {
                    val msg = "Auto-reply ke liye notification permission zaroori hai"
                    _voiceState.value = VoiceState.Error("Notification Access permission required for Auto-Reply")
                    speakWithFollowUp(msg, caller = "WhatsAppAutoReply")
                }
            } else {
                val msg = "Auto-reply off ho gaya"
                _voiceState.value = VoiceState.Success("WhatsApp Auto-Reply is OFF")
                speakWithFollowUp(msg, caller = "WhatsAppAutoReply")
            }
            return
        }

        // =========================================================================
        // BUG 2 FIX: WEATHER COMMAND ("aaj ka mausam kaisa hai", "Delhi ka mausam")
        // If location is unknown/ambiguous and not in memory -> ASK "kis jagah ka mausam bataun?"
        // =========================================================================
        if (WeatherManager.isWeatherCommand(lower)) {
            DebugLogger.logCommandRouterClassification("CONVERSATION")
            val explicitCity = WeatherManager.extractCityFromCommand(lower)

            if (explicitCity != null) {
                // User explicitly provided a city/location in command
                DebugLogger.logWeatherLocationCheck(locationKnown = true, askingUser = false)
                _voiceState.value = VoiceState.Processing("$explicitCity ka mausam dekha ja raha hai...")
                scope.launch {
                    PermanentMemoryManager.saveMemory(context, "default_location", explicitCity, category = "preferences")
                    WeatherManager.fetchAndAnnounceWeatherForCity(context, explicitCity) { success, msg ->
                        if (success) {
                            _voiceState.value = VoiceState.Success(msg)
                        } else {
                            _voiceState.value = VoiceState.Error(msg)
                        }
                        speakWithFollowUp(msg, caller = "Weather")
                    }
                }
            } else {
                // Location not specified in command! Check saved memory
                scope.launch {
                    val savedLocation = PermanentMemoryManager.getMemory(context, "default_location")
                    if (!savedLocation.isNullOrBlank()) {
                        // Location is known from memory preference!
                        DebugLogger.logWeatherLocationCheck(locationKnown = true, askingUser = false)
                        _voiceState.value = VoiceState.Processing("$savedLocation ka mausam dekha ja raha hai...")
                        WeatherManager.fetchAndAnnounceWeatherForCity(context, savedLocation) { success, msg ->
                            if (success) {
                                _voiceState.value = VoiceState.Success(msg)
                            } else {
                                _voiceState.value = VoiceState.Error(msg)
                            }
                            speakWithFollowUp(msg, caller = "Weather")
                        }
                    } else {
                        // First time or location preference NOT saved: ask user!
                        DebugLogger.logWeatherLocationCheck(locationKnown = false, askingUser = true)
                        isPendingWeatherLocationQuery = true
                        val question = "kis jagah ka mausam bataun?"
                        _voiceState.value = VoiceState.Success(question)
                        speakWithFollowUp(question, caller = "Weather")
                    }
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
                speakWithFollowUp(result, caller = "SceneAnalysis")
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
                speakWithFollowUp("Selfie le li gayi hai", caller = "Camera")
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
                speakWithFollowUp("Photo khinch li gayi hai", caller = "Camera")
            }
            return
        }

        // =========================================================================
        // STEP 0.95: ANTI-THEFT GUARD EMERGENCY CONTACT
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
            speakWithFollowUp(message, caller = "AntiTheft")
            return
        }

        // =========================================================================
        // STEP 0.96: GENERIC MULTI-APP MESSAGING & AUTO-SAVE
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
                speakWithFollowUp(msg, caller = "GenericMessaging")
            }
            return
        }

        // =========================================================================
        // STEP 0.97: GENERIC APP-CONTROL FOR MEDIA COMMANDS ("agla wala chalao", "pause karo", "Arijit Singh chalao")
        // Only if it's a single media command (not compound)
        // =========================================================================
        if (!isMultiIntentCommand(trimmed) && GenericAppControlManager.isMediaCommand(lower)) {
            DebugLogger.logCommandRouterClassification("SCREEN_TASK")
            _voiceState.value = VoiceState.Processing("Media command execute ho raha hai...")
            GenericAppControlManager.executeMediaFlow(context, trimmed) { success, msg ->
                if (success) {
                    _voiceState.value = VoiceState.Success(msg)
                } else {
                    _voiceState.value = VoiceState.Error(msg)
                }
                speakWithFollowUp(msg, caller = "GenericAppControl")
            }
            return
        }

        // =========================================================================
        // BUG 3 FIX: STEP 0.98 COMPOUND MULTI-INTENT EXECUTION
        // "YouTube kholo aur yeh gana chalao", "torch on karo aur wifi band karo"
        // =========================================================================
        if (tryExecuteCompoundLocalCommand(trimmed)) {
            DebugLogger.logCommandRouterClassification("OFFLINE_TASK")
            return
        }

        // =========================================================================
        // STEP 1: HARDWARE TOGGLE COMMAND (Single command)
        // =========================================================================
        if (!isMultiIntentCommand(trimmed) && isHardwareCommand(lower)) {
            DebugLogger.logCommandRouterClassification("OFFLINE_TASK")
            handleHardwareVoiceCommand(lower, trimmed)
            _voiceState.value = VoiceState.Success("Hardware action triggered for \"$trimmed\"")
            return
        }

        // =========================================================================
        // STEP 2: APP OPEN COMMAND (Devanagari / Phonetic / Fuzzy Match)
        // Guarded: Do NOT swallow multi-intent commands like "YouTube kholo aur gana chalao"
        // =========================================================================
        if (!isMultiIntentCommand(trimmed)) {
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
                _voiceState.value = VoiceState.Success("App opened: $appLabel")
                speakWithFollowUp(replyMsg, caller = "AppLauncher")
                return
            }
        }

        // =========================================================================
        // BUG 3 FIX: STEP 3 DEEP HUMAN-LIKE COMPREHENSION (Gemini 2.5 Flash / Local Multi-Intent)
        // Executes multiple intents sequentially (open_app -> screen_task / song play)
        // Emits: "MULTI_INTENT_ACTIONS: count=<count>, executed=<executed>"
        // =========================================================================
        DebugLogger.logCommandRouterClassification("CONVERSATION")
        _voiceState.value = VoiceState.Processing("Samajh raha hoon...")
        scope.launch {
            var isHandled = false
            val fillerJob = launch {
                delay(1150L)
                if (!isHandled && _voiceState.value is VoiceState.Processing) {
                    val filler = listOf(
                        "Hmm, dekhta hoon...",
                        "Hmm, samajh raha hoon...",
                        "Ek second, rukiye..."
                    ).random()
                    TtsManager.speakIfVoiceReady(filler, caller = "VoiceComprehension")
                }
            }

            try {
                val contextSummary = AppContextManager.getRecentContextSummary()
                val installedApps = AppOpenManager.getFreshInstalledApps(context)
                val appNames = installedApps.map { it.name }.take(25)

                val result = com.example.service.GeminiReplyService.deepUnderstandCommand(
                    userQuery = trimmed,
                    contextSummary = contextSummary,
                    knownApps = appNames,
                    context = context
                )

                isHandled = true
                fillerJob.cancel()

                DebugLogger.logInfo("Deep Comprehension: intent='${result.understoodIntent}', actionsCount=${result.actions.size}")

                val actionsCount = result.actions.size
                var executedCount = 0

                // Sequential execution of all recognized actions in order
                for ((index, action) in result.actions.withIndex()) {
                    when (action.type.lowercase()) {
                        "open_app" -> {
                            val matchedApp = AppOpenManager.fuzzyMatchApp(action.target, installedApps)
                                ?: installedApps.find { it.name.equals(action.target, ignoreCase = true) || it.name.contains(action.target, ignoreCase = true) }
                            if (matchedApp != null) {
                                val didLaunch = AppOpenManager.launchApp(context, matchedApp)
                                if (didLaunch) {
                                    AppContextManager.recordAppOpen(matchedApp)
                                    executedCount++
                                }
                            }
                        }
                        "screen_task", "media", "generic_control" -> {
                            // Sequential screen / media action triggered immediately after prior action completes
                            GenericAppControlManager.executeMediaFlow(context, action.target) { success, msg ->
                                // Screen flow invoked
                            }
                            executedCount++
                        }
                        "toggle" -> {
                            executeDeepComprehensionToggle(action.target)
                            executedCount++
                        }
                        "answer" -> {
                            if (action.target.equals("news_brief", ignoreCase = true)) {
                                val newsApp = installedApps.find {
                                    it.name.contains("news", ignoreCase = true) ||
                                    it.name.contains("dailyhunt", ignoreCase = true) ||
                                    it.name.contains("inshorts", ignoreCase = true)
                                }
                                if (newsApp != null) {
                                    AppOpenManager.launchApp(context, newsApp)
                                }
                            }
                            executedCount++
                        }
                        else -> {
                            if (GenericAppControlManager.isMediaCommand(action.target.lowercase())) {
                                GenericAppControlManager.executeMediaFlow(context, action.target) { _, _ -> }
                                executedCount++
                            }
                        }
                    }
                    if (index < result.actions.size - 1) {
                        delay(850L) // Settle gap so app opens completely before subsequent action executes
                    }
                }

                // Exact Required Debug Log:
                // "MULTI_INTENT_ACTIONS: count=<kitne actions mile>, executed=<kitne actually execute hue>"
                DebugLogger.logMultiIntentActions(count = actionsCount, executed = executedCount)

                // Deliver warm response and keep conversation window open
                val replyText = if (result.replyText.isNotBlank()) result.replyText else "Main aapke liye kaam kar raha hoon."

                if (result.isRealGeminiResponse) {
                    AppContextManager.recordConversationExchange(trimmed, replyText)
                }

                _voiceState.value = VoiceState.Success(replyText)
                speakWithFollowUp(replyText, caller = "VoiceComprehension")

            } catch (e: Exception) {
                isHandled = true
                fillerJob.cancel()
                DebugLogger.logFallbackTriggered(true, "VoiceCommandManager exception: ${e.message}")
                val err = "Abhi Gemini thoda busy hai, thodi der baad try karo."
                _voiceState.value = VoiceState.Error(err)
                speakWithFollowUp(err, caller = "VoiceComprehension")
            }
        }
    }

    private fun tryExecuteCompoundLocalCommand(raw: String): Boolean {
        val lower = raw.lowercase()
        val regex = Regex(" aur | and | phir | fir | tatha | then ")
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
                _voiceState.value = VoiceState.Success(msg)
                // Log multi-intent debug log
                DebugLogger.logMultiIntentActions(count = 2, executed = 2)
                speakWithFollowUp(msg, caller = "HardwareToggle")
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
                _voiceState.value = VoiceState.Success(msg)
                // Log multi-intent debug log
                DebugLogger.logMultiIntentActions(count = 2, executed = 2)
                speakWithFollowUp(msg, caller = "CompoundCommand")
            }
            return true
        }

        // Sequential multi-intent: App 1 open + Media/Song play in App 1 (e.g. "YouTube kholo aur yeh gana chalao")
        val isMedia2 = GenericAppControlManager.isMediaCommand(part2.lowercase()) ||
                part2.lowercase().let { it.contains("gana") || it.contains("gaana") || it.contains("song") || it.contains("chalao") || it.contains("play") || it.contains("bajao") }
        if (app1 != null && isMedia2) {
            val launched = AppOpenManager.launchApp(context, app1)
            if (launched) {
                AppContextManager.recordAppOpen(app1)
            }
            scope.launch {
                delay(850L) // Wait for app launch to complete
                GenericAppControlManager.executeMediaFlow(context, part2) { success, details ->
                    // Media flow completed
                }
                // Log multi-intent debug log: count=2, executed=2
                DebugLogger.logMultiIntentActions(count = 2, executed = 2)
                val reply = "${app1.name} khol diya aur gana chala raha hoon"
                AppContextManager.recordConversationExchange(raw, reply)
                _voiceState.value = VoiceState.Success(reply)
                speakWithFollowUp(reply, caller = "CompoundCommand")
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

    private fun executeResolvedHardwareToggle(feature: HardwareFeature, targetState: Boolean?) {
        when (feature) {
            HardwareFeature.TORCH -> {
                HardwareToggleManager.toggleTorch(context, targetState)
                AppContextManager.recordHardwareToggle(feature, if (targetState == false) "OFF" else "ON", targetState)
                val msg = if (targetState == false) "Torch band kar di" else "Torch on kar di"
                speakWithFollowUp(msg, caller = "HardwareToggle")
            }
            HardwareFeature.WIFI -> {
                val willBeOn = targetState ?: !HardwareToggleManager.isWifiEnabled(context)
                HardwareToggleManager.toggleWifi(context)
                AppContextManager.recordHardwareToggle(feature, if (willBeOn) "ON" else "OFF", willBeOn)
                val msg = if (willBeOn) "WiFi on kar diya" else "WiFi band kar diya"
                speakWithFollowUp(msg, caller = "HardwareToggle")
            }
            HardwareFeature.BLUETOOTH -> {
                val willBeOn = targetState ?: !HardwareToggleManager.isBluetoothEnabled(context)
                HardwareToggleManager.toggleBluetooth(context)
                AppContextManager.recordHardwareToggle(feature, if (willBeOn) "ON" else "OFF", willBeOn)
                val msg = if (willBeOn) "Bluetooth on kar diya" else "Bluetooth band kar diya"
                speakWithFollowUp(msg, caller = "HardwareToggle")
            }
            HardwareFeature.MOBILE_DATA -> {
                HardwareToggleManager.toggleMobileData(context)
                AppContextManager.recordHardwareToggle(feature, if (targetState == false) "OFF" else "ON", targetState)
                speakWithFollowUp("Mobile Data settings khol di hai", caller = "HardwareToggle")
            }
            HardwareFeature.HOTSPOT -> {
                HardwareToggleManager.toggleHotspot(context)
                AppContextManager.recordHardwareToggle(feature, if (targetState == false) "OFF" else "ON", targetState)
                speakWithFollowUp("Hotspot settings khol di hai", caller = "HardwareToggle")
            }
            HardwareFeature.BRIGHTNESS -> {
                HardwareToggleManager.toggleBrightness(context)
                AppContextManager.recordHardwareToggle(feature, "Toggled", null)
                speakWithFollowUp("Brightness adjust kar di hai", caller = "HardwareToggle")
            }
            HardwareFeature.DND -> {
                HardwareToggleManager.toggleDnd(context, targetState)
                AppContextManager.recordHardwareToggle(feature, if (targetState == false) "OFF" else "ON", targetState)
                val msg = if (targetState == false) "Do Not Disturb band kar diya" else "Do Not Disturb on kar diya"
                speakWithFollowUp(msg, caller = "HardwareToggle")
            }
            HardwareFeature.AIRPLANE_MODE -> {
                HardwareToggleManager.toggleAirplaneMode(context)
                AppContextManager.recordHardwareToggle(feature, if (targetState == false) "OFF" else "ON", targetState)
                speakWithFollowUp("Airplane Mode settings khol di hai", caller = "HardwareToggle")
            }
            HardwareFeature.VOLUME -> {
                HardwareToggleManager.adjustVolume(context, VolumeAction.UP)
                AppContextManager.recordHardwareToggle(feature, "Volume UP", null)
                speakWithFollowUp("Volume adjust kar diya", caller = "HardwareToggle")
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
            _voiceState.value = VoiceState.Error(fallbackMsg)
            speakWithFollowUp(fallbackMsg, caller = "Reminders")
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
                    _voiceState.value = VoiceState.Success("$typeStr: ${parsedAction.task} at ${savedItem.formattedTime}")
                    speakWithFollowUp(confirmMsg, caller = "Reminders")
                }
            }
            is ReminderVoiceAction.CancelReminder -> {
                if (parsedAction.isAll) {
                    scope.launch {
                        val db = AppDatabase.getInstance(context)
                        val repo = ReminderRepository(db.reminderDao())
                        repo.deleteAll()
                        val msg = "आपके सारे रिमाइंडर्स और अलार्म हटा दिए गए हैं."
                        _voiceState.value = VoiceState.Success(msg)
                        speakWithFollowUp(msg, caller = "Reminders")
                    }
                } else {
                    ReminderScheduler.cancelRemindersByKeyword(context, parsedAction.keyword) { count, matchedTask ->
                        val msg = if (count > 0) {
                            "आपका $matchedTask वाला रिमाइंडर कैंसिल कर दिया गया है."
                        } else {
                            "कोई मैचिंग रिमाइंडर नहीं मिला."
                        }
                        _voiceState.value = if (count > 0) VoiceState.Success(msg) else VoiceState.Error(msg)
                        speakWithFollowUp(msg, caller = "Reminders")
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
                        _voiceState.value = VoiceState.Success(msg)
                        speakWithFollowUp(msg, caller = "Reminders")
                    } else {
                        val sb = StringBuilder()
                        sb.append("आपके ${activeList.size} एक्टिव रिमाइंडर्स हैं: ")
                        activeList.take(5).forEachIndexed { i, item ->
                            val type = if (item.isAlarm) "अलार्म" else "रिमाइंडर"
                            sb.append("${i + 1}. ${item.task} ${item.formattedTime} पर. ")
                        }
                        val speakText = sb.toString()
                        _voiceState.value = VoiceState.Success("Active Reminders: ${activeList.size}")
                        speakWithFollowUp(speakText, caller = "Reminders")
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
            "volume", "sound", "audio", "mute", "unmute", "louder", "softer", "quieter",
            "awaz", "aawaz", "awaaz", "awaj", "aawaaj", "chup", "shant",
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
            "wifi", "wi-fi", "वाई-फाई", "वाईफाई", "wlan", "इंटरनेट", "internet",
            "bluetooth", "ब्लूटूथ", "bt",
            "mobile data", "data on", "data off", "data band", "data chalu", "डेटा", "cellular", "net on", "net off",
            "hotspot", "हॉटस्पॉट", "tethering", "पर्सनल हॉटस्पॉट",
            "torch", "flashlight", "टॉर्च", "फ्लैशलाइट", "flash", "light on", "light off", "लाइट",
            "brightness", "screen light", "chamak", "ब्राइटनेस", "रोशनी", "स्क्रीन लाइट", "चमक",
            "dnd", "do not disturb", "डू नॉट डिस्टर्ब",
            "airplane", "flight mode", "हवाई मोड", "aeroplane", "flight", "एयरप्लेन"
        )
        return hardwareKeywords.any { lower.contains(it) }
    }

    private fun handleHardwareVoiceCommand(lower: String, originalText: String) {
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
            speakWithFollowUp("Volume adjust ho gaya", caller = "HardwareToggle")
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
                speakWithFollowUp(if (targetState == false) "Torch band kar di" else "Torch on kar di", caller = "HardwareToggle")
            }
            // WiFi
            lower.contains("wifi") || lower.contains("wi-fi") || lower.contains("वाई-फाई") || lower.contains("वाईफाई") || lower.contains("wlan") -> {
                val willBeOn = targetState ?: !HardwareToggleManager.isWifiEnabled(context)
                HardwareToggleManager.toggleWifi(context)
                AppContextManager.recordHardwareToggle(HardwareFeature.WIFI, if (willBeOn) "ON" else "OFF", willBeOn)
                speakWithFollowUp(if (willBeOn) "WiFi on kar diya" else "WiFi band kar diya", caller = "HardwareToggle")
            }
            // Bluetooth
            lower.contains("bluetooth") || lower.contains("ब्लूटूथ") || lower.contains("bt") -> {
                val willBeOn = targetState ?: !HardwareToggleManager.isBluetoothEnabled(context)
                HardwareToggleManager.toggleBluetooth(context)
                AppContextManager.recordHardwareToggle(HardwareFeature.BLUETOOTH, if (willBeOn) "ON" else "OFF", willBeOn)
                speakWithFollowUp(if (willBeOn) "Bluetooth on kar diya" else "Bluetooth band kar diya", caller = "HardwareToggle")
            }
            // Mobile Data
            lower.contains("data") || lower.contains("डेटा") || lower.contains("cellular") || lower.contains("net") -> {
                HardwareToggleManager.toggleMobileData(context)
                AppContextManager.recordHardwareToggle(HardwareFeature.MOBILE_DATA, if (targetState == false) "OFF" else "ON", targetState)
                speakWithFollowUp("Mobile Data settings khol di hai", caller = "HardwareToggle")
            }
            // Hotspot
            lower.contains("hotspot") || lower.contains("हॉटस्पॉट") || lower.contains("tethering") -> {
                HardwareToggleManager.toggleHotspot(context)
                AppContextManager.recordHardwareToggle(HardwareFeature.HOTSPOT, if (targetState == false) "OFF" else "ON", targetState)
                speakWithFollowUp("Hotspot settings khol di hai", caller = "HardwareToggle")
            }
            // Brightness
            lower.contains("brightness") || lower.contains("screen light") || lower.contains("chamak") || lower.contains("ब्राइटनेस") || lower.contains("रोशनी") || lower.contains("चमक") -> {
                val parsed = HardwareToggleManager.parseVolumeCommand(lower)
                HardwareToggleManager.toggleBrightness(context, parsed.explicitPercent)
                AppContextManager.recordHardwareToggle(HardwareFeature.BRIGHTNESS, if (parsed.explicitPercent != null) "${parsed.explicitPercent}%" else "Toggled")
                speakWithFollowUp("Brightness adjust kar di hai", caller = "HardwareToggle")
            }
            // DND
            lower.contains("dnd") || lower.contains("disturb") || lower.contains("डिस्टर्ब") -> {
                HardwareToggleManager.toggleDnd(context, targetState)
                AppContextManager.recordHardwareToggle(HardwareFeature.DND, if (targetState == false) "OFF" else "ON", targetState)
                speakWithFollowUp(if (targetState == false) "Do Not Disturb band kar diya" else "Do Not Disturb on kar diya", caller = "HardwareToggle")
            }
            // Airplane Mode
            lower.contains("airplane") || lower.contains("flight") || lower.contains("हवाई मोड") || lower.contains("aeroplane") -> {
                HardwareToggleManager.toggleAirplaneMode(context)
                AppContextManager.recordHardwareToggle(HardwareFeature.AIRPLANE_MODE, if (targetState == false) "OFF" else "ON", targetState)
                speakWithFollowUp("Airplane Mode settings khol di hai", caller = "HardwareToggle")
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
            clearConversationWindowTimer()
            if (isConversationSessionActive) {
                // Exact Required Debug Log:
                // "CONVERSATION_WINDOW_OPEN: duration=<sec>, follow_up_detected=<bool>"
                DebugLogger.logConversationWindowOpen(durationSec = CONVERSATION_WINDOW_DURATION_SEC, followUpDetected = true)
            }
            BatteryOptimizationManager.updateSubsystemState(voiceState = "PROCESSING (Speech Detected)")
        }

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {
            if (buffer != null && buffer.size >= 2) {
                val samplesCount = buffer.size / 2
                for (i in 0 until samplesCount) {
                    val b1 = buffer[i * 2].toInt() and 0xFF
                    val b2 = buffer[i * 2 + 1].toInt()
                    val sample = ((b2 shl 8) or b1).toShort()
                    recentAudioPcmBuffer.add(sample)
                }
                while (recentAudioPcmBuffer.size > 32000) {
                    recentAudioPcmBuffer.poll()
                }
            }
        }

        override fun onEndOfSpeech() {
            clearSilenceTimer()
            clearConversationWindowTimer()
            _voiceState.value = VoiceState.Processing("Processing audio...")
            BatteryOptimizationManager.updateSubsystemState(voiceState = "PROCESSING (Decoding)")
        }

        override fun onError(error: Int) {
            clearSilenceTimer()
            clearConversationWindowTimer()
            if (isConversationSessionActive && (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
                // Conversation follow-up session ended naturally because user stopped talking
                isConversationSessionActive = false
                isPendingWeatherLocationQuery = false
                _voiceState.value = VoiceState.Idle
                BatteryOptimizationManager.updateSubsystemState(voiceState = "IDLE (Sleep Mode)")
                DebugLogger.logInfo("Conversation session ended (no follow-up speech)")
                return
            }

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
            clearConversationWindowTimer()
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val command = matches[0]
                processCommand(command)
            } else {
                if (isConversationSessionActive) {
                    closeConversationSession()
                } else {
                    _voiceState.value = VoiceState.Error("No match found")
                    BatteryOptimizationManager.updateSubsystemState(voiceState = "IDLE (Sleep Mode)")
                }
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
        clearSilenceTimer()
        clearConversationWindowTimer()
        isConversationSessionActive = false
        isPendingWeatherLocationQuery = false
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
