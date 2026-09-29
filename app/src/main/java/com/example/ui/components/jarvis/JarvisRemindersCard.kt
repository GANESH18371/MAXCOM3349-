package com.example.ui.components.jarvis

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppDatabase
import com.example.data.ReminderItem
import com.example.ui.theme.JarvisCard
import com.example.ui.theme.JarvisCardBorder
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisNeonGreen
import com.example.ui.theme.JarvisNeonAmber
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisTextDim
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun JarvisRemindersCard(
    onNavigateToReminders: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val reminders by db.reminderDao().getAllRemindersFlow().collectAsStateWithLifecycle(initialValue = emptyList())

    val activeReminders = reminders.filter { !it.isCompleted }.take(3)
    val timeFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.US)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(JarvisCard, RoundedCornerShape(16.dp))
            .border(1.dp, JarvisCardBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
            .clickable(onClick = onNavigateToReminders)
            .testTag("jarvis_reminders_card")
    ) {
        Column {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(28.dp)
                            .background(JarvisNeonAmber.copy(alpha = 0.15f), CircleShape)
                            .border(1.dp, JarvisNeonAmber.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Alarm,
                            contentDescription = null,
                            tint = JarvisNeonAmber,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "CHRONO TASKS & REMINDERS",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = JarvisTextSecondary,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "${activeReminders.size} ACTIVE SCHEDULED EVENT(S)",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            color = JarvisTextDim
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "VIEW ALL",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = JarvisCyan
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "View Reminders",
                        tint = JarvisCyan,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (activeReminders.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(JarvisSurface, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "No pending alarms or reminder tasks. Say \"Remind me to...\" to create one.",
                        fontSize = 11.sp,
                        color = JarvisTextSecondary
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    activeReminders.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(JarvisSurface, RoundedCornerShape(8.dp))
                                .border(1.dp, JarvisCardBorder.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.task,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = JarvisTextPrimary
                                )
                                Text(
                                    text = timeFormat.format(Date(item.triggerTimeMillis)),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    color = JarvisCyan
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .background(JarvisNeonGreen.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (item.isAlarm) "ALARM" else "TASK",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = JarvisNeonGreen
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
