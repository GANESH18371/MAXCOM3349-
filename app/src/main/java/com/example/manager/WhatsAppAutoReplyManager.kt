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
import com.example.service.WhatsAppNotificationListenerService
import com.example.util.DebugLogger
import com.example.util.TtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    // Default OFF as required by specification
    private val _isAutoReplyEnabled = MutableStateFlow(false)
    val isAutoReplyEnabled: StateFlow<Boolean> = _isAutoReplyEnabled.asStateFlow()

    private val _recentMessages = MutableStateFlow<List<WhatsAppMessageEntry>>(emptyList())
    val recentMessages: StateFlow<List<WhatsAppMessageEntry>> = _recentMessages.asStateFlow()

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
     * Handles incoming notification from WhatsAppNotificationListenerService
     */
    fun onWhatsAppMessageReceived(
        context: Context,
        sender: String,
        messageText: String,
        sbn: StatusBarNotification?
    ) {
        // Step 1: Always log receipt
        DebugLogger.logWhatsAppMessageReceived(sender, messageText)

        val entry = WhatsAppMessageEntry(
            sender = sender,
            message = messageText
        )
        _recentMessages.value = (listOf(entry) + _recentMessages.value).take(50)

        // Step 2: Only proceed to auto-reply if enabled
        if (!_isAutoReplyEnabled.value) {
            DebugLogger.logInfo("Auto-reply is currently OFF. Skipping reply generation.")
            return
        }

        // Step 3: Generate and send reply asynchronously
        scope.launch {
            try {
                // Generate reply via Gemini
                val reply = GeminiReplyService.generateAutoReply(sender, messageText)
                DebugLogger.logWhatsAppReplyGenerated(reply)

                // Update entry
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
}
