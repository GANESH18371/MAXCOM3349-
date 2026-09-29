package com.example.ui.components.jarvis

import android.app.ActivityManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.manager.BatteryOptimizationManager
import com.example.ui.theme.JarvisCard
import com.example.ui.theme.JarvisCardBorder
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisNeonGreen
import com.example.ui.theme.JarvisNeonAmber
import com.example.ui.theme.JarvisTeal
import com.example.ui.theme.JarvisTextDim
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary
import kotlinx.coroutines.delay

data class MemoryTelemetry(
    val usedPercentage: Int,
    val usedGb: Float,
    val totalGb: Float
)

data class StorageTelemetry(
    val usedPercentage: Int,
    val usedGb: Float,
    val totalGb: Float
)

@Composable
fun JarvisTelemetryGauges(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val batteryStatus by BatteryOptimizationManager.batteryStatus.collectAsState()

    var memTelemetry by remember {
        mutableStateOf(getMemoryTelemetry(context))
    }
    var storageTelemetry by remember {
        mutableStateOf(getStorageTelemetry())
    }

    // Periodic telemetry refresh
    LaunchedEffect(Unit) {
        while (true) {
            memTelemetry = getMemoryTelemetry(context)
            storageTelemetry = getStorageTelemetry()
            delay(5000)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(JarvisCard, RoundedCornerShape(16.dp))
            .border(1.dp, JarvisCardBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
            .testTag("jarvis_telemetry_card")
    ) {
        Column {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SYSTEM TELEMETRY GAUGES",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = JarvisTextSecondary,
                    letterSpacing = 1.sp
                )

                Text(
                    text = "HARDWARE HUD",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = JarvisCyan
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3 Circular Arc Gauges in a Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Battery Gauge
                CircularArcGauge(
                    title = "BATTERY",
                    percent = batteryStatus.batteryPercentage,
                    subtext = if (batteryStatus.isCharging) "CHARGING" else "OPTIMAL",
                    icon = if (batteryStatus.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                    accentColor = if (batteryStatus.batteryPercentage > 20) JarvisNeonGreen else JarvisNeonAmber,
                    modifier = Modifier.weight(1f)
                )

                // RAM Gauge
                CircularArcGauge(
                    title = "RAM",
                    percent = memTelemetry.usedPercentage,
                    subtext = "%.1f/%.0fGB".format(memTelemetry.usedGb, memTelemetry.totalGb),
                    icon = Icons.Default.Memory,
                    accentColor = JarvisCyan,
                    modifier = Modifier.weight(1f)
                )

                // Storage Gauge
                CircularArcGauge(
                    title = "STORAGE",
                    percent = storageTelemetry.usedPercentage,
                    subtext = "%.0f/%.0fGB".format(storageTelemetry.usedGb, storageTelemetry.totalGb),
                    icon = Icons.Default.Storage,
                    accentColor = JarvisTeal,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun CircularArcGauge(
    title: String,
    percent: Int,
    subtext: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val animatedProgress = remember { Animatable(0f) }
    LaunchedEffect(percent) {
        animatedProgress.animateTo(
            targetValue = percent.coerceIn(0, 100) / 100f,
            animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(horizontal = 4.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(86.dp)
        ) {
            Canvas(modifier = Modifier.size(86.dp)) {
                val strokeWidth = 5.dp.toPx()
                val padding = strokeWidth / 2 + 2.dp.toPx()
                val arcSize = Size(size.width - padding * 2, size.height - padding * 2)
                val topLeft = Offset(padding, padding)

                // 270 degree arc gauge from 135 deg to 405 deg (270 sweep)
                val startAngle = 135f
                val totalSweep = 270f

                // Track
                drawArc(
                    color = JarvisCardBorder,
                    startAngle = startAngle,
                    sweepAngle = totalSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Progress Arc
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(
                            accentColor.copy(alpha = 0.6f),
                            accentColor
                        )
                    ),
                    startAngle = startAngle,
                    sweepAngle = totalSweep * animatedProgress.value,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            // Center Content
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "$percent%",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = JarvisTextPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = title,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = JarvisTextPrimary,
            letterSpacing = 1.sp
        )

        Text(
            text = subtext,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            color = JarvisTextDim
        )
    }
}

private fun getMemoryTelemetry(context: Context): MemoryTelemetry {
    return try {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)
        val totalBytes = memInfo.totalMem
        val availBytes = memInfo.availMem
        val usedBytes = totalBytes - availBytes

        val totalGb = totalBytes.toDouble() / (1024 * 1024 * 1024)
        val usedGb = usedBytes.toDouble() / (1024 * 1024 * 1024)
        val percent = if (totalBytes > 0) ((usedBytes.toDouble() / totalBytes) * 100).toInt() else 0

        MemoryTelemetry(percent, usedGb.toFloat(), totalGb.toFloat())
    } catch (_: Exception) {
        MemoryTelemetry(50, 3.0f, 6.0f)
    }
}

private fun getStorageTelemetry(): StorageTelemetry {
    return try {
        val statFs = StatFs(Environment.getDataDirectory().path)
        val blockSize = statFs.blockSizeLong
        val totalBlocks = statFs.blockCountLong
        val availBlocks = statFs.availableBlocksLong

        val totalBytes = totalBlocks * blockSize
        val availBytes = availBlocks * blockSize
        val usedBytes = totalBytes - availBytes

        val totalGb = totalBytes.toDouble() / (1024 * 1024 * 1024)
        val usedGb = usedBytes.toDouble() / (1024 * 1024 * 1024)
        val percent = if (totalBytes > 0) ((usedBytes.toDouble() / totalBytes) * 100).toInt() else 0

        StorageTelemetry(percent, usedGb.toFloat(), totalGb.toFloat())
    } catch (_: Exception) {
        StorageTelemetry(55, 70f, 128f)
    }
}
