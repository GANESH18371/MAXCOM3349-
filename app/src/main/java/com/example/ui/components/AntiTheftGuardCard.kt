package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.manager.AntiTheftManager
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
import kotlinx.coroutines.launch

@Composable
fun AntiTheftGuardCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val trustedContact by AntiTheftManager.trustedContact.collectAsState()
    val isGuardEnabled by AntiTheftManager.isGuardEnabled.collectAsState()
    val incidents by AntiTheftManager.incidents.collectAsState()

    var isAdminActive by remember { mutableStateOf(AntiTheftManager.isDeviceAdminActive(context)) }
    var hasCameraPerm by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var hasLocationPerm by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }
    var hasSmsPerm by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED)
    }
    var hasPhonePerm by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED)
    }

    var editName by remember(trustedContact.name) { mutableStateOf(trustedContact.name) }
    var editPhone by remember(trustedContact.phoneNumber) { mutableStateOf(trustedContact.phoneNumber) }
    var isEditingContact by remember { mutableStateOf(false) }
    var isTestingAlert by remember { mutableStateOf(false) }

    fun refreshStatus() {
        isAdminActive = AntiTheftManager.isDeviceAdminActive(context)
        hasCameraPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        hasLocationPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        hasSmsPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        hasPhonePerm = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshStatus()
    }

    val deviceAdminLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        refreshStatus()
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, if (isAdminActive && isGuardEnabled) NeonLime.copy(alpha = 0.4f) else NeonAmber.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .testTag("anti_theft_guard_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                if (isAdminActive && isGuardEnabled) NeonLime.copy(alpha = 0.15f) else NeonAmber.copy(alpha = 0.15f),
                                CircleShape
                            )
                            .border(
                                1.dp,
                                if (isAdminActive && isGuardEnabled) NeonLime.copy(alpha = 0.5f) else NeonAmber.copy(alpha = 0.5f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = if (isAdminActive && isGuardEnabled) NeonLime else NeonAmber,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = "Anti-Theft Guard",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = if (isAdminActive) "Wrong PIN & SIM Guard Active" else "Setup Required (Device Admin)",
                            fontSize = 11.sp,
                            color = if (isAdminActive) NeonLime else NeonAmber
                        )
                    }
                }

                Switch(
                    checked = isGuardEnabled,
                    onCheckedChange = { enabled ->
                        AntiTheftManager.setGuardEnabled(context, enabled)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = NeonLime,
                        checkedTrackColor = NeonLime.copy(alpha = 0.3f),
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = DarkSurfaceVariant
                    ),
                    modifier = Modifier.testTag("anti_theft_switch")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // EXPLICIT DEVICE ADMIN PROMPT / BANNER
            if (!isAdminActive) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = NeonAmber.copy(alpha = 0.12f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, NeonAmber.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = null,
                                tint = NeonAmber,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Device Admin Enable Karo (Security)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "⚠️ यह Accessibility से अलग परमिशन है! गलत PIN / Pattern detect करने के लिए Device Admin App चालू करना ज़रूरी है (Settings → Security → Device Admin apps).",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            lineHeight = 15.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val intent = AntiTheftManager.createDeviceAdminIntent(context)
                                deviceAdminLauncher.launch(intent)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonAmber,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .testTag("enable_device_admin_button")
                        ) {
                            Text("Device Admin Enable Karein", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Permissions Status Checklist
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, DarkOutline.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "Anti-Theft Permissions Status:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        PermissionStatusBadge("Admin", isAdminActive)
                        PermissionStatusBadge("Camera", hasCameraPerm)
                        PermissionStatusBadge("Location", hasLocationPerm)
                        PermissionStatusBadge("SMS", hasSmsPerm)
                        PermissionStatusBadge("SIM State", hasPhonePerm)
                    }

                    if (!hasCameraPerm || !hasLocationPerm || !hasSmsPerm || !hasPhonePerm) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                permissionsLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.CAMERA,
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                        Manifest.permission.SEND_SMS,
                                        Manifest.permission.READ_PHONE_STATE
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyberCyan.copy(alpha = 0.2f),
                                contentColor = CyberCyan
                            ),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                                .border(1.dp, CyberCyan.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .testTag("grant_theft_permissions_button")
                        ) {
                            Text("Grant Missing Permissions (Camera / SMS / GPS)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // TRUSTED EMERGENCY CONTACT SECTION
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, DarkOutline.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                tint = CyberCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Trusted Emergency Contact:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }

                        Text(
                            text = if (isEditingContact) "Cancel" else "Edit",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberCyan,
                            modifier = Modifier
                                .clickable { isEditingContact = !isEditingContact }
                                .padding(4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    if (!isEditingContact) {
                        if (trustedContact.phoneNumber.isNotBlank()) {
                            Text(
                                text = "${trustedContact.name.ifBlank { "Emergency Contact" }}: ${trustedContact.phoneNumber}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = NeonLime
                            )
                            Text(
                                text = "Galat PIN lagne par is number par silent SMS & live GPS location link jayega.",
                                fontSize = 10.sp,
                                color = TextMuted
                            )
                        } else {
                            Text(
                                text = "No trusted contact set yet. Add a phone number to receive alerts!",
                                fontSize = 11.sp,
                                color = NeonRed,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = editName,
                            onValueChange = { editName = it },
                            label = { Text("Contact Name (e.g. Papa / Friend)") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = DarkOutline,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = editPhone,
                            onValueChange = { editPhone = it },
                            label = { Text("10-Digit Mobile Number") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = DarkOutline,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                AntiTheftManager.setTrustedContact(context, editName, editPhone)
                                isEditingContact = false
                                Toast.makeText(context, "Trusted contact saved!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .testTag("save_trusted_contact_button")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Emergency Contact", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "💡 Voice se bhi set kar sakte hain: 'Mera emergency contact 9876543210 hai'",
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Test Simulation Button & How It Works
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        isTestingAlert = true
                        scope.launch {
                            AntiTheftManager.triggerTheftAlertPipeline(context, triggerType = "TEST")
                            isTestingAlert = false
                            Toast.makeText(context, "Test Alert Executed (Photo + Location + SMS)", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !isTestingAlert,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DarkSurfaceVariant,
                        contentColor = CyberCyan
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .border(1.dp, CyberCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .testTag("test_theft_alert_button")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isTestingAlert) "Testing..." else "Test Silent Alert", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Features Checklist
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                FeatureRow(icon = Icons.Default.Lock, title = "Wrong Password Detection", desc = "Galat PIN hone par DeviceAdmin silently trigger hota hai.")
                FeatureRow(icon = Icons.Default.CameraAlt, title = "Silent Front Photo", desc = "Chor ko pata chale bina front camera se photo capture.")
                FeatureRow(icon = Icons.Default.LocationOn, title = "Live GPS Coordinates", desc = "Google Maps location link trusted contact ko SMS se bheja jata hai.")
                FeatureRow(icon = Icons.Default.SimCard, title = "SIM Change Alert", desc = "Naya SIM lagne par turant alert SMS trigger hota hai.")
            }

            // Incidents list if any
            if (incidents.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Recent Security Events (${incidents.size}):",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))

                incidents.take(3).forEach { incident ->
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .border(1.dp, DarkOutline.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${incident.triggerType} at ${incident.timestamp}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (incident.triggerType == "TEST") CyberCyan else NeonRed
                                )
                                Text(
                                    text = "Photo: ${if (incident.photoPath != null) "Captured" else "Failed"} | SMS: ${if (incident.alertSent) "Sent" else "Not Sent"}",
                                    fontSize = 9.sp,
                                    color = TextMuted
                                )
                            }
                            if (incident.locationUrl != null) {
                                Text(
                                    text = "View Map",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyberCyan,
                                    modifier = Modifier
                                        .clickable {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(incident.locationUrl))
                                                context.startActivity(intent)
                                            } catch (_: Exception) {}
                                        }
                                        .padding(4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionStatusBadge(name: String, isGranted: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Error,
            contentDescription = null,
            tint = if (isGranted) NeonLime else NeonAmber,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = name,
            fontSize = 10.sp,
            color = if (isGranted) NeonLime else NeonAmber,
            fontWeight = if (isGranted) FontWeight.Normal else FontWeight.Bold
        )
    }
}

@Composable
private fun FeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = CyberCyan,
            modifier = Modifier
                .size(14.dp)
                .padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(text = title, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(text = desc, fontSize = 9.sp, color = TextSecondary, lineHeight = 12.sp)
        }
    }
}
