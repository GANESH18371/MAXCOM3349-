package com.example.util

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ToggleMethod {
    DIRECT,
    ACCESSIBILITY
}

data class LogEntry(
    val id: Long = System.currentTimeMillis() + (0..999).random(),
    val timestamp: String,
    val tag: String,
    val message: String,
    val type: LogType
)

enum class LogType {
    MATCH,
    LAUNCH,
    TOGGLE_ATTEMPT,
    TOGGLE_RESULT,
    INFO
}

object DebugLogger {
    private const val TAG = "MaxApp"
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private fun safeLog(priority: Int, tag: String, message: String) {
        try {
            when (priority) {
                Log.DEBUG -> Log.d(tag, message)
                Log.INFO -> Log.i(tag, message)
                Log.ERROR -> Log.e(tag, message)
                Log.WARN -> Log.w(tag, message)
                else -> Log.v(tag, message)
            }
        } catch (_: Throwable) {
            // Safe in unit test environments where android.util.Log is not mocked
        }
    }

    private fun addEntry(message: String, type: LogType) {
        val entry = LogEntry(
            timestamp = timeFormat.format(Date()),
            tag = TAG,
            message = message,
            type = type
        )
        // Keep last 150 entries in memory
        _logs.value = (listOf(entry) + _logs.value).take(150)
    }

    /**
     * Exact required format: "APP_OPEN_MATCH: <found/not-found>"
     */
    fun logMatch(found: Boolean, appDetails: String = "") {
        val status = if (found) "found${if (appDetails.isNotBlank()) " ($appDetails)" else ""}" else "not-found"
        val logLine = "APP_OPEN_MATCH: $status"
        safeLog(Log.DEBUG, TAG, logLine)
        addEntry(logLine, LogType.MATCH)
    }

    /**
     * Exact required format: "APP_OPEN_LAUNCH: success/fail"
     */
    fun logLaunch(success: Boolean, details: String = "") {
        val status = if (success) "success${if (details.isNotBlank()) " ($details)" else ""}" else "fail${if (details.isNotBlank()) " ($details)" else ""}"
        val logLine = "APP_OPEN_LAUNCH: $status"
        if (success) {
            safeLog(Log.INFO, TAG, logLine)
        } else {
            safeLog(Log.ERROR, TAG, logLine)
        }
        addEntry(logLine, LogType.LAUNCH)
    }

    /**
     * Exact required format: "TOGGLE_ATTEMPT: <name>, method=<DIRECT/ACCESSIBILITY>"
     */
    fun logToggleAttempt(name: String, method: ToggleMethod) {
        val logLine = "TOGGLE_ATTEMPT: $name, method=$method"
        safeLog(Log.DEBUG, TAG, logLine)
        addEntry(logLine, LogType.TOGGLE_ATTEMPT)
    }

    /**
     * Exact required format: "TOGGLE_RESULT: success/fail"
     */
    fun logToggleResult(success: Boolean, details: String = "") {
        val status = if (success) "success" else "fail"
        val extra = if (details.isNotBlank()) " ($details)" else ""
        val logLine = "TOGGLE_RESULT: $status$extra"
        if (success) {
            safeLog(Log.INFO, TAG, logLine)
        } else {
            safeLog(Log.ERROR, TAG, logLine)
        }
        addEntry(logLine, LogType.TOGGLE_RESULT)
    }

