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
        Log.d(TAG, logLine)
        addEntry(logLine, LogType.MATCH)
    }

    /**
     * Exact required format: "APP_OPEN_LAUNCH: success/fail"
     */
    fun logLaunch(success: Boolean, details: String = "") {
        val status = if (success) "success${if (details.isNotBlank()) " ($details)" else ""}" else "fail${if (details.isNotBlank()) " ($details)" else ""}"
        val logLine = "APP_OPEN_LAUNCH: $status"
        if (success) {
            Log.i(TAG, logLine)
        } else {
            Log.e(TAG, logLine)
        }
        addEntry(logLine, LogType.LAUNCH)
    }

    /**
     * Exact required format: "TOGGLE_ATTEMPT: <name>, method=<DIRECT/ACCESSIBILITY>"
     */
    fun logToggleAttempt(name: String, method: ToggleMethod) {
        val logLine = "TOGGLE_ATTEMPT: $name, method=$method"
        Log.d(TAG, logLine)
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
            Log.i(TAG, logLine)
        } else {
            Log.e(TAG, logLine)
        }
        addEntry(logLine, LogType.TOGGLE_RESULT)
    }

    fun logInfo(msg: String) {
        val logLine = "INFO: $msg"
        Log.d(TAG, logLine)
        addEntry(logLine, LogType.INFO)
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }
}
