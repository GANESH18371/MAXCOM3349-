package com.example.manager

import android.content.Context
import android.content.Intent
import com.example.util.DebugLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

sealed class InteractionRecord {
    data class AppOpened(
        val app: InstalledApp,
        val timestamp: Long = System.currentTimeMillis()
    ) : InteractionRecord()

    data class HardwareToggled(
        val feature: HardwareFeature,
        val actionDescription: String,
        val targetState: Boolean? = null,
        val timestamp: Long = System.currentTimeMillis()
    ) : InteractionRecord()
}

data class ContextState(
    val currentActiveApp: InstalledApp? = null,
    val lastHardwareAction: InteractionRecord.HardwareToggled? = null,
    val recentInteractions: List<InteractionRecord> = emptyList()
)

sealed class ContextResolutionResult {
    data class ResolvedHardware(
        val feature: HardwareFeature,
        val targetState: Boolean?,
        val description: String
    ) : ContextResolutionResult()

    data class ResolvedVolume(
        val parsed: VolumeCommandParsed,
        val description: String
    ) : ContextResolutionResult()

    data class ResolvedAppOpen(
        val app: InstalledApp,
        val description: String
    ) : ContextResolutionResult()

    data class ResolvedAppClose(
        val app: InstalledApp,
        val description: String
    ) : ContextResolutionResult()

    data class Ambiguous(
        val message: String = "kiska matlab hai? Please specify."
    ) : ContextResolutionResult()

    object NoReference : ContextResolutionResult()
}

object AppContextManager {
    private const val MAX_HISTORY = 20

    private val _contextState = MutableStateFlow(ContextState())
    val contextState: StateFlow<ContextState> = _contextState.asStateFlow()

    // Referring pronouns and ambiguous reference markers
    private val REFERRING_KEYWORDS = listOf(
        // Devanagari Hindi pronouns
        "इसका", "इसकी", "इसके", "इसको", "इसे", "इस",
        "यह वाला", "ये वाला", "यह वाली", "ये वाली", "यह वाले", "ये वाले",
        "यह", "ये",
        "उसका", "उसकी", "उसके", "उसको", "उसे", "उस",
        "वह वाला", "वो वाला", "वह वाली", "वो वाली",
        "वही", "वो", "वह", "उसी", "इसी", "उसी को", "इसी को",
        "पिछला", "पिछली", "पिछले", "दोबारा",

        // Hinglish / Roman Hindi
        "iska", "iski", "iske", "isko", "ise", "is",
        "yeh wala", "ye wala", "yeh wali", "ye wali", "ye waala",
        "yeh", "ye",
        "uska", "uski", "uske", "usko", "use", "us",
        "woh wala", "wo wala", "woh wali", "wo wali",
        "wahi", "woh", "wo", "usi", "isi", "usi ko", "isi ko",
        "pichla", "pichli", "pichhle", "dobara",

        // English pronouns / referring terms
        "this", "that", "it", "its", "this one", "that one", "the same", "previous", "again"
    )

    fun recordAppOpen(app: InstalledApp) {
        val currentList = _contextState.value.recentInteractions
        val newRecord = InteractionRecord.AppOpened(app)
        val updatedList = (listOf(newRecord) + currentList).take(MAX_HISTORY)

        _contextState.value = _contextState.value.copy(
            currentActiveApp = app,
            recentInteractions = updatedList
        )
        DebugLogger.logInfo("Context updated: Current App is '${app.name}'")
    }

    fun recordHardwareToggle(feature: HardwareFeature, actionDescription: String, targetState: Boolean? = null) {
        val currentList = _contextState.value.recentInteractions
        val newRecord = InteractionRecord.HardwareToggled(feature, actionDescription, targetState)
        val updatedList = (listOf(newRecord) + currentList).take(MAX_HISTORY)

        _contextState.value = _contextState.value.copy(
            lastHardwareAction = newRecord,
            recentInteractions = updatedList
        )
        DebugLogger.logInfo("Context updated: Last Toggle is '${feature.displayName}' ($actionDescription)")
    }

    fun getCurrentApp(): InstalledApp? = _contextState.value.currentActiveApp

    fun getLastHardwareAction(): InteractionRecord.HardwareToggled? = _contextState.value.lastHardwareAction

    fun getRecentInteractions(): List<InteractionRecord> = _contextState.value.recentInteractions

    fun clearMemory() {
        _contextState.value = ContextState()
    }

    /**
     * Checks if a command contains referring words like "iska", "usko", "wahi", "yeh wala", "it", etc.
     * or is an ambiguous action without explicit subject.
     */
    fun containsReferringWord(lowerCommand: String): Boolean {
        // Check for multi-word referring phrases first
        val multiWordPhrases = listOf(
            "yeh wala", "ye wala", "woh wala", "wo wala", "this one", "that one",
            "the same", "usi ko", "isi ko", "यह वाला", "ये वाला", "वह वाला", "वो वाला",
            "उसी को", "इसी को"
        )
        if (multiWordPhrases.any { lowerCommand.contains(it) }) {
            return true
        }

        // Check word tokens for single referring words
        val tokens = lowerCommand.split("\\s+".toRegex()).map { it.trim() }
        return tokens.any { token -> REFERRING_KEYWORDS.contains(token) }
    }

