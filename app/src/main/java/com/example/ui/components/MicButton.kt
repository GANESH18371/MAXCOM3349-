package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.manager.VoiceState
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.NeonLime
import com.example.ui.theme.NeonRed

@Composable
fun MicButton(
    voiceState: VoiceState,
    onMicClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isListening = voiceState is VoiceState.Listening
    val isProcessing = voiceState is VoiceState.Processing

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.25f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = if (isListening) 0.8f else 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(130.dp)
        ) {
            // Pulse Wave Ring 1
            if (isListening) {
                Box(
                    modifier = Modifier
                        .size(126.dp)
                        .scale(pulseScale)
                        .background(CyberCyan.copy(alpha = glowAlpha * 0.4f), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(108.dp)
                        .scale(pulseScale * 0.9f)
                        .background(NeonLime.copy(alpha = glowAlpha * 0.3f), CircleShape)
                )
            }

            // Main Mic Button
            val buttonBg = when {
                isListening -> Brush.radialGradient(listOf(NeonLime, CyberCyan))
                isProcessing -> Brush.radialGradient(listOf(CyberCyan, Color(0xFF0288D1)))
                voiceState is VoiceState.Error -> Brush.radialGradient(listOf(NeonRed, Color(0xFF990000)))
                else -> Brush.radialGradient(listOf(Color(0xFF00E5FF), Color(0xFF007799)))
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(86.dp)
                    .background(buttonBg, CircleShape)
                    .border(2.dp, CyberCyan.copy(alpha = 0.8f), CircleShape)
                    .testTag("mic_voice_button")
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onMicClick
                    )
            ) {
                Icon(
                    imageVector = when {
                        isListening -> Icons.Default.Stop
                        isProcessing -> Icons.Default.GraphicEq
                        else -> Icons.Default.Mic
                    },
                    contentDescription = "Microphone Command Button",
                    tint = Color.Black,
                    modifier = Modifier.size(42.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        val statusText = when (voiceState) {
            is VoiceState.Idle -> "Tap to Speak (Hindi / English)"
            is VoiceState.Listening -> "Listening… Say 'Open App' or 'Toggle Hardware'"
            is VoiceState.Processing -> "Processing: \"${voiceState.recognizedText}\""
            is VoiceState.Success -> voiceState.message
            is VoiceState.Error -> voiceState.message
        }

        val textColor = when (voiceState) {
            is VoiceState.Listening -> NeonLime
            is VoiceState.Success -> CyberCyan
            is VoiceState.Error -> NeonRed
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }

        Text(
            text = statusText,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
