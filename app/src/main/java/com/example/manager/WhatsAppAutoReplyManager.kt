package com.example.manager

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.example.service.GeminiReplyService
import com.example.service.MaxAccessibilityService
import com.example.util.DebugLogger
import com.example.util.TtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.LinkedHashMap
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class WhatsAppMessageEntry(
    val id: Long = System.currentTimeMillis() + (0..999).random(),
    val sender: String,
    val message: String,
    val reply: String? = null,
    val isSent: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

object WhatsAppAutoReplyManager {
    private const val TAG = "WhatsAppAutoReply"
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    // Hard safety rate limit: 1 reply per sender every 60 seconds (1 minute)
    const val RATE_LIMIT_COOLDOWN_MS = 60_000L

    // Pause window to ignore our own notification echoes when typing/sending
    private const val SELF_REPLY_IGNORE_WINDOW_MS = 4_000L

    // Default OFF as required by specification
    private val _isAutoReplyEnabled = MutableStateFlow(false)
    val isAutoReplyEnabled: StateFlow<Boolean> = _isAutoReplyEnabled.asStateFlow()

    private val _recentMessages = MutableStateFlow<List<WhatsAppMessageEntry>>(emptyList())
    val recentMessages: StateFlow<List<WhatsAppMessageEntry>> = _recentMessages.asStateFlow()

    // 1. DEDUPLICATION & PROCESSED TRACKING (Bounded Sets / Maps)
    private val processedNotificationKeys = Collections.synchronizedSet(
        Collections.newSetFromMap(object : LinkedHashMap<String, Boolean>(100, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
                return size > 200
            }
        })
    )

    private val processedMessageHashes = ConcurrentHashMap<String, Long>()

    // 2. RATE LIMITING: Last auto-reply timestamp per sender
    private val lastReplyTimePerSender = ConcurrentHashMap<String, Long>()

    // 3. SELF-TRIGGER PREVENTION
    @Volatile
    var isSendingReply: Boolean = false
        private set

    @Volatile
    var lastSelfReplyTimeMs: Long = 0L
        private set

    private val recentSentReplies = Collections.synchronizedSet(
        Collections.newSetFromMap(object : LinkedHashMap<String, Boolean>(50, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
                return size > 50
            }
        })
    )

    fun setAutoReplyEnabled(context: Context, enabled: Boolean, announceWithTts: Boolean = true): Boolean {
        if (enabled) {
            val hasPermission = isNotificationAccessGranted(context)
            if (!hasPermission) {
                val warning = "Notification Access permission required for WhatsApp auto reply. Please enable Notification Access in Settings."
                if (announceWithTts) {
                    TtsManager.speak(warning)
                }
                DebugLogger.logInfo(warning)
                openNotificationAccessSettings(context)
                _isAutoReplyEnabled.value = false
                return false
            }
            _isAutoReplyEnabled.value = true
            DebugLogger.logInfo("WhatsApp Auto-Reply ACTIVATED")
            if (announceWithTts) {
                TtsManager.speak("WhatsApp auto-reply is now turned on.")
            }
            return true
        } else {
            _isAutoReplyEnabled.value = false
            DebugLogger.logInfo("WhatsApp Auto-Reply DEACTIVATED")
            if (announceWithTts) {
                TtsManager.speak("WhatsApp auto-reply is now turned off.")
            }
            return true
        }
    }

    fun isNotificationAccessGranted(context: Context): Boolean {
        return try {
            val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(context)
            if (enabledListeners.contains(context.packageName)) {
                return true
            }

            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            if (!TextUtils.isEmpty(flat)) {
                val names = flat.split(":").toTypedArray()
                for (name in names) {
                    val cn = ComponentName.unflattenFromString(name)
                    if (cn != null && TextUtils.equals(context.packageName, cn.packageName)) {
                        return true
                    }
                }
            }
            false
        } catch (e: Exception) {
            Log.w(TAG, "Error checking notification listener permission", e)
            false
        }
    }

    fun openNotificationAccessSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Could not open notification listener settings", e)
        }
    }

    /**
     * Checks if the notification is a self-trigger echo created by Max's own reply
     */
    fun isSelfTrigger(sender: String, messageText: String): Boolean {
        val now = System.currentTimeMillis()

        // Check if Max is currently in the middle of sending a reply
        if (isSendingReply) {
            return true
        }

        // Check if reply was sent in the last 4 seconds
        if (now - lastSelfReplyTimeMs < SELF_REPLY_IGNORE_WINDOW_MS) {
            return true
        }

        val lowerMsg = messageText.lowercase(Locale.getDefault()).trim()

        // Check if message starts with outgoing prefix like "You:" or "आप:"
        if (lowerMsg.startsWith("you:") || lowerMsg.startsWith("you :") ||
            lowerMsg.startsWith("आप:") || lowerMsg.startsWith("आप :")) {
            return true
        }

        // Check if the message matches any recently sent auto-reply
        synchronized(recentSentReplies) {
            for (sentReply in recentSentReplies) {
                if (lowerMsg == sentReply || lowerMsg.contains(sentReply) || sentReply.contains(lowerMsg)) {
                    return true
                }
            }
        }

        return false
    }

    /**
     * Core handler for incoming WhatsApp message notifications with:
     * 1. Deduplication
     * 2. Self-trigger prevention
     * 3. 1-minute Rate Limiting
     * 4. Already-processed tracking
     */
    @Synchronized
    fun onWhatsAppMessageReceived(
        context: Context,
        sender: String,
        messageText: String,
        sbn: StatusBarNotification?
    ) {
        val now = System.currentTimeMillis()
        val normalizedSender = sender.trim().lowercase(Locale.getDefault())
        val cleanMsg = messageText.trim()

        // 1. UNIQUE NOTIFICATION ID & DEDUPLICATION CHECK
        val notificationId = sbn?.key ?: "${normalizedSender}_${cleanMsg.hashCode()}"
        val contentHash = "${normalizedSender}:::${cleanMsg.lowercase(Locale.getDefault())}"

        val isKeyDuplicate = processedNotificationKeys.contains(notificationId)
        val lastSeenTime = processedMessageHashes[contentHash]
        // Message is considered duplicate if same notification key OR same sender+text within last 2 minutes
        val isContentDuplicate = lastSeenTime != null && (now - lastSeenTime < 120_000L)
        val isDuplicate = isKeyDuplicate || isContentDuplicate

        // Debug Log 1: WHATSAPP_NOTIFICATION_RECEIVED
        DebugLogger.logWhatsAppNotificationReceived(notificationId, isDuplicate)

        if (isDuplicate) {
            DebugLogger.logInfo("Duplicate WhatsApp notification ignored: id=$notificationId")
            return
        }

        // Mark as processed
        processedNotificationKeys.add(notificationId)
        processedMessageHashes[contentHash] = now

        // Clean up old message hashes older than 5 minutes
        if (processedMessageHashes.size > 200) {
            val iterator = processedMessageHashes.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (now - entry.value > 300_000L) {
                    iterator.remove()
                }
            }
        }

        // 2. SELF-TRIGGER PREVENTION CHECK
        val selfTrigger = isSelfTrigger(sender, cleanMsg)
        DebugLogger.logWhatsAppSelfTriggerIgnored(selfTrigger, if (selfTrigger) "Active reply cooldown or echo match" else "")

        if (selfTrigger) {
            DebugLogger.logInfo("Self-trigger echo ignored: \"$cleanMsg\"")
            return
        }

        // Log the actual incoming user message
        DebugLogger.logWhatsAppMessageReceived(sender, cleanMsg)

        val entry = WhatsAppMessageEntry(
            sender = sender,
            message = cleanMsg
        )
        _recentMessages.value = (listOf(entry) + _recentMessages.value).take(50)

        // 3. AUTO-REPLY STATUS CHECK
        if (!_isAutoReplyEnabled.value) {
            DebugLogger.logInfo("Auto-reply is currently OFF. Skipping reply generation.")
            return
        }

        // 4. RATE LIMITING CHECK (Hard safety limit: 1 reply per sender per 60 seconds)
        val lastReplyTime = lastReplyTimePerSender[normalizedSender] ?: 0L
        val timeSinceLastReply = now - lastReplyTime
        if (timeSinceLastReply < RATE_LIMIT_COOLDOWN_MS) {
            val waitSeconds = ((RATE_LIMIT_COOLDOWN_MS - timeSinceLastReply) / 1000).coerceAtLeast(1)
            DebugLogger.logWhatsAppRateLimitCheck(false, "sender=$sender, cooldown=${waitSeconds}s")
            return
        }

        // Rate limit passed!
        DebugLogger.logWhatsAppRateLimitCheck(true, "sender=$sender")

        // 5. PROCESS & DISPATCH AUTO-REPLY
        // Immediately reserve rate limit timestamp to avoid concurrent processing race conditions
        lastReplyTimePerSender[normalizedSender] = now

        scope.launch {
            try {
                // Generate reply via Gemini
                val reply = GeminiReplyService.generateAutoReply(sender, cleanMsg)
                DebugLogger.logWhatsAppReplyGenerated(reply)

                // Track sent reply text to prevent self-trigger echoes
                val normalizedReply = reply.lowercase(Locale.getDefault()).trim()
                recentSentReplies.add(normalizedReply)

                // Set sending flag and timestamp
                isSendingReply = true
                lastSelfReplyTimeMs = System.currentTimeMillis()

                // Update UI entry
                val updatedEntry = entry.copy(reply = reply)
                _recentMessages.value = _recentMessages.value.map { if (it.id == entry.id) updatedEntry else it }

                // Attempt sending via Notification RemoteInput first
                var sent = false
                if (sbn != null) {
                    sent = sendViaNotificationAction(context, sbn.notification, reply)
                }

                // If not sent via notification, fallback to Accessibility Service
                if (!sent) {
                    sent = sendViaAccessibility(context, sender, reply)
                }

                if (sent) {
                    DebugLogger.logWhatsAppReplySent(true, "Sent to $sender")
                    _recentMessages.value = _recentMessages.value.map {
                        if (it.id == entry.id) it.copy(isSent = true) else it
                    }
                } else {
                    DebugLogger.logWhatsAppReplySent(false, "Could not dispatch reply to $sender")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing WhatsApp auto reply", e)
                DebugLogger.logWhatsAppReplySent(false, e.message ?: "Unknown error")
            } finally {
                // Keep self-trigger pause active for 3 seconds after dispatch to absorb any UI/notification bounce
                scope.launch {
                    delay(3000)
                    isSendingReply = false
                    lastSelfReplyTimeMs = System.currentTimeMillis()
                }
            }
        }
    }

    private fun sendViaNotificationAction(context: Context, notification: Notification, replyText: String): Boolean {
        try {
            val actions = notification.actions ?: return false
            for (action in actions) {
                val remoteInputs = action.remoteInputs ?: continue
                for (remoteInput in remoteInputs) {
                    if (remoteInput.resultKey.isNotBlank()) {
                        val intent = Intent()
                        val bundle = Bundle()
                        bundle.putCharSequence(remoteInput.resultKey, replyText)
                        RemoteInput.addResultsToIntent(arrayOf(remoteInput), intent, bundle)
                        action.actionIntent.send(context, 0, intent)
                        DebugLogger.logInfo("Sent reply via Notification RemoteInput")
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not send reply via notification action", e)
        }
        return false
    }

    private fun sendViaAccessibility(context: Context, sender: String, replyText: String): Boolean {
        val service = MaxAccessibilityService.instance
        if (service != null) {
            return service.sendWhatsAppMessage(sender, replyText)
        } else {
            // If accessibility is not active, launch WhatsApp directly
            try {
                val launchIntent = context.packageManager.getLaunchIntentForPackage("com.whatsapp")
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not launch WhatsApp", e)
            }
        }
        return false
    }

    fun clearHistoryForTesting() {
        processedNotificationKeys.clear()
        processedMessageHashes.clear()
        lastReplyTimePerSender.clear()
        recentSentReplies.clear()
        isSendingReply = false
        lastSelfReplyTimeMs = 0L
    }
}
