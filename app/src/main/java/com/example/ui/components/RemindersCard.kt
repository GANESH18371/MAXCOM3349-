package com.example.ui.components

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessAlarm
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppDatabase
import com.example.data.ReminderItem
import com.example.manager.ReminderScheduler
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DarkCard
import com.example.ui.theme.DarkOutline
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonYellow
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.TtsManager
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun RemindersCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getInstance(context) }
    val reminders by db.reminderDao().getAllRemindersFlow().collectAsStateWithLifecycle(initialValue = emptyList())

    val activeReminders = reminders.filter { !it.isCompleted }

    var showAddDialog by remember { mutableStateOf(false) }
    var newTaskText by remember { mutableStateOf("") }
    var newMinutesText by remember { mutableStateOf("10") }
    var newIsAlarm by remember { mutableStateOf(false) }

    val alarmManager = remember { context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager }
    val needsExactAlarmPermission = remember(reminders) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager != null && !alarmManager.canScheduleExactAlarms()
        } else {
            false
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkCard)
            .border(1.dp, NeonPink.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(16.dp)
            .testTag("reminders_card")
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(NeonPink.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Alarm,
                            contentDescription = "Reminders Icon",
                            tint = NeonPink,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "REMINDERS & ALARMS",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(NeonPink.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${activeReminders.size} Active",
                                    color = NeonPink,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            text = "Room DB • Offline Local AlarmManager",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Read aloud button
                    IconButton(
                        onClick = {
                            if (activeReminders.isEmpty()) {
                                TtsManager.speak("आपका कोई एक्टिव रिमाइंडर या अलार्म नहीं है.")
                            } else {
                                val sb = StringBuilder("आपके ${activeReminders.size} एक्टिव रिमाइंडर्स हैं: ")
                                activeReminders.forEachIndexed { i, rem ->
                                    val type = if (rem.isAlarm) "अलार्म" else "रिमाइंडर"
                                    sb.append("${i + 1}. ${rem.task} ${rem.formattedTime} पर. ")
                                }
                                TtsManager.speak(sb.toString())
                            }
                        },
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(DarkSurface)
                            .testTag("speak_reminders_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Speak Reminders",
                            tint = CyberCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Add button
                    IconButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(NeonPink.copy(alpha = 0.2f))
                            .testTag("add_reminder_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Reminder",
                            tint = NeonPink,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Android 12+ Exact Alarm Permission warning
            if (needsExactAlarmPermission) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurface)
                        .border(1.dp, NeonYellow.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = NeonYellow,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Exact Alarm Permission needed on Android 12+",
                                color = NeonYellow,
                                fontSize = 11.sp
                            )
                        }
                        Button(
                            onClick = { ReminderScheduler.checkAndRequestExactAlarmPermission(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonYellow),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.testTag("exact_alarm_perm_btn")
                        ) {
                            Text("Grant", color = DarkCard, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Reminders List
            if (reminders.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkSurface)
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No Reminders or Alarms set yet",
                            color = TextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Bolein: \"mujhe 5 baje chai ka yaad dilana\" ya \"7 baje alarm laga do\"",
                            color = CyberCyan.copy(alpha = 0.8f),
                            fontSize = 11.sp
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    reminders.take(6).forEach { reminder ->
                        ReminderRowItem(
                            reminder = reminder,
                            onDelete = {
                                ReminderScheduler.cancelReminder(context, reminder.id)
                                Toast.makeText(context, "Deleted: ${reminder.task}", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                    if (reminders.size > 6) {
                        Text(
                            text = "+ ${reminders.size - 6} more stored in Room database",
                            color = TextMuted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Voice Examples & Clear All
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Bolein: \"mera [kaam] wala reminder cancel karo\"",
                    color = TextMuted,
                    fontSize = 10.sp
                )
                if (reminders.isNotEmpty()) {
                    Text(
                        text = "Clear All",
                        color = Color(0xFFFF5252),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable {
                                scope.launch {
                                    db.reminderDao().deleteAllReminders()
                                    Toast.makeText(context, "All reminders cleared", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(4.dp)
                            .testTag("clear_all_reminders_btn")
                    )
                }
            }
        }
    }

    // Add Reminder Dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = DarkCard,
            title = {
                Text(
                    text = if (newIsAlarm) "Add New Alarm" else "Add New Reminder",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { newIsAlarm = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!newIsAlarm) NeonPink else DarkSurface
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("🔔 Reminder", color = if (!newIsAlarm) DarkCard else TextSecondary, fontSize = 12.sp)
                        }
                        Button(
                            onClick = { newIsAlarm = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (newIsAlarm) CyberCyan else DarkSurface
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("⏰ Alarm", color = if (newIsAlarm) DarkCard else TextSecondary, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = newTaskText,
                        onValueChange = { newTaskText = it },
                        label = { Text("Task description (kaam)", color = TextMuted) },
                        placeholder = { Text("e.g. Medicine lena, Meeting", color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = DarkOutline
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = newMinutesText,
                        onValueChange = { newMinutesText = it },
                        label = { Text("Trigger after minutes (samay)", color = TextMuted) },
                        placeholder = { Text("Minutes (e.g. 5, 10, 30)", color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = DarkOutline
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val task = if (newTaskText.isNotBlank()) newTaskText.trim() else if (newIsAlarm) "Alarm" else "Reminder"
                        val mins = newMinutesText.toIntOrNull() ?: 5
                        val targetCal = Calendar.getInstance().apply {
                            add(Calendar.MINUTE, mins)
                        }
                        ReminderScheduler.scheduleReminder(
                            context = context,
                            task = task,
                            triggerTimeMillis = targetCal.timeInMillis,
                            isAlarm = newIsAlarm
                        )
                        Toast.makeText(context, "$task scheduled in $mins min", Toast.LENGTH_SHORT).show()
                        showAddDialog = false
                        newTaskText = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (newIsAlarm) CyberCyan else NeonPink),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Set", color = DarkCard, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }
}

@Composable
private fun ReminderRowItem(
    reminder: ReminderItem,
    onDelete: () -> Unit
) {
    val isAlarm = reminder.isAlarm
    val badgeColor = if (isAlarm) CyberCyan else NeonPink

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurface)
            .border(1.dp, if (reminder.isCompleted) DarkOutline else badgeColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(badgeColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isAlarm) Icons.Default.AccessAlarm else Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = badgeColor,
                    modifier = Modifier.size(15.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = reminder.task,
                        color = if (reminder.isCompleted) TextMuted else TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (reminder.isCompleted) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Completed",
                            tint = NeonGreen,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
                Text(
                    text = "${reminder.formattedTime} • ${if (isAlarm) "Alarm" else "Reminder"}",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .size(30.dp)
                .testTag("delete_reminder_${reminder.id}")
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete Reminder",
                tint = Color(0xFFFF5252).copy(alpha = 0.8f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
