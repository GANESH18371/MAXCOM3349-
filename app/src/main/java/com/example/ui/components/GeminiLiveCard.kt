package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VoiceOverOff
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.live.GeminiLiveManager
import com.example.live.LiveConnectionState
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
fun GeminiLiveCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val state by GeminiLiveManager.connectionState.collectAsState()
    val micAmp by GeminiLiveManager.micAmplitude.collectAsState()
    val speakerAmp by GeminiLiveManager.speakerAmplitude.collectAsState()
    val liveMessages by GeminiLiveManager.liveMessages.collectAsState()
    val errorMessage by GeminiLiveManager.lastErrorMessage.collectAsState()
    val isBargeInActive by GeminiLiveManager.isBargeInActive.collectAsState()

    val isSessionActive = state == LiveConnectionState.LISTENING ||
            state == LiveConnectionState.SPEAKING ||
            state == LiveConnectionState.CONNECTING

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            GeminiLiveManager.startLiveSession(context)
        } else {
            Toast.makeText(context, "Microphone permission required for Gemini Live", Toast.LENGTH_SHORT).show()
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isSessionActive) CyberCyan.copy(alpha = 0.6f) else DarkOutline,
                RoundedCornerShape(16.dp)
            )
            .testTag("gemini_live_card")
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
                            .background(
                                if (isSessionActive) CyberCyan.copy(alpha = 0.2f) else DarkSurfaceVariant,
                                CircleShape
                            )
                            .border(
                                1.dp,
                                if (isSessionActive) CyberCyan else DarkOutline,
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.HeadsetMic,
                            contentDescription = "Gemini Live",
                            tint = if (isSessionActive) CyberCyan else TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Gemini Live Real-Time Audio",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .background(CyberCyan.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                    .border(1.dp, CyberCyan.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = GeminiLiveManager.GEMINI_LIVE_MODEL,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyberCyan
                                )
                            }
                        }
                        Text(
                            text = "Audio-to-Audio Streaming • Chunked PCM • Full Barge-In",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }

                // Live Connection State Indicator Pill
                ConnectionStatePill(state = state)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Error Banner (if any, e.g. model not found / key missing)
            if (errorMessage != null) {
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = NeonRed.copy(alpha = 0.15f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, NeonRed.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = NeonRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = errorMessage ?: "",
                            fontSize = 11.sp,
                            color = NeonRed,
                            lineHeight = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Real-Time Audio Streaming Visualizer Card
            LiveAudioVisualizerCard(
                state = state,
                micAmplitude = micAmp,
                speakerAmplitude = speakerAmp,
                isBargeInActive = isBargeInActive
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Action Button Row
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Main Start/Stop Streaming Button
                Button(
                    onClick = {
                        if (isSessionActive) {
                            GeminiLiveManager.stopLiveSession()
                        } else {
                            val hasMic = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasMic) {
                                GeminiLiveManager.startLiveSession(context)
                            } else {
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSessionActive) NeonRed else CyberCyan
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1.3f)
                        .height(44.dp)
                        .testTag("gemini_live_toggle_button")
                ) {
                    Icon(
                        imageVector = if (isSessionActive) Icons.Default.Stop else Icons.Default.HeadsetMic,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isSessionActive) "End Live Stream" else "Start Live Audio",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }

                // Barge-In Interruption Button (Simulate speech interrupt)
                if (state == LiveConnectionState.SPEAKING) {
                    Button(
                        onClick = {
                            GeminiLiveManager.triggerBargeIn()
                            Toast.makeText(context, "Interrupted Max! Listening to you...", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonAmber),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("barge_in_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Hearing,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Interrupt / Speak",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Live Real-Time Conversation Transcript
            Text(
                text = "LIVE CONVERSATION TRANSCRIPT",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(6.dp))

            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .border(1.dp, DarkOutline.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            ) {
                if (liveMessages.isEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = if (isSessionActive)
                                "Streaming audio to Gemini Live... Speak in Hindi or English."
                            else
                                "Tap 'Start Live Audio' to begin instant real-time conversation.",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(liveMessages) { msg ->
                            Row(
                                horizontalArrangement = if (msg.sender == "User") Arrangement.End else Arrangement.Start,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (msg.sender == "User") CyberCyan.copy(alpha = 0.2f) else DarkSurfaceCard,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .border(
                                            1.dp,
                                            if (msg.sender == "User") CyberCyan.copy(alpha = 0.4f) else DarkOutline,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = msg.sender,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (msg.sender == "User") CyberCyan else NeonLime
                                        )
                                        Text(
                                            text = msg.text,
                                            fontSize = 10.sp,
                                            color = TextPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionStatePill(state: LiveConnectionState) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val (bg, textColor, label) = when (state) {
        LiveConnectionState.DISCONNECTED -> Triple(DarkSurfaceVariant, TextMuted, "DISCONNECTED")
        LiveConnectionState.CONNECTING -> Triple(NeonAmber.copy(alpha = 0.2f), NeonAmber, "CONNECTING...")
        LiveConnectionState.LISTENING -> Triple(NeonLime.copy(alpha = 0.2f), NeonLime, "LISTENING (MIC)")
        LiveConnectionState.SPEAKING -> Triple(CyberCyan.copy(alpha = 0.2f), CyberCyan, "MAX SPEAKING")
        LiveConnectionState.ERROR -> Triple(NeonRed.copy(alpha = 0.2f), NeonRed, "ERROR")
    }

    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(12.dp))
            .border(1.dp, textColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .scale(if (state == LiveConnectionState.LISTENING || state == LiveConnectionState.SPEAKING) pulseScale else 1f)
                    .background(textColor, CircleShape)
            )
            Text(
                text = label,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
    }
}

@Composable
private fun LiveAudioVisualizerCard(
    state: LiveConnectionState,
    micAmplitude: Float,
    speakerAmplitude: Float,
    isBargeInActive: Boolean
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                when (state) {
                    LiveConnectionState.LISTENING -> NeonLime.copy(alpha = 0.4f)
                    LiveConnectionState.SPEAKING -> CyberCyan.copy(alpha = 0.5f)
                    else -> DarkOutline.copy(alpha = 0.5f)
                },
                RoundedCornerShape(12.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = when (state) {
                        LiveConnectionState.LISTENING -> "LIVE MIC STREAMING (16kHz PCM)"
                        LiveConnectionState.SPEAKING -> "STREAMING AUDIO OUTPUT (24kHz PCM)"
                        LiveConnectionState.CONNECTING -> "ESTABLISHING WEBSOCKET CHANNEL..."
                        LiveConnectionState.ERROR -> "CONNECTION FAILED"
                        else -> "AUDIO STREAM IDLE"
                    },
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (state) {
                        LiveConnectionState.LISTENING -> NeonLime
                        LiveConnectionState.SPEAKING -> CyberCyan
                        LiveConnectionState.ERROR -> NeonRed
                        else -> TextMuted
                    },
                    letterSpacing = 0.5.sp
                )

                if (isBargeInActive) {
                    Text(
                        text = "⚡ BARGE-IN TRIGGERED",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonAmber
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Animated Waveform Bars
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
            ) {
                val activeAmp = if (state == LiveConnectionState.SPEAKING) speakerAmplitude else micAmplitude
                val activeColor = if (state == LiveConnectionState.SPEAKING) CyberCyan else NeonLime

                val multipliers = listOf(0.4f, 0.7f, 1.0f, 1.3f, 1.6f, 1.2f, 0.9f, 1.4f, 1.8f, 1.1f, 0.6f, 1.3f, 0.8f, 0.5f)
                multipliers.forEach { mul ->
                    val barHeight = if (state == LiveConnectionState.LISTENING || state == LiveConnectionState.SPEAKING) {
                        (activeAmp * mul * 32.dp.value).coerceIn(4f, 32f).dp
                    } else {
                        4.dp
                    }

                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(barHeight)
                            .background(
                                if (state == LiveConnectionState.DISCONNECTED) DarkOutline else activeColor,
                                RoundedCornerShape(2.dp)
                            )
                    )
                }
            }
        }
    }
}
