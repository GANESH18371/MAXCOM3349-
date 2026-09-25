package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SettingsAccessibility
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.material.icons.filled.Security
import com.example.manager.AppContextManager
import com.example.manager.VoiceCommandManager
import com.example.manager.VoiceState
import com.example.service.MaxAccessibilityService
import com.example.ui.components.AntiTheftGuardCard
import com.example.ui.components.AppLauncherSection
import com.example.ui.components.CameraControlCard
import com.example.ui.components.DebugLogConsole
import com.example.ui.components.HardwareToggleGrid
import com.example.ui.components.MicButton
import com.example.ui.components.RemindersCard
import com.example.ui.components.WeatherCard
import com.example.ui.components.WhatsAppAutoReplyCard
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkOutline
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceCard
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonLime
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val voiceManager = remember { VoiceCommandManager(context) }
    val voiceState by voiceManager.voiceState.collectAsState()
    val isAccessibilityServiceLive by MaxAccessibilityService.isServiceEnabled.collectAsState()

    var isAccessibilityPermissionEnabled by remember {
        mutableStateOf(MaxAccessibilityService.checkAccessibilityPermission(context))
    }

    var selectedTab by remember { mutableIntStateOf(0) }

    // Re-check permissions on resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityPermissionEnabled = MaxAccessibilityService.checkAccessibilityPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            voiceManager.destroy()
        }
    }

    val isAccessibilityActive = isAccessibilityServiceLive || isAccessibilityPermissionEnabled

    // Audio Permission Launcher
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            voiceManager.startListening()
        } else {
            Toast.makeText(context, "Microphone permission required for voice commands", Toast.LENGTH_SHORT).show()
        }
    }

    BackHandler(enabled = selectedTab != 0) {
        selectedTab = 0
    }

    Scaffold(
        containerColor = DarkBackground,
        bottomBar = {
            NavigationBar(
                containerColor = DarkSurface,
                tonalElevation = 0.dp,
                modifier = Modifier.border(1.dp, DarkOutline.copy(alpha = 0.5f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            ) {
                val tabs = listOf("Dashboard", "Guard", "Camera", "Reminders", "Weather", "WhatsApp", "Hardware", "Logs")
                tabs.forEachIndexed { index, title ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        label = { Text(title, fontSize = 8.sp, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) },
                        icon = {
                            Icon(
                                imageVector = when (index) {
                                    0 -> Icons.Default.Bolt
                                    1 -> Icons.Default.Security
                                    2 -> Icons.Default.CameraAlt
                                    3 -> Icons.Default.Alarm
                                    4 -> Icons.Default.WbSunny
                                    5 -> Icons.Default.Mic
                                    6 -> Icons.Default.SettingsAccessibility
                                    else -> Icons.Default.Info
                                },
                                contentDescription = title,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CyberCyan,
                            selectedTextColor = CyberCyan,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted,
                            indicatorColor = CyberCyan.copy(alpha = 0.15f)
                        )
                    )
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Top Header
            HeaderBar(
                isAccessibilityActive = isAccessibilityActive,
                onEnableAccessibility = {
                    MaxAccessibilityService.openAccessibilitySettings(context)
                }
            )

            // Guidance Banner when Accessibility is not yet enabled
            if (!isAccessibilityActive) {
                Spacer(modifier = Modifier.height(10.dp))
                AccessibilitySetupCard(
                    onEnableClick = {
                        MaxAccessibilityService.openAccessibilitySettings(context)
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            when (selectedTab) {
                0 -> {
                    // TAB 0: DASHBOARD (ALL IN ONE)
                    // Voice Mic Section
                    VoiceControlCard(
                        voiceState = voiceState,
                        onMicClick = {
                            if (voiceState is VoiceState.Listening) {
                                voiceManager.stopListening()
                            } else {
                                val hasPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED

                                if (hasPermission) {
                                    voiceManager.startListening()
                                } else {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        },
                        onQuickCommand = { command ->
                            voiceManager.processCommand(command)
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Anti-Theft Guard Section
                    AntiTheftGuardCard()

                    Spacer(modifier = Modifier.height(16.dp))

                    // Camera & Vision Section
                    CameraControlCard()

                    Spacer(modifier = Modifier.height(16.dp))

                    // Live Weather Section
                    WeatherCard()

                    Spacer(modifier = Modifier.height(16.dp))

                    // Reminders & Alarms Section
                    RemindersCard()

                    Spacer(modifier = Modifier.height(16.dp))

                    // WhatsApp Auto-Reply Section
                    WhatsAppAutoReplyCard()

                    Spacer(modifier = Modifier.height(16.dp))

                    // Hardware Controls Grid
                    HardwareToggleGrid(
                        isAccessibilityEnabled = isAccessibilityActive,
                        onOpenAccessibilitySettings = {
                            MaxAccessibilityService.openAccessibilitySettings(context)
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // App Open Section
                    AppLauncherSection()

                    Spacer(modifier = Modifier.height(16.dp))

                    // Live Debug Log Terminal
                    DebugLogConsole()
                }
                1 -> {
                    // TAB 1: ANTI-THEFT GUARD FOCUS
                    AntiTheftGuardCard()
                    Spacer(modifier = Modifier.height(16.dp))
                    DebugLogConsole()
                }
                2 -> {
                    // TAB 2: CAMERA & VISION FOCUS
                    CameraControlCard()
                    Spacer(modifier = Modifier.height(16.dp))
                    DebugLogConsole()
                }
                3 -> {
                    // TAB 3: REMINDERS & ALARMS FOCUS
                    RemindersCard()
                    Spacer(modifier = Modifier.height(16.dp))
                    DebugLogConsole()
                }
                4 -> {
                    // TAB 4: WEATHER FOCUS
                    WeatherCard()
                    Spacer(modifier = Modifier.height(16.dp))
                    DebugLogConsole()
                }
                5 -> {
                    // TAB 5: WHATSAPP AUTO-REPLY FOCUS
                    WhatsAppAutoReplyCard()
                    Spacer(modifier = Modifier.height(16.dp))
                    DebugLogConsole()
                }
                6 -> {
                    // TAB 6: HARDWARE FOCUS
                    HardwareToggleGrid(
                        isAccessibilityEnabled = isAccessibilityActive,
                        onOpenAccessibilitySettings = {
                            MaxAccessibilityService.openAccessibilitySettings(context)
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    DebugLogConsole()
                }
                7 -> {
                    // TAB 7: LOGS ONLY
                    DebugLogConsole()
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun AccessibilitySetupCard(
    onEnableClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, NeonAmber.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = NeonAmber,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Enable Max Accessibility Service",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Allows Max to automatically tap Quick Settings tiles (WiFi, BT, Data, Hotspot) without leaving the app.",
                    fontSize = 10.sp,
                    color = TextSecondary,
                    lineHeight = 14.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onEnableClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonAmber,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp).testTag("enable_accessibility_button")
            ) {
                Text("Enable", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun HeaderBar(
    isAccessibilityActive: Boolean,
    onEnableAccessibility: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(38.dp)
                    .background(
                        Brush.radialGradient(listOf(CyberCyan, Color(0xFF0D47A1))),
                        CircleShape
                    )
                    .border(1.5.dp, CyberCyan, CircleShape)
            ) {
                Text(
                    text = "M",
                    color = Color.Black,
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "MAX",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(NeonLime.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .border(1.dp, NeonLime.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "100% OFFLINE",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonLime
                        )
                    }
                }
                Text(
                    text = "App Launcher & Hardware Toggles",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        }

        // Accessibility Service Button
        Box(
            modifier = Modifier
                .background(
                    if (isAccessibilityActive) NeonLime.copy(alpha = 0.12f) else NeonAmber.copy(alpha = 0.15f),
                    RoundedCornerShape(20.dp)
                )
                .border(
                    1.dp,
                    if (isAccessibilityActive) NeonLime.copy(alpha = 0.4f) else NeonAmber.copy(alpha = 0.5f),
                    RoundedCornerShape(20.dp)
                )
                .clickable { onEnableAccessibility() }
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .testTag("header_accessibility_pill")
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(if (isAccessibilityActive) NeonLime else NeonAmber, CircleShape)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = if (isAccessibilityActive) "Accessibility ON" else "Enable Service",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isAccessibilityActive) NeonLime else NeonAmber
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VoiceControlCard(
    voiceState: VoiceState,
    onMicClick: () -> Unit,
    onQuickCommand: (String) -> Unit
) {
    val contextState by AppContextManager.contextState.collectAsState()

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DarkOutline, RoundedCornerShape(16.dp))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            MicButton(
                voiceState = voiceState,
                onMicClick = onMicClick
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Context Awareness Info Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurfaceVariant, RoundedCornerShape(8.dp))
                    .border(1.dp, DarkOutline.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Active Context: ",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted
                )
                val activeAppName = contextState.currentActiveApp?.name
                val lastToggleName = contextState.lastHardwareAction?.feature?.displayName
                val contextLabel = when {
                    activeAppName != null && lastToggleName != null -> "App: $activeAppName | Last Toggle: $lastToggleName"
                    activeAppName != null -> "App: $activeAppName"
                    lastToggleName != null -> "Toggle: $lastToggleName"
                    else -> "None (Say 'YouTube kholo' or 'Torch on')"
                }
                Text(
                    text = contextLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (activeAppName != null || lastToggleName != null) CyberCyan else TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Quick Example Command Chips
            Text(
                text = "Voice command test chips (English / Hindi / Context):",
                fontSize = 10.sp,
                color = TextMuted,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(6.dp))

            val sampleCommands = listOf(
                "mera emergency contact 9876543210 hai",
                "selfie lo",
                "photo lo",
                "saamne kya hai",
                "aaj ka mausam kaisa hai",
                "5 baje chai ka yaad dilana",
                "7 baje alarm laga do",
                "mere saare reminders batao",
                "auto-reply on karo",
                "यूट्यूब खोलो",
                "इसका वॉल्यूम बढ़ाओ",
                "Torch on karo",
                "WiFi band karo"
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                sampleCommands.forEach { cmd ->
                    Box(
                        modifier = Modifier
                            .background(DarkSurfaceVariant, RoundedCornerShape(12.dp))
                            .border(1.dp, DarkOutline, RoundedCornerShape(12.dp))
                            .clickable { onQuickCommand(cmd) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = cmd,
                            fontSize = 10.sp,
                            color = CyberCyan
                        )
                    }
                }
            }
        }
    }
}
