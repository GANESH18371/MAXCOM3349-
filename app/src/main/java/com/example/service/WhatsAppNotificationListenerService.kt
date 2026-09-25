package com.example.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.manager.WhatsAppAutoReplyManager

class WhatsAppNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val pkg = sbn.packageName ?: return
        if (pkg != "com.whatsapp" && pkg != "com.whatsapp.w4b") return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        // Extract sender title and text
        val title = extras.getString(Notification.EXTRA_TITLE)
            ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: extras.getString(Notification.EXTRA_CONVERSATION_TITLE)
            ?: ""

        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: ""

        val cleanTitle = title.trim()
        val cleanText = text.trim()

        if (cleanTitle.isBlank() || cleanText.isBlank()) return

        // Filter out non-message system notices like "WhatsApp Web is active" or "Checking for messages"
        if (cleanTitle.equals("WhatsApp", ignoreCase = true) &&
            (cleanText.contains("messages", ignoreCase = true) ||
             cleanText.contains("web is currently active", ignoreCase = true) ||
             cleanText.contains("checking for new", ignoreCase = true))) {
            return
        }

        // Filter out summary lines like "2 new messages"
        if (cleanText.matches(Regex("""^\d+\s+new\s+messages$""", RegexOption.IGNORE_CASE))) {
            return
        }

        WhatsAppAutoReplyManager.onWhatsAppMessageReceived(
            context = applicationContext,
            sender = cleanTitle,
            messageText = cleanText,
            sbn = sbn
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }

    companion object {
        private const val TAG = "WhatsAppNotification"
    }
}
