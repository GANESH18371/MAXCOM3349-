package com.example.ui.components.jarvis

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.manager.AntiTheftManager
import com.example.manager.WhatsAppAutoReplyManager
import com.example.service.MaxAccessibilityService
import com.example.ui.theme.JarvisCard
import com.example.ui.theme.JarvisCardBorder
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisNeonGreen
import com.example.ui.theme.JarvisNeonAmber
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisTextDim
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary

@Composable
fun JarvisSystemNotificationsCard(
    modifier: Modifier = Modifier
) {
    val isAccessibilityLive by MaxAccessibilityService.isServiceEnabled.collectAsState()
    val isAutoReplyEnabled by WhatsAppAutoReplyManager.isAutoReplyEnabled.collectAsState()
    val isAntiTheftEnabled by AntiTheftManager.isGuardEnabled.collectAsState()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(JarvisCard, RoundedCornerShape(16.dp))
            .border(1.dp, JarvisCardBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
            .testTag("jarvis_notifications_card")
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
                            .background(JarvisCyan.copy(alpha = 0.15f), CircleShape)
                            .border(1.dp, JarvisCyan.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = JarvisCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "SYSTEM STATUS & NOTIFICATIONS",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = JarvisTextSecondary,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "DAEMON TELEMETRY & SECURITY MATRIX",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            color = JarvisTextDim
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Notifications List / Status Items
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Item 1: Accessibility Daemon
                StatusNotificationRow(
                    label = "ACCESSIBILITY DAEMON",
                    detail = if (isAccessibilityLive) "Global navigation & screen control active" else "Passive mode • Click Tools to enable",
                    isActive = isAccessibilityLive
                )

                // Item 2: WhatsApp Auto-Reply Listener
                StatusNotificationRow(
                    label = "WHATSAPP AUTO-REPLY",
                    detail = if (isAutoReplyEnabled) "Neural smart auto-reply listening" else "Standby mode • Ready to engage",
                    isActive = isAutoReplyEnabled
                )

                // Item 3: Anti-Theft Guard Security
                StatusNotificationRow(
                    label = "ANTI-THEFT SENTRY",
                    detail = if (isAntiTheftEnabled) "Armed • Pocket & motion sensors active" else "Standby mode • Ready to arm",
                    isActive = isAntiTheftEnabled
                )
            }
        }
    }
}

@Composable
private fun StatusNotificationRow(
    label: String,
    detail: String,
    isActive: Boolean
) {
    val statusColor = if (isActive) JarvisNeonGreen else JarvisNeonAmber
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(JarvisSurface, RoundedCornerShape(8.dp))
            .border(1.dp, JarvisCardBorder.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = JarvisTextPrimary
            )
            Text(
                text = detail,
                fontSize = 11.sp,
                color = JarvisTextSecondary
            )
        }

        Box(
            modifier = Modifier
                .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = if (isActive) "ACTIVE" else "STANDBY",
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = statusColor
            )
        }
    }
}
