package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.ReminderRepository
import com.example.manager.ReminderScheduler
import com.example.util.DebugLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Boot completed: Rescheduling active reminders")
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getInstance(context)
                    val repo = ReminderRepository(db.reminderDao())
                    val activeList = repo.getActiveReminders()
                    val now = System.currentTimeMillis()
                    var count = 0
                    for (reminder in activeList) {
                        if (reminder.triggerTimeMillis > now) {
                            ReminderScheduler.setSystemAlarm(context, reminder)
                            count++
                        }
                    }
                    DebugLogger.logInfo("Boot completed: Rescheduled $count active reminders")
                    // Check SIM state on boot for anti-theft
                    com.example.manager.AntiTheftManager.checkSimState(context)
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Failed to reschedule reminders on boot", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
