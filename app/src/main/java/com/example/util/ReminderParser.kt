package com.example.util

import java.util.Calendar
import java.util.regex.Pattern

sealed class ReminderVoiceAction {
    data class SetReminder(
        val task: String,
        val triggerTimeMillis: Long,
        val isAlarm: Boolean,
        val humanTimeDescription: String
    ) : ReminderVoiceAction()

    data class CancelReminder(
        val keyword: String,
        val isAll: Boolean = false
    ) : ReminderVoiceAction()

    object ListReminders : ReminderVoiceAction()
}

object ReminderParser {

    /**
     * Checks if a voice command text is related to Reminders or Alarms.
     */
    fun isReminderOrAlarmCommand(input: String): Boolean {
        val lower = input.lowercase()
        val keywords = listOf(
            "yaad dila", "yaad dilana", "yaad dila do", "yaad dilao", "remind", "reminder",
            "अलार्म", "alarm", "alaram", "अलार्म लगा", "अलार्म सेट", "अलार्म बंद",
            "याद दिलाना", "याद दिलाओ", "याद दिला",
            "reminders batao", "saare reminders", "reminders dikhao", "kya reminders"
        )
        return keywords.any { lower.contains(it) }
    }

    /**
     * Parses the command and returns the recognized action (Set, Cancel, List), or null if unparseable.
     */
    fun parseCommand(input: String): ReminderVoiceAction? {
        val lower = input.trim().lowercase()

        // 1. Check for Query / List reminders
        if (isListRemindersCommand(lower)) {
            return ReminderVoiceAction.ListReminders
        }

        // 2. Check for Cancel reminder
        if (isCancelReminder(lower)) {
            return parseCancelAction(input)
        }

        // 3. Parse Set Reminder / Alarm
        return parseSetReminderOrAlarm(input)
    }

    private fun isListRemindersCommand(lower: String): Boolean {
        val listPhrases = listOf(
            "mere saare reminders batao",
            "saare reminders batao",
            "mere reminders batao",
            "reminders batao",
            "reminders dikhao",
            "saare reminders dikhao",
            "kya reminders hain",
            "kya reminder hai",
            "show all reminders",
            "show reminders",
            "list reminders",
            "what are my reminders",
            "मेरे सारे रिमाइंडर बताओ",
            "रिमाइंडर बताओ"
        )
        return listPhrases.any { lower.contains(it) } ||
                (lower.contains("reminder") && (lower.contains("batao") || lower.contains("dikhao") || lower.contains("list")))
    }

    private fun isCancelReminder(lower: String): Boolean {
        val cancelWords = listOf("cancel", "hatao", "hata do", "band karo", "delete", "rok do", "khatam karo", "कैंसिल", "हटाओ", "बंद करो")
        val isCancel = cancelWords.any { lower.contains(it) }
        if (!isCancel) return false

        val isReminderOrAlarm = lower.contains("reminder") || lower.contains("alarm") || lower.contains("अलार्म") || lower.contains("रिमाइंडर")
        return isReminderOrAlarm
    }

    fun parseCancelAction(input: String): ReminderVoiceAction.CancelReminder {
        val lower = input.trim().lowercase()

        // Check if all
        val allWords = listOf("saare", "sare", "sab", "all", "सभी", "सारे")
        val isAll = allWords.any { lower.contains(it) }
        if (isAll) {
            return ReminderVoiceAction.CancelReminder(keyword = "all", isAll = true)
        }

        // Extract keyword: "mera dawai wala reminder cancel karo" -> "dawai"
        // Clean words:
        var cleaned = lower
        val removeWords = listOf(
            "mera", "meri", "mere", "wala", "wali", "wale", "ka", "ki", "ke",
            "reminder", "reminders", "alarm", "alarms", "cancel", "karo", "kar do",
            "hatao", "hata do", "band karo", "delete", "please", "kripya",
            "रिमाइंडर", "अलार्म", "कैंसिल", "हटाओ", "करो", "कर दो"
        )
        for (w in removeWords) {
            cleaned = cleaned.replace(w, " ")
        }
        cleaned = cleaned.trim().replace("\\s+".toRegex(), " ")

        val keyword = if (cleaned.isNotBlank()) cleaned else "alarm"
        return ReminderVoiceAction.CancelReminder(keyword = keyword, isAll = false)
    }

