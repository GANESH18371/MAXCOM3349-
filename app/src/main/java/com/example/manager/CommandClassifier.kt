package com.example.manager

import java.util.Locale

/**
 * Result of user intent classification: TASK vs CONVERSATION
 */
data class ClassificationResult(
    val isTask: Boolean,
    val classifiedAs: String, // "TASK" or "CONVERSATION"
    val confidence: String,
    val falsePositiveRisk: String,
    val taskType: String? = null // "HARDWARE_TOGGLE", "APP_OPEN", "WEATHER", "REMINDER", "CAMERA", "WHATSAPP_AUTOREPLY", "COMPOUND_TASK", etc.
)

object CommandClassifier {

    // Hardware toggle target keywords (English + Hindi/Devanagari) sorted by specificity
    private val HARDWARE_KEYWORDS = listOf(
        "do not disturb", "डू नॉट डिस्टर्ब",
        "flight mode", "हवाई मोड", "aeroplane", "airplane",
        "mobile data", "cellular", "हॉटस्पॉट", "hotspot", "tethering",
        "flashlight", "फ्लैशलाइट", "screen light", "स्क्रीन लाइट",
        "brightness", "ब्राइटनेस", "रोशनी", "चमक", "chamak",
        "bluetooth", "ब्लूटूथ",
        "wi-fi", "wifi", "वाई-फाई", "वाईफाई", "wlan",
        "torch", "टॉर्च", "flash", "light", "लाइट",
        "volume", "वॉल्यूम", "वोल्यूम", "sound", "audio", "साउंड", "ऑडियो",
        "aawaz", "awaaz", "aawaaj", "awaj", "awaz", "आवाज़", "आवाज",
        "dnd", "data", "डेटा", "net", "vol", "bt"
    )

    // Hardware toggle imperative action verbs (explicit intent to modify state)
    private val HARDWARE_ACTION_VERBS = listOf(
        // On verbs
        "on karo", "on kar do", "on kar", "turn on", "switch on", "chalu karo", "chalu kar do", "chalu kar",
        "jala do", "jalao", "jalayein", "चालू करो", "चालू कर", "जलाओ", "ऑन करो", "ऑन", "enable", "start",
        // Off verbs
        "off karo", "off kar do", "off kar", "turn off", "switch off", "band karo", "band kar do", "band kar",
        "bujha do", "bujhao", "bujhayein", "बंद करो", "बंद कर", "बुझाओ", "ऑफ करो", "disable", "stop", "rok do",
        // Volume adjustment verbs
        "badhao", "badha do", "badha", "tez karo", "tez", "up", "louder", "बढ़ाओ", "तेज़",
        "kam karo", "kam kar do", "kam", "dheere karo", "dheeme karo", "dheere", "dheeme", "down", "quieter", "softer", "कम करो", "धीमे",
        "mute", "unmute", "chup", "chup karo", "shant", "शांत", "म्यूट", "अनम्यूट",
        // Brightness adjustments
        "chamak kam", "chamak badhao", "roshni kam", "roshni badhao",
        // Percentage / level
        "percent", "%", "प्रतिशत",
        // Direct toggle
        "toggle karo", "toggle kar do", "toggle"
    )

    // App launch imperative verbs
    private val APP_LAUNCH_VERBS = listOf(
        "kholo", "khol", "kholiye", "khol do", "khol de", "khol dena", "khol dijiye",
        "खोलो", "खोल", "खोलिए", "खोल दो", "खोल दे", "खोल देना", "खोल दीजिए",
        "open", "launch", "start", "run",
        "ओपन", "स्टार्ट", "लॉन्च", "रन",
        "chalao", "chala", "chala do", "chala de", "chala dena", "chala dijiye",
        "चलाओ", "चला", "चलाइए", "चला दो", "चला दे",
        "chalu karo", "chalu kar", "chalu kar do", "चालू करो", "चालू कर"
    )

    // Narrative, past-tense, and storytelling markers indicating conversational context
    private val CONVERSATIONAL_STORY_MARKERS = listOf(
        "dekha tha", "dekhi thi", "dekhe the", "suna tha", "suni thi", "sune the",
        "hua tha", "hui thi", "hue the", "gaya tha", "gayi thi", "gaye the",
        "aaya tha", "aayi thi", "aaye the", "use kiya tha", "use kiya", "use kar raha tha",
        "use kar rahi thi", "kharida tha", "khareeda tha", "liya tha", "li thi",
        "chal raha tha", "chal rahi thi", "baat kar raha tha", "baat kar rahi thi",
        "bol raha tha", "bol rahi thi", "ho gaya tha", "ho gayi thi", "padha tha",
        "dekh raha tha", "dekh rahi thi", "sun raha tha", "soch raha tha", "soch rahi thi"
    )

