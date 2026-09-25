package com.example.ui.components

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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.example.manager.WhatsAppAutoReplyManager
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DarkOutline
import com.example.ui.theme.DarkSurfaceCard
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonLime
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun WhatsAppAutoReplyCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isAutoReplyOn by WhatsAppAutoReplyManager.isAutoReplyEnabled.collectAsState()
    val recentMessages by WhatsAppAutoReplyManager.recentMessages.collectAsState()
    val isNotificationAccessGranted = WhatsAppAutoReplyManager.isNotificationAccessGranted(context)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, if (isAutoReplyOn) NeonLime.copy(alpha = 0.5f) else DarkOutline, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Card Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFF25D366).copy(alpha = 0.15f), CircleShape)
                            .border(1.dp, Color(0xFF25D366).copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Chat,
                            contentDescription = null,
                            tint = Color(0xFF25D366),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "WhatsApp Auto-Reply",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .background(CyberCyan.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                    .border(0.5.dp, CyberCyan.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = CyberCyan,
                                        modifier = Modifier.size(9.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "GEMINI AI",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CyberCyan
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (isAutoReplyOn) "Active (Say 'auto-reply band karo')" else "Default OFF (Say 'auto-reply on karo')",
                            fontSize = 11.sp,
                            color = if (isAutoReplyOn) NeonLime else TextMuted
                        )
                    }
                }

                Switch(
                    checked = isAutoReplyOn,
                    onCheckedChange = { enable ->
                        WhatsAppAutoReplyManager.setAutoReplyEnabled(context, enable, announceWithTts = true)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = NeonLime,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = DarkSurfaceVariant
                    ),
                    modifier = Modifier.testTag("auto_reply_toggle_switch")
                )
            }

            // Notification Access Banner if missing
            if (!isNotificationAccessGranted) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NeonAmber.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                        .border(1.dp, NeonAmber.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = NeonAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Notification Access Required",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonAmber
                        )
                        Text(
                            text = "Required to capture incoming WhatsApp messages for auto-reply.",
                            fontSize = 10.sp,
                            color = TextSecondary,
                            lineHeight = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = { WhatsAppAutoReplyManager.openNotificationAccessSettings(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonAmber, contentColor = Color.Black),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp).testTag("grant_notification_access_btn")
                    ) {
                        Text("Grant", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Test Simulation Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Simulate Incoming Message:",
                    fontSize = 11.sp,
                    color = TextMuted,
                    fontWeight = FontWeight.Medium
                )
                Box(
                    modifier = Modifier
                        .background(DarkSurfaceVariant, RoundedCornerShape(8.dp))
                        .border(1.dp, CyberCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .clickable {
                            WhatsAppAutoReplyManager.onWhatsAppMessageReceived(
                                context = context,
                                sender = "Rahul Sharma",
                                messageText = "Kaha ho bhai? Kab free hoge?",
                                sbn = null
                            )
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("simulate_whatsapp_msg_chip")
                ) {
                    Text(
                        text = "📩 Test 'Kaha ho bhai?'",
                        fontSize = 10.sp,
                        color = CyberCyan,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Recent Messages List
            if (recentMessages.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Recent Messages & Auto-Replies:",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    recentMessages.take(3).forEach { item ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkSurfaceVariant, RoundedCornerShape(8.dp))
                                .border(0.5.dp, DarkOutline, RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "From: ${item.sender}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                if (item.isSent) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = NeonLime,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(text = "Sent", fontSize = 9.sp, color = NeonLime)
                                    }
                                }
                            }
                            Text(
                                text = "\"${item.message}\"",
                                fontSize = 10.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            if (item.reply != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "🤖 Reply: \"${item.reply}\"",
                                    fontSize = 10.sp,
                                    color = CyberCyan,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
