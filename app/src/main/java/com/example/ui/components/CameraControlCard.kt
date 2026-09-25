package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.manager.MaxCameraManager
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DarkCard
import com.example.ui.theme.DarkOutline
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonYellow
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.TtsManager

@Composable
fun CameraControlCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isBusy by MaxCameraManager.isBusy.collectAsStateWithLifecycle()
    val lastUri by MaxCameraManager.lastCapturedUri.collectAsStateWithLifecycle()
    val lastAnalysis by MaxCameraManager.lastSceneAnalysis.collectAsStateWithLifecycle()

    var hasCameraPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPerm = granted
        if (granted) {
            Toast.makeText(context, "Camera permission granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkCard)
            .border(1.dp, CyberCyan.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(16.dp)
            .testTag("camera_control_card")
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(CyberCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Camera Icon",
                            tint = CyberCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "CAMERA & SCENE VISION",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(NeonGreen.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "CameraX + Gemini",
                                    color = NeonGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            text = "Voice Selfie • DCIM/Max Gallery • Scene AI",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }

                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = CyberCyan,
                        strokeWidth = 2.dp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Permission missing banner
            if (!hasCameraPerm) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkSurface)
                        .border(1.dp, NeonYellow.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Camera Permission Required",
                                color = NeonYellow,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Voice se selfie lene ya scene samajhne ke liye camera permission allow karein.",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("grant_camera_perm_btn")
                        ) {
                            Text("Allow", color = DarkCard, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Quick Camera Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Selfie button
                Button(
                    onClick = {
                        if (!hasCameraPerm) {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        } else {
                            MaxCameraManager.capturePhoto(context, isFrontCamera = true)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPink.copy(alpha = 0.2f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonPink),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("take_selfie_button"),
                    enabled = !isBusy
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.FlipCameraAndroid,
                            contentDescription = null,
                            tint = NeonPink,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("Selfie Lo", color = NeonPink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Back Photo button
                Button(
                    onClick = {
                        if (!hasCameraPerm) {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        } else {
                            MaxCameraManager.capturePhoto(context, isFrontCamera = false)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan.copy(alpha = 0.2f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("take_photo_button"),
                    enabled = !isBusy
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("Photo Lo", color = CyberCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Scene Analysis button
                Button(
                    onClick = {
                        if (!hasCameraPerm) {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        } else {
                            MaxCameraManager.analyzeScene(context)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen.copy(alpha = 0.2f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonGreen),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1.2f)
                        .testTag("analyze_scene_button"),
                    enabled = !isBusy
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.RemoveRedEye,
                            contentDescription = null,
                            tint = NeonGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("Saamne Kya Hai", color = NeonGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Last Scene Analysis / Capture feedback
            if (lastAnalysis != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurface)
                        .border(1.dp, NeonGreen.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.RemoveRedEye,
                                    contentDescription = null,
                                    tint = NeonGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Scene Analysis (Gemini Vision)",
                                    color = NeonGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = lastAnalysis ?: "",
                                color = TextPrimary,
                                fontSize = 12.sp
                            )
                        }
                        IconButton(
                            onClick = { TtsManager.speak(lastAnalysis ?: "") },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "Speak analysis",
                                tint = CyberCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (lastUri != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurface)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = NeonGreen,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Saved in DCIM/Max folder",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Silent photo indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Silent background capture engine ready (Anti-theft)",
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}
