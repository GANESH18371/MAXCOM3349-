package com.example.ui.components.jarvis

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.live.GeminiLiveManager
import com.example.live.LiveConnectionState
import com.example.manager.VoiceState
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
import com.example.util.TtsManager
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun JarvisArcReactor(
    voiceState: VoiceState,
    onTriggerListening: () -> Unit,
    modifier: Modifier = Modifier
) {
    val liveState by GeminiLiveManager.connectionState.collectAsState()
    val isTtsSpeaking by TtsManager.isSpeaking.collectAsState()

    val isListening = voiceState is VoiceState.Listening || liveState == LiveConnectionState.LISTENING
    val isSpeaking = isTtsSpeaking || liveState == LiveConnectionState.SPEAKING
    val isProcessing = voiceState is VoiceState.Processing || liveState == LiveConnectionState.CONNECTING

    // Animations
    val infiniteTransition = rememberInfiniteTransition(label = "reactorAnimation")

    // Outer slow clockwise rotation
    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isProcessing) 3000 else if (isListening) 5000 else 12000,
                easing = LinearEasing
            )
        ),
        label = "outerRotation"
    )

    // Inner fast counter-clockwise rotation
    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isProcessing) 2000 else if (isListening) 4000 else 8000,
                easing = LinearEasing
            )
        ),
        label = "innerRotation"
    )

    // Breathing core pulse
    val corePulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isListening) 400 else if (isSpeaking) 600 else 1500,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "corePulse"
    )

    val activeColor = when {
        isListening -> JarvisNeonGreen
        isSpeaking -> JarvisCyan
        isProcessing -> JarvisNeonAmber
        else -> JarvisCyan
    }

    val stateTitle = when {
        isListening -> "AUDIO MATRIX ACTIVE"
        isSpeaking -> "NEURAL TRANSMISSION"
        isProcessing -> "ANALYZING COMMAND"
        else -> "STANDBY // TAP TO ENGAGE"
    }

    val stateSubtitle = when {
        isListening -> "Streaming voice input • Speak now..."
        isSpeaking -> "Max is currently speaking..."
        isProcessing -> "Routing command to local engine..."
        else -> "Jarvis core ready for voice or touch commands"
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
            // Header Tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "NEURAL ARC-REACTOR",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = JarvisTextSecondary,
                    letterSpacing = 1.5.sp
                )

                Box(
                    modifier = Modifier
                        .background(activeColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .border(1.dp, activeColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isListening) "LISTENING" else if (isSpeaking) "SPEAKING" else "ONLINE",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = activeColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Central Interactive Arc Reactor Canvas
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(190.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onTriggerListening)
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
                                activeColor.copy(alpha = if (isListening || isSpeaking) 0.35f else 0.12f),
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
                            color = JarvisCyan.copy(alpha = 0.5f),
                            start = Offset(startX, startY),
                            end = Offset(endX, endY),
                            strokeWidth = 1.5.dp.toPx()
                        )
                    }

                    // Inner Stator Circle
                    drawCircle(
                        color = JarvisCyan.copy(alpha = 0.35f),
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
                                    activeColor.copy(alpha = 0.3f),
                                    JarvisSurface
                                )
                            ),
                            CircleShape
                        )
                        .border(1.5.dp, activeColor, CircleShape)
                ) {
                    Icon(
                        imageVector = if (isSpeaking) Icons.Default.RecordVoiceOver else Icons.Default.Mic,
                        contentDescription = "Voice Assistant Core",
                        tint = activeColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // State Display
            Text(
                text = stateTitle,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = activeColor,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = stateSubtitle,
                fontSize = 11.sp,
                color = JarvisTextSecondary,
                letterSpacing = 0.3.sp
            )
        }
    }
}