    private fun parseSetReminderOrAlarm(input: String): ReminderVoiceAction.SetReminder? {
        val lower = input.trim().lowercase()
        val isAlarm = lower.contains("alarm") || lower.contains("alaram") || lower.contains("अलार्म")

        val now = Calendar.getInstance()
        var targetCal = Calendar.getInstance()
        var timeFound = false
        var timeDesc = ""

        // Check relative time: e.g. "10 minute baad", "15 minute me", "2 ghante baad"
        val minPattern = Pattern.compile("(\\d+)\\s*(?:minute|min|मिनट)")
        val minMatcher = minPattern.matcher(lower)

        val hourPattern = Pattern.compile("(\\d+)\\s*(?:ghante|ghanta|hour|hours|घंटे|घंटा)")
        val hourMatcher = hourPattern.matcher(lower)

        if (lower.contains("aadha ghanta") || lower.contains("aadhe ghante") || lower.contains("half an hour") || lower.contains("आधा घंटा")) {
            targetCal.add(Calendar.MINUTE, 30)
            timeFound = true
            timeDesc = "30 minute baad"
        } else if (minMatcher.find()) {
            val mins = minMatcher.group(1)?.toIntOrNull() ?: 10
            targetCal.add(Calendar.MINUTE, mins)
            timeFound = true
            timeDesc = "$mins minute baad"
        } else if (hourMatcher.find()) {
            val hrs = hourMatcher.group(1)?.toIntOrNull() ?: 1
            targetCal.add(Calendar.HOUR_OF_DAY, hrs)
            timeFound = true
            timeDesc = "$hrs ghante baad"
        } else {
            // Check absolute time: e.g. "5:30", "5 baje", "7:00", "subah 7 baje", "shaam 6 baje"
            val isTomorrow = lower.contains("kal") || lower.contains("tomorrow") || lower.contains("कल")
            if (isTomorrow) {
                targetCal.add(Calendar.DAY_OF_YEAR, 1)
            }

            var isPm: Boolean? = null
            if (lower.contains("shaam") || lower.contains("dopahar") || lower.contains("raat") || lower.contains("pm") ||
                lower.contains("शाम") || lower.contains("दोपहर") || lower.contains("रात")) {
                isPm = true
            } else if (lower.contains("subah") || lower.contains("morning") || lower.contains("am") || lower.contains("सुबह")) {
                isPm = false
            }

            // Look for HH:MM (e.g. 5:30, 07:15)
            val timeColonPattern = Pattern.compile("(\\d{1,2})[:.](\\d{2})")
            val colonMatcher = timeColonPattern.matcher(lower)

            // Look for "saadhe X baje" -> X:30
            val saadhePattern = Pattern.compile("(?:saadhe|sadhe|साढ़े)\\s*(\\d{1,2})")
            val saadheMatcher = saadhePattern.matcher(lower)

            // Look for "sawa X baje" -> X:15
            val sawaPattern = Pattern.compile("(?:sawa|सवा)\\s*(\\d{1,2})")
            val sawaMatcher = sawaPattern.matcher(lower)

            // Look for "paune X baje" -> (X-1):45
            val paunePattern = Pattern.compile("(?:paune|पौने)\\s*(\\d{1,2})")
            val pauneMatcher = paunePattern.matcher(lower)

            // Look for "X baje" or "at X"
            val bajePattern = Pattern.compile("(\\d{1,2})\\s*(?:baje|बजे|o'clock|am|pm)")
            val bajeMatcher = bajePattern.matcher(lower)

            if (colonMatcher.find()) {
                var hour = colonMatcher.group(1)?.toIntOrNull() ?: 12
                val min = colonMatcher.group(2)?.toIntOrNull() ?: 0
                if (isPm == true && hour < 12) hour += 12
                else if (isPm == false && hour == 12) hour = 0
                else if (isPm == null && hour in 1..11 && !isTomorrow) {
                    // If hour already passed today in AM, assume PM
                    targetCal.set(Calendar.HOUR_OF_DAY, hour)
                    targetCal.set(Calendar.MINUTE, min)
                    targetCal.set(Calendar.SECOND, 0)
                    if (targetCal.timeInMillis <= now.timeInMillis) {
                        hour += 12
                    }
                }
                targetCal.set(Calendar.HOUR_OF_DAY, hour)
                targetCal.set(Calendar.MINUTE, min)
                targetCal.set(Calendar.SECOND, 0)
                timeFound = true
                timeDesc = String.format("%02d:%02d", hour, min)
            } else if (saadheMatcher.find()) {
                var hour = saadheMatcher.group(1)?.toIntOrNull() ?: 5
                if (isPm == true && hour < 12) hour += 12
                else if (isPm == null && hour in 1..11 && !isTomorrow) {
                    targetCal.set(Calendar.HOUR_OF_DAY, hour)
                    targetCal.set(Calendar.MINUTE, 30)
                    targetCal.set(Calendar.SECOND, 0)
                    if (targetCal.timeInMillis <= now.timeInMillis) hour += 12
                }
                targetCal.set(Calendar.HOUR_OF_DAY, hour)
                targetCal.set(Calendar.MINUTE, 30)
                targetCal.set(Calendar.SECOND, 0)
                timeFound = true
                timeDesc = "साढ़े $hour बजे"
            } else if (sawaMatcher.find()) {
                var hour = sawaMatcher.group(1)?.toIntOrNull() ?: 5
                if (isPm == true && hour < 12) hour += 12
                targetCal.set(Calendar.HOUR_OF_DAY, hour)
                targetCal.set(Calendar.MINUTE, 15)
                targetCal.set(Calendar.SECOND, 0)
                timeFound = true
                timeDesc = "सवा $hour बजे"
            } else if (pauneMatcher.find()) {
                var targetH = pauneMatcher.group(1)?.toIntOrNull() ?: 5
                var hour = if (targetH > 1) targetH - 1 else 12
                if (isPm == true && hour < 12) hour += 12
                targetCal.set(Calendar.HOUR_OF_DAY, hour)
                targetCal.set(Calendar.MINUTE, 45)
                targetCal.set(Calendar.SECOND, 0)
                timeFound = true
                timeDesc = "पौने $targetH बजे"
            } else if (bajeMatcher.find()) {
                var hour = bajeMatcher.group(1)?.toIntOrNull() ?: 7
                if (isPm == true && hour < 12) hour += 12
                else if (isPm == false && hour == 12) hour = 0
                else if (isPm == null && hour in 1..11 && !isTomorrow) {
                    targetCal.set(Calendar.HOUR_OF_DAY, hour)
                    targetCal.set(Calendar.MINUTE, 0)
                    targetCal.set(Calendar.SECOND, 0)
                    if (targetCal.timeInMillis <= now.timeInMillis) hour += 12
                }
                targetCal.set(Calendar.HOUR_OF_DAY, hour)
                targetCal.set(Calendar.MINUTE, 0)
                targetCal.set(Calendar.SECOND, 0)
                timeFound = true
                timeDesc = "$hour बजे"
            }
        }

        // If time was not found or if the calculated time is already in past, add 1 day if absolute
        if (!timeFound) {
            // Default fallback: 10 minutes from now if user said "yaad dilana" without time
            targetCal.add(Calendar.MINUTE, 10)
            timeDesc = "10 minute baad"
        } else if (targetCal.timeInMillis <= now.timeInMillis) {
            // Time already passed today, assume tomorrow
            targetCal.add(Calendar.DAY_OF_YEAR, 1)
        }

        // Extract Task description
        val task = extractTask(lower, isAlarm)

        return ReminderVoiceAction.SetReminder(
            task = task,
            triggerTimeMillis = targetCal.timeInMillis,
            isAlarm = isAlarm,
            humanTimeDescription = timeDesc
        )
    }

