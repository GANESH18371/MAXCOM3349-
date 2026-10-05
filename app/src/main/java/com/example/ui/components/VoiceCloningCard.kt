package com.example.ui.components

import android.Manifest
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.manager.OfflineVoiceCloneManager
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

    val isEnabled by OfflineVoiceCloneManager.isEnabled.collectAsStateWithLifecycle()
    val hasSample by OfflineVoiceCloneManager.hasRecordedSample.collectAsStateWithLifecycle()
    val sampleDuration by OfflineVoiceCloneManager.sampleDurationSec.collectAsStateWithLifecycle()
    val detectedPitch by OfflineVoiceCloneManager.detectedPitchHz.collectAsStateWithLifecycle()
    val engineMode by OfflineVoiceCloneManager.engineMode.collectAsStateWithLifecycle()
    val localApiUrl by OfflineVoiceCloneManager.localApiUrl.collectAsStateWithLifecycle()
    val isRecording by OfflineVoiceCloneManager.isRecording.collectAsStateWithLifecycle()
    val isPlayingSample by OfflineVoiceCloneManager.playingSample.collectAsStateWithLifecycle()
    val isSynthesizing by OfflineVoiceCloneManager.isSynthesizing.collectAsStateWithLifecycle()

    var testStatusMessage by remember { mutableStateOf<String?>(null) }
    var isTestSuccess by remember { mutableStateOf<Boolean?>(null) }
    var showArchitectureGuide by remember { mutableStateOf(false) }
    var inputLocalEndpoint by remember { mutableStateOf(localApiUrl) }

    var isUploading by remember { mutableStateOf(false) }
    var uploadStatusMessage by remember { mutableStateOf<String?>(null) }
    var isUploadSuccess by remember { mutableStateOf<Boolean?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            OfflineVoiceCloneManager.startRecording(context)
        } else {
            Toast.makeText(context, "Microphone permission required for voice sample recording", Toast.LENGTH_SHORT).show()
        }
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isUploading = true
            uploadStatusMessage = "Processing audio file..."
            isUploadSuccess = null
            scope.launch {
                val res = OfflineVoiceCloneManager.importAudioSampleFromUri(context, uri)
                isUploading = false
                if (res.isSuccess) {
                    isUploadSuccess = true
                    uploadStatusMessage = "PROFILE EXTRACTED ✓"
                    Toast.makeText(context, "PROFILE EXTRACTED ✓ - Voice sample imported", Toast.LENGTH_SHORT).show()
                } else {
                    isUploadSuccess = false
                    val errMsg = res.exceptionOrNull()?.message ?: "1-3 second ki .wav/.mp3 file chahiye"
                    uploadStatusMessage = "Voice cloning fail hui: $errMsg, dobara try karein"
                    Toast.makeText(context, "Voice cloning fail hui: $errMsg, dobara try karein", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val isActive = isEnabled && hasSample

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = JarvisCard),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, JarvisCardBorder, RoundedCornerShape(16.dp))
            .testTag("offline_voice_cloning_card")
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
                            text = "VOICE CLONING (CLONETTS OFFLINE)",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = JarvisTextPrimary,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "100% ON-DEVICE (ZERO CLOUD / NO API KEYS)",
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
                            isActive -> "OFFLINE CLONE ACTIVE ✓"
                            isEnabled -> "SAMPLE NEEDED"
                            else -> "DEFAULT TTS"
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

            // Master Activation Switch
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
                        text = if (isEnabled) "Active across all Max voice outputs (with automatic offline local TTS fallback)" else "Using default high-definition local Android TTS",
                        fontSize = 9.sp,
                        color = JarvisTextSecondary
                    )
                }

                Switch(
                    checked = isEnabled,
                    onCheckedChange = { OfflineVoiceCloneManager.setEnabled(context, it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = JarvisCyan,
                        uncheckedThumbColor = JarvisTextDim,
                        uncheckedTrackColor = JarvisSurface
                    ),
                    modifier = Modifier.testTag("offline_voice_clone_toggle")
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // SECTION 1: VOICE SETUP (1-3 SECOND SAMPLE RECORDING)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "VOICE SETUP (RECORD 1-3 SECOND SAMPLE):",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = JarvisCyan
                )

                if (hasSample) {
                    Text(
                        text = "READY ✓ (${sampleDuration}s | ${detectedPitch}Hz)",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = JarvisNeonGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Guided Recording Script Box
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
                            text = "SCRIPT TO READ ALOUD:",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = JarvisCyan
                        )
                        Text(
                            text = if (hasSample) "PROFILE EXTRACTED ✓" else "NOT RECORDED",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            color = if (hasSample) JarvisNeonGreen else JarvisTextDim
                        )
                    }

                    Text(
                        text = "\"नमस्ते! मैं मैक्स का ओनर हूँ। यह मेरी आवाज़ है।\"",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = JarvisTextPrimary
                    )

                    Text(
                        text = "Tip: Shanti me natural tone me 1-3 second bol kar record karein. Zero cloud upload — sab device ke andar analyze hota hai.",
                        fontSize = 8.sp,
                        color = JarvisTextDim
                    )

                    // Sample Creation Action Buttons (RECORD + UPLOAD FILE)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // 1. Record / Stop Button
                        Button(
                            onClick = {
                                if (isRecording) {
                                    OfflineVoiceCloneManager.stopRecording(context)
                                } else {
                                    val hasMic = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasMic) {
                                        OfflineVoiceCloneManager.startRecording(context)
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isRecording) JarvisNeonRed else JarvisCyan
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .testTag("record_offline_sample_button")
                        ) {
                            Icon(
                                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isRecording) "STOPPING..." else "RECORD",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        // 2. Upload Audio File Button (PART 2)
                        OutlinedButton(
                            onClick = {
                                uploadStatusMessage = null
                                audioPickerLauncher.launch("audio/*")
                            },
                            enabled = !isRecording && !isUploading,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(36.dp)
                                .testTag("upload_audio_file_button")
                        ) {
                            if (isUploading) {
                                CircularProgressIndicator(
                                    color = JarvisCyan,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            } else {
                                Icon(
                                    imageVector = Icons.Default.UploadFile,
                                    contentDescription = "Upload audio file",
                                    tint = JarvisCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = if (isUploading) "PROCESSING..." else "UPLOAD AUDIO FILE",
                                color = JarvisCyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Existing Sample Playback & Delete Row
                    if (hasSample) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (isPlayingSample) {
                                        OfflineVoiceCloneManager.stopPlayback()
                                    } else {
                                        OfflineVoiceCloneManager.playSample(context)
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPlayingSample) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = JarvisCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isPlayingSample) "STOP PLAYBACK" else "LISTEN SAMPLE",
                                    color = JarvisCyan,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            // Delete / Re-record button
                            IconButton(
                                onClick = {
                                    OfflineVoiceCloneManager.deleteSample(context)
                                    uploadStatusMessage = null
                                    Toast.makeText(context, "Voice sample removed", Toast.LENGTH_SHORT).show()
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
                    }

                    // Upload Status Message / Error Feedback
                    AnimatedVisibility(visible = uploadStatusMessage != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isUploadSuccess == true) JarvisNeonGreen.copy(alpha = 0.12f)
                                    else JarvisNeonRed.copy(alpha = 0.12f),
                                    RoundedCornerShape(6.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isUploadSuccess == true) JarvisNeonGreen.copy(alpha = 0.4f)
                                    else JarvisNeonRed.copy(alpha = 0.4f),
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = uploadStatusMessage ?: "",
                                color = if (isUploadSuccess == true) JarvisNeonGreen else JarvisNeonRed,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // SECTION 2: SYNTHESIS ENGINE MODE
            Text(
                text = "OFFLINE SYNTHESIS ENGINE MODE:",
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = JarvisCyan
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Mode 1: On-Device Vocal Tract Morphing (Default)
                val isDeviceMode = engineMode == "ON_DEVICE"
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .background(if (isDeviceMode) JarvisCyan.copy(alpha = 0.15f) else JarvisSurface, RoundedCornerShape(8.dp))
                        .border(1.dp, if (isDeviceMode) JarvisCyan else JarvisCardBorder, RoundedCornerShape(8.dp))
                        .clickable { OfflineVoiceCloneManager.setEngineMode(context, "ON_DEVICE") }
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "ON-DEVICE NATIVE",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDeviceMode) JarvisCyan else JarvisTextPrimary
                        )
                        Text(
                            text = "0ms Latency • Offline",
                            fontSize = 8.sp,
                            color = JarvisTextDim
                        )
                    }
                }

                // Mode 2: CloneTTS Local HTTP API (127.0.0.1:8080)
                val isHttpMode = engineMode == "LOCAL_HTTP_API"
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .background(if (isHttpMode) JarvisCyan.copy(alpha = 0.15f) else JarvisSurface, RoundedCornerShape(8.dp))
                        .border(1.dp, if (isHttpMode) JarvisCyan else JarvisCardBorder, RoundedCornerShape(8.dp))
                        .clickable { OfflineVoiceCloneManager.setEngineMode(context, "LOCAL_HTTP_API") }
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "CLONETTS LOCAL PORT",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isHttpMode) JarvisCyan else JarvisTextPrimary
                        )
                        Text(
                            text = "127.0.0.1:8080 • Sherpa",
                            fontSize = 8.sp,
                            color = JarvisTextDim
                        )
                    }
                }
            }

            // Optional Local HTTP Endpoint Configuration
            if (engineMode == "LOCAL_HTTP_API") {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = inputLocalEndpoint,
                    onValueChange = {
                        inputLocalEndpoint = it
                        OfflineVoiceCloneManager.setLocalApiUrl(context, it)
                    },
                    label = { Text("Local CloneTTS API Endpoint", fontSize = 10.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JarvisCyan,
                        unfocusedBorderColor = JarvisCardBorder,
                        focusedTextColor = JarvisTextPrimary,
                        unfocusedTextColor = JarvisTextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // SECTION 3: TEST CLONED VOICE
            Button(
                onClick = {
                    if (!hasSample) {
                        Toast.makeText(context, "Please record a voice sample first", Toast.LENGTH_SHORT).show()
                    } else {
                        testStatusMessage = "Synthesizing speech in your cloned voice..."
                        isTestSuccess = null
                        val phrase = "नमस्ते! यह मेरी अपनी आवाज़ का ऑफ़लाइन क्लोन है। मैक्स अब इसी आवाज़ में आपसे बात करेगा।"
                        val handled = OfflineVoiceCloneManager.speakWithClonedVoice(
                            text = phrase,
                            onDone = {
                                testStatusMessage = "Speech playback completed in your cloned voice ✓"
                                isTestSuccess = true
                            }
                        )
                        if (!handled) {
                            testStatusMessage = "Voice cloning fail hui: profile nahi mila, dobara try karein"
                            isTestSuccess = false
                        }
                    }
                },
                enabled = hasSample && !isSynthesizing,
                colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .testTag("test_offline_cloned_voice_button")
            ) {
                if (isSynthesizing) {
                    CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = "TEST CLONED VOICE OUTPUT",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Test Feedback Box
            AnimatedVisibility(visible = testStatusMessage != null) {
                testStatusMessage?.let { msg ->
                    val color = if (isTestSuccess == true) JarvisNeonGreen else if (isTestSuccess == false) JarvisNeonRed else JarvisCyan
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

            // Expandable Technical Architecture Guide
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showArchitectureGuide = !showArchitectureGuide },
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
                        text = "CloneTTS Offline Architecture Details",
                        fontSize = 10.sp,
                        color = JarvisTextSecondary
                    )
                }
                Text(
                    text = if (showArchitectureGuide) "▲ HIDE" else "▼ DETAILS",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    color = JarvisCyan
                )
            }

            if (showArchitectureGuide) {
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
                            text = "100% OFFLINE ZERO-CLOUD CLONETTS PIPELINE:",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = JarvisCyan
                        )
                        Text(
                            text = "1. Pure Local Voice Setup:\n" +
                                    "   • 1-3 second ka chhota audio sample aapke device me record hota hai.\n" +
                                    "   • Fundamental frequency (F0) aur vocal tract formants extract hokar profile banti hai.",
                            fontSize = 9.sp,
                            color = JarvisTextPrimary,
                            lineHeight = 13.sp
                        )
                        Text(
                            text = "2. On-Device Acoustic Morphing:\n" +
                                    "   • Sherpa-onnx / ZipVoice style local processing se Max ke TTS ko aapki aawaz ke pitch aur timbre me convert kiya jaata hai.\n" +
                                    "   • No internet connection, no external servers, zero API costs.",
                            fontSize = 9.sp,
                            color = JarvisTextSecondary,
                            lineHeight = 13.sp
                        )
                        Text(
                            text = "3. Zero-Failure Protection:\n" +
                                    "   • Agar sample record na ho, to Max automatic default local Android TTS se bolega — app kabhi bhi silent nahi hoga.",
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
