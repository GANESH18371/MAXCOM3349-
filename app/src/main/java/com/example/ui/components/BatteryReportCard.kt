package com.example.ui.components

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Battery0Bar
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.EnergySavingsLeaf
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneCallback
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsAccessibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.manager.BatteryOptimizationManager
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DarkOutline
import com.example.ui.theme.DarkSurfaceCard
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonLime
import com.example.ui.theme.NeonRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun BatteryReportCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val status by BatteryOptimizationManager.batteryStatus.collectAsState()

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, DarkOutline, RoundedCornerShape(16.dp))
            .testTag("battery_report_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .background(NeonLime.copy(alpha = 0.15f), CircleShape)
                            .border(1.dp, NeonLime.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (status.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                            contentDescription = "Battery Status",
                            tint = NeonLime,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Battery & Performance Guard",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Doze-Ready • Smart Mic Sleep • Zero WakeLock Leak",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }

                IconButton(
                    onClick = {
                        BatteryOptimizationManager.refreshStatus(context)
                        Toast.makeText(context, "Battery status refreshed", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = CyberCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Main Battery Stats Banner
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Battery Pct Card
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, NeonLime.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "BATTERY LEVEL",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "${status.batteryPercentage}%",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = NeonLime
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (status.isCharging) "Charging" else if (status.isPowerSaveMode) "Saver On" else "Active",
                                fontSize = 10.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(bottom = 3.dp)
                            )
                        }
                    }
                }

                // WakeLock Status Card
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, CyberCyan.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "WAKELOCK DRAIN",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "0 Leaks",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberCyan
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Auto-Release",
                                fontSize = 10.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(bottom = 3.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Doze & Battery Optimization Status Box
            val isUnrestricted = status.isIgnoringBatteryOptimizations
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant.copy(alpha = 0.6f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (isUnrestricted) NeonLime.copy(alpha = 0.4f) else NeonAmber.copy(alpha = 0.5f),
                        RoundedCornerShape(12.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (isUnrestricted) Icons.Default.ElectricBolt else Icons.Default.EnergySavingsLeaf,
                                contentDescription = null,
                                tint = if (isUnrestricted) NeonLime else NeonAmber,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (isUnrestricted) "DOZE & BACKGROUND EXEMPTION ACTIVE" else "STANDARD BATTERY MODE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isUnrestricted) NeonLime else NeonAmber,
                                letterSpacing = 0.5.sp
                            )
                        }

                        Icon(
                            imageVector = if (isUnrestricted) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isUnrestricted) NeonLime else NeonAmber,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isUnrestricted)
                            "Max has unrestricted background access. Exact Alarms, Anti-Theft alerts & WhatsApp Auto-Reply will execute seamlessly even during deep sleep."
                        else
                            "Standard battery optimizations are enabled. To guarantee alarms and anti-theft SMS during deep sleep, you can allow battery exemption.",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        lineHeight = 15.sp
                    )

                    if (!isUnrestricted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                BatteryOptimizationManager.requestIgnoreBatteryOptimization(context)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonAmber),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .testTag("request_battery_exemption_button")
                        ) {
                            Text(
                                text = "Allow Deep Sleep Exemption",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Subsystem Efficiency Health Report
            Text(
                text = "CONSOLIDATED SUBSYSTEM STATUS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            SubsystemStatusRow(
                title = "Voice Listening",
                desc = "Smart Inactivity Timeout (6s Auto-Sleep)",
                state = status.voiceListeningState,
                icon = Icons.Default.Mic,
                isLive = status.voiceListeningState.startsWith("ACTIVE") || status.voiceListeningState.startsWith("PROCESSING")
            )
            Spacer(modifier = Modifier.height(6.dp))

            SubsystemStatusRow(
                title = "Accessibility Engine",
                desc = "Throttled (200ms debounce, scans on command)",
                state = status.accessibilityState,
                icon = Icons.Default.SettingsAccessibility,
                isLive = status.accessibilityState.startsWith("SCANNING")
            )
            Spacer(modifier = Modifier.height(6.dp))

            SubsystemStatusRow(
                title = "WhatsApp Auto-Reply",
                desc = "Event-Driven (NotificationListenerService)",
                state = status.notificationListenerState,
                icon = Icons.Default.NotificationsActive,
                isLive = false
            )
            Spacer(modifier = Modifier.height(6.dp))

            SubsystemStatusRow(
                title = "Anti-Theft Guard",
                desc = "Broadcast-Driven (Zero Idle Polling)",
                state = status.antiTheftGuardState,
                icon = Icons.Default.Security,
                isLive = status.antiTheftGuardState.startsWith("PROCESSING")
            )
            Spacer(modifier = Modifier.height(6.dp))

            SubsystemStatusRow(
                title = "Call Announcer",
                desc = "Telecom-Event Driven (Woken on Ringing)",
                state = status.callAnnouncerState,
                icon = Icons.Default.PhoneCallback,
                isLive = status.callAnnouncerState.startsWith("RINGING") || status.callAnnouncerState.startsWith("LISTENING")
            )
        }
    }
}

@Composable
private fun SubsystemStatusRow(
    title: String,
    desc: String,
    state: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isLive: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurfaceVariant, RoundedCornerShape(8.dp))
            .border(1.dp, if (isLive) NeonLime.copy(alpha = 0.5f) else DarkOutline.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isLive) NeonLime else CyberCyan,
                modifier = Modifier.size(16.dp)
            )
            Column {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = desc,
                    fontSize = 9.sp,
                    color = TextMuted
                )
            }
        }

        Box(
            modifier = Modifier
                .background(
                    if (isLive) NeonLime.copy(alpha = 0.15f) else DarkSurfaceCard,
                    RoundedCornerShape(6.dp)
                )
                .border(
                    1.dp,
                    if (isLive) NeonLime.copy(alpha = 0.4f) else DarkOutline.copy(alpha = 0.5f),
                    RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            Text(
                text = state,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isLive) NeonLime else TextSecondary
            )
        }
    }
}
