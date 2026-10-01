package com.example.ui.components

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.manager.ClonedVoiceManager
import com.example.ui.theme.JarvisCard
import com.example.ui.theme.JarvisCardBorder
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisNeonAmber
import com.example.ui.theme.JarvisNeonGreen
import com.example.ui.theme.JarvisNeonRed
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisTextDim
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun VoiceCloningCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isEnabled by ClonedVoiceManager.isEnabled.collectAsStateWithLifecycle()
    val provider by ClonedVoiceManager.provider.collectAsStateWithLifecycle()
    val apiKey by ClonedVoiceManager.apiKey.collectAsStateWithLifecycle()
    val voiceId by ClonedVoiceManager.voiceId.collectAsStateWithLifecycle()
    val customEndpoint by ClonedVoiceManager.customEndpoint.collectAsStateWithLifecycle()
    val samplesCount by ClonedVoiceManager.samplesCount.collectAsStateWithLifecycle()
    val isRecording by ClonedVoiceManager.isRecording.collectAsStateWithLifecycle()
    val activeRecIndex by ClonedVoiceManager.recordingIndex.collectAsStateWithLifecycle()
    val playingSampleIndex by ClonedVoiceManager.playingSampleIndex.collectAsStateWithLifecycle()
    val isSynthesizing by ClonedVoiceManager.isSynthesizing.collectAsStateWithLifecycle()

    var selectedSampleSlot by remember { mutableIntStateOf(0) }
    var inputApiKey by remember { mutableStateOf("") }
    var inputVoiceId by remember { mutableStateOf("") }
    var inputCustomEndpoint by remember { mutableStateOf("") }
    var isKeyVisible by remember { mutableStateOf(false) }
    var isTestingVoice by remember { mutableStateOf(false) }
    var isUploadingClone by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isStatusSuccess by remember { mutableStateOf<Boolean?>(null) }
    var showGuide by remember { mutableStateOf(false) }

    // Sync input credentials
    LaunchedEffect(apiKey, voiceId, customEndpoint) {
        if (inputApiKey.isBlank()) inputApiKey = apiKey
        if (inputVoiceId.isBlank()) inputVoiceId = voiceId
        if (inputCustomEndpoint.isBlank()) inputCustomEndpoint = customEndpoint
    }

    // Audio file picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val success = ClonedVoiceManager.importSampleFromUri(context, selectedSampleSlot, uri)
            if (success) {
                statusMessage = "Sample ${selectedSampleSlot + 1} imported successfully!"
                isStatusSuccess = true
                Toast.makeText(context, "Voice sample imported", Toast.LENGTH_SHORT).show()
            } else {
                statusMessage = "Could not import sample file"
                isStatusSuccess = false
            }
        }
    }

    // Audio record permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            ClonedVoiceManager.startRecording(context, selectedSampleSlot)
        } else {
            Toast.makeText(context, "Microphone permission required to record voice sample", Toast.LENGTH_SHORT).show()
        }
    }

    val isActive = isEnabled && apiKey.isNotBlank() && voiceId.isNotBlank()

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = JarvisCard),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, JarvisCardBorder, RoundedCornerShape(16.dp))
            .testTag("voice_cloning_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(34.dp)
                            .background(JarvisCyan.copy(alpha = 0.15f), CircleShape)
                            .border(1.dp, JarvisCyan.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            tint = JarvisCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "VOICE CLONING (MERI AAWAAZ)",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = JarvisTextPrimary,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "OWNER VOICE REPLICATION FOR TTS",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            color = JarvisTextDim
                        )
                    }
                }

                // Status Badge
                Box(
                    modifier = Modifier
                        .background(
                            when {
                                isActive -> JarvisNeonGreen.copy(alpha = 0.15f)
                                isEnabled -> JarvisNeonAmber.copy(alpha = 0.15f)
                                else -> JarvisSurface
                            },
                            RoundedCornerShape(6.dp)
                        )
                        .border(
                            1.dp,
                            when {
                                isActive -> JarvisNeonGreen.copy(alpha = 0.5f)
                                isEnabled -> JarvisNeonAmber.copy(alpha = 0.5f)
                                else -> JarvisCardBorder
                            },
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = when {
                            isActive -> "CLONE ACTIVE ✓"
                            isEnabled -> "KEY/ID MISSING"
                            else -> "OFFLINE TTS"
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isActive -> JarvisNeonGreen
                            isEnabled -> JarvisNeonAmber
                            else -> JarvisTextDim
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Explanation & Master Switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(JarvisSurface, RoundedCornerShape(10.dp))
                    .border(1.dp, JarvisCardBorder, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Speak in My Cloned Voice",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = JarvisTextPrimary
                    )
                    Text(
                        text = if (isEnabled) "Active for all Max speech outputs (with offline auto-fallback)" else "Using default high-quality Android natural TTS voice",
                        fontSize = 9.sp,
                        color = JarvisTextSecondary
                    )
                }

                Switch(
                    checked = isEnabled,
                    onCheckedChange = { ClonedVoiceManager.setEnabled(context, it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = JarvisCyan,
                        uncheckedThumbColor = JarvisTextDim,
                        uncheckedTrackColor = JarvisSurface
                    ),
                    modifier = Modifier.testTag("cloned_voice_toggle")
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // SECTION 1: VOICE SAMPLE RECORDINGS (3-5 samples)
            Text(
                text = "STEP 1: RECORD YOUR VOICE SAMPLES (3-5 SAMPLES)",
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = JarvisCyan
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Progress bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Samples recorded: $samplesCount / 5",
                    fontSize = 9.sp,
                    color = JarvisTextSecondary
                )
                Text(
                    text = if (samplesCount >= 3) "Ready to clone ✓" else "Min 1-3 needed",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (samplesCount >= 3) JarvisNeonGreen else JarvisTextDim
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            LinearProgressIndicator(
                progress = { samplesCount / 5f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = if (samplesCount >= 3) JarvisNeonGreen else JarvisCyan,
                trackColor = JarvisSurface,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Slot Tabs (1 to 5)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (i in 0 until 5) {
                    val exists = ClonedVoiceManager.doesSampleExist(context, i)
                    val isSelected = selectedSampleSlot == i
                    val isCurrentRecording = isRecording && activeRecIndex == i
                    val isCurrentPlaying = playingSampleIndex == i

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .background(
                                when {
                                    isCurrentRecording -> JarvisNeonRed.copy(alpha = 0.25f)
                                    isSelected -> JarvisCyan.copy(alpha = 0.2f)
                                    exists -> JarvisNeonGreen.copy(alpha = 0.1f)
                                    else -> JarvisSurface
                                },
                                RoundedCornerShape(8.dp)
                            )
                            .border(
                                1.dp,
                                when {
                                    isCurrentRecording -> JarvisNeonRed
                                    isSelected -> JarvisCyan
                                    exists -> JarvisNeonGreen.copy(alpha = 0.4f)
                                    else -> JarvisCardBorder
                                },
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { selectedSampleSlot = i }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "S${i + 1}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) JarvisCyan else JarvisTextPrimary
                            )
                            if (exists) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Recorded",
                                    tint = JarvisNeonGreen,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Active Slot Guided Sentence Card
            val currentSlotExists = ClonedVoiceManager.doesSampleExist(context, selectedSampleSlot)
            val isCurrentSlotRecording = isRecording && activeRecIndex == selectedSampleSlot
            val isCurrentSlotPlaying = playingSampleIndex == selectedSampleSlot

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(JarvisSurface, RoundedCornerShape(10.dp))
                    .border(1.dp, JarvisCardBorder, RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "GUIDED SCRIPT ${selectedSampleSlot + 1}:",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = JarvisCyan
                        )
                        Text(
                            text = if (currentSlotExists) "RECORDED ✓" else "NOT RECORDED",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            color = if (currentSlotExists) JarvisNeonGreen else JarvisTextDim
                        )
                    }

                    // Prompt Sentence
                    Text(
                        text = "\"${ClonedVoiceManager.SAMPLE_PROMPTS[selectedSampleSlot]}\"",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = JarvisTextPrimary,
                        lineHeight = 16.sp
                    )

                    Text(
                        text = "Tip: Shanti me 10-15 second saaf aawaz me naturally bolkar record karein.",
                        fontSize = 8.sp,
                        color = JarvisTextDim
                    )

                    // Action Buttons for this slot
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Record / Stop Button
                        Button(
                            onClick = {
                                if (isCurrentSlotRecording) {
                                    ClonedVoiceManager.stopRecording(context)
                                } else {
                                    val hasMicPermission = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasMicPermission) {
                                        ClonedVoiceManager.startRecording(context, selectedSampleSlot)
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isCurrentSlotRecording) JarvisNeonRed else JarvisCyan
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(34.dp)
                                .testTag("record_sample_${selectedSampleSlot}_button")
                        ) {
                            Icon(
                                imageVector = if (isCurrentSlotRecording) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isCurrentSlotRecording) "STOP" else "RECORD",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        // Play Button (if exists)
                        if (currentSlotExists) {
                            OutlinedButton(
                                onClick = {
                                    if (isCurrentSlotPlaying) {
                                        ClonedVoiceManager.stopPlayback()
                                    } else {
                                        ClonedVoiceManager.playSample(context, selectedSampleSlot)
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                            ) {
                                Icon(
                                    imageVector = if (isCurrentSlotPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = JarvisCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = if (isCurrentSlotPlaying) "STOP" else "PLAY",
                                    color = JarvisCyan,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            // Delete button
                            IconButton(
                                onClick = {
                                    ClonedVoiceManager.deleteSample(context, selectedSampleSlot)
                                    Toast.makeText(context, "Sample deleted", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete sample",
                                    tint = JarvisNeonRed.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Import File Button
                        OutlinedButton(
                            onClick = { filePickerLauncher.launch("audio/*") },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Upload,
                                contentDescription = null,
                                tint = JarvisTextSecondary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("FILE", color = JarvisTextSecondary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // SECTION 2: SERVICE & CREDENTIALS
            Text(
                text = "STEP 2: VOICE CLONING SERVICE & CREDENTIALS",
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = JarvisCyan
            )

            Spacer(modifier = Modifier.height(8.dp))

            // API Key Input
            OutlinedTextField(
                value = inputApiKey,
                onValueChange = { inputApiKey = it },
                label = { Text("ElevenLabs API Key", fontSize = 10.sp) },
                placeholder = { Text("Paste xi-api-key", fontSize = 10.sp, color = JarvisTextDim) },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Key, contentDescription = null, tint = JarvisCyan, modifier = Modifier.size(16.dp))
                },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val text = clip?.primaryClip?.getItemAt(0)?.text?.toString()?.trim() ?: ""
                                if (text.isNotBlank()) inputApiKey = text
                            }
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = JarvisCyan, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                            Icon(if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null, tint = JarvisTextDim, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = JarvisCyan,
                    unfocusedBorderColor = JarvisCardBorder,
                    focusedTextColor = JarvisTextPrimary,
                    unfocusedTextColor = JarvisTextPrimary
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("cloned_voice_api_key_input")
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Voice ID Input
            OutlinedTextField(
                value = inputVoiceId,
                onValueChange = { inputVoiceId = it },
                label = { Text("Cloned Voice ID", fontSize = 10.sp) },
                placeholder = { Text("e.g. 21m00Tcm4TlvDq8ikWAM", fontSize = 10.sp, color = JarvisTextDim) },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.RecordVoiceOver, contentDescription = null, tint = JarvisCyan, modifier = Modifier.size(16.dp))
                },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val text = clip?.primaryClip?.getItemAt(0)?.text?.toString()?.trim() ?: ""
                            if (text.isNotBlank()) inputVoiceId = text
                        }
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = JarvisCyan, modifier = Modifier.size(16.dp))
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = JarvisCyan,
                    unfocusedBorderColor = JarvisCardBorder,
                    focusedTextColor = JarvisTextPrimary,
                    unfocusedTextColor = JarvisTextPrimary
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("cloned_voice_id_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons: Save Credentials & Test Cloned Voice
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Save Button
                Button(
                    onClick = {
                        val key = inputApiKey.trim()
                        val id = inputVoiceId.trim()
                        if (key.isBlank() || id.isBlank()) {
                            Toast.makeText(context, "Please enter both API Key and Voice ID", Toast.LENGTH_SHORT).show()
                        } else {
                            ClonedVoiceManager.setCredentials(context, key, id)
                            ClonedVoiceManager.setEnabled(context, true)
                            statusMessage = "Credentials saved! Cloned voice is now ACTIVE."
                            isStatusSuccess = true
                            Toast.makeText(context, "Voice cloning configured successfully!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .testTag("save_voice_clone_button")
                ) {
                    Text("SAVE & ACTIVATE", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }

                // Test Voice Button
                OutlinedButton(
                    onClick = {
                        val key = inputApiKey.trim()
                        val id = inputVoiceId.trim()
                        if (key.isBlank() || id.isBlank()) {
                            Toast.makeText(context, "Enter API Key and Voice ID first", Toast.LENGTH_SHORT).show()
                        } else {
                            ClonedVoiceManager.setCredentials(context, key, id)
                            ClonedVoiceManager.setEnabled(context, true)
                            isTestingVoice = true
                            statusMessage = "Synthesizing test speech with cloned voice..."
                            isStatusSuccess = null

                            val testText = "नमस्ते! यह मेरी अपनी आवाज़ है। मैक्स अब इसी आवाज़ में आपसे बात करेगा।"
                            val handled = ClonedVoiceManager.speakWithClonedVoice(
                                text = testText,
                                onDone = {
                                    isTestingVoice = false
                                    statusMessage = "Voice playback successful! Cloned voice verified ✓"
                                    isStatusSuccess = true
                                },
                                onFallback = {
                                    isTestingVoice = false
                                    statusMessage = "Test failed: API error or offline. Check key and Voice ID."
                                    isStatusSuccess = false
                                }
                            )
                            if (!handled) {
                                isTestingVoice = false
                                statusMessage = "Configuration incomplete"
                                isStatusSuccess = false
                            }
                        }
                    },
                    enabled = !isTestingVoice,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                ) {
                    if (isTestingVoice) {
                        CircularProgressIndicator(color = JarvisCyan, strokeWidth = 2.dp, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text("TEST VOICE", color = JarvisCyan, fontWeight = FontWeight.Bold, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Auto-create from samples button (if samples >= 1 and API key is present)
            if (samplesCount > 0 && inputApiKey.isNotBlank()) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            isUploadingClone = true
                            statusMessage = "Uploading ${samplesCount} samples to ElevenLabs to create voice..."
                            isStatusSuccess = null
                            val res = ClonedVoiceManager.createVoiceFromSamples(context, "Max Owner Voice")
                            isUploadingClone = false
                            res.onSuccess { newId ->
                                inputVoiceId = newId
                                statusMessage = "Voice Clone created on ElevenLabs! Voice ID: $newId"
                                isStatusSuccess = true
                                Toast.makeText(context, "Voice cloned successfully!", Toast.LENGTH_SHORT).show()
                            }.onFailure { err ->
                                statusMessage = "Clone creation failed: ${err.message}"
                                isStatusSuccess = false
                            }
                        }
                    },
                    enabled = !isUploadingClone,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                ) {
                    if (isUploadingClone) {
                        CircularProgressIndicator(color = JarvisNeonGreen, strokeWidth = 2.dp, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = "AUTO-CLONE: UPLOAD SAMPLES TO ELEVENLABS",
                        color = JarvisNeonGreen,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Status Message Box
            AnimatedVisibility(visible = statusMessage != null) {
                statusMessage?.let { msg ->
                    val color = if (isStatusSuccess == true) JarvisNeonGreen else if (isStatusSuccess == false) JarvisNeonRed else JarvisCyan
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .background(color.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = msg,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = color
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Expandable Guide & Free Tier Details
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showGuide = !showGuide },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = JarvisTextDim,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Voice Cloning Service Guide & Pricing (2026)",
                        fontSize = 10.sp,
                        color = JarvisTextSecondary
                    )
                }
                Text(
                    text = if (showGuide) "▲ HIDE" else "▼ DETAILS",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    color = JarvisCyan
                )
            }

            if (showGuide) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(JarvisSurface, RoundedCornerShape(8.dp))
                        .border(1.dp, JarvisCardBorder, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "BEST SERVICES FOR HINDI + ENGLISH VOICE CLONING:",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = JarvisCyan
                        )
                        Text(
                            text = "1. ElevenLabs (Recommended / Gold Standard):\n" +
                                    "   • Free Tier: 10,000 characters/month (for testing standard voices)\n" +
                                    "   • Starter Plan: $5/month gives 30,000 chars + Instant Voice Cloning from 1 min audio sample.\n" +
                                    "   • Quality: Flawless Indian English and Hindi accent reproduction.\n" +
                                    "   • Steps: Signup at elevenlabs.io -> VoiceLab -> Add Cloned Voice -> Copy Voice ID and Profile API Key.",
                            fontSize = 9.sp,
                            color = JarvisTextPrimary,
                            lineHeight = 13.sp
                        )
                        Text(
                            text = "2. OpenVoice & Fish Audio (Free / Open-Source):\n" +
                                    "   • Fish Audio has free 500 requests/month.\n" +
                                    "   • Self-hosted XTTS v2 or OpenVoice allows 100% free cloning with local custom endpoint.",
                            fontSize = 9.sp,
                            color = JarvisTextSecondary,
                            lineHeight = 13.sp
                        )
                        Text(
                            text = "3. Zero-Failure Protection:\n" +
                                    "   • Agar internet band ho ya API quota khatam ho jaye, Max bina kisi rukawat ke Android ke offline local TTS engine se bolega.",
                            fontSize = 9.sp,
                            color = JarvisNeonGreen,
                            lineHeight = 13.sp
                        )
                    }
                }
            }
        }
    }
}
