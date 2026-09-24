package com.example.ui.components

import android.content.Context
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.manager.HardwareToggleManager
import com.example.manager.VolumeAction
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HardwareToggleGrid(
    isAccessibilityEnabled: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isTorchOn by HardwareToggleManager.isTorchOn.collectAsState()

    val isWifiOn = HardwareToggleManager.isWifiEnabled(context)
    val isBtOn = HardwareToggleManager.isBluetoothEnabled(context)
    val isDataOn = HardwareToggleManager.isMobileDataConnected(context)
    val isAirplaneOn = HardwareToggleManager.isAirplaneModeOn(context)
    val isDndOn = HardwareToggleManager.isDndOn(context)
    val brightnessPercent = HardwareToggleManager.getBrightnessPercent(context)
    val volumePercent = HardwareToggleManager.getVolumePercent(context)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "HARDWARE CONTROLS (100% LOCAL)",
                style = MaterialTheme.typography.labelLarge,
                color = CyberCyan
            )

            // Accessibility Status Indicator
            Box(
                modifier = Modifier
                    .background(
                        if (isAccessibilityEnabled) NeonLime.copy(alpha = 0.15f) else NeonAmber.copy(alpha = 0.15f),
                        RoundedCornerShape(12.dp)
                    )
                    .border(
                        1.dp,
                        if (isAccessibilityEnabled) NeonLime.copy(alpha = 0.4f) else NeonAmber.copy(alpha = 0.4f),
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { if (!isAccessibilityEnabled) onOpenAccessibilitySettings() }
                    .padding(horizontal = 8.dp, vertical = 3.dp)
                    .testTag("accessibility_status_badge")
            ) {
                Text(
                    text = if (isAccessibilityEnabled) "ACCESSIBILITY: ACTIVE" else "ACCESSIBILITY: ENABLE",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isAccessibilityEnabled) NeonLime else NeonAmber
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Grid of toggles
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            maxItemsInEachRow = 2,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. Torch (DIRECT)
            ToggleCard(
                title = "Torch",
                method = "DIRECT API",
                status = if (isTorchOn) "ON" else "OFF",
                isActive = isTorchOn,
                icon = Icons.Default.FlashlightOn,
                activeColor = NeonAmber,
                testTag = "toggle_torch_card",
                onClick = { HardwareToggleManager.toggleTorch(context) },
                modifier = Modifier.weight(1f)
            )

            // 2. WiFi (ACCESSIBILITY)
            ToggleCard(
                title = "Wi-Fi",
                method = "ACCESSIBILITY",
                status = if (isWifiOn) "CONNECTED" else "TOGGLE",
                isActive = isWifiOn,
                icon = Icons.Default.Wifi,
                activeColor = CyberCyan,
                testTag = "toggle_wifi_card",
                onClick = { HardwareToggleManager.toggleWifi(context) },
                modifier = Modifier.weight(1f)
            )

            // 3. Bluetooth (ACCESSIBILITY)
            ToggleCard(
                title = "Bluetooth",
                method = "ACCESSIBILITY",
                status = if (isBtOn) "ON" else "OFF",
                isActive = isBtOn,
                icon = Icons.Default.Bluetooth,
                activeColor = CyberCyan,
                testTag = "toggle_bluetooth_card",
                onClick = { HardwareToggleManager.toggleBluetooth(context) },
                modifier = Modifier.weight(1f)
            )

            // 4. Mobile Data (ACCESSIBILITY)
            ToggleCard(
                title = "Mobile Data",
                method = "ACCESSIBILITY",
                status = if (isDataOn) "ACTIVE" else "TOGGLE",
                isActive = isDataOn,
                icon = Icons.Default.CellTower,
                activeColor = NeonLime,
                testTag = "toggle_data_card",
                onClick = { HardwareToggleManager.toggleMobileData(context) },
                modifier = Modifier.weight(1f)
            )

            // 5. Hotspot (ACCESSIBILITY)
            ToggleCard(
                title = "Hotspot",
                method = "ACCESSIBILITY",
                status = "QUICK TOGGLE",
                isActive = false,
                icon = Icons.Default.WifiTethering,
                activeColor = NeonAmber,
                testTag = "toggle_hotspot_card",
                onClick = { HardwareToggleManager.toggleHotspot(context) },
                modifier = Modifier.weight(1f)
            )

            // 6. DND (DIRECT)
            ToggleCard(
                title = "Do Not Disturb",
                method = "DIRECT API",
                status = if (isDndOn) "DND ON" else "OFF",
                isActive = isDndOn,
                icon = Icons.Default.DoNotDisturbOn,
                activeColor = NeonRed,
                testTag = "toggle_dnd_card",
                onClick = { HardwareToggleManager.toggleDnd(context) },
                modifier = Modifier.weight(1f)
            )

            // 7. Airplane Mode (ACCESSIBILITY)
            ToggleCard(
                title = "Airplane Mode",
                method = "ACCESSIBILITY",
                status = if (isAirplaneOn) "ON" else "OFF",
                isActive = isAirplaneOn,
                icon = Icons.Default.AirplanemodeActive,
                activeColor = NeonAmber,
                testTag = "toggle_airplane_card",
                onClick = { HardwareToggleManager.toggleAirplaneMode(context) },
                modifier = Modifier.weight(1f)
            )

            // 8. Brightness (DIRECT)
            ToggleCard(
                title = "Brightness ($brightnessPercent%)",
                method = "DIRECT API",
                status = "CYCLE LVL",
                isActive = true,
                icon = Icons.Default.BrightnessHigh,
                activeColor = CyberCyan,
                testTag = "toggle_brightness_card",
                onClick = { HardwareToggleManager.toggleBrightness(context) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Volume Quick Bar (DIRECT)
        VolumeControlCard(
            volumePercent = volumePercent,
            context = context
        )
    }
}

@Composable
private fun ToggleCard(
    title: String,
    method: String,
    status: String,
    isActive: Boolean,
    icon: ImageVector,
    activeColor: Color,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
        modifier = modifier
            .border(
                1.dp,
                if (isActive) activeColor.copy(alpha = 0.6f) else DarkOutline,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        if (isActive) activeColor.copy(alpha = 0.2f) else DarkSurfaceVariant,
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (isActive) activeColor else TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = method,
                        fontSize = 9.sp,
                        color = if (method.contains("DIRECT")) NeonLime else CyberCyan,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = " • $status",
                        fontSize = 9.sp,
                        color = if (isActive) activeColor else TextMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun VolumeControlCard(
    volumePercent: Int,
    context: Context
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DarkOutline, RoundedCornerShape(12.dp))
            .testTag("volume_control_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Volume Stream: $volumePercent%",
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = 13.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "DIRECT API • AudioManager",
                    fontSize = 9.sp,
                    color = NeonLime
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { HardwareToggleManager.adjustVolume(context, VolumeAction.DOWN) },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("volume_down_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeDown,
                        contentDescription = "Volume Down",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = { HardwareToggleManager.adjustVolume(context, VolumeAction.MUTE) },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("volume_mute_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeMute,
                        contentDescription = "Mute",
                        tint = NeonRed,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = { HardwareToggleManager.adjustVolume(context, VolumeAction.UP) },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("volume_up_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "Volume Up",
                        tint = CyberCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
