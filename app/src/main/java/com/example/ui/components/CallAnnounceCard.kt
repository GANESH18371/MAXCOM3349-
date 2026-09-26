package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhoneCallback
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.manager.CallHistoryItem
import com.example.manager.CallManager
import com.example.manager.CallUiState
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
fun CallAnnounceCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val isAnnounceEnabled by CallManager.isCallAnnounceEnabled.collectAsState()
    val callUiState by CallManager.callUiState.collectAsState()
    val callHistory by CallManager.callHistory.collectAsState()

    var hasPhonePerm by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED)
    }
    var hasAnswerPerm by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }
    var hasContactsPerm by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
    }
    var hasAudioPerm by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }

    val refreshPermissions = {
        hasPhonePerm = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        hasAnswerPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED
        } else true
        hasContactsPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        hasAudioPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        refreshPermissions()
        val allGranted = results.values.all { it }
        if (allGranted) {
            Toast.makeText(context, "All Call permissions granted!", Toast.LENGTH_SHORT).show()
        }
    }

    var customCallerName by remember { mutableStateOf("Rahul Sharma") }
    var customCallerNumber by remember { mutableStateOf("9876543210") }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, DarkOutline, RoundedCornerShape(16.dp))
            .testTag("call_announce_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row with Toggle
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
                            .background(CyberCyan.copy(alpha = 0.15f), CircleShape)
                            .border(1.dp, CyberCyan.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneCallback,
                            contentDescription = "Call Announce",
                            tint = CyberCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Call Announce + Voice Control",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Hindi Caller Name TTS + Voice Pick/Cut",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }

                Switch(
                    checked = isAnnounceEnabled,
                    onCheckedChange = { CallManager.setCallAnnounceEnabled(context, it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = CyberCyan,
                        checkedTrackColor = CyberCyan.copy(alpha = 0.3f),
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = DarkSurfaceVariant
                    ),
                    modifier = Modifier.testTag("call_announce_switch")
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // =========================================================================
            // PERMISSIONS SECTION (READ_PHONE_STATE, ANSWER_PHONE_CALLS, READ_CONTACTS, RECORD_AUDIO)
            // =========================================================================
            val allGranted = hasPhonePerm && hasAnswerPerm && hasContactsPerm && hasAudioPerm
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (allGranted) NeonLime.copy(alpha = 0.3f) else NeonAmber.copy(alpha = 0.5f),
                        RoundedCornerShape(12.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "REQUIRED PERMISSIONS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (allGranted) NeonLime else NeonAmber,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = if (allGranted) "ALL ACTIVE" else "SETUP NEEDED",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (allGranted) NeonLime else NeonAmber
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    CallPermissionItem(
                        title = "Phone State (Incoming Call Detect)",
                        granted = hasPhonePerm,
                        icon = Icons.Default.Call
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    CallPermissionItem(
                        title = "Answer Phone Calls (Voice Pick/Cut)",
                        granted = hasAnswerPerm,
                        icon = Icons.Default.CallEnd
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    CallPermissionItem(
                        title = "Read Contacts (Caller Name Lookup)",
                        granted = hasContactsPerm,
                        icon = Icons.Default.Contacts
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    CallPermissionItem(
                        title = "Microphone (Listen 'Utha lo' / 'Katt do')",
                        granted = hasAudioPerm,
                        icon = Icons.Default.Mic
                    )

                    if (!allGranted) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                val list = mutableListOf(
                                    Manifest.permission.READ_PHONE_STATE,
                                    Manifest.permission.READ_CONTACTS,
                                    Manifest.permission.RECORD_AUDIO
                                )
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    list.add(Manifest.permission.ANSWER_PHONE_CALLS)
                                }
                                permissionsLauncher.launch(list.toTypedArray())
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonAmber),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .testTag("grant_call_permissions_button")
                        ) {
                            Text("Grant Missing Permissions", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // =========================================================================
            // LIVE CALL STATUS / RINGING BANNER
            // =========================================================================
            when (val state = callUiState) {
                is CallUiState.Ringing -> {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = CyberCyan.copy(alpha = 0.1f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.5.dp, CyberCyan, RoundedCornerShape(12.dp))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(NeonLime, CircleShape)
                                )
                                Text(
                                    text = "INCOMING CALL RINGING NOW",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeonLime
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Caller: ${state.callerName}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            if (state.callerNumber.isNotBlank() && state.callerNumber != state.callerName) {
                                Text(
                                    text = "Number: ${state.callerNumber}",
                                    fontSize = 12.sp,
                                    color = TextMuted
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .background(DarkSurfaceVariant, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = if (state.isListeningForVoice) Icons.Default.Mic else Icons.Default.RecordVoiceOver,
                                    contentDescription = null,
                                    tint = if (state.isListeningForVoice) NeonLime else CyberCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (state.isListeningForVoice)
                                        "Voice listening: Bolo 'Utha lo' ya 'Katt do'"
                                    else
                                        "Announcing caller name in Hindi...",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (state.isListeningForVoice) NeonLime else CyberCyan
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = {
                                        CallManager.answerIncomingCall(context)
                                        Toast.makeText(context, "Call Answered!", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonLime),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                ) {
                                    Icon(Icons.Default.Call, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Utha lo (Answer)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                }

                                Button(
                                    onClick = {
                                        CallManager.rejectIncomingCall(context)
                                        Toast.makeText(context, "Call Rejected!", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonRed),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                ) {
                                    Icon(Icons.Default.CallEnd, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Katt do (Cut)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                is CallUiState.CallActionSummary -> {
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, CyberCyan.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp)
                        ) {
                            Column {
                                Text(
                                    text = "Last Call Action: ${state.action.uppercase()}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (state.action) {
                                        "answered" -> NeonLime
                                        "rejected" -> NeonRed
                                        else -> TextMuted
                                    }
                                )
                                Text(
                                    text = "${state.caller} • ${state.details}",
                                    fontSize = 10.sp,
                                    color = TextSecondary
                                )
                            }
                            Text(
                                text = state.timestamp,
                                fontSize = 10.sp,
                                color = TextMuted
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                CallUiState.Idle -> {}
            }

            // =========================================================================
            // HOW IT WORKS INFO BOX
            // =========================================================================
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, DarkOutline.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "VOICE COMMANDS SUPPORTED",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberCyan
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• Announce: TTS speaks 'Aapko [naam/number] ki call aa rahi hai'",
                        fontSize = 11.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "• Pick Call: Bolo 'Utha lo' / 'Receive karo' / 'Pick up'",
                        fontSize = 11.sp,
                        color = NeonLime
                    )
                    Text(
                        text = "• Cut Call: Bolo 'Katt do' / 'Reject karo' / 'Decline'",
                        fontSize = 11.sp,
                        color = NeonRed
                    )
                    Text(
                        text = "• Silence/Timeout: 7s me koi command na aane par normal ringtone chalti rahegi",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // =========================================================================
            // SIMULATE / TEST CALL SECTION
            // =========================================================================
            Text(
                text = "TEST CALL SIMULATION",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = customCallerName,
                    onValueChange = { customCallerName = it },
                    label = { Text("Caller Name", fontSize = 11.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = DarkOutline,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("test_caller_name_input")
                )

                OutlinedTextField(
                    value = customCallerNumber,
                    onValueChange = { customCallerNumber = it },
                    label = { Text("Number", fontSize = 11.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = DarkOutline,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("test_caller_number_input")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    CallManager.simulateIncomingCall(context, customCallerName, customCallerNumber)
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .testTag("simulate_call_button")
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Simulate Incoming Call & Voice Test",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }

            // Quick Preset Chips
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val presets = listOf(
                    "Rahul Sharma" to "9876543210",
                    "Pooja Sharma" to "9123456789",
                    "Agyat Number" to "9988776655"
                )
                presets.forEach { (name, number) ->
                    Box(
                        modifier = Modifier
                            .background(DarkSurfaceVariant, RoundedCornerShape(12.dp))
                            .border(1.dp, DarkOutline, RoundedCornerShape(12.dp))
                            .clickable {
                                customCallerName = name
                                customCallerNumber = number
                                CallManager.simulateIncomingCall(context, name, number)
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Test: $name",
                            fontSize = 10.sp,
                            color = CyberCyan
                        )
                    }
                }
            }

            // =========================================================================
            // RECENT CALL HISTORY LOG
            // =========================================================================
            if (callHistory.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "RECENT CALL ACTIVITY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                callHistory.take(5).forEach { item ->
                    CallHistoryRow(item)
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun CallPermissionItem(
    title: String,
    granted: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (granted) NeonLime else TextMuted,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = title,
                fontSize = 11.sp,
                color = if (granted) TextPrimary else TextMuted
            )
        }

        Icon(
            imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.Error,
            contentDescription = null,
            tint = if (granted) NeonLime else NeonAmber,
            modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
private fun CallHistoryRow(item: CallHistoryItem) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurfaceVariant, RoundedCornerShape(8.dp))
            .border(1.dp, DarkOutline.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = when (item.action) {
                    "ANSWERED" -> Icons.Default.Call
                    "REJECTED" -> Icons.Default.CallEnd
                    else -> Icons.Default.PhoneCallback
                },
                contentDescription = null,
                tint = when (item.action) {
                    "ANSWERED" -> NeonLime
                    "REJECTED" -> NeonRed
                    else -> TextMuted
                },
                modifier = Modifier.size(14.dp)
            )
            Column {
                Text(
                    text = item.callerName,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "${item.callerNumber} • Action: ${item.action}",
                    fontSize = 9.sp,
                    color = TextSecondary
                )
            }
        }

        Text(
            text = item.timestamp,
            fontSize = 9.sp,
            color = TextMuted
        )
    }
}