    /**
     * Resolves context for ambiguous / referring commands.
     * Evaluates context locally without any remote API.
     */
    fun resolveContext(rawCommand: String, isVolume: Boolean, hasHardwareName: Boolean): ContextResolutionResult {
        val lower = rawCommand.lowercase(Locale.getDefault()).trim()
        val hasReferring = containsReferringWord(lower)

        val turnOffWords = listOf("off", "band", "close", "disable", "stop", "बंद", "हटाओ", "rok", "bujhao", "बुझाओ")
        val isExplicitOff = turnOffWords.any { lower.contains(it) }

        val turnOnWords = listOf("on", "chalu", "open", "enable", "start", "चालू", "जलाओ", "kholo", "खोलो", "chalao", "चलाओ", "launch")
        val isExplicitOn = turnOnWords.any { lower.contains(it) }

        // Is this a naked/ambiguous action (e.g. "band karo", "off karo", "on karo", "chalu karo", "kholo", "chalao")
        val isBareAction = (isExplicitOff || isExplicitOn) && !hasHardwareName && !isVolume && rawCommand.split("\\s+".toRegex()).size <= 3

        if (!hasReferring && !isBareAction) {
            return ContextResolutionResult.NoReference
        }

        val state = _contextState.value
        val currentApp = state.currentActiveApp
        val lastHardware = state.lastHardwareAction
        val lastInteraction = state.recentInteractions.firstOrNull()

        // Case 1: Volume command with referring words (e.g. "iska volume badhao", "iski aawaz kam karo")
        // Volume is a system-level feature; context confirms active state.
        if (isVolume) {
            val parsed = HardwareToggleManager.parseVolumeCommand(lower)
            val desc = if (currentApp != null) "Volume adjusted for active app '${currentApp.name}'" else "System volume adjusted"
            return ContextResolutionResult.ResolvedVolume(parsed, desc)
        }

        // Case 2: User explicitly mentions a hardware feature (e.g. "iski torch on karo")
        if (hasHardwareName) {
            // Already has explicit feature name, so standard hardware handler will handle it
            return ContextResolutionResult.NoReference
        }

        // Case 3: Action like "isko band karo" / "ise off karo" / "turn it off" / "band karo"
        if (isExplicitOff) {
            // Check most recent interaction
            if (lastInteraction is InteractionRecord.HardwareToggled) {
                return ContextResolutionResult.ResolvedHardware(
                    feature = lastInteraction.feature,
                    targetState = false,
                    description = "Turned OFF ${lastInteraction.feature.displayName} (from recent toggle context)"
                )
            } else if (lastInteraction is InteractionRecord.AppOpened || currentApp != null) {
                val appToClose = (lastInteraction as? InteractionRecord.AppOpened)?.app ?: currentApp!!
                return ContextResolutionResult.ResolvedAppClose(
                    app = appToClose,
                    description = "Closed ${appToClose.name} (from active app context)"
                )
            } else if (lastHardware != null) {
                return ContextResolutionResult.ResolvedHardware(
                    feature = lastHardware.feature,
                    targetState = false,
                    description = "Turned OFF ${lastHardware.feature.displayName} (from last hardware context)"
                )
            } else {
                return ContextResolutionResult.Ambiguous("kiska matlab hai? Please specify.")
            }
        }

        // Case 4: Action like "isko chalu karo" / "ise on karo" / "wahi kholo" / "yeh wala open karo" / "open it"
        if (isExplicitOn) {
            val isOpenAppAction = listOf("kholo", "खोलो", "chalao", "चलाओ", "open", "launch", "wahi", "वही", "yeh wala").any { lower.contains(it) }

            if (isOpenAppAction && (currentApp != null || lastInteraction is InteractionRecord.AppOpened)) {
                val appToOpen = (lastInteraction as? InteractionRecord.AppOpened)?.app ?: currentApp!!
                return ContextResolutionResult.ResolvedAppOpen(
                    app = appToOpen,
                    description = "Opened ${appToOpen.name} (from active app context)"
                )
            } else if (lastInteraction is InteractionRecord.HardwareToggled) {
                return ContextResolutionResult.ResolvedHardware(
                    feature = lastInteraction.feature,
                    targetState = true,
                    description = "Turned ON ${lastInteraction.feature.displayName} (from recent toggle context)"
                )
            } else if (currentApp != null) {
                return ContextResolutionResult.ResolvedAppOpen(
                    app = currentApp,
                    description = "Opened ${currentApp.name} (from active app context)"
                )
            } else if (lastHardware != null) {
                return ContextResolutionResult.ResolvedHardware(
                    feature = lastHardware.feature,
                    targetState = true,
                    description = "Turned ON ${lastHardware.feature.displayName} (from last hardware context)"
                )
            } else {
                return ContextResolutionResult.Ambiguous("kiska matlab hai? Please specify.")
            }
        }

        // Case 5: Referring word used without clear action or no context
        if (currentApp != null) {
            return ContextResolutionResult.ResolvedAppOpen(
                app = currentApp,
                description = "Referenced active app '${currentApp.name}'"
            )
        }

        return ContextResolutionResult.Ambiguous("kiska matlab hai? Please specify.")
    }
}
