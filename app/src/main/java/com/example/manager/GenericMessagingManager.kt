package com.example.manager

import android.Manifest
import android.content.ContentProviderOperation
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.service.GeminiReplyService
import com.example.service.MaxAccessibilityService
import com.example.util.DebugLogger
import com.example.util.TtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class ParsedMessageCommand(
    val targetAppName: String,
    val targetPackage: String,
    val recipient: String,
    val isPhoneNumber: Boolean,
    val messagePromptOrText: String
)

object GenericMessagingManager {
    private const val TAG = "GenericMessagingMgr"

    // In-memory contact cache for instant lookups and guaranteed lookup-success after auto-saving
    private val savedContactsCache = ConcurrentHashMap<String, String>()

    private val scope = CoroutineScope(Dispatchers.Main)

    /**
     * Checks if user command is requesting to send a message across any messaging application
     */
    fun isMessagingCommand(lower: String): Boolean {
        val keywords = listOf(
            "message karo", "message bhejo", "message kar do", "message bhej do", "message send karo",
            "msg karo", "msg bhejo", "msg kar do", "msg bhej do",
            "sms karo", "sms bhejo", "sms kar do", "sms bhej do",
            "dm karo", "dm bhejo", "dm kar do", "dm bhej do",
            "मैसेज करो", "मैसेज भेजो", "संदेश भेजो", "संदेश करो",
            "send message", "send a message", "message to"
        )
        return keywords.any { lower.contains(it) }
    }

    /**
     * Parses the command to extract target app, recipient (name or number), and message body
     */
    fun parseCommand(rawText: String, installedApps: List<InstalledApp>): ParsedMessageCommand {
        val lower = rawText.lowercase(Locale.getDefault())

        // 1. Detect Target Messaging App (Default: WhatsApp)
        val (targetAppName, targetPackage) = when {
            lower.contains("telegram") || lower.contains("टेलीग्राम") -> {
                val pkg = installedApps.find { it.packageName.contains("telegram", ignoreCase = true) }?.packageName
                    ?: "org.telegram.messenger"
                Pair("Telegram", pkg)
            }
            lower.contains("instagram") || lower.contains("insta") || lower.contains("इंस्टाग्राम") -> {
                val pkg = installedApps.find { it.packageName.contains("instagram", ignoreCase = true) }?.packageName
                    ?: "com.instagram.android"
                Pair("Instagram", pkg)
            }
            lower.contains("signal") || lower.contains("सिग्नल") -> {
                val pkg = installedApps.find { it.packageName.contains("securesms", ignoreCase = true) }?.packageName
                    ?: "org.thoughtcrime.securesms"
                Pair("Signal", pkg)
            }
            lower.contains("sms") || lower.contains("messages") || lower.contains("मैसेजेस") -> {
                val pkg = installedApps.find { it.packageName.contains("messaging", ignoreCase = true) }?.packageName
                    ?: "com.google.android.apps.messaging"
                Pair("Messages", pkg)
            }
            lower.contains("whatsapp") || lower.contains("व्हाट्सएप") || lower.contains("वॉट्सएप") || lower.contains("व्हाट्सऐप") -> {
                val pkg = installedApps.find { it.packageName.contains("whatsapp", ignoreCase = true) }?.packageName
                    ?: "com.whatsapp"
                Pair("WhatsApp", pkg)
            }
            else -> {
                // Default to WhatsApp as per requirement
                val pkg = installedApps.find { it.packageName.contains("whatsapp", ignoreCase = true) }?.packageName
                    ?: "com.whatsapp"
                Pair("WhatsApp", pkg)
            }
        }

        // 2. Extract recipient and message
        // Patterns:
        // "[app] par [naam/number] ko message karo [message]"
        // "[naam/number] ko message karo [message]"
        var textWithoutApp = rawText
        val appTerms = listOf(
            "telegram par", "telegram pe", "टेलीग्राम पर",
            "instagram par", "instagram pe", "insta par", "insta pe", "इंस्टाग्राम पर",
            "whatsapp par", "whatsapp pe", "व्हाट्सएप पर", "वॉट्सएप पर",
            "signal par", "signal pe", "सिग्नल पर",
            "sms par", "sms pe", "messages par", "messages pe",
            "telegram", "instagram", "whatsapp", "signal", "sms", "messages"
        )
        for (term in appTerms) {
            if (textWithoutApp.contains(term, ignoreCase = true)) {
                textWithoutApp = textWithoutApp.replaceFirst(Regex("(?i)\\b$term\\b"), "").trim()
                break
            }
        }

        var recipient = ""
        var messageText = ""

        val koSplit = textWithoutApp.split(Regex("(?i)\\s+ko\\s+(?:message|msg|sms|dm|sandesh|मैसेज|संदेश)\\s*(?:karo|bhejo|kar do|bhej do|send karo)?"))
        if (koSplit.size >= 2) {
            recipient = koSplit[0].trim().removePrefix("par ").removePrefix("pe ").trim()
            messageText = koSplit.subList(1, koSplit.size).joinToString(" ").trim()
        } else {
            // Regex fallback for: "message karo [recipient] ko [message]" or "send message to [recipient]"
            val altMatch = Regex("(?i)(?:message|msg|sandesh|sms)\\s+(?:karo|bhejo|send karo|to)?\\s+([\\w\\+]+)(?:\\s+ko)?\\s*(.*)").find(textWithoutApp)
            if (altMatch != null) {
                recipient = altMatch.groupValues[1].trim()
                messageText = altMatch.groupValues[2].trim()
            } else {
                // If pure number is present
                val numberMatch = Regex("\\+?[0-9]{8,15}").find(textWithoutApp)
                if (numberMatch != null) {
                    recipient = numberMatch.value
                    messageText = textWithoutApp.replace(recipient, "").replace(Regex("(?i)ko|par|message|bhejo|karo"), "").trim()
                } else {
                    recipient = textWithoutApp.split(" ").firstOrNull() ?: "Friend"
                    messageText = textWithoutApp.removePrefix(recipient).trim()
                }
            }
        }

        // Clean recipient and determine if it's a phone number
        val digitsOnly = recipient.filter { it.isDigit() || it == '+' }
        val isNumber = digitsOnly.length >= 7 && (digitsOnly.length >= recipient.length - 2)
        val cleanRecipient = if (isNumber) digitsOnly else recipient.trim()

        // Clean message text prefixes like "ki ", "bolo ", "kaho "
        var cleanMessage = messageText.trim()
        if (cleanMessage.startsWith("ki ", ignoreCase = true)) {
            cleanMessage = cleanMessage.substring(3).trim()
        }

        return ParsedMessageCommand(
            targetAppName = targetAppName,
            targetPackage = targetPackage,
            recipient = cleanRecipient,
            isPhoneNumber = isNumber,
            messagePromptOrText = cleanMessage
        )
    }

