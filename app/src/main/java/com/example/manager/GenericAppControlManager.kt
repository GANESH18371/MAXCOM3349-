package com.example.manager

import android.content.Context
import android.util.Log
import com.example.service.MaxAccessibilityService
import com.example.util.DebugLogger
import com.example.util.TtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

data class ParsedMediaCommand(
    val action: String, // "play", "pause", "next", "previous", "volume_up", "volume_down"
    val query: String?,
    val targetApp: String, // from context or explicitly mentioned
    val isAppAlreadyActive: Boolean
)

object GenericAppControlManager {
    private const val TAG = "GenericAppControlMgr"
    private val scope = CoroutineScope(Dispatchers.Main)

    /**
     * Checks if the voice command matches a media control instruction
     */
    fun isMediaCommand(lower: String): Boolean {
        // Next commands
        if (lower.contains("agla wala") || lower.contains("agla gaana") || lower.contains("agla video") ||
            lower.contains("next song") || lower.contains("next video") || lower.contains("next track") ||
            lower.contains("next chalao") || lower.contains("next karo") || lower.trim() == "next"
        ) return true

        // Previous commands
        if (lower.contains("pichla wala") || lower.contains("pichla gaana") || lower.contains("pichla video") ||
            lower.contains("previous song") || lower.contains("previous video") || lower.contains("previous track") ||
            lower.contains("previous chalao") || lower.contains("previous karo") || lower.trim() == "previous"
        ) return true

        // Pause / stop commands
        if (lower.contains("pause karo") || lower.contains("pause kar do") || lower.contains("gaana pause") ||
            lower.contains("video pause") || lower.contains("gaana band") || lower.contains("video band") ||
            lower.contains("gaana roko") || lower.contains("video roko") || lower.contains("roko") ||
            lower.trim() == "pause" || lower.trim() == "band karo"
        ) return true

        // Play commands ("... chalao", "... play karo", "... bajao")
        if (lower.endsWith("chalao") || lower.endsWith("play karo") || lower.endsWith("bajao") ||
            lower.startsWith("play ") || lower.contains("chala do") || lower.contains("baja do")
        ) {
            // Avoid false positives like "torch chalao", "wifi chalao", "app kholo"
            val nonMedia = listOf("torch", "flashlight", "wifi", "bluetooth", "hotspot", "data", "brightness", "dnd", "airplane")
            if (nonMedia.any { lower.contains(it) }) return false
            return true
        }

        return false
    }

    /**
     * Parses the command and resolves target app from context or explicit mention
     */
    fun parseMediaCommand(rawText: String): ParsedMediaCommand {
        val lower = rawText.lowercase(Locale.getDefault())

        // 1. Identify Action
        val action = when {
            lower.contains("agla") || lower.contains("next") || lower.contains("skip") -> "next"
            lower.contains("pichla") || lower.contains("previous") || lower.contains("prev") -> "previous"
            lower.contains("pause") || lower.contains("band") || lower.contains("roko") || lower.contains("stop") -> "pause"
            else -> "play"
        }

        // 2. Identify Target App
        // Check if an explicit media app is mentioned (YouTube, Spotify, Gaana, JioSaavn, Wynk)
        val explicitApp: String? = when {
            lower.contains("spotify") -> "Spotify"
            lower.contains("youtube") || lower.contains("yt") -> "YouTube"
            lower.contains("gaana") || lower.contains("गाना ऐप") -> "Gaana"
            lower.contains("jiosaavn") || lower.contains("saavn") -> "JioSaavn"
            lower.contains("wynk") -> "Wynk"
            else -> null
        }

        // If no explicit app, check active app from AppContextManager
        val activeApp = AppContextManager.getCurrentApp()
        val targetAppName: String
        val isAlreadyActive: Boolean

        if (explicitApp != null) {
            targetAppName = explicitApp
            isAlreadyActive = activeApp?.name?.contains(explicitApp, ignoreCase = true) == true
        } else if (activeApp != null) {
            // Use active app from context directly!
            targetAppName = activeApp.name
            isAlreadyActive = true
        } else {
            // Default to YouTube
            targetAppName = "YouTube"
            isAlreadyActive = false
        }

        // 3. Extract Query (for Play commands)
        var query: String? = null
        if (action == "play") {
            var cleaned = rawText
            val appKeywords = listOf("youtube par", "youtube pe", "spotify par", "spotify pe", "gaana par", "gaana pe")
            for (kw in appKeywords) {
                cleaned = cleaned.replace(Regex("(?i)\\b$kw\\b"), "")
            }
            cleaned = cleaned.replace(Regex("(?i)\\b(chalao|play karo|bajao|baja do|chala do|play|song|gaana|video)\\b"), "").trim()
            if (cleaned.isNotBlank()) {
                query = cleaned
            }
        }

        return ParsedMediaCommand(
            action = action,
            query = query,
            targetApp = targetAppName,
            isAppAlreadyActive = isAlreadyActive
        )
    }

    /**
     * Executes the media command via Generic App-Control
     * Emits Debug Log: "MEDIA_COMMAND: <command>, target_app=<active-app-from-context>, action=<play/pause/next/previous>"
     */
    fun executeMediaFlow(
        context: Context,
        commandText: String,
        onComplete: (Boolean, String) -> Unit
    ) {
        val parsed = parseMediaCommand(commandText)

        // Exact Required Debug Log:
        // "MEDIA_COMMAND: <command>, target_app=<active-app-from-context>, action=<play/pause/next/previous>"
        DebugLogger.logMediaCommand(
            command = commandText,
            targetApp = parsed.targetApp,
            action = parsed.action
        )

        scope.launch {
            try {
                // If app is not currently active, launch it first
                if (!parsed.isAppAlreadyActive) {
                    val installedApps = AppOpenManager.getFreshInstalledApps(context)
                    val targetAppObj = AppOpenManager.fuzzyMatchApp(parsed.targetApp, installedApps)
                        ?: installedApps.find { it.name.contains(parsed.targetApp, ignoreCase = true) }
                        ?: InstalledApp(parsed.targetApp, "com.google.android.youtube")

                    val didLaunch = AppOpenManager.launchApp(context, targetAppObj)
                    if (didLaunch) {
                        AppContextManager.recordAppOpen(targetAppObj)
                        delay(900) // Allow media app UI to settle
                    }
                }

                // Execute via Generic Accessibility Control
                val accessibilityActive = MaxAccessibilityService.isRunning()
                if (accessibilityActive) {
                    MaxAccessibilityService.instance?.executeGenericMediaAction(
                        action = parsed.action,
                        query = parsed.query
                    ) { success, details ->
                        val spoken = when (parsed.action) {
                            "pause" -> "Pause kar diya"
                            "next" -> "Agla gaana chala diya"
                            "previous" -> "Pichla gaana chala diya"
                            "play" -> if (!parsed.query.isNullOrBlank()) "${parsed.query} chala diya" else "Play kar diya"
                            else -> details
                        }
                        TtsManager.speak(spoken)
                        onComplete(success, spoken)
                    }
                } else {
                    // Accessibility fallback: open media app directly and confirm
                    val fallbackMsg = "${parsed.targetApp} par ${parsed.action} request bhej di gayi"
                    TtsManager.speak(fallbackMsg)
                    onComplete(true, fallbackMsg)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing generic media flow", e)
                val errMsg = "Media control me samasya aayi"
                TtsManager.speak(errMsg)
                onComplete(false, errMsg)
            }
        }
    }
}
