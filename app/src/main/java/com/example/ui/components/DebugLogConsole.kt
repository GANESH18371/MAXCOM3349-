package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.DebugLogger
import com.example.util.LogEntry
import com.example.util.LogType
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DarkOutline
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonLime
import com.example.ui.theme.NeonRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun DebugLogConsole(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val logs by DebugLogger.logs.collectAsState()
    var selectedFilter by remember { mutableStateOf("ALL") }

    val filteredLogs = remember(logs, selectedFilter) {
        when (selectedFilter) {
            "APP_OPEN" -> logs.filter { it.type == LogType.MATCH || it.type == LogType.LAUNCH }
            "TOGGLES" -> logs.filter { it.type == LogType.TOGGLE_ATTEMPT || it.type == LogType.TOGGLE_RESULT }
            else -> logs
        }
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF070B12)),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, DarkOutline, RoundedCornerShape(12.dp))
            .testTag("debug_log_console")
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = "Console",
                        tint = NeonLime,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "LIVE DEBUG LOG TERMINAL",
                        style = MaterialTheme.typography.labelLarge,
                        color = NeonLime,
                        fontSize = 11.sp
                    )
                }

                Row {
                    IconButton(
                        onClick = {
                            val text = logs.joinToString("\n") { "[${it.timestamp}] ${it.message}" }
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Max Logs", text))
                            Toast.makeText(context, "Copied ${logs.size} logs", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(28.dp).testTag("copy_logs_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Logs",
                            tint = TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    IconButton(
                        onClick = { DebugLogger.clearLogs() },
                        modifier = Modifier.size(28.dp).testTag("clear_logs_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear Logs",
                            tint = TextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Filter Chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                listOf("ALL" to "All Logs", "APP_OPEN" to "App Open", "TOGGLES" to "Hardware").forEach { (key, label) ->
                    FilterChip(
                        selected = selectedFilter == key,
                        onClick = { selectedFilter = key },
                        label = { Text(label, fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyberCyan.copy(alpha = 0.2f),
                            selectedLabelColor = CyberCyan,
                            containerColor = DarkSurfaceVariant,
                            labelColor = TextMuted
                        ),
                        modifier = Modifier.height(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No logs yet. Try speaking or tapping any toggle!",
                        fontSize = 11.sp,
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(Color(0xFF03060A), RoundedCornerShape(6.dp))
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredLogs, key = { it.id }) { log ->
                        LogItemRow(log)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogItemRow(log: LogEntry) {
    val badgeColor = when (log.type) {
        LogType.MATCH -> CyberCyan
        LogType.LAUNCH -> if (log.message.contains("success")) NeonLime else NeonRed
        LogType.TOGGLE_ATTEMPT -> NeonAmber
        LogType.TOGGLE_RESULT -> if (log.message.contains("success")) NeonLime else NeonRed
        LogType.INFO -> TextSecondary
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = log.timestamp,
            fontSize = 9.sp,
            color = TextMuted,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(62.dp)
        )

        Spacer(modifier = Modifier.width(4.dp))

        Text(
            text = log.message,
            fontSize = 10.sp,
            fontWeight = if (log.type != LogType.INFO) FontWeight.Bold else FontWeight.Normal,
            color = badgeColor,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )
    }
}