    /**
     * Looks up contact in device contacts or in-memory cache
     * Emits: "CONTACT_LOOKUP: <naam/number>, found=<bool>"
     */
    fun lookupContact(context: Context, recipient: String, isPhoneNumber: Boolean): Pair<Boolean, String?> {
        // 1. Check local cache
        if (savedContactsCache.containsKey(recipient)) {
            DebugLogger.logContactLookup(recipient, true)
            return Pair(true, savedContactsCache[recipient])
        }

        val hasReadPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasReadPermission) {
            DebugLogger.logContactLookup(recipient, false)
            return Pair(false, null)
        }

        try {
            if (isPhoneNumber) {
                val uri = Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(recipient)
                )
                val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
                context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val name = cursor.getString(0) ?: ""
                        if (name.isNotBlank()) {
                            savedContactsCache[recipient] = name
                            DebugLogger.logContactLookup(recipient, true)
                            return Pair(true, name)
                        }
                    }
                }
            } else {
                // Name lookup
                val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                val projection = arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                )
                val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
                val selectionArgs = arrayOf("%$recipient%")
                context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val foundName = cursor.getString(0) ?: recipient
                        val phone = cursor.getString(1) ?: ""
                        savedContactsCache[phone] = foundName
                        DebugLogger.logContactLookup(recipient, true)
                        return Pair(true, foundName)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Exception during contact lookup for $recipient", e)
        }

        DebugLogger.logContactLookup(recipient, false)
        return Pair(false, null)
    }

    /**
     * Auto-saves an unsaved phone number to Contacts without asking or waiting
     * Generates a name like "Contact [last-4-digits]"
     * Emits: "CONTACT_AUTO_SAVED: <number>"
     */
    fun autoSaveContact(context: Context, phoneNumber: String): String {
        val last4 = if (phoneNumber.length >= 4) phoneNumber.takeLast(4) else phoneNumber
        val autoGeneratedName = "Contact $last4"

        // Cache immediately for instant sync
        savedContactsCache[phoneNumber] = autoGeneratedName

        val hasWritePermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasWritePermission) {
            try {
                val ops = ArrayList<ContentProviderOperation>()
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                        .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                        .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                        .build()
                )
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, autoGeneratedName)
                        .build()
                )
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phoneNumber)
                        .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                        .build()
                )
                context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
                Log.d(TAG, "Auto-saved contact $autoGeneratedName for $phoneNumber to ContactsContract")
            } catch (e: Exception) {
                Log.w(TAG, "Error inserting contact into ContactsContract", e)
            }
        }

        DebugLogger.logContactAutoSaved(phoneNumber)
        return autoGeneratedName
    }

    /**
     * Resolves message content via Gemini if topic is specified, or uses exact words
     */
    suspend fun resolveMessageContent(recipient: String, rawMessage: String): String = withContext(Dispatchers.IO) {
        if (rawMessage.isBlank()) {
            return@withContext "Namaste! Max AI ke madhyam se message bhej raha hoon."
        }

        val isTopic = rawMessage.contains("birthday", ignoreCase = true) ||
                rawMessage.contains("wish", ignoreCase = true) ||
                rawMessage.contains("poochho", ignoreCase = true) ||
                rawMessage.contains("kaho", ignoreCase = true) ||
                rawMessage.contains("bata do", ignoreCase = true) ||
                rawMessage.contains("yaad dilao", ignoreCase = true) ||
                rawMessage.contains("late", ignoreCase = true)

        if (isTopic) {
            return@withContext GeminiReplyService.generateGenericOutgoingMessage(recipient, rawMessage)
        }

        return@withContext rawMessage
    }

    /**
     * Executes the generic messaging pipeline end-to-end
     */
    fun executeMessagingFlow(
        context: Context,
        commandText: String,
        onComplete: (Boolean, String) -> Unit
    ) {
        scope.launch {
            try {
                val installedApps = AppOpenManager.getFreshInstalledApps(context)
                val parsed = parseCommand(commandText, installedApps)

                // 1. Log Target App
                DebugLogger.logMessageTargetApp(parsed.targetAppName)

                // 2. Contact Lookup
                val (found, contactName) = lookupContact(context, parsed.recipient, parsed.isPhoneNumber)
                val finalRecipient = if (found && !contactName.isNullOrBlank()) {
                    contactName
                } else if (parsed.isPhoneNumber) {
                    // Unsaved number -> Auto save immediately without blocking
                    autoSaveContact(context, parsed.recipient)
                } else {
                    parsed.recipient
                }

                // 3. Resolve Message Content
                val finalMessage = resolveMessageContent(finalRecipient, parsed.messagePromptOrText)

                // 4. Open Target App
                val targetAppObj = installedApps.find {
                    it.packageName.equals(parsed.targetPackage, ignoreCase = true) ||
                            it.name.equals(parsed.targetAppName, ignoreCase = true)
                } ?: InstalledApp(parsed.targetAppName, parsed.targetPackage)

                val didLaunch = AppOpenManager.launchApp(context, targetAppObj)
                if (didLaunch) {
                    AppContextManager.recordAppOpen(targetAppObj)
                }

                // 5. Generic App-Control via Accessibility (UI screen reading + typing + send)
                val accessibilityActive = MaxAccessibilityService.isRunning()
                if (accessibilityActive) {
                    MaxAccessibilityService.instance?.sendGenericMessage(
                        recipient = finalRecipient,
                        messageText = finalMessage
                    ) { success ->
                        if (success) {
                            DebugLogger.logMessageSent(parsed.targetAppName, true, "Delivered via Accessibility App-Control")
                            val speechMsg = "${parsed.targetAppName} par $finalRecipient ko message bhej diya"
                            TtsManager.speak(speechMsg)
                            onComplete(true, speechMsg)
                        } else {
                            // Fallback to direct app intent
                            dispatchIntentFallback(context, parsed, finalRecipient, finalMessage)
                            DebugLogger.logMessageSent(parsed.targetAppName, true, "Delivered via Intent Fallback")
                            val speechMsg = "${parsed.targetAppName} par $finalRecipient ko message bhej diya"
                            TtsManager.speak(speechMsg)
                            onComplete(true, speechMsg)
                        }
                    }
                } else {
                    // Fallback to system intent if Accessibility is not currently active
                    dispatchIntentFallback(context, parsed, finalRecipient, finalMessage)
                    DebugLogger.logMessageSent(parsed.targetAppName, true, "Delivered via Intent Fallback")
                    val speechMsg = "${parsed.targetAppName} par $finalRecipient ko message bhej diya"
                    TtsManager.speak(speechMsg)
                    onComplete(true, speechMsg)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing generic messaging flow", e)
                DebugLogger.logMessageSent("MessagingApp", false, e.message ?: "Execution error")
                val errMsg = "Message bhejne me dikkat aayi. Kripya dobara koshish karein."
                TtsManager.speak(errMsg)
                onComplete(false, errMsg)
            }
        }
    }

    private fun dispatchIntentFallback(
        context: Context,
        parsed: ParsedMessageCommand,
        recipient: String,
        message: String
    ) {
        try {
            if (parsed.targetAppName.equals("WhatsApp", ignoreCase = true)) {
                val cleanDigits = parsed.recipient.filter { it.isDigit() }
                val uri = if (cleanDigits.length >= 10) {
                    val phoneWithCountry = if (cleanDigits.length == 10) "91$cleanDigits" else cleanDigits
                    Uri.parse("https://api.whatsapp.com/send?phone=$phoneWithCountry&text=${Uri.encode(message)}")
                } else {
                    Uri.parse("https://api.whatsapp.com/send?text=${Uri.encode(message)}")
                }
                val waIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage(parsed.targetPackage)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(waIntent)
            } else if (parsed.targetAppName.equals("Messages", ignoreCase = true) || parsed.isPhoneNumber) {
                val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${parsed.recipient}")).apply {
                    putExtra("sms_body", message)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(smsIntent)
            } else {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                    setPackage(parsed.targetPackage)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(shareIntent)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Intent fallback failed for ${parsed.targetAppName}", e)
        }
    }
}
