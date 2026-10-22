package com.example.ui.components.jarvis

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JarvisBackground
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisNeonGreen
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisTeal
import com.example.ui.theme.JarvisTextDim
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun JarvisHeader(
    modifier: Modifier = Modifier
) {
    var currentTimeString by remember { mutableStateOf("") }
    var currentDateString by remember { mutableStateOf("") }

    // Live clock ticker
    LaunchedEffect(Unit) {
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
        val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy", Locale.US)
        while (true) {
            val now = Date()
            currentTimeString = timeFormat.format(now)
            currentDateString = dateFormat.format(now).uppercase()
            delay(1000)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 2.dp)
    ) {
        // Top HUD Metadata Row (Tech Timestamp & Protocol)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .scale(pulseAlpha)
                        .background(JarvisNeonGreen, CircleShape)
                )
                Text(
                    text = "SYS: ACTIVE // HUD PROTOCOL v3.8",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = JarvisNeonGreen,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Text(
                text = if (currentTimeString.isNotEmpty()) "$currentDateString // $currentTimeString" else "SYNCHRONIZING...",
                fontFamily = FontFamily.Monospace,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Medium,
                color = JarvisTextSecondary,
                letterSpacing = 0.3.sp,
                maxLines = 1,
                softWrap = false
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Main Title & Greeting Box
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "M A X",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = JarvisCyan,
                        letterSpacing = 3.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                JarvisCyan.copy(alpha = 0.15f),
                                RoundedCornerShape(3.dp)
                            )
                            .border(1.dp, JarvisCyan.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "AI CORE",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = JarvisCyan,
                            letterSpacing = 0.8.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Text(
                    text = "\"I'm online, Sir. All systems fully operational.\"",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = JarvisTextPrimary,
                    letterSpacing = 0.2.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }

            // Tech Status Badge (Key & Security)
            Box(
                modifier = Modifier
                    .background(
                        Brush.linearGradient(
                            listOf(
                                JarvisSurface,
                                JarvisCyan.copy(alpha = 0.1f)
                            )
                        ),
                        RoundedCornerShape(6.dp)
                    )
                    .border(1.dp, JarvisCyan.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "NEURAL KEY",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                        color = JarvisTextDim,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "ACTIVE // AES-256",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = JarvisCyan,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Glowing divider line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            JarvisCyan.copy(alpha = 0.8f),
                            JarvisTeal.copy(alpha = 0.6f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}
