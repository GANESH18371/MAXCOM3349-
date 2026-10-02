package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.manager.AppContextManager
import com.example.manager.DefaultAssistantManager
import com.example.manager.VoiceCommandManager
import com.example.manager.VoiceState
import com.example.service.MaxAccessibilityService
import com.example.util.DebugLogger
import com.example.ui.components.AntiTheftGuardCard
import com.example.ui.components.AppLauncherSection
import com.example.ui.components.BatteryReportCard
import com.example.ui.components.CallAnnounceCard
import com.example.ui.components.CameraControlCard
import com.example.ui.components.DebugLogConsole
import com.example.ui.components.DefaultAssistantCard
import com.example.ui.components.GeminiApiKeyCard
import com.example.ui.components.HardwareToggleGrid
import com.example.ui.components.PermanentMemoryCard
import com.example.ui.components.RemindersCard
import com.example.ui.components.VoiceCloningCard
import com.example.ui.components.VoiceSettingsCard
import com.example.ui.components.WakeWordSettingsCard
import com.example.ui.components.WeatherCard
import com.example.ui.components.WhatsAppAutoReplyCard
import com.example.ui.components.jarvis.JarvisArcReactor
import com.example.ui.components.jarvis.JarvisControlCenterGrid
import com.example.ui.components.jarvis.JarvisHeader
import com.example.ui.components.jarvis.JarvisRemindersCard
import com.example.ui.components.jarvis.JarvisSystemNotificationsCard
import com.example.ui.components.jarvis.JarvisTelemetryGauges
import com.example.ui.components.jarvis.JarvisWeatherCard
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DarkOutline
import com.example.ui.theme.DarkSurfaceCard
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.JarvisBackground
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
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.TextMuted
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

    // Re-check permissions on resume & link Gemini Live to Command Router
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
        DebugLogger.logAudioPermissionStatus(isGranted)
        if (isGranted) {
            voiceManager.startListening()
            if (com.example.manager.WakeWordManager.isEnabled.value) {
                com.example.service.WakeWordBackgroundService.start(context)
            }
        } else {
            Toast.makeText(context, "Microphone permission required for voice commands", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        DebugLogger.logAudioPermissionStatus(hasMic)
        if (com.example.manager.WakeWordManager.isEnabled.value && hasMic) {
            com.example.service.WakeWordBackgroundService.start(context)
        }
    }

    // Trigger voice listening helper
    val triggerVoiceListening: () -> Unit = {
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
    }

    // Auto-start voice listening when Digital Assistant gesture / Assist Intent fires
    val autoListenRequest by DefaultAssistantManager.autoListenRequest.collectAsState()
    LaunchedEffect(autoListenRequest) {
        if (autoListenRequest > 0L) {
            val hasMicPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (hasMicPermission) {
                voiceManager.startListening()
            } else {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    BackHandler(enabled = selectedTab != 0) {
        selectedTab = 0
    }

    Scaffold(
        containerColor = JarvisBackground,
        bottomBar = {
            NavigationBar(
                containerColor = JarvisSurface,
                tonalElevation = 0.dp,
                modifier = Modifier.border(
                    1.dp,
                    JarvisCardBorder,
                    RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                )
            ) {
                val tabs = listOf(
                    Triple("Dashboard", Icons.Default.Dashboard, 0),
                    Triple("Assistant", Icons.Default.HeadsetMic, 1),
                    Triple("Tools", Icons.Default.Build, 2),
                    Triple("Settings", Icons.Default.Settings, 3)
                )
                tabs.forEach { (title, icon, index) ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        label = {
                            Text(
                                text = title,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        icon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = title,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = JarvisCyan,
                            selectedTextColor = JarvisCyan,
                            unselectedIconColor = JarvisTextDim,
                            unselectedTextColor = JarvisTextDim,
                            indicatorColor = JarvisCyan.copy(alpha = 0.15f)
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
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            when (selectedTab) {
                0 -> {
                    // ==========================================
                    // TAB 0: JARVIS FUTURISTIC DASHBOARD
                    // ==========================================

                    // 1. Futuristic Header
                    JarvisHeader()

                    // Accessibility Guidance Banner if inactive
                    if (!isAccessibilityActive) {
                        Spacer(modifier = Modifier.height(10.dp))
                        AccessibilitySetupCard(
                            onEnableClick = {
                                MaxAccessibilityService.openAccessibilitySettings(context)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 2. Central Animated Arc Reactor Visual
                    JarvisArcReactor(
                        voiceState = voiceState,
                        onTriggerListening = triggerVoiceListening
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 3. Circular / Arc Progress Gauges (Battery %, RAM %, Storage %)
                    JarvisTelemetryGauges()

                    Spacer(modifier = Modifier.height(14.dp))

                    // 4. Live Meteorological / Weather HUD Card
                    JarvisWeatherCard()

                    Spacer(modifier = Modifier.height(14.dp))

                    // 5. Upcoming Reminders & Chrono Tasks Card
                    JarvisRemindersCard(
                        onNavigateToReminders = { selectedTab = 2 }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 6. System Daemon & Security Notifications
                    JarvisSystemNotificationsCard()

                    Spacer(modifier = Modifier.height(14.dp))

                    // 7. 2-Column Glowing Control-Center Matrix Grid
                    JarvisControlCenterGrid(
                        onTriggerVoice = triggerVoiceListening,
                        onNavigateToAssistant = { selectedTab = 1 },
                        onNavigateToTools = { selectedTab = 2 },
                        onNavigateToSettings = { selectedTab = 3 }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }

                1 -> {
                    // ==========================================
                    // TAB 1: ASSISTANT & CONVERSATION MATRIX
                    // ==========================================
                    JarvisSectionHeader(
                        title = "NEURAL CONVERSATION CONSOLE",
                        subtitle = "Real-time command transcripts, active context & quick chips"
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Voice Command Card with Quick Chips & Context Aware State
                    VoiceControlCard(
                        voiceState = voiceState,
                        onQuickCommand = { command ->
                            voiceManager.processCommand(command)
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Default Digital Assistant System Status
                    DefaultAssistantCard(
                        onTriggerVoiceListening = triggerVoiceListening
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }

                2 -> {
                    // ==========================================
                    // TAB 2: HARDWARE & SUBSYSTEM MATRIX (TOOLS)
                    // ==========================================
                    JarvisSectionHeader(
                        title = "SUBSYSTEM & UTILITY MATRIX",
                        subtitle = "Hardware toggles, cameras, sentry guard & local daemons"
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Hardware Controls Grid (Torch, Wi-Fi, BT, Volume, etc.)
                    HardwareToggleGrid(
                        isAccessibilityEnabled = isAccessibilityActive,
                        onOpenAccessibilitySettings = {
                            MaxAccessibilityService.openAccessibilitySettings(context)
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // App Launcher Matrix
                    AppLauncherSection()

                    Spacer(modifier = Modifier.height(16.dp))

                    // Camera & Vision Section (Gemini Scene Analysis & Silent Photo)
                    CameraControlCard()

                    Spacer(modifier = Modifier.height(16.dp))

                    // Anti-Theft Guard Security Sentry
                    AntiTheftGuardCard()

                    Spacer(modifier = Modifier.height(16.dp))

                    // Call Announce + Voice Accept/Reject
                    CallAnnounceCard()

                    Spacer(modifier = Modifier.height(16.dp))

                    // Battery & Performance Diagnostics Section
                    BatteryReportCard()

                    Spacer(modifier = Modifier.height(16.dp))

                    // Full Weather Module
                    WeatherCard()

                    Spacer(modifier = Modifier.height(16.dp))

                    // Full Reminders & Alarms Module
                    RemindersCard()

                    Spacer(modifier = Modifier.height(16.dp))

                    // WhatsApp Auto-Reply Section
                    WhatsAppAutoReplyCard()

                    Spacer(modifier = Modifier.height(16.dp))
                }

                3 -> {
                    // ==========================================
                    // TAB 3: SETTINGS & SYSTEM STUDIO
                    // ==========================================
                    JarvisSectionHeader(
                        title = "SETTINGS & NEURAL VAULT",
                        subtitle = "Centralized Gemini API key, permanent memory & TTS studio"
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Centralized Gemini API Key Vault Card (AES-256 Encrypted)
                    GeminiApiKeyCard()

                    Spacer(modifier = Modifier.height(16.dp))

                    // Voice Cloning Card (Apni Khud Ki Awaaz)
                    VoiceCloningCard()

                    Spacer(modifier = Modifier.height(16.dp))

                    // Wake-Word & Owner Biometrics Card
                    WakeWordSettingsCard()

                    Spacer(modifier = Modifier.height(16.dp))

                    // Permanent Long-Term Memory Core (Room Persistent Store)
                    PermanentMemoryCard()

                    Spacer(modifier = Modifier.height(16.dp))

                    // Voice & TTS Customization Studio (Pitch, Speed, Voice, Language)
                    VoiceSettingsCard()

                    Spacer(modifier = Modifier.height(16.dp))

                    // Live Debug Log Terminal Console
                    DebugLogConsole()

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun JarvisSectionHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(JarvisCyan, CircleShape)
            )
            Text(
                text = title,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = JarvisCyan,
                letterSpacing = 1.sp
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtitle,
            fontSize = 11.sp,
            color = JarvisTextSecondary
        )
    }
}

@Composable
private fun AccessibilitySetupCard(
    onEnableClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = JarvisCard),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, JarvisNeonAmber.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .testTag("accessibility_setup_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = JarvisNeonAmber,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "ACCESSIBILITY PERMISSION REQUIRED",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = JarvisNeonAmber
                    )
                    Text(
                        text = "Enable Max service for hardware toggles & back button control",
                        fontSize = 10.sp,
                        color = JarvisTextSecondary
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onEnableClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = JarvisNeonAmber,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VoiceControlCard(
    voiceState: VoiceState,
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
            // Voice Engine Status & Live Transcript Box (Activation centralized at Arc-Reactor)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurfaceVariant, RoundedCornerShape(12.dp))
                    .border(1.dp, DarkOutline, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(
                                        when (voiceState) {
                                            is VoiceState.Listening -> JarvisNeonGreen
                                            is VoiceState.Processing -> JarvisNeonAmber
                                            is VoiceState.Success -> JarvisCyan
                                            is VoiceState.Error -> JarvisNeonRed
                                            is VoiceState.Idle -> JarvisTextDim
                                        },
                                        CircleShape
                                    )
                            )
                            Text(
                                text = when (voiceState) {
                                    is VoiceState.Listening -> "VOICE ENGINE: LISTENING..."
                                    is VoiceState.Processing -> "VOICE ENGINE: ANALYZING..."
                                    is VoiceState.Success -> "STATUS: COMPLETED"
                                    is VoiceState.Error -> "STATUS: ERROR"
                                    is VoiceState.Idle -> "VOICE ENGINE: STANDBY"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = when (voiceState) {
                                    is VoiceState.Listening -> JarvisNeonGreen
                                    is VoiceState.Processing -> JarvisNeonAmber
                                    is VoiceState.Success -> JarvisCyan
                                    is VoiceState.Error -> JarvisNeonRed
                                    is VoiceState.Idle -> JarvisTextSecondary
                                }
                            )
                        }

                        Text(
                            text = "OFFLINE ROUTER",
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            color = JarvisTextDim
                        )
                    }

                    // Transcript Text Box
                    val transcript = when (voiceState) {
                        is VoiceState.Listening -> "Listening... Speak in Hindi or English (e.g. 'YouTube kholo', 'WiFi on karo')."
                        is VoiceState.Processing -> "Processing command... Matching installed apps and hardware toggles."
                        is VoiceState.Success -> voiceState.message
                        is VoiceState.Error -> voiceState.message
                        is VoiceState.Idle -> "Standby. Engage voice via Dashboard Neural Arc-Reactor (Tap: Local Commands • Hold: Gemini Live)."
                    }

                    Text(
                        text = transcript,
                        fontSize = 12.sp,
                        color = when (voiceState) {
                            is VoiceState.Success -> JarvisCyan
                            is VoiceState.Error -> JarvisNeonRed
                            is VoiceState.Listening -> JarvisTextPrimary
                            else -> JarvisTextSecondary
                        },
                        lineHeight = 16.sp
                    )
                }
            }

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
                "meri favorite app YouTube hai",
                "meri favorite app kholo",
                "tumhe mere baare me kya pata hai",
                "mujhe chai pasand hai",
                "mujhe kya pasand hai",
                "yeh yaad rakhna: subah walk par jaana hai",
                "walk bhool jao",
                "utha lo",
                "katt do",
                "mera emergency contact 9876543210 hai",
                "selfie lo",
                "photo lo",
                "saamne kya hai",
                "aaj ka mausam kaisa hai",
                "5 baje chai ka yaad dilana",
                "7 baje alarm laga do",
                "auto-reply on karo",
                "यूट्यूब खोलो",
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
