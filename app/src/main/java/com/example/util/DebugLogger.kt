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
            safeLog(Log.ERROR, TAG, logLine)
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
            safeLog(Log.ERROR, TAG, logLine)
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

    fun logInfo(msg: String) {
        val logLine = "INFO: $msg"
        safeLog(Log.DEBUG, TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }
}