    // Conversational feeling, chitchat, compliment, and opinion markers
    private val CONVERSATIONAL_CHITCHAT_MARKERS = listOf(
        "lagta hai", "lagti hai", "kaisa laga", "kaisi lagi", "achha hai", "acchi hai",
        "accha hai", "bura hai", "mast hai", "pasand hai", "pyaari hai", "pyaara hai",
        "neend nahi aa rahi", "boring lag raha", "kya haal", "kaise ho", "kaisi ho",
        "kuch batao", "kahani sunao", "chutkula sunao", "dost ho", "meri baat suno",
        "kya chal raha hai", "tum kon ho", "tum kaun ho", "theek se connect nahi hota",
        "theek nahi chal raha", "kharab ho gaya", "kaise kaam karta hai", "kya feature hai",
        "aaj ka din", "din kaisa raha", "kaise ho bhai", "kya haal chal"
    )

    // Informational question markers (asking what/how/why/when without imperative action command)
    private val INFORMATIONAL_QUESTION_MARKERS = listOf(
        "kya hota hai", "kaise karte hain", "kaise hota hai", "kyun hota hai", "kab aayega",
        "kahan milega", "password kya hai", "kitne ka hai", "kaise chalega", "kaise bhejte hain",
        "kya difference hai", "kya farak hai", "kaun sa acha hai", "kaun sa achha hai",
        "kaise use karein", "kaise use karte hain", "kaise connect karein"
    )

