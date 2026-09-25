package com.example.manager

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.example.data.AppDatabase
import com.example.data.ReminderItem
import com.example.data.ReminderRepository
import com.example.receiver.ReminderReceiver
import com.example.util.DebugLogger
import com.example.util.TtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReminderScheduler {
    private const val TAG = "ReminderScheduler"
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Checks if exact alarm permission is granted on Android 12+ (API 31+).
     * If not granted, requests permission via system settings.
     */
    fun checkAndRequestExactAlarmPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                DebugLogger.logInfo("Exact alarm permission missing on Android 12+; opening settings")
                TtsManager.speak("Exact alarm permission required. Please allow in settings.")
                Toast.makeText(
                    context,
                    "Please allow Exact Alarms permission for Max Assistant",
                    Toast.LENGTH_LONG
                ).show()

                try {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Error opening exact alarm settings", e)
                }
                return false
            }
        }
        return true
    }

    /**
     * Schedules a reminder or alarm, saves it into Room database,
     * and registers it with the Android AlarmManager.
     */
    fun scheduleReminder(
        context: Context,
        task: String,
        triggerTimeMillis: Long,
        isAlarm: Boolean = false,
        onScheduled: ((ReminderItem) -> Unit)? = null
    ) {
        val hasPermission = checkAndRequestExactAlarmPermission(context)
        if (!hasPermission) {
            Log.w(TAG, "Exact alarm permission not granted yet; scheduling with best effort")
        }

        val formattedTime = formatTimestamp(triggerTimeMillis)

        scope.launch {
            val db = AppDatabase.getInstance(context)
            val repo = ReminderRepository(db.reminderDao())

            val item = ReminderItem(
                task = task,
                triggerTimeMillis = triggerTimeMillis,
                formattedTime = formattedTime,
                isAlarm = isAlarm,
                isCompleted = false
            )

            val insertedId = repo.insert(item)
            val savedItem = item.copy(id = insertedId)

            setSystemAlarm(context, savedItem)

            // Required debug log: "REMINDER_SET: time=<time>, task=<text>"
            DebugLogger.logReminderSet(time = formattedTime, task = task)

            onScheduled?.invoke(savedItem)
        }
    }

    /**
     * Directly configures the AlarmManager for an existing ReminderItem.
     */
    fun setSystemAlarm(context: Context, item: ReminderItem) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_TRIGGER_REMINDER
            putExtra(ReminderReceiver.EXTRA_REMINDER_ID, item.id)
            putExtra(ReminderReceiver.EXTRA_TASK, item.task)
            putExtra(ReminderReceiver.EXTRA_IS_ALARM, item.isAlarm)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            item.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (item.isAlarm) {
                    // For alarms, setAlarmClock is ideal as it shows on lock screen
                    val showIntent = Intent(context, com.example.MainActivity::class.java)
                    val showPendingIntent = PendingIntent.getActivity(
                        context,
                        item.id.toInt(),
                        showIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    val alarmClockInfo = AlarmManager.AlarmClockInfo(item.triggerTimeMillis, showPendingIntent)
                    alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
                } else {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        item.triggerTimeMillis,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    item.triggerTimeMillis,
                    pendingIntent
                )
            }
            Log.d(TAG, "Alarm set successfully for id=${item.id} at ${item.formattedTime}")
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm permission denied, falling back to inexact alarm", e)
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                item.triggerTimeMillis,
                pendingIntent
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set alarm for reminder id=${item.id}", e)
        }
    }

    /**
     * Cancels an alarm in AlarmManager and removes it from Room database.
     */
    fun cancelReminder(context: Context, reminderId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_TRIGGER_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null && alarmManager != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }

        scope.launch {
            val db = AppDatabase.getInstance(context)
            val repo = ReminderRepository(db.reminderDao())
            repo.deleteById(reminderId)
            DebugLogger.logInfo("Cancelled reminder with id=$reminderId")
        }
    }

    /**
     * Cancels reminders matching task keyword (e.g. "dawai", "meeting", "exercise")
     */
    fun cancelRemindersByKeyword(
        context: Context,
        keyword: String,
        onResult: (Int, String) -> Unit
    ) {
        scope.launch {
            val db = AppDatabase.getInstance(context)
            val repo = ReminderRepository(db.reminderDao())
            val matching = repo.findRemindersByTaskKeyword(keyword)

            if (matching.isEmpty()) {
                onResult(0, "")
                return@launch
            }

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            for (item in matching) {
                val intent = Intent(context, ReminderReceiver::class.java).apply {
                    action = ReminderReceiver.ACTION_TRIGGER_REMINDER
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    item.id.toInt(),
                    intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
                if (pendingIntent != null && alarmManager != null) {
                    alarmManager.cancel(pendingIntent)
                    pendingIntent.cancel()
                }
            }

            val count = repo.deleteByTaskKeyword(keyword)
            val firstTask = matching.firstOrNull()?.task ?: keyword
            DebugLogger.logInfo("Cancelled $count reminder(s) for keyword: $keyword")
            onResult(count, firstTask)
        }
    }

    private fun formatTimestamp(millis: Long): String {
        val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
        return sdf.format(Date(millis))
    }
}