    private fun extractTask(lower: String, isAlarm: Boolean): String {
        var text = lower

        // Remove typical boilerplate words
        val boilerplates = listOf(
            "mujhe", "mujhko", "please", "kripya", "par", "pe", "ko", "ka", "ki", "ke", "liye",
            "yaad dilana", "yaad dila dena", "yaad dila do", "yaad dilao", "yaad",
            "alarm laga do", "alarm lagao", "alarm set karo", "alarm set kar do",
            "remind me to", "remind me", "set alarm for", "set alarm at", "set an alarm",
            "ek reminder lagao", "reminder lagao", "reminder set karo",
            "kal", "aaj", "subah", "shaam", "dopahar", "raat",
            "baje", "minute", "min", "baad", "me", "mein", "later", "tomorrow", "today"
        )

        // Remove numeric time patterns from task text
        text = text.replace("\\b\\d{1,2}[:.]\\d{2}\\b".toRegex(), " ")
        text = text.replace("\\b\\d{1,2}\\s*(?:baje|am|pm|बजे)\\b".toRegex(), " ")
        text = text.replace("\\b\\d+\\s*(?:minute|min|ghante|ghanta|hour|hours)\\b".toRegex(), " ")

        for (bp in boilerplates) {
            text = text.replace(bp, " ")
        }

        text = text.trim().replace("\\s+".toRegex(), " ")

        return if (text.isNotBlank() && text.length > 2) {
            text.replaceFirstChar { it.uppercase() }
        } else {
            if (isAlarm) "Alarm" else "General Reminder"
        }
    }
}
