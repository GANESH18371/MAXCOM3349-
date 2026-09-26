package com.example.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.AppDatabase
import com.example.data.ReminderRepository
import com.example.util.DebugLogger
import com.example.util.TtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        val task = intent.getStringExtra(EXTRA_TASK) ?: "Reminder"
        val isAlarm = intent.getBooleanExtra(EXTRA_IS_ALARM, false)

        Log.d(TAG, "Reminder triggered: id=$reminderId, task=$task, isAlarm=$isAlarm")

        com.example.manager.BatteryOptimizationManager.runWithSafeWakeLock(context, "ReminderNotification", 5000L) {
            // Exact required format: "REMINDER_TRIGGERED: task=<text>"
            DebugLogger.logReminderTriggered(task)

            // Show Notification
            showNotification(context, reminderId, task, isAlarm)

            // Speak aloud via TTS in Hindi/English
            val speakText = if (isAlarm) {
                "अलार्म: $task. उठने या काम करने का समय हो गया है."
            } else {
                "याद दिला रहा हूँ: $task. समय हो गया है."
            }
            TtsManager.speak(speakText)
        }

        // Update database to mark reminder as completed
        if (reminderId != -1L) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getInstance(context)
                    val repo = ReminderRepository(db.reminderDao())
                    repo.markCompleted(reminderId)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to mark reminder completed in DB", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private fun showNotification(
        context: Context,
        reminderId: Long,
        task: String,
        isAlarm: Boolean
    ) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val channelId = if (isAlarm) CHANNEL_ALARM_ID else CHANNEL_REMINDER_ID
        val channelName = if (isAlarm) "Max Alarms" else "Max Reminders"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for Max reminders and alarms"
                enableVibration(true)
                enableLights(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_HIGHLIGHT_REMINDER_ID", reminderId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            reminderId.toInt(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(
            if (isAlarm) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION
        )

        val title = if (isAlarm) "⏰ Max Alarm" else "🔔 Max Reminder"

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(task)
            .setStyle(NotificationCompat.BigTextStyle().bigText(task))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(if (isAlarm) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setSound(soundUri)
            .setContentIntent(pendingIntent)
            .build()

        val notifId = if (reminderId > 0) reminderId.toInt() else System.currentTimeMillis().toInt()
        notificationManager.notify(notifId, notification)
    }

    companion object {
        private const val TAG = "ReminderReceiver"
        const val ACTION_TRIGGER_REMINDER = "com.example.ACTION_TRIGGER_REMINDER"
        const val EXTRA_REMINDER_ID = "EXTRA_REMINDER_ID"
        const val EXTRA_TASK = "EXTRA_TASK"
        const val EXTRA_IS_ALARM = "EXTRA_IS_ALARM"

        const val CHANNEL_REMINDER_ID = "max_reminders_channel"
        const val CHANNEL_ALARM_ID = "max_alarms_channel"
    }
}
