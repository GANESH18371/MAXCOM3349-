package com.example.ui.components

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.JarvisCard
import com.example.ui.theme.JarvisCardBorder
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisNeonGreen
import com.example.ui.theme.JarvisNeonRed
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisTextDim
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary
import com.example.util.DebugLogger
import com.example.util.SecureApiKeyManager
import kotlinx.coroutines.launch

@Composable
fun GeminiApiKeyCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val currentKey by SecureApiKeyManager.apiKeyFlow.collectAsStateWithLifecycle()

    var inputKey by remember { mutableStateOf("") }
    var isKeyVisible by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    var isValidating by remember { mutableStateOf(false) }
    var validationStatus by remember { mutableStateOf<String?>(null) }
    var validationSuccess by remember { mutableStateOf<Boolean?>(null) }

    val isConfigured = currentKey.isNotBlank()

    // Sync input when key changes
    LaunchedEffect(currentKey) {
        if (inputKey.isBlank()) {
            inputKey = currentKey
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = JarvisCard),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, JarvisCardBorder, RoundedCornerShape(16.dp))
            .testTag("gemini_api_key_card")
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
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = null,
                            tint = JarvisCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "GEMINI NEURAL ENGINE KEY",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = JarvisTextPrimary,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "CENTRALIZED SECURE VAULT (AES-256)",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            color = JarvisTextDim
                        )
                    }
                }

                // Configured Badge
                Box(
                    modifier = Modifier
                        .background(
                            if (isConfigured) JarvisNeonGreen.copy(alpha = 0.15f) else JarvisNeonRed.copy(alpha = 0.15f),
                            RoundedCornerShape(6.dp)
                        )
                        .border(
                            1.dp,
                            if (isConfigured) JarvisNeonGreen.copy(alpha = 0.5f) else JarvisNeonRed.copy(alpha = 0.5f),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (isConfigured) "CONFIGURED ✓" else "KEY MISSING",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isConfigured) JarvisNeonGreen else JarvisNeonRed
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Info note
            Text(
                text = "Used everywhere across Max (Gemini Live Audio, WhatsApp AI Auto-Reply, and Scene Vision). Encrypted 100% on-device.",
                fontSize = 10.sp,
                color = JarvisTextSecondary,
                lineHeight = 14.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // State 1: Already Configured & not editing
            if (isConfigured && !isEditing) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(JarvisSurface, RoundedCornerShape(10.dp))
                        .border(1.dp, JarvisCardBorder, RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = JarvisNeonGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = SecureApiKeyManager.getMaskedKey(currentKey),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = JarvisTextPrimary
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(
                                    onClick = {
                                        inputKey = currentKey
                                        isEditing = true
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Key",
                                        tint = JarvisCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        SecureApiKeyManager.clearApiKey(context)
                                        inputKey = ""
                                        validationStatus = null
                                        Toast.makeText(context, "API Key removed", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Key",
                                        tint = JarvisNeonRed.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        // Test Key Action
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (SecureApiKeyManager.isUserSuppliedKey(context)) "Source: User Settings Vault" else "Source: Environment Configuration",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.sp,
                                color = JarvisTextDim
                            )

                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        isValidating = true
                                        validationStatus = "Testing connection to Google Gemini API..."
                                        validationSuccess = null
                                        DebugLogger.logApiKeyValidationAttempt()
                                        val result = SecureApiKeyManager.validateKey(currentKey)
                                        isValidating = false
                                        result.onSuccess {
                                            DebugLogger.logApiKeyValidationResult(true)
                                            validationStatus = "Configured ✓"
                                            validationSuccess = true
                                        }.onFailure {
                                            val err = it.message ?: "Yeh API key invalid hai, sahi key daaliye"
                                            DebugLogger.logApiKeyValidationResult(false, err)
                                            validationStatus = "Yeh API key invalid hai, sahi key daaliye"
                                            validationSuccess = false
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                if (isValidating) {
                                    CircularProgressIndicator(
                                        color = JarvisCyan,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text("TEST KEY", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = JarvisCyan)
                            }
                        }
                    }
                }
            } else {
                // State 2: Input field for entering / updating API key
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = inputKey,
                        onValueChange = {
                            inputKey = it
                            validationStatus = null
                        },
                        placeholder = {
                            Text(
                                text = "Paste Gemini API Key (AIzaSy...)",
                                fontSize = 11.sp,
                                color = JarvisTextDim
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = JarvisCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Paste from clipboard
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        val clipData = clipboard?.primaryClip
                                        if (clipData != null && clipData.itemCount > 0) {
                                            val text = clipData.getItemAt(0)?.text?.toString()?.trim() ?: ""
                                            if (text.isNotBlank()) {
                                                inputKey = text
                                                Toast.makeText(context, "Pasted from clipboard", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentPaste,
                                        contentDescription = "Paste",
                                        tint = JarvisCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                // Visibility Toggle
                                IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                                    Icon(
                                        imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle visibility",
                                        tint = JarvisTextDim,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JarvisCyan,
                            unfocusedBorderColor = JarvisCardBorder,
                            focusedTextColor = JarvisTextPrimary,
                            unfocusedTextColor = JarvisTextPrimary,
                            cursorColor = JarvisCyan
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("gemini_api_key_input")
                    )

                    // Action buttons: Save & Cancel
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val trimmed = inputKey.trim()
                                if (trimmed.isBlank()) {
                                    validationStatus = "Yeh API key invalid hai, sahi key daaliye"
                                    validationSuccess = false
                                    Toast.makeText(context, "Yeh API key invalid hai, sahi key daaliye", Toast.LENGTH_SHORT).show()
                                } else {
                                    scope.launch {
                                        isValidating = true
                                        validationStatus = "Testing key with Google Gemini API..."
                                        validationSuccess = null

                                        // Real test API call to Gemini BEFORE saving!
                                        val result = SecureApiKeyManager.validateAndSaveApiKey(context, trimmed)
                                        isValidating = false

                                        result.onSuccess {
                                            isEditing = false
                                            validationStatus = "Configured ✓"
                                            validationSuccess = true
                                            Toast.makeText(context, "Configured ✓: Gemini API Key verified & saved!", Toast.LENGTH_SHORT).show()
                                        }.onFailure {
                                            validationStatus = "Yeh API key invalid hai, sahi key daaliye"
                                            validationSuccess = false
                                            Toast.makeText(context, "Yeh API key invalid hai, sahi key daaliye", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            },
                            enabled = !isValidating,
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .testTag("save_api_key_button")
                        ) {
                            if (isValidating) {
                                CircularProgressIndicator(
                                    color = Color.Black,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "VERIFYING...",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            } else {
                                Text(
                                    text = "SAVE API KEY",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        if (isEditing) {
                            OutlinedButton(
                                onClick = {
                                    inputKey = currentKey
                                    isEditing = false
                                    validationStatus = null
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("CANCEL", color = JarvisTextSecondary, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }

            // Validation Status Feedback
            AnimatedVisibility(visible = validationStatus != null) {
                validationStatus?.let { msg ->
                    val color = if (validationSuccess == true) JarvisNeonGreen else if (validationSuccess == false) JarvisNeonRed else JarvisCyan
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

            // Get API Key Link: https://aistudio.google.com/app/apikey
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey")).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open browser: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.OpenInNew,
                    contentDescription = null,
                    tint = JarvisCyan,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "Get your free Gemini API key from Google AI Studio",
                    fontSize = 10.sp,
                    color = JarvisCyan,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
