package com.example.ui.components.jarvis

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.live.GeminiLiveManager
import com.example.live.LiveConnectionState
import com.example.manager.VoiceCommandManager
import com.example.manager.VoiceState
import com.example.ui.theme.JarvisCard
import com.example.ui.theme.JarvisCardBorder
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisNeonAmber
import com.example.ui.theme.JarvisNeonGreen
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisTeal
import com.example.ui.theme.JarvisTextDim
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary
import com.example.util.SecureApiKeyManager
import com.example.util.TtsManager
import kotlin.math.cos
import kotlin.math.sin

enum class AssistantMode {
    LOCAL_OFFLINE,
    GEMINI_LIVE
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun JarvisArcReactor(
    voiceState: VoiceState,
    onTriggerListening: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val liveState by GeminiLiveManager.connectionState.collectAsState()
    val isTtsSpeaking by TtsManager.isSpeaking.collectAsState()

    var activeMode by remember { mutableStateOf(AssistantMode.LOCAL_OFFLINE) }

    val isLiveSessionActive = liveState == LiveConnectionState.LISTENING ||
            liveState == LiveConnectionState.SPEAKING ||
            liveState == LiveConnectionState.CONNECTING

    val isLocalListening = voiceState is VoiceState.Listening
    val isLocalProcessing = voiceState is VoiceState.Processing
    val isSpeaking = isTtsSpeaking || liveState == LiveConnectionState.SPEAKING

    val isAnyListening = isLocalListening || liveState == LiveConnectionState.LISTENING
    val isAnyProcessing = isLocalProcessing || liveState == LiveConnectionState.CONNECTING

    // Mic permission launcher for Gemini Live mode
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            GeminiLiveManager.startLiveSession(context)
        } else {
            Toast.makeText(context, "Microphone permission required for Gemini Live", Toast.LENGTH_SHORT).show()
        }
    }

    // Function to trigger Gemini Live
    val engageLiveMode = {
        if (isLiveSessionActive) {
            GeminiLiveManager.stopLiveSession()
            Toast.makeText(context, "Gemini Live Stream Ended", Toast.LENGTH_SHORT).show()
        } else {
            // Check API key configuration first
            if (!SecureApiKeyManager.isKeyConfigured(context)) {
                Toast.makeText(context, "Please set Gemini API Key in Settings first!", Toast.LENGTH_LONG).show()
            } else {
                val hasMic = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (hasMic) {
                    Toast.makeText(context, "Engaging Gemini Live Audio...", Toast.LENGTH_SHORT).show()
                    GeminiLiveManager.startLiveSession(context)
                } else {
                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
    }

    // Single unified reactor action handler
    val handleArcReactorTap = {
        if (isLiveSessionActive) {
            // If Gemini Live is active, tap stops it
            GeminiLiveManager.stopLiveSession()
        } else if (isTtsSpeaking) {
            // If speaking, tap stops speech
            TtsManager.stop()
        } else {
            // Normal default tap -> trigger offline local command router
            if (activeMode == AssistantMode.GEMINI_LIVE) {
                engageLiveMode()
            } else {
                onTriggerListening()
            }
        }
    }

    val handleArcReactorLongPress = {
        // Long press ALWAYS toggles Gemini Live mode
        engageLiveMode()
    }

    // Animations
    val infiniteTransition = rememberInfiniteTransition(label = "reactorAnimation")

    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isAnyProcessing) 2500 else if (isAnyListening) 4000 else 10000,
                easing = LinearEasing
            )
        ),
        label = "outerRotation"
    )

    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isAnyProcessing) 1800 else if (isAnyListening) 3500 else 7500,
                easing = LinearEasing
            )
        ),
        label = "innerRotation"
    )

    val corePulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isAnyListening) 400 else if (isSpeaking) 550 else 1400,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "corePulse"
    )

    // Dynamic Color Palette depending on State & Mode
    val targetColor = when {
        isLiveSessionActive -> JarvisNeonGreen
        isLocalListening -> JarvisNeonGreen
        isSpeaking -> JarvisCyan
        isAnyProcessing -> JarvisNeonAmber
        activeMode == AssistantMode.GEMINI_LIVE -> Color(0xFFB388FF) // Purple glow for Live standby
        else -> JarvisCyan
    }

    val activeColor by animateColorAsState(targetValue = targetColor, label = "activeColor")

    val stateTitle = when {
        liveState == LiveConnectionState.CONNECTING -> "CONNECTING GEMINI LIVE..."
        liveState == LiveConnectionState.LISTENING -> "GEMINI LIVE LISTENING"
        liveState == LiveConnectionState.SPEAKING -> "GEMINI LIVE RESPONDING"
        isLocalListening -> "LOCAL ENGINE LISTENING..."
        isLocalProcessing -> "ANALYZING COMMAND..."
        isSpeaking -> "MAX EXECUTING // TAP TO STOP"
        activeMode == AssistantMode.GEMINI_LIVE -> "GEMINI LIVE // TAP OR HOLD TO ENGAGE"
        else -> "LOCAL ENGINE // TAP TO ENGAGE"
    }

    val stateSubtitle = when {
        isLiveSessionActive -> "Real-time streaming conversation • Tap to disconnect"
        isLocalListening -> "Say 'YouTube kholo', 'WiFi on', 'Weather', or 'Live mode'"
        isLocalProcessing -> "Routing command to local offline subsystem..."
        isSpeaking -> "Executing response • Tap reactor to stop speech"
        activeMode == AssistantMode.GEMINI_LIVE -> "Continuous natural AI dialogue • Long-press anytime"
        else -> "Tap for Offline Commands (100% fast) • Hold for Gemini Live"
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        JarvisCard,
                        JarvisSurface
                    )
                )
            )
            .border(1.dp, JarvisCardBorder, RoundedCornerShape(20.dp))
            .padding(18.dp)
            .testTag("jarvis_arc_reactor_card")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Header: Card Title & Unified Mode Selector Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = activeColor,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "NEURAL ARC-REACTOR",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = JarvisTextPrimary,
                        letterSpacing = 1.2.sp
                    )
                }

                // Status Badge
                Box(
                    modifier = Modifier
                        .background(activeColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .border(1.dp, activeColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isLiveSessionActive) "GEMINI LIVE" else if (isLocalListening) "LOCAL LISTENING" else if (isSpeaking) "SPEAKING" else "READY",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = activeColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Unified Dual-Mode Switcher Pills (Local Offline vs Gemini Live)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(JarvisSurface, RoundedCornerShape(10.dp))
                    .border(1.dp, JarvisCardBorder, RoundedCornerShape(10.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Pill 1: Offline Local Mode (Default)
                val isLocalSelected = activeMode == AssistantMode.LOCAL_OFFLINE && !isLiveSessionActive
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isLocalSelected) JarvisCyan.copy(alpha = 0.2f) else Color.Transparent)
                        .border(
                            1.dp,
                            if (isLocalSelected) JarvisCyan.copy(alpha = 0.7f) else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable {
                            activeMode = AssistantMode.LOCAL_OFFLINE
                            if (isLiveSessionActive) {
                                GeminiLiveManager.stopLiveSession()
                            }
                        }
                        .padding(vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = if (isLocalSelected) JarvisCyan else JarvisTextDim,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "LOCAL OFFLINE (Tap)",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (isLocalSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isLocalSelected) JarvisCyan else JarvisTextSecondary
                        )
                    }
                }

                // Pill 2: Gemini Live Mode
                val isLiveSelected = activeMode == AssistantMode.GEMINI_LIVE || isLiveSessionActive
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isLiveSelected) JarvisNeonGreen.copy(alpha = 0.2f) else Color.Transparent)
                        .border(
                            1.dp,
                            if (isLiveSelected) JarvisNeonGreen.copy(alpha = 0.7f) else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable {
                            activeMode = AssistantMode.GEMINI_LIVE
                            if (!isLiveSessionActive) {
                                engageLiveMode()
                            }
                        }
                        .padding(vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.HeadsetMic,
                            contentDescription = null,
                            tint = if (isLiveSelected) JarvisNeonGreen else JarvisTextDim,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "GEMINI LIVE (Hold)",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (isLiveSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isLiveSelected) JarvisNeonGreen else JarvisTextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Central Interactive Arc Reactor Canvas (Handles Both Tap and Long-Press)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(190.dp)
                    .clip(CircleShape)
                    .combinedClickable(
                        onClick = handleArcReactorTap,
                        onLongClick = handleArcReactorLongPress
                    )
                    .testTag("arc_reactor_button")
            ) {
                // Background radial glow effect
                Canvas(modifier = Modifier.size(190.dp)) {
                    val center = Offset(size.width / 2, size.height / 2)
                    val radius = size.minDimension / 2

                    // Glow aura
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                activeColor.copy(alpha = if (isAnyListening || isSpeaking || isLiveSessionActive) 0.38f else 0.12f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = radius
                        ),
                        radius = radius
                    )

                    // Outer Track
                    drawCircle(
                        color = JarvisCardBorder,
                        radius = radius - 8.dp.toPx(),
                        style = Stroke(width = 1.5.dp.toPx())
                    )

                    // Outer Rotating Segmented Ring (8 arcs)
                    val numSegments = 8
                    val arcAngle = 360f / numSegments
                    val gap = 12f
                    for (i in 0 until numSegments) {
                        val startAngle = outerRotation + (i * arcAngle) + (gap / 2)
                        drawArc(
                            color = activeColor.copy(alpha = if (i % 2 == 0) 0.85f else 0.4f),
                            startAngle = startAngle,
                            sweepAngle = arcAngle - gap,
                            useCenter = false,
                            topLeft = Offset(14.dp.toPx(), 14.dp.toPx()),
                            size = Size(size.width - 28.dp.toPx(), size.height - 28.dp.toPx()),
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    // Middle Counter-Rotating Ticks (16 tick marks)
                    val tickRadius = radius - 26.dp.toPx()
                    val numTicks = 16
                    for (i in 0 until numTicks) {
                        val angleRad = Math.toRadians((innerRotation + (i * (360.0 / numTicks))).toDouble())
                        val startX = center.x + (tickRadius - 5.dp.toPx()) * cos(angleRad).toFloat()
                        val startY = center.y + (tickRadius - 5.dp.toPx()) * sin(angleRad).toFloat()
                        val endX = center.x + (tickRadius + 2.dp.toPx()) * cos(angleRad).toFloat()
                        val endY = center.y + (tickRadius + 2.dp.toPx()) * sin(angleRad).toFloat()

                        drawLine(
                            color = activeColor.copy(alpha = 0.5f),
                            start = Offset(startX, startY),
                            end = Offset(endX, endY),
                            strokeWidth = 1.5.dp.toPx()
                        )
                    }

                    // Inner Stator Circle
                    drawCircle(
                        color = activeColor.copy(alpha = 0.35f),
                        radius = radius - 38.dp.toPx(),
                        style = Stroke(width = 1.5.dp.toPx())
                    )

                    // Glowing Triangular Core Frame
                    val triRadius = radius - 44.dp.toPx()
                    val path = Path()
                    for (i in 0 until 3) {
                        val angle = Math.toRadians((outerRotation * 0.5 + i * 120.0).toDouble())
                        val x = center.x + triRadius * cos(angle).toFloat()
                        val y = center.y + triRadius * sin(angle).toFloat()
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    path.close()
                    drawPath(
                        path = path,
                        color = activeColor.copy(alpha = 0.6f),
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Center Pulsing Core & Icon
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(64.dp)
                        .scale(corePulse)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    activeColor.copy(alpha = 0.35f),
                                    JarvisSurface
                                )
                            ),
                            CircleShape
                        )
                        .border(1.5.dp, activeColor, CircleShape)
                ) {
                    val coreIcon = when {
                        isSpeaking -> Icons.Default.RecordVoiceOver
                        isLiveSessionActive -> Icons.Default.GraphicEq
                        isLocalListening -> Icons.Default.Mic
                        else -> Icons.Default.Mic
                    }
                    Icon(
                        imageVector = coreIcon,
                        contentDescription = "Voice Assistant Core",
                        tint = activeColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // State Title Display
            Text(
                text = stateTitle,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = activeColor,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Subtitle & Operational Guidance
            Text(
                text = stateSubtitle,
                fontSize = 11.sp,
                color = JarvisTextSecondary,
                letterSpacing = 0.3.sp
            )
        }
    }
}