    /**
     * Strict and fast local intent classification.
     * Evaluates in < 1ms whether an utterance has genuine action-intent or is casual conversation.
     */
    fun classify(rawInput: String, installedApps: List<InstalledApp>): ClassificationResult {
        val trimmed = rawInput.trim()
        val lower = trimmed.lowercase(Locale.getDefault())

        if (trimmed.isBlank()) {
            return ClassificationResult(
                isTask = false,
                classifiedAs = "CONVERSATION",
                confidence = "Blank input",
                falsePositiveRisk = "none"
            )
        }

        // 1. Identify any keywords present (hardware features or app names)
        val matchedHardwareKeyword = HARDWARE_KEYWORDS.firstOrNull { kw ->
            if (kw.all { it in 'a'..'z' || it in 'A'..'Z' || it == '-' || it == ' ' }) {
                lower.contains(Regex("\\b${Regex.escape(kw)}\\b"))
            } else {
                lower.contains(kw)
            }
        }

        val matchedApp = AppOpenManager.fuzzyMatchApp(trimmed, installedApps)
        val matchedAppKeyword = if (matchedApp != null) matchedApp.name else null

        val anyKeywordMentioned = matchedHardwareKeyword ?: matchedAppKeyword

        // 2. Identify conversational markers
        val hasStoryMarker = CONVERSATIONAL_STORY_MARKERS.any { lower.contains(it) }
        val hasChitchatMarker = CONVERSATIONAL_CHITCHAT_MARKERS.any { lower.contains(it) }
        val hasQuestionMarker = INFORMATIONAL_QUESTION_MARKERS.any { lower.contains(it) }
        val isConversationalContext = hasStoryMarker || hasChitchatMarker || hasQuestionMarker

        // 3. Identify action verbs
        val hasHardwareAction = HARDWARE_ACTION_VERBS.any { lower.contains(it) }
        val hasAppLaunchVerb = APP_LAUNCH_VERBS.any { lower.contains(it) }

        // 4. Multi-intent / Compound command check
        val isCompound = (lower.contains(" aur ") || lower.contains(" and ") || lower.contains(" phir ") || lower.contains(" fir ")) &&
                (hasHardwareAction || hasAppLaunchVerb)
        if (isCompound && !isConversationalContext) {
            return ClassificationResult(
                isTask = true,
                classifiedAs = "TASK",
                confidence = "Multi-intent compound command with action verbs",
                falsePositiveRisk = "none",
                taskType = "COMPOUND_TASK"
            )
        }

        // 5. Explicit Camera photo / Scene analysis commands
        if (MaxCameraManager.isSelfieCommand(lower) || MaxCameraManager.isBackPhotoCommand(lower) || MaxCameraManager.isSceneAnalysisCommand(lower)) {
            return ClassificationResult(
                isTask = true,
                classifiedAs = "TASK",
                confidence = "Explicit camera capture / scene analysis command",
                falsePositiveRisk = "none",
                taskType = "CAMERA"
            )
        }

        // 6. Explicit Reminders and Alarms
        if (com.example.util.ReminderParser.isReminderOrAlarmCommand(lower)) {
            return ClassificationResult(
                isTask = true,
                classifiedAs = "TASK",
                confidence = "Explicit reminder/alarm schedule command",
                falsePositiveRisk = "none",
                taskType = "REMINDER"
            )
        }

        // 7. Explicit WhatsApp Auto-Reply command (requires on/off verb)
        val isAutoReplyMention = lower.contains("auto reply") || lower.contains("auto-reply") || lower.contains("autoreply") || lower.contains("व्हाट्सएप ऑटो")
        if (isAutoReplyMention) {
            val hasOnOff = listOf("on", "off", "band", "chalu", "enable", "disable", "stop", "चालू", "बंद").any { lower.contains(it) }
            if (hasOnOff) {
                return ClassificationResult(
                    isTask = true,
                    classifiedAs = "TASK",
                    confidence = "Explicit WhatsApp auto-reply toggle command",
                    falsePositiveRisk = "none",
                    taskType = "WHATSAPP_AUTOREPLY"
                )
            } else {
                return ClassificationResult(
                    isTask = false,
                    classifiedAs = "CONVERSATION",
                    confidence = "Auto-reply mentioned without toggle verb",
                    falsePositiveRisk = "auto-reply keyword mentioned in question/chitchat"
                )
            }
        }

        // 8. Weather Command
        if (WeatherManager.isWeatherCommand(lower)) {
            return ClassificationResult(
                isTask = true,
                classifiedAs = "TASK",
                confidence = "Explicit weather report query",
                falsePositiveRisk = "none",
                taskType = "WEATHER"
            )
        }

        // 9. Permanent Memory Commands
        if (PermanentMemoryManager.isMemoryCommand(lower)) {
            return ClassificationResult(
                isTask = true,
                classifiedAs = "TASK",
                confidence = "Explicit permanent memory command",
                falsePositiveRisk = "none",
                taskType = "MEMORY"
            )
        }

        // 10. Anti-Theft Guard Contact
        if (AntiTheftManager.isTrustedContactCommand(lower)) {
            return ClassificationResult(
                isTask = true,
                classifiedAs = "TASK",
                confidence = "Explicit anti-theft contact command",
                falsePositiveRisk = "none",
                taskType = "ANTI_THEFT"
            )
        }

        // 11. FALSE-POSITIVE FILTER: If conversational context is detected (story/chitchat/question),
        // reject false-positive hardware toggles or app launches!
        if (isConversationalContext) {
            val riskMsg = if (anyKeywordMentioned != null) {
                "keyword '$anyKeywordMentioned' mentioned in conversational context"
            } else {
                "none"
            }
            val reason = when {
                hasStoryMarker -> "Past-tense / narrative storytelling detected"
                hasChitchatMarker -> "Casual conversational feeling / chitchat detected"
                else -> "Informational query / question detected"
            }
            return ClassificationResult(
                isTask = false,
                classifiedAs = "CONVERSATION",
                confidence = reason,
                falsePositiveRisk = riskMsg
            )
        }

        // 12. Strict HARDWARE TOGGLE Check:
        // Must have BOTH: a recognized hardware target AND an explicit action verb (on/off/volume/percentage)
        if (matchedHardwareKeyword != null && hasHardwareAction) {
            val actionVerb = HARDWARE_ACTION_VERBS.firstOrNull { lower.contains(it) } ?: "action"
            return ClassificationResult(
                isTask = true,
                classifiedAs = "TASK",
                confidence = "Explicit action-verb '$actionVerb' targeting '$matchedHardwareKeyword'",
                falsePositiveRisk = "none",
                taskType = "HARDWARE_TOGGLE"
            )
        }

        // 13. Strict APP OPEN Check:
        // Case A: Query has explicit launch verb + matches an installed app
        // Case B: Direct app name spoken alone (<= 3 words, e.g. "YouTube", "यूट्यूब", "WhatsApp", "Chrome")
        val words = trimmed.split("\\s+".toRegex()).filter { it.isNotBlank() }
        val isDirectAppNameAlone = matchedApp != null && words.size <= 2 && !hasChitchatMarker && !hasStoryMarker

        if (matchedApp != null && (hasAppLaunchVerb || isDirectAppNameAlone)) {
            val verbDesc = if (isDirectAppNameAlone) "Direct app name spoken alone" else "Explicit launch verb targeting '${matchedApp.name}'"
            return ClassificationResult(
                isTask = true,
                classifiedAs = "TASK",
                confidence = verbDesc,
                falsePositiveRisk = "none",
                taskType = "APP_OPEN"
            )
        }

        // 14. If a hardware or app keyword was mentioned WITHOUT an explicit action verb,
        // it is a FALSE-POSITIVE RISK! Route strictly as CONVERSATION!
        if (anyKeywordMentioned != null) {
            return ClassificationResult(
                isTask = false,
                classifiedAs = "CONVERSATION",
                confidence = "Keyword '$anyKeywordMentioned' mentioned without imperative action verb",
                falsePositiveRisk = "keyword '$anyKeywordMentioned' mentioned casually, avoided false trigger"
            )
        }

        // 15. Standard casual conversation / answer
        return ClassificationResult(
            isTask = false,
            classifiedAs = "CONVERSATION",
            confidence = "Conversational statement without task verbs",
            falsePositiveRisk = "none"
        )
    }

    /**
     * Checks if a hardware command text contains genuine toggle action intent
     */
    fun isExplicitHardwareToggleIntent(lower: String): Boolean {
        val hasHwKeyword = HARDWARE_KEYWORDS.any { lower.contains(it) }
        val hasActionVerb = HARDWARE_ACTION_VERBS.any { lower.contains(it) }
        val hasConversational = CONVERSATIONAL_STORY_MARKERS.any { lower.contains(it) } ||
                CONVERSATIONAL_CHITCHAT_MARKERS.any { lower.contains(it) } ||
                INFORMATIONAL_QUESTION_MARKERS.any { lower.contains(it) }

        return hasHwKeyword && hasActionVerb && !hasConversational
    }
}