    /**
     * Exact required format: "CONTEXT_CURRENT_APP: <naam>"
     */
    fun logContextCurrentApp(appName: String?) {
        val name = if (!appName.isNullOrBlank()) appName else "none"
        val logLine = "CONTEXT_CURRENT_APP: $name"
        safeLog(Log.DEBUG, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    /**
     * Exact required format: "CONTEXT_USED: <true/false>"
     */
    fun logContextUsed(used: Boolean, details: String = "") {
        val status = if (used) "true" else "false"
        val extra = if (details.isNotBlank()) " ($details)" else ""
        val logLine = "CONTEXT_USED: $status$extra"
        safeLog(Log.DEBUG, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    /**
     * Exact required format: "WHATSAPP_NOTIFICATION_RECEIVED: id=<id>, isDuplicate=<true/false>"
     */
    fun logWhatsAppNotificationReceived(id: String, isDuplicate: Boolean) {
        val logLine = "WHATSAPP_NOTIFICATION_RECEIVED: id=$id, isDuplicate=$isDuplicate"
        safeLog(Log.INFO, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    /**
     * Exact required format: "WHATSAPP_REPLY_RATE_LIMIT_CHECK: allowed/blocked"
     */
    fun logWhatsAppRateLimitCheck(allowed: Boolean, details: String = "") {
        val status = if (allowed) "allowed" else "blocked"
        val extra = if (details.isNotBlank()) " ($details)" else ""
        val logLine = "WHATSAPP_REPLY_RATE_LIMIT_CHECK: $status$extra"
        if (allowed) {
            safeLog(Log.INFO, TAG, logLine)
        } else {
            safeLog(Log.WARN, TAG, logLine)
        }
        addEntry(logLine, LogType.INFO)
    }

    /**
     * Exact required format: "WHATSAPP_SELF_TRIGGER_IGNORED: true/false"
     */
    fun logWhatsAppSelfTriggerIgnored(ignored: Boolean, details: String = "") {
        val status = if (ignored) "true" else "false"
        val extra = if (details.isNotBlank()) " ($details)" else ""
        val logLine = "WHATSAPP_SELF_TRIGGER_IGNORED: $status$extra"
        safeLog(Log.INFO, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    /**
     * Exact required format: "WHATSAPP_MESSAGE_RECEIVED: sender=<naam>, text=<message>"
     */
    fun logWhatsAppMessageReceived(sender: String, text: String) {
        val logLine = "WHATSAPP_MESSAGE_RECEIVED: sender=$sender, text=$text"
        safeLog(Log.INFO, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    /**
     * Exact required format: "WHATSAPP_REPLY_GENERATED: <reply-text>"
     */
    fun logWhatsAppReplyGenerated(replyText: String) {
        val logLine = "WHATSAPP_REPLY_GENERATED: $replyText"
        safeLog(Log.INFO, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    /**
     * Exact required format: "WHATSAPP_REPLY_SENT: success/fail"
     */
    fun logWhatsAppReplySent(success: Boolean, details: String = "") {
        val status = if (success) "success" else "fail"
        val extra = if (details.isNotBlank()) " ($details)" else ""
        val logLine = "WHATSAPP_REPLY_SENT: $status$extra"
        if (success) {
            safeLog(Log.INFO, TAG, logLine)
        } else {
            safeLog(Log.ERROR, TAG, logLine)
        }
        addEntry(logLine, LogType.INFO)
    }

    /**
     * Exact required format: "REMINDER_SET: time=<time>, task=<text>"
     */
    fun logReminderSet(time: String, task: String) {
        val logLine = "REMINDER_SET: time=$time, task=$task"
        safeLog(Log.INFO, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    /**
     * Exact required format: "REMINDER_TRIGGERED: task=<text>"
     */
    fun logReminderTriggered(task: String) {
        val logLine = "REMINDER_TRIGGERED: task=$task"
        safeLog(Log.INFO, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    /**
     * Exact required format: "WEATHER_LOCATION: lat=<>, lon=<>"
     */
    fun logWeatherLocation(lat: Double, lon: Double) {
        val logLine = "WEATHER_LOCATION: lat=$lat, lon=$lon"
        safeLog(Log.INFO, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    /**
     * Exact required format: "WEATHER_API_CALL: success/fail"
     */
    fun logWeatherApiCall(success: Boolean, details: String = "") {
        val status = if (success) "success" else "fail"
        val extra = if (details.isNotBlank()) " ($details)" else ""
        val logLine = "WEATHER_API_CALL: $status$extra"
        if (success) {
            safeLog(Log.INFO, TAG, logLine)
        } else {
            safeLog(Log.ERROR, TAG, logLine)
        }
        addEntry(logLine, LogType.INFO)
    }

    /**
     * Exact required format: "CAMERA_CAPTURE: type=<selfie/back>, result=success/fail"
     */
    fun logCameraCapture(type: String, success: Boolean, details: String = "") {
        val result = if (success) "success" else "fail"
        val extra = if (details.isNotBlank()) " ($details)" else ""
        val logLine = "CAMERA_CAPTURE: type=$type, result=$result$extra"
        if (success) {
            safeLog(Log.INFO, TAG, logLine)
        } else if (details.contains("permission", ignoreCase = true)) {
            safeLog(Log.WARN, TAG, logLine)
        } else {
            safeLog(Log.ERROR, TAG, logLine)
        }
        addEntry(logLine, LogType.INFO)
    }

    /**
     * Exact required format: "SCENE_ANALYSIS: gemini_response=<summary>"
     */
    fun logSceneAnalysis(summary: String) {
        val logLine = "SCENE_ANALYSIS: gemini_response=$summary"
        safeLog(Log.INFO, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    /**
     * Exact required format: "DEVICE_ADMIN_STATUS: enabled/disabled"
     */
    fun logDeviceAdminStatus(enabled: Boolean) {
        val status = if (enabled) "enabled" else "disabled"
        val logLine = "DEVICE_ADMIN_STATUS: $status"
        safeLog(Log.INFO, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    /**
     * Exact required format: "WRONG_PASSWORD_DETECTED: true"
     */
    fun logWrongPasswordDetected() {
        val logLine = "WRONG_PASSWORD_DETECTED: true"
        safeLog(Log.WARN, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    /**
     * Exact required format: "PASSWORD_FAIL_CALLBACK_COUNT: <count>"
     */
    fun logPasswordFailCallbackCount(count: Int) {
        val logLine = "PASSWORD_FAIL_CALLBACK_COUNT: $count"
        safeLog(Log.INFO, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    /**
     * Exact required format: "THEFT_PHOTO_CAPTURED: success/fail"
     */
    fun logTheftPhotoCaptured(success: Boolean, details: String = "") {
        val status = if (success) "success" else "fail"
        val extra = if (details.isNotBlank()) " ($details)" else ""
        val logLine = "THEFT_PHOTO_CAPTURED: $status$extra"
        if (success) {
            safeLog(Log.INFO, TAG, logLine)
        } else {
            safeLog(Log.WARN, TAG, logLine)
        }
        addEntry(logLine, LogType.INFO)
    }

    /**
     * Exact required format: "THEFT_ALERT_SENT: success/fail"
     */
    fun logTheftAlertSent(success: Boolean, details: String = "") {
        val status = if (success) "success" else "fail"
        val extra = if (details.isNotBlank()) " ($details)" else ""
        val logLine = "THEFT_ALERT_SENT: $status$extra"
        if (success) {
            safeLog(Log.INFO, TAG, logLine)
        } else {
            safeLog(Log.WARN, TAG, logLine)
        }
        addEntry(logLine, LogType.INFO)
    }

    /**
     * Exact required format: "CALL_INCOMING: caller=<naam/number>"
     */
    fun logCallIncoming(caller: String) {
        val logLine = "CALL_INCOMING: caller=$caller"
        safeLog(Log.INFO, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    /**
     * Exact required format: "CALL_ANNOUNCE: TTS spoken"
     */
    fun logCallAnnounce() {
        val logLine = "CALL_ANNOUNCE: TTS spoken"
        safeLog(Log.INFO, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    /**
     * Exact required format: "CALL_VOICE_COMMAND: <utha lo/katt do>, action=<answered/rejected>"
     */
    fun logCallVoiceCommand(command: String, action: String) {
        val logLine = "CALL_VOICE_COMMAND: $command, action=$action"
        safeLog(Log.INFO, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    /**
     * Exact required format: "MESSAGE_TARGET_APP: <app-naam>"
     */
    fun logMessageTargetApp(appName: String) {
        val logLine = "MESSAGE_TARGET_APP: $appName"
        safeLog(Log.INFO, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    /**
     * Exact required format: "CONTACT_LOOKUP: <naam/number>, found=<bool>"
     */
    fun logContactLookup(nameOrNumber: String, found: Boolean) {
        val logLine = "CONTACT_LOOKUP: $nameOrNumber, found=$found"
        safeLog(Log.INFO, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    /**
     * Exact required format: "CONTACT_AUTO_SAVED: <number>"
     */
    fun logContactAutoSaved(number: String) {
        val logLine = "CONTACT_AUTO_SAVED: $number"
        safeLog(Log.INFO, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    /**
     * Exact required format: "MESSAGE_SENT: app=<>, success/fail"
     */
    fun logMessageSent(appName: String, success: Boolean, details: String = "") {
        val status = if (success) "success" else "fail"
        val extra = if (details.isNotBlank()) " ($details)" else ""
        val logLine = "MESSAGE_SENT: app=$appName, $status$extra"
        if (success) {
            safeLog(Log.INFO, TAG, logLine)
        } else {
            safeLog(Log.WARN, TAG, logLine)
        }
        addEntry(logLine, LogType.INFO)
    }

    /**
     * Exact required format: "MEDIA_COMMAND: <command>, target_app=<active-app-from-context>, action=<play/pause/next/previous>"
     */
    fun logMediaCommand(command: String, targetApp: String, action: String) {
        val logLine = "MEDIA_COMMAND: $command, target_app=$targetApp, action=$action"
        safeLog(Log.INFO, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    /**
     * Exact required format: "STT_RAW_TEXT: <jo bhi voice-se-text convert hua, exact>"
     */
    fun logSttRawText(rawText: String) {
        val logLine = "STT_RAW_TEXT: $rawText"
        safeLog(Log.INFO, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    /**
     * Exact required format: "COMMAND_ROUTER_CLASSIFICATION: <OFFLINE_TASK / SCREEN_TASK / CONVERSATION>"
     */
    fun logCommandRouterClassification(classification: String) {
        val logLine = "COMMAND_ROUTER_CLASSIFICATION: $classification"
        safeLog(Log.INFO, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    /**
     * Exact required format: "GEMINI_REQUEST_SENT: <true/false>, payload=<kya bheja gaya>"
     */
    fun logGeminiRequestSent(sent: Boolean, payload: String) {
        val logLine = "GEMINI_REQUEST_SENT: $sent, payload=$payload"
        safeLog(Log.INFO, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    /**
     * Exact required format: "GEMINI_RESPONSE_RECEIVED: <true/false>, raw_response=<jo bhi mila>"
     */
    fun logGeminiResponseReceived(received: Boolean, rawResponse: String) {
        val logLine = "GEMINI_RESPONSE_RECEIVED: $received, raw_response=$rawResponse"
        safeLog(Log.INFO, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    /**
     * Exact required format: "TTS_SPEAK_CALLED: <true/false>, text=<kya bola gaya>"
     */
    fun logTtsSpeakCalled(called: Boolean, text: String) {
        val logLine = "TTS_SPEAK_CALLED: $called, text=$text"
        safeLog(Log.INFO, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    /**
     * Exact required format: "API_KEY_VALIDATION_ATTEMPT: true"
     */
    fun logApiKeyValidationAttempt() {
        val logLine = "API_KEY_VALIDATION_ATTEMPT: true"
        safeLog(Log.INFO, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    /**
     * Exact required format: "API_KEY_VALIDATION_RESULT: valid/invalid, error=<agar koi ho>"
     */
    fun logApiKeyValidationResult(isValid: Boolean, error: String = "") {
        val logLine = if (isValid) {
            "API_KEY_VALIDATION_RESULT: valid"
        } else {
            "API_KEY_VALIDATION_RESULT: invalid, error=${if (error.isNotBlank()) error else "Yeh API key invalid hai, sahi key daaliye"}"
        }
        if (isValid) {
            safeLog(Log.INFO, TAG, logLine)
        } else {
            safeLog(Log.WARN, TAG, logLine)
        }
        addEntry(logLine, LogType.INFO)
    }

    /**
     * Exact required format: "API_KEY_READ_ATTEMPT: location=<kaha se padhi ja rahi hai>, found=<true/false>"
     */
    fun logApiKeyReadAttempt(location: String, found: Boolean) {
        val logLine = "API_KEY_READ_ATTEMPT: location=$location, found=$found"
        safeLog(if (found) Log.INFO else Log.WARN, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    /**
     * Exact required format: "TTS_GATE_CHECK: voice_profile_exists=<true/false>, caller=<kaunsa feature>, action=<speak/skip>"
     */
    fun logTtsGateCheck(profileExists: Boolean, caller: String, action: String) {
        val logLine = "TTS_GATE_CHECK: voice_profile_exists=$profileExists, caller=$caller, action=$action"
        safeLog(if (action == "speak") Log.INFO else Log.WARN, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    fun logOfflineCloneStatus(enabled: Boolean, hasSample: Boolean, engine: String) {
        val logLine = "OFFLINE_CLONE_STATUS: enabled=$enabled, sample=$hasSample, engine=$engine"
        safeLog(Log.INFO, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    fun logOfflineCloneSynthesis(success: Boolean, details: String = "") {
        val logLine = "OFFLINE_CLONE_SYNTHESIS: success=$success${if (details.isNotBlank()) " ($details)" else ""}"
        safeLog(if (success) Log.INFO else Log.WARN, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    /**
     * Exact required format: "WAKE_PHRASE_DETECTED: <phrase>"
     */
    fun logWakePhraseDetected(phrase: String) {
        val logLine = "WAKE_PHRASE_DETECTED: $phrase"
        safeLog(Log.INFO, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    /**
     * Exact required format: "VOICE_VERIFICATION: match=<true/false>, confidence=<score>"
     */
    fun logVoiceVerification(match: Boolean, confidence: Float) {
        val formattedConfidence = String.format(java.util.Locale.US, "%.2f", confidence)
        val logLine = "VOICE_VERIFICATION: match=$match, confidence=$formattedConfidence"
        safeLog(if (match) Log.INFO else Log.WARN, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    /**
     * Exact required format: "EMBEDDING_FILE_EXISTS: <true/false>"
     */
    fun logEmbeddingFileExists(exists: Boolean) {
        val logLine = "EMBEDDING_FILE_EXISTS: $exists"
        safeLog(if (exists) Log.INFO else Log.WARN, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    /**
     * Exact required format: "NEW_AUDIO_EMBEDDING_EXTRACTED: <true/false>"
     */
    fun logNewAudioEmbeddingExtracted(extracted: Boolean) {
        val logLine = "NEW_AUDIO_EMBEDDING_EXTRACTED: $extracted"
        safeLog(if (extracted) Log.INFO else Log.WARN, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    /**
     * Exact required format: "SIMILARITY_SCORE: <exact numeric value, NA hardcoded>"
     */
    fun logSimilarityScore(score: Float) {
        val formatted = String.format(java.util.Locale.US, "%.3f", score)
        val logLine = "SIMILARITY_SCORE: $formatted"
        safeLog(Log.INFO, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    /**
     * Exact required format: "VERIFICATION_RESULT: <threshold ke against pass/fail>"
     */
    fun logVerificationResult(passed: Boolean) {
        val status = if (passed) "pass" else "fail"
        val logLine = "VERIFICATION_RESULT: $status"
        safeLog(if (passed) Log.INFO else Log.WARN, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    // =========================================================================
    // API KEY SOURCE & TTS PATH VERIFICATION DEBUG LOGS (EXACT FORMAT)
    // =========================================================================

    /**
     * Exact required format:
     * "API_KEY_SOURCE_CHECK: feature=<kaunsa feature call kar raha hai>, key_found=<true/false>, source=<kahan se padhi>"
     */
    fun logApiKeySourceCheck(feature: String, keyFound: Boolean, source: String) {
        val logLine = "API_KEY_SOURCE_CHECK: feature=$feature, key_found=$keyFound, source=$source"
        safeLog(if (keyFound) Log.INFO else Log.WARN, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    /**
     * Exact required format:
     * "TTS_CALL_PATH: feature=<kaunsa feature bol raha hai>, used_central_gate=<true/false>, voice_used=<cloned/default>"
     */
    fun logTtsCallPath(feature: String, usedCentralGate: Boolean, voiceUsed: String) {
        val logLine = "TTS_CALL_PATH: feature=$feature, used_central_gate=$usedCentralGate, voice_used=$voiceUsed"
        safeLog(Log.INFO, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    /**
     * Exact required format:
     * "TTS_SYNTHESIS_METHOD: <CLONETTS_REAL_SERVER / PITCH_SHIFT_FAKE>"
     */
    fun logTtsSynthesisMethod(method: String) {
        val logLine = "TTS_SYNTHESIS_METHOD: $method"
        safeLog(Log.INFO, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    /**
     * Exact required format:
     * "CLONETTS_SERVER_STATUS: <running/not-started/failed>"
     */
    fun logCloneTtsServerStatus(status: String) {
        val logLine = "CLONETTS_SERVER_STATUS: $status"
        safeLog(Log.INFO, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    /**
     * Exact required format:
     * "CLONETTS_SYNTHESIS_ATTEMPT: success/fail, error=<exact reason>"
     */
    fun logCloneTtsSynthesisAttempt(success: Boolean, error: String = "none") {
        val status = if (success) "success" else "fail"
        val logLine = "CLONETTS_SYNTHESIS_ATTEMPT: $status, error=$error"
        safeLog(if (success) Log.INFO else Log.WARN, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    // =========================================================================
    // GEMINI CONVERSATION PIPELINE DEBUG LOGS (5 EXACT REQUIRED FORMATS)
    // =========================================================================

    /**
     * 1. Exact format: "GEMINI_REQUEST_PAYLOAD: <exact kya text/prompt Gemini ko bheja gaya>"
     */
    fun logGeminiRequestPayload(payload: String) {
        val logLine = "GEMINI_REQUEST_PAYLOAD: $payload"
        safeLog(Log.INFO, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    /**
     * 2. Exact format: "GEMINI_API_CALL_STATUS: <success/fail, HTTP-status-code>"
     */
    fun logGeminiApiCallStatus(status: String) {
        val logLine = "GEMINI_API_CALL_STATUS: $status"
        safeLog(Log.INFO, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    fun logGeminiApiCallStatus(success: Boolean, statusCode: Int) {
        val status = if (success) "success, $statusCode" else "fail, $statusCode"
        logGeminiApiCallStatus(status)
    }

    /**
     * 3. Exact format: "GEMINI_RAW_RESPONSE: <poora raw response jo Gemini se wapas aaya, ya agar fail hua to EXACT error-message>"
     */
    fun logGeminiRawResponse(rawResponse: String) {
        val logLine = "GEMINI_RAW_RESPONSE: $rawResponse"
        safeLog(Log.INFO, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    /**
     * 4. Exact format: "GEMINI_RESPONSE_PARSED: <jo response app ne nikaala/samjha, parse karne ke baad>"
     */
    fun logGeminiResponseParsed(parsedResponse: String) {
        val logLine = "GEMINI_RESPONSE_PARSED: $parsedResponse"
        safeLog(Log.INFO, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    /**
     * 5. Exact format: "FALLBACK_TRIGGERED: <true/false>, reason=<agar fallback/echo-response use hua to EXACT wajah kyun>"
     */
    fun logFallbackTriggered(triggered: Boolean, reason: String) {
        val logLine = "FALLBACK_TRIGGERED: $triggered, reason=$reason"
        safeLog(if (triggered) Log.WARN else Log.INFO, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    // =========================================================================
    // WAKE-WORD SYSTEM HEALTH & DIAGNOSTIC DEBUG LOGS (EXACT FORMAT)
    // =========================================================================

    /**
     * 1. Exact format: "WAKEWORD_SERVICE_STARTED: <true/false>"
     */
    fun logWakeWordServiceStarted(started: Boolean) {
        val logLine = "WAKEWORD_SERVICE_STARTED: $started"
        safeLog(if (started) Log.INFO else Log.WARN, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    /**
     * 2. Exact format: "WAKEWORD_SERVICE_RUNNING: <true/false>, timestamp=<>"
     */
    fun logWakeWordServiceRunning(running: Boolean, timestamp: String = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())) {
        val logLine = "WAKEWORD_SERVICE_RUNNING: $running, timestamp=$timestamp"
        safeLog(Log.DEBUG, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    /**
     * 3. Exact format: "AUDIO_PERMISSION_STATUS: <granted/denied>"
     */
    fun logAudioPermissionStatus(granted: Boolean) {
        val status = if (granted) "granted" else "denied"
        val logLine = "AUDIO_PERMISSION_STATUS: $status"
        safeLog(if (granted) Log.INFO else Log.WARN, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    /**
     * 4. Exact format: "MIC_STREAM_ACTIVE: <true/false>"
     */
    fun logMicStreamActive(active: Boolean) {
        val logLine = "MIC_STREAM_ACTIVE: $active"
        safeLog(if (active) Log.INFO else Log.WARN, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    /**
     * 5. Exact format: "WAKEWORD_MODEL_LOADED: <true/false>, error=<agar koi ho>"
     */
    fun logWakeWordModelLoaded(loaded: Boolean, error: String = "none") {
        val logLine = "WAKEWORD_MODEL_LOADED: $loaded, error=$error"
        safeLog(if (loaded) Log.INFO else Log.WARN, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    /**
     * 6. Exact format: "WAKEWORD_DETECTION_ATTEMPT: <details>"
     */
    fun logWakeWordDetectionAttempt(details: String = "evaluating_audio_frame") {
        val logLine = "WAKEWORD_DETECTION_ATTEMPT: $details"
        safeLog(Log.DEBUG, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    /**
     * 7. Exact format: "BATTERY_OPTIMIZATION_STATUS: <exempted/not-exempted>"
     */
    fun logBatteryOptimizationStatus(exempted: Boolean) {
        val status = if (exempted) "exempted" else "not-exempted"
        val logLine = "BATTERY_OPTIMIZATION_STATUS: $status"
        safeLog(if (exempted) Log.INFO else Log.WARN, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    /**
     * Exact format: "MIC_PERMISSION_GRANTED: <bool>"
     */
    fun logMicPermissionGranted(granted: Boolean) {
        val logLine = "MIC_PERMISSION_GRANTED: $granted"
        safeLog(if (granted) Log.INFO else Log.WARN, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    /**
     * Exact format: "AUDIORECORD_STATE: <initialized/recording/error>"
     */
    fun logAudioRecordState(state: String) {
        val logLine = "AUDIORECORD_STATE: $state"
        safeLog(if (state == "recording") Log.INFO else Log.WARN, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    /**
     * Exact format: "AUDIO_SOURCE_CONFLICT: <true/false, agar koi aur app mic use kar raha ho>"
     */
    fun logAudioSourceConflict(conflict: Boolean, details: String? = null) {
        val logLine = if (details != null) "AUDIO_SOURCE_CONFLICT: $conflict, $details" else "AUDIO_SOURCE_CONFLICT: $conflict"
        safeLog(if (conflict) Log.WARN else Log.INFO, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    // =========================================================================
    // OWNER-VOICE-ENROLLMENT DIAGNOSTIC DEBUG LOGS (5 EXACT REQUIRED FORMATS)
    // =========================================================================

    /**
     * 1. Exact format: "ENROLLMENT_RECORD_STARTED: <true/false>, sample_number=<kaunsa sample, 1-5>"
     */
    fun logEnrollmentRecordStarted(started: Boolean, sampleNumber: Int) {
        val logLine = "ENROLLMENT_RECORD_STARTED: $started, sample_number=$sampleNumber"
        safeLog(if (started) Log.INFO else Log.WARN, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    /**
     * 2. Exact format: "ENROLLMENT_AUDIO_CAPTURED: <true/false>, duration=<sec>, rms_level=<>"
     */
    fun logEnrollmentAudioCaptured(captured: Boolean, duration: String, rmsLevel: Int) {
        val logLine = "ENROLLMENT_AUDIO_CAPTURED: $captured, duration=$duration, rms_level=$rmsLevel"
        safeLog(if (captured) Log.INFO else Log.WARN, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    /**
     * 3. Exact format: "ENROLLMENT_EMBEDDING_EXTRACTED: <true/false>, error=<agar fail ho to exact reason>"
     */
    fun logEnrollmentEmbeddingExtracted(extracted: Boolean, error: String = "none") {
        val logLine = "ENROLLMENT_EMBEDDING_EXTRACTED: $extracted, error=$error"
        safeLog(if (extracted) Log.INFO else Log.WARN, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    /**
     * 4. Exact format: "ENROLLMENT_PROFILE_SAVED: <true/false>, file_path=<>, file_size=<>"
     */
    fun logEnrollmentProfileSaved(saved: Boolean, filePath: String, fileSize: Long) {
        val logLine = "ENROLLMENT_PROFILE_SAVED: $saved, file_path=$filePath, file_size=$fileSize"
        safeLog(if (saved) Log.INFO else Log.WARN, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    /**
     * 5. Exact format: "ENROLLMENT_VALIDATION_RESULT: <valid/invalid>, reason=<exact wajah agar invalid>"
     */
    fun logEnrollmentValidationResult(valid: Boolean, reason: String = "none") {
        val status = if (valid) "valid" else "invalid"
        val logLine = "ENROLLMENT_VALIDATION_RESULT: $status, reason=$reason"
        safeLog(if (valid) Log.INFO else Log.WARN, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    fun logInfo(msg: String) {
        val logLine = "INFO: $msg"
        safeLog(Log.DEBUG, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }
}
