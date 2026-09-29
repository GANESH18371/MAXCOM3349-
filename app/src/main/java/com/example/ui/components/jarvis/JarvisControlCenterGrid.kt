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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhoneCallback
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JarvisCard
import com.example.ui.theme.JarvisCardBorder
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisNeonGreen
import com.example.ui.theme.JarvisNeonAmber
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisTeal
import com.example.ui.theme.JarvisTextDim
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary

@Composable
fun JarvisControlCenterGrid(
    onTriggerVoice: () -> Unit,
    onNavigateToAssistant: () -> Unit,
    onNavigateToTools: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CONTROL CENTER MATRIX",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = JarvisTextSecondary,
                letterSpacing = 1.5.sp
            )

            Text(
                text = "8 SUBSYSTEMS",
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = JarvisCyan
            )
        }

        // Row 1
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            JarvisGridCard(
                title = "VOICE MATRIX",
                subtitle = "Speech Assistant",
                icon = Icons.Default.Mic,
                accentColor = JarvisNeonGreen,
                onClick = onTriggerVoice,
                modifier = Modifier.weight(1f),
                tag = "control_voice_matrix"
            )

            JarvisGridCard(
                title = "DEVICE MATRIX",
                subtitle = "Hardware Controls",
                icon = Icons.Default.Bolt,
                accentColor = JarvisCyan,
                onClick = onNavigateToTools,
                modifier = Modifier.weight(1f),
                tag = "control_device_matrix"
            )
        }

        // Row 2
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            JarvisGridCard(
                title = "TASKS & ALARMS",
                subtitle = "Chrono Scheduler",
                icon = Icons.Default.Alarm,
                accentColor = JarvisNeonAmber,
                onClick = onNavigateToTools,
                modifier = Modifier.weight(1f),
                tag = "control_tasks_matrix"
            )

            JarvisGridCard(
                title = "WHATSAPP BOT",
                subtitle = "Smart Auto-Reply",
                icon = Icons.Default.Chat,
                accentColor = JarvisNeonGreen,
                onClick = onNavigateToTools,
                modifier = Modifier.weight(1f),
                tag = "control_whatsapp_matrix"
            )
        }

        // Row 3
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            JarvisGridCard(
                title = "GEMINI VISION",
                subtitle = "Scene Analyzer",
                icon = Icons.Default.CameraAlt,
                accentColor = JarvisCyan,
                onClick = onNavigateToTools,
                modifier = Modifier.weight(1f),
                tag = "control_vision_matrix"
            )

            JarvisGridCard(
                title = "SECURITY SENTRY",
                subtitle = "Anti-Theft Guard",
                icon = Icons.Default.Security,
                accentColor = JarvisTeal,
                onClick = onNavigateToTools,
                modifier = Modifier.weight(1f),
                tag = "control_security_matrix"
            )
        }

        // Row 4
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            JarvisGridCard(
                title = "CALL CONTROL",
                subtitle = "Voice Telecom",
                icon = Icons.Default.PhoneCallback,
                accentColor = JarvisCyan,
                onClick = onNavigateToTools,
                modifier = Modifier.weight(1f),
                tag = "control_calls_matrix"
            )

            JarvisGridCard(
                title = "SYSTEM STUDIO",
                subtitle = "Voice & Settings",
                icon = Icons.Default.Tune,
                accentColor = JarvisNeonAmber,
                onClick = onNavigateToSettings,
                modifier = Modifier.weight(1f),
                tag = "control_settings_matrix"
            )
        }
    }
}

@Composable
private fun JarvisGridCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String
) {
    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    listOf(
                        JarvisCard,
                        JarvisSurface
                    )
                ),
                RoundedCornerShape(14.dp)
            )
            .border(1.dp, JarvisCardBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
            .testTag(tag)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(34.dp)
                        .background(accentColor.copy(alpha = 0.15f), CircleShape)
                        .border(1.dp, accentColor.copy(alpha = 0.4f), CircleShape)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Tech corner notch indicator
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(accentColor, CircleShape)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = title,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = JarvisTextPrimary,
                letterSpacing = 0.8.sp
            )

            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = JarvisTextSecondary
            )
        }
    }
}
