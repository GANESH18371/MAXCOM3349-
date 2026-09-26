package com.example.manager

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.util.DebugLogger
import com.example.util.TtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface CallUiState {
    object Idle : CallUiState
    data class Ringing(
        val callerName: String,
        val callerNumber: String,
        val isListeningForVoice: Boolean = false,
        val timeStamp: String = ""
    ) : CallUiState
    data class CallActionSummary(
        val caller: String,
        val action: String, // "answered", "rejected", "timed_out", "ended"
        val timestamp: String,
        val details: String
    ) : CallUiState
}

data class CallHistoryItem(
    val id: Long = System.currentTimeMillis() + (0..999).random(),
    val callerName: String,
    val callerNumber: String,
    val action: String, // "ANSWERED", "REJECTED", "IGNORED"
    val timestamp: String
)

object CallManager {
    private const val TAG = "CallManager"
    private const val PREFS_NAME = "max_call_prefs"
    private const val KEY_ANNOUNCE_ENABLED = "call_announce_enabled"

    private val scope = CoroutineScope(Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _isCallAnnounceEnabled = MutableStateFlow(true)
    val isCallAnnounceEnabled: StateFlow<Boolean> = _isCallAnnounceEnabled.asStateFlow()

    private val _callUiState = MutableStateFlow<CallUiState>(CallUiState.Idle)
    val callUiState: StateFlow<CallUiState> = _callUiState.asStateFlow()

    private val _callHistory = MutableStateFlow<List<CallHistoryItem>>(emptyList())
    val callHistory: StateFlow<List<CallHistoryItem>> = _callHistory.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var isCurrentlyListening = false
    private var listeningTimeoutRunnable: Runnable? = null
    private var lastRingingNumber: String? = null
    private var lastRingingTimestamp: Long = 0L

    fun init(context: Context) {
        val prefs = getPrefs(context)
        _isCallAnnounceEnabled.value = prefs.getBoolean(KEY_ANNOUNCE_ENABLED, true)
        DebugLogger.logInfo("CallManager initialized, Announce enabled: ${_isCallAnnounceEnabled.value}")
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun setCallAnnounceEnabled(context: Context, enabled: Boolean) {
        _isCallAnnounceEnabled.value = enabled
        getPrefs(context).edit().putBoolean(KEY_ANNOUNCE_ENABLED, enabled).apply()
        DebugLogger.logInfo("Call Announce enabled setting changed: $enabled")
    }

    fun hasRequiredPermissions(context: Context): Boolean {
        val readPhone = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        val answerCalls = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ANSWER_PHONE_CALLS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val readContacts = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        val audio = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        return readPhone && answerCalls && readContacts && audio
    }

    /**
     * Resolves contact name from Contacts provider using phone number.
     */
    fun resolveContactName(context: Context, rawNumber: String?): String {
        if (rawNumber.isNullOrBlank()) return ""
        val hasContactsPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasContactsPermission) {
            Log.d(TAG, "READ_CONTACTS permission not granted, using raw number")
            return ""
        }

        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(rawNumber)
            )
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0) ?: ""
                } else {
                    ""
                }
            } ?: ""
        } catch (e: Exception) {
            Log.w(TAG, "Error looking up contact for $rawNumber", e)
            ""
        }
    }

    /**
     * Main Entry point when incoming call is ringing.
     */
    fun handleIncomingCall(context: Context, rawNumber: String?) {
        val now = System.currentTimeMillis()
        // Debounce ringing state events within 3 seconds for the same call
        if (rawNumber == lastRingingNumber && (now - lastRingingTimestamp) < 3000L) {
            Log.d(TAG, "Ignoring duplicate ringing broadcast for $rawNumber")
            return
        }

        lastRingingNumber = rawNumber
        lastRingingTimestamp = now

        if (!_isCallAnnounceEnabled.value) {
            Log.d(TAG, "Call Announce is disabled; skipping announcement")
            return
        }

        val contactName = resolveContactName(context, rawNumber)
        val callerDisplayName = when {
            contactName.isNotBlank() -> contactName
            !rawNumber.isNullOrBlank() -> rawNumber
            else -> "अज्ञात नंबर"
        }

        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        _callUiState.value = CallUiState.Ringing(
            callerName = callerDisplayName,
            callerNumber = rawNumber ?: "Private",
            isListeningForVoice = false,
            timeStamp = timeStr
        )
        BatteryOptimizationManager.updateSubsystemState(callState = "RINGING (Announcing $callerDisplayName)")

        // 1. Required debug log: "CALL_INCOMING: caller=<naam/number>"
        DebugLogger.logCallIncoming(callerDisplayName)

        // 2. Announce via TTS in Hindi: "Aapko [naam/number] ki call aa rahi hai"
        val announcementText = "आपको $callerDisplayName की कॉल आ रही है."
        TtsManager.speak(announcementText, onDone = {
            // 3. Required debug log: "CALL_ANNOUNCE: TTS spoken"
            DebugLogger.logCallAnnounce()

            // 4. Immediately launch voice recognition window for Accept/Reject
            mainHandler.post {
                startVoiceListeningForCall(context, callerDisplayName, rawNumber ?: "")
            }
        })
    }

    /**
     * Called when phone state becomes IDLE or OFFHOOK.
     */
    fun onCallEnded(context: Context) {
        stopVoiceListening()
        lastRingingNumber = null
        BatteryOptimizationManager.updateSubsystemState(callState = "STANDBY (Telecom-Event)")
        if (_callUiState.value is CallUiState.Ringing) {
            _callUiState.value = CallUiState.Idle
        }
    }

    /**
     * Starts listening for user's voice command: "utha lo" / "katt do"
     * Active for 6-8 seconds window.
     */
    private fun startVoiceListeningForCall(
        context: Context,
        callerDisplayName: String,
        callerNumber: String
    ) {
        if (!hasAudioPermission(context)) {
            Log.w(TAG, "RECORD_AUDIO permission missing; cannot listen for call voice commands")
            return
        }

        // Update UI state to show active listening
        val current = _callUiState.value
        if (current is CallUiState.Ringing) {
            _callUiState.value = current.copy(isListeningForVoice = true)
        }

        stopVoiceListening() // Clean up any previous session

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(createCallRecognitionListener(context, callerDisplayName, callerNumber))
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
                putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-IN", "en-US", "hi-IN"))
            }

            isCurrentlyListening = true
            BatteryOptimizationManager.updateSubsystemState(callState = "LISTENING (Voice Accept/Reject)")
            speechRecognizer?.startListening(intent)
            DebugLogger.logInfo("Call voice listener started (Listening for 'utha lo' / 'katt do')...")

            // Timeout safety: 7 seconds window. If no command received, stop listening and let ringtone continue
            listeningTimeoutRunnable = Runnable {
                if (isCurrentlyListening) {
                    DebugLogger.logInfo("Call voice listening window timed out; normal ringing continues.")
                    stopVoiceListening()
                    val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    addCallHistory(callerDisplayName, callerNumber, "NO_RESPONSE", timeStr)
                    _callUiState.value = CallUiState.CallActionSummary(
                        caller = callerDisplayName,
                        action = "timed_out",
                        timestamp = timeStr,
                        details = "No voice response; normal ringtone continued"
                    )
                }
            }
            mainHandler.postDelayed(listeningTimeoutRunnable!!, 7000L)

        } catch (e: Exception) {
            Log.e(TAG, "Error starting call speech recognition", e)
            stopVoiceListening()
        }
    }

    private fun createCallRecognitionListener(
        context: Context,
        callerDisplayName: String,
        callerNumber: String
    ) = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            Log.d(TAG, "Call speech recognizer error ($error)")
            // On error / no speech, do nothing so normal ringtone continues
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val recognized = matches[0].trim()
                handleCallVoiceInput(context, recognized, callerDisplayName, callerNumber)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val partial = matches[0].trim()
                if (isAcceptCommand(partial.lowercase(Locale.getDefault())) || isRejectCommand(partial.lowercase(Locale.getDefault()))) {
                    handleCallVoiceInput(context, partial, callerDisplayName, callerNumber)
                }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun handleCallVoiceInput(
        context: Context,
        rawSpeech: String,
        callerDisplayName: String,
        callerNumber: String
    ) {
        val lower = rawSpeech.lowercase(Locale.getDefault())
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

        if (isAcceptCommand(lower)) {
            stopVoiceListening()
            // 5. Required debug log: "CALL_VOICE_COMMAND: <utha lo/katt do>, action=<answered/rejected>"
            DebugLogger.logCallVoiceCommand(command = rawSpeech, action = "answered")

            val answered = answerIncomingCall(context)
            addCallHistory(callerDisplayName, callerNumber, "ANSWERED", timeStr)

            _callUiState.value = CallUiState.CallActionSummary(
                caller = callerDisplayName,
                action = "answered",
                timestamp = timeStr,
                details = if (answered) "Call answered successfully" else "Answer triggered (Telecom/Simulation)"
            )
            TtsManager.speak("कॉल रिसीव कर ली गई है.")

        } else if (isRejectCommand(lower)) {
            stopVoiceListening()
            // 5. Required debug log: "CALL_VOICE_COMMAND: <utha lo/katt do>, action=<answered/rejected>"
            DebugLogger.logCallVoiceCommand(command = rawSpeech, action = "rejected")

            val rejected = rejectIncomingCall(context)
            addCallHistory(callerDisplayName, callerNumber, "REJECTED", timeStr)

            _callUiState.value = CallUiState.CallActionSummary(
                caller = callerDisplayName,
                action = "rejected",
                timestamp = timeStr,
                details = if (rejected) "Call rejected successfully" else "Reject triggered (Telecom/Simulation)"
            )
            TtsManager.speak("कॉल रिजेक्ट कर दी गई है.")
        }
    }

    fun isAcceptCommand(lower: String): Boolean {
        val acceptKeywords = listOf(
            "utha lo", "uthalo", "uthao", "receive karo", "receive", "call uthao", "call utha lo",
            "call receive karo", "pick up", "pickup", "answer", "haan", "ha", "uthaye", "uthana",
            "उठा लो", "उठाओ", "रिसीव करो", "कॉल उठाओ", "कॉल रिसीव करो", "हाँ", "हा", "रिसीव"
        )
        return acceptKeywords.any { lower.contains(it) }
    }

    fun isRejectCommand(lower: String): Boolean {
        val rejectKeywords = listOf(
            "katt do", "kat do", "kato", "reject karo", "reject", "call kato", "call kat do",
            "call reject karo", "decline", "cut", "kardo cut", "kaat do", "katt",
            "काट दो", "कट कर दो", "रिजेक्ट करो", "कॉल काटो", "काटो", "डिक्लाइन", "रिजेक्ट"
        )
        return rejectKeywords.any { lower.contains(it) }
    }

    /**
     * Answering call via TelecomManager
     */
    fun answerIncomingCall(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                    telecomManager?.acceptRingingCall()
                    true
                } else {
                    Log.w(TAG, "ANSWER_PHONE_CALLS permission not granted")
                    false
                }
            } else {
                Log.d(TAG, "TelecomManager answer supported on API 26+")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to answer call via TelecomManager", e)
            false
        }
    }

    /**
     * Rejecting call via TelecomManager
     */
    fun rejectIncomingCall(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                    telecomManager?.endCall() ?: false
                } else {
                    Log.w(TAG, "ANSWER_PHONE_CALLS permission not granted")
                    false
                }
            } else {
                Log.d(TAG, "TelecomManager endCall supported on API 28+")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to end call via TelecomManager", e)
            false
        }
    }

    fun stopVoiceListening() {
        listeningTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        listeningTimeoutRunnable = null
        if (isCurrentlyListening) {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                Log.w(TAG, "Error cleaning up speech recognizer", e)
            } finally {
                speechRecognizer = null
                isCurrentlyListening = false
                BatteryOptimizationManager.updateSubsystemState(callState = "STANDBY (Telecom-Event)")
            }
        }
    }

    private fun hasAudioPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun addCallHistory(callerName: String, number: String, action: String, time: String) {
        val item = CallHistoryItem(
            callerName = callerName,
            callerNumber = number,
            action = action,
            timestamp = time
        )
        _callHistory.value = listOf(item) + _callHistory.value.take(29)
    }

    /**
     * Interactive Simulation method for UI testing.
     */
    fun simulateIncomingCall(context: Context, callerName: String, callerNumber: String) {
        handleIncomingCall(context, callerNumber.ifBlank { callerName })
    }
}
