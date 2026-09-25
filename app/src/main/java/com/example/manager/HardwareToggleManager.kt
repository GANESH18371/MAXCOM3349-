package com.example.manager

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.example.service.MaxAccessibilityService
import com.example.util.DebugLogger
import com.example.util.ToggleMethod
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class HardwareFeature(val displayName: String, val isAccessibilityPreferred: Boolean) {
    WIFI("WiFi", true),
    BLUETOOTH("Bluetooth", true),
    MOBILE_DATA("Mobile Data", true),
    HOTSPOT("Hotspot", true),
    TORCH("Torch", false),
    VOLUME("Volume", false),
    BRIGHTNESS("Brightness", false),
    DND("Do Not Disturb", false),
    AIRPLANE_MODE("Airplane Mode", true)
}

enum class VolumeAction {
    UP,
    DOWN,
    MUTE,
    UNMUTE,
    MAX
}

data class VolumeCommandParsed(
    val action: VolumeAction,
    val explicitPercent: Int? = null
)

object HardwareToggleManager {
    private const val TAG = "HardwareToggleManager"

    // Track torch state
    private val _isTorchOn = MutableStateFlow(false)
    val isTorchOn: StateFlow<Boolean> = _isTorchOn.asStateFlow()

    private var cameraManager: CameraManager? = null
    private var torchCameraId: String? = null
    private var torchCallbackRegistered = false

    fun initTorch(context: Context) {
        if (cameraManager != null) return
        try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            cameraManager = cm
            if (cm != null && !torchCallbackRegistered) {
                cm.registerTorchCallback(object : CameraManager.TorchCallback() {
                    override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                        super.onTorchModeChanged(cameraId, enabled)
                        if (cameraId == torchCameraId || torchCameraId == null) {
                            _isTorchOn.value = enabled
                        }
                    }
                }, null)
                torchCallbackRegistered = true

                // Find back camera with flash
                for (id in cm.cameraIdList) {
                    val characteristics = cm.getCameraCharacteristics(id)
                    val flashAvailable = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                    val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                    if (flashAvailable && facing == CameraCharacteristics.LENS_FACING_BACK) {
                        torchCameraId = id
                        break
                    }
                }
                if (torchCameraId == null && cm.cameraIdList.isNotEmpty()) {
                    torchCameraId = cm.cameraIdList[0]
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error initializing torch callback", e)
        }
    }

    // ==========================================
    // PART 2: DIRECT SYSTEM API CONTROLS
    // ==========================================

    /**
     * Torch On/Off (DIRECT API)
     */
    fun toggleTorch(context: Context, explicitState: Boolean? = null): Boolean {
        DebugLogger.logToggleAttempt("Torch", ToggleMethod.DIRECT)
        initTorch(context)
        val cm = cameraManager ?: run {
            DebugLogger.logToggleResult(false, "CameraManager not available")
            return false
        }
        val camId = torchCameraId ?: "0"
        val targetState = explicitState ?: !_isTorchOn.value

        return try {
            cm.setTorchMode(camId, targetState)
            _isTorchOn.value = targetState
            DebugLogger.logToggleResult(true, if (targetState) "ON" else "OFF")
            true
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Torch toggle failed", e)
            DebugLogger.logToggleResult(false, e.message ?: "CameraAccessException")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Torch toggle error", e)
            DebugLogger.logToggleResult(false, e.message ?: "Unknown error")
            false
        }
    }

    /**
     * Parse Volume Command to determine action and explicit percentage (if any).
     * Supports English, Devanagari Hindi, and Hinglish.
     */
    fun parseVolumeCommand(rawCommand: String): VolumeCommandParsed {
        val lower = rawCommand.lowercase(Locale.getDefault())

        // 1. Normalize Devanagari numerals to standard ASCII digits
        val devanagariDigits = "०१२३४५६७८९"
        val normalized = buildString {
            for (ch in lower) {
                val idx = devanagariDigits.indexOf(ch)
                if (idx != -1) append(idx) else append(ch)
            }
        }

        // 2. Extract percentage or target volume numbers (0-100)
        // Matches "60%", "60 %", "60 percent", "60 प्रतिशत", "60 परसेंट", or numbers next to volume words
        val numberRegex = Regex("""(\d{1,3})\s*(%|percent|प्रतिशत|परसेंट|प्रति\s*शत)?""")
        val matches = numberRegex.findAll(normalized).toList()
        var explicitPercent: Int? = null

        for (m in matches) {
            val num = m.groupValues[1].toIntOrNull()
            if (num != null && num in 0..100) {
                explicitPercent = num
                break
            }
        }

        // 3. Determine specific action type
        val isMute = listOf(
            "mute", "म्यूट", "चुप", "chup", "शांत", "shant", "silent", "साइलेंट",
            "band", "off", "बंद", "चुप करो", "आवाज बंद", "आवाज़ बंद", "वॉल्यूम बंद"
        ).any { lower.contains(it) }

        val isUnmute = listOf("unmute", "अनम्यूट", "un-mute").any { lower.contains(it) }

        val isMax = listOf("max", "full", "फुल", "मैक्स", "100%", "100 %", "100").any { lower.contains(it) }

        val isDown = listOf(
            "down", "kam", "kam karo", "kam kar", "ghatao", "घटाओ", "घटा", "कम",
            "कम करो", "कम कर", "धीमे", "dheeme", "decrease", "lower", "reduce", "softer", "quieter"
        ).any { lower.contains(it) }

        val isUp = listOf(
            "up", "badhao", "badha", "badao", "jyada", "बढ़ाओ", "बढ़ा", "बढ़ाओ", "बढ़ाइए",
            "बढ़ा दो", "बढ़ा दे", "tez", "तेज़", "तेज", "increase", "raise", "high", "louder"
        ).any { lower.contains(it) }

        val action = when {
            explicitPercent != null -> VolumeAction.UP
            isMute -> VolumeAction.MUTE
            isUnmute -> VolumeAction.UNMUTE
            isMax -> VolumeAction.MAX
            isDown -> VolumeAction.DOWN
            isUp -> VolumeAction.UP
            else -> VolumeAction.UP
        }

        return VolumeCommandParsed(action = action, explicitPercent = explicitPercent)
    }

    /**
     * Volume Controls (DIRECT API)
     * Directly uses AudioManager without Accessibility tap or special permissions.
     * Logs:
     * TOGGLE_ATTEMPT: Volume, method=DIRECT
     * TOGGLE_RESULT: success (Set to X%)
     */
    fun adjustVolume(context: Context, action: VolumeAction, explicitPercent: Int? = null): Boolean {
        DebugLogger.logToggleAttempt("Volume", ToggleMethod.DIRECT)
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val stream = AudioManager.STREAM_MUSIC
            val maxVolume = audioManager.getStreamMaxVolume(stream)

            if (explicitPercent != null) {
                val clampedPercent = explicitPercent.coerceIn(0, 100)
                val targetVolume = if (maxVolume > 0) (clampedPercent * maxVolume) / 100 else 0
                audioManager.setStreamVolume(stream, targetVolume, AudioManager.FLAG_SHOW_UI)
                val actualPercent = if (maxVolume > 0) (targetVolume * 100) / maxVolume else clampedPercent
                DebugLogger.logToggleResult(true, "Set to $actualPercent%")
                return true
            }

            when (action) {
                VolumeAction.UP -> {
                    audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                    val newVol = audioManager.getStreamVolume(stream)
                    val newPercent = if (maxVolume > 0) (newVol * 100) / maxVolume else 0
                    DebugLogger.logToggleResult(true, "Set to $newPercent%")
                }
                VolumeAction.DOWN -> {
                    audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                    val newVol = audioManager.getStreamVolume(stream)
                    val newPercent = if (maxVolume > 0) (newVol * 100) / maxVolume else 0
                    DebugLogger.logToggleResult(true, "Set to $newPercent%")
                }
                VolumeAction.MUTE -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                    } else {
                        @Suppress("DEPRECATION")
                        audioManager.setStreamMute(stream, true)
                    }
                    audioManager.setStreamVolume(stream, 0, AudioManager.FLAG_SHOW_UI)
                    DebugLogger.logToggleResult(true, "Set to 0%")
                }
                VolumeAction.UNMUTE -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
                    } else {
                        @Suppress("DEPRECATION")
                        audioManager.setStreamMute(stream, false)
                    }
                    val newVol = audioManager.getStreamVolume(stream)
                    val newPercent = if (maxVolume > 0) (newVol * 100) / maxVolume else 50
                    DebugLogger.logToggleResult(true, "Set to $newPercent%")
                }
                VolumeAction.MAX -> {
                    audioManager.setStreamVolume(stream, maxVolume, AudioManager.FLAG_SHOW_UI)
                    DebugLogger.logToggleResult(true, "Set to 100%")
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Volume change error", e)
            DebugLogger.logToggleResult(false, e.message ?: "Volume error")
            false
        }
    }

    /**
     * Brightness Toggle/Step (DIRECT API)
     */
    fun toggleBrightness(context: Context, targetLevelPercent: Int? = null): Boolean {
        DebugLogger.logToggleAttempt("Brightness", ToggleMethod.DIRECT)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(context)) {
            DebugLogger.logToggleResult(false, "Requires Write Settings permission")
            try {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Could not open write settings intent", e)
            }
            return false
        }

        return try {
            val contentResolver = context.contentResolver
            val current = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
            val newLevel = when {
                targetLevelPercent != null -> (targetLevelPercent * 255) / 100
                current < 70 -> 128  // Go to 50%
                current < 180 -> 255 // Go to 100%
                else -> 40          // Go to ~15%
            }

            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, newLevel)
            val percent = (newLevel * 100) / 255
            DebugLogger.logToggleResult(true, "Set to $percent%")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Brightness adjustment failed", e)
            DebugLogger.logToggleResult(false, e.message ?: "Write failed")
            false
        }
    }

    /**
     * Do Not Disturb (DND) (DIRECT API)
     */
    fun toggleDnd(context: Context, explicitEnable: Boolean? = null): Boolean {
        DebugLogger.logToggleAttempt("DND", ToggleMethod.DIRECT)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (!nm.isNotificationPolicyAccessGranted) {
            DebugLogger.logToggleResult(false, "Requires DND Policy Access permission")
            try {
                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error opening DND settings", e)
            }
            return false
        }

        return try {
            val currentFilter = nm.currentInterruptionFilter
            val isCurrentlyOn = currentFilter != NotificationManager.INTERRUPTION_FILTER_ALL

            val turnOn = explicitEnable ?: !isCurrentlyOn
            val newFilter = if (turnOn) {
                NotificationManager.INTERRUPTION_FILTER_PRIORITY
            } else {
                NotificationManager.INTERRUPTION_FILTER_ALL
            }

            nm.setInterruptionFilter(newFilter)
            DebugLogger.logToggleResult(true, if (turnOn) "ON (Priority)" else "OFF")
            true
        } catch (e: Exception) {
            Log.e(TAG, "DND toggle failed", e)
            DebugLogger.logToggleResult(false, e.message ?: "Notification policy error")
            false
        }
    }

    // ==========================================
    // PART 2: ACCESSIBILITY QUICK SETTINGS TOGGLES
    // ==========================================

    /**
     * WiFi Toggle (ACCESSIBILITY via Quick Settings Tile or Direct Panel Fallback)
     */
    fun toggleWifi(context: Context, onComplete: ((Boolean) -> Unit)? = null) {
        val keywords = listOf("wi-fi", "wifi", "internet", "wlan", "वाई-फाई", "इंटरनेट")
        
        // Try direct legacy API first if on older Android or if accessible
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifiManager != null) {
                DebugLogger.logToggleAttempt("WiFi", ToggleMethod.DIRECT)
                @Suppress("DEPRECATION")
                val success = wifiManager.setWifiEnabled(!wifiManager.isWifiEnabled)
                DebugLogger.logToggleResult(success, "Direct WifiManager API")
                onComplete?.invoke(success)
                return
            }
        }

        val panelAction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Settings.Panel.ACTION_WIFI
        } else {
            Settings.ACTION_WIFI_SETTINGS
        }
        executeAccessibilityToggle(context, "WiFi", keywords, panelAction, onComplete)
    }

    /**
     * Bluetooth Toggle (ACCESSIBILITY via Quick Settings Tile or Direct Fallback)
     */
    @SuppressLint("MissingPermission")
    fun toggleBluetooth(context: Context, onComplete: ((Boolean) -> Unit)? = null) {
        val keywords = listOf("bluetooth", "ब्लूटूथ", "bt")

        // Try direct toggle if adapter allows
        try {
            val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = btManager?.adapter
            if (adapter != null && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                DebugLogger.logToggleAttempt("Bluetooth", ToggleMethod.DIRECT)
                val success = if (adapter.isEnabled) {
                    @Suppress("DEPRECATION")
                    adapter.disable()
                } else {
                    @Suppress("DEPRECATION")
                    adapter.enable()
                }
                if (success) {
                    DebugLogger.logToggleResult(true, "Direct BluetoothAdapter API")
                    onComplete?.invoke(true)
                    return
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Direct bluetooth toggle not supported without prompt, using accessibility", e)
        }

        executeAccessibilityToggle(context, "Bluetooth", keywords, Settings.ACTION_BLUETOOTH_SETTINGS, onComplete)
    }

    /**
     * Mobile Data Toggle (ACCESSIBILITY via Quick Settings Tile)
     */
    fun toggleMobileData(context: Context, onComplete: ((Boolean) -> Unit)? = null) {
        val keywords = listOf("mobile data", "cellular", "मोबाइल डेटा", "डेटा", "data")
        val panelAction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Settings.Panel.ACTION_INTERNET_CONNECTIVITY
        } else {
            Settings.ACTION_DATA_ROAMING_SETTINGS
        }
        executeAccessibilityToggle(context, "Mobile Data", keywords, panelAction, onComplete)
    }

    /**
     * Hotspot Toggle (ACCESSIBILITY via Quick Settings Tile)
     */
    fun toggleHotspot(context: Context, onComplete: ((Boolean) -> Unit)? = null) {
        val keywords = listOf("hotspot", "tethering", "हॉटस्पॉट", "personal hotspot", "portable hotspot")
        executeAccessibilityToggle(context, "Hotspot", keywords, Settings.ACTION_WIRELESS_SETTINGS, onComplete)
    }

    /**
     * Airplane Mode Toggle (ACCESSIBILITY via Quick Settings Tile or DIRECT Settings)
     */
    fun toggleAirplaneMode(context: Context, onComplete: ((Boolean) -> Unit)? = null) {
        val keywords = listOf("airplane mode", "flight mode", "हवाई मोड", "aeroplane")
        executeAccessibilityToggle(context, "Airplane Mode", keywords, Settings.ACTION_AIRPLANE_MODE_SETTINGS, onComplete)
    }

    private fun executeAccessibilityToggle(
        context: Context,
        featureName: String,
        keywords: List<String>,
        fallbackSettingsAction: String,
        onComplete: ((Boolean) -> Unit)?
    ) {
        val service = MaxAccessibilityService.instance
        if (service != null) {
            service.triggerQuickSettingTile(featureName, keywords) { success ->
                onComplete?.invoke(success)
            }
        } else {
            // If service is not yet enabled, launch the direct System Panel / Settings so user can toggle immediately
            DebugLogger.logToggleAttempt(featureName, ToggleMethod.ACCESSIBILITY)
            DebugLogger.logInfo("Accessibility Service not running; launching system settings/panel fallback for $featureName")

            try {
                val intent = Intent(fallbackSettingsAction).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                DebugLogger.logToggleResult(true, "Opened $featureName System Panel")
                onComplete?.invoke(true)
            } catch (e: Exception) {
                // If specific panel failed, open general accessibility settings
                try {
                    MaxAccessibilityService.openAccessibilitySettings(context)
                    DebugLogger.logToggleResult(true, "Opened Accessibility Settings")
                    onComplete?.invoke(true)
                } catch (e2: Exception) {
                    DebugLogger.logToggleResult(false, "Could not open settings: ${e2.message}")
                    onComplete?.invoke(false)
                }
            }
        }
    }

    // ==========================================
    // CURRENT STATE DETECTORS
    // ==========================================

    fun isWifiEnabled(context: Context): Boolean {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifiManager?.isWifiEnabled ?: false
        } catch (e: Exception) {
            false
        }
    }

    fun isBluetoothEnabled(context: Context): Boolean {
        return try {
            val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            btManager?.adapter?.isEnabled ?: false
        } catch (e: Exception) {
            false
        }
    }

    fun isMobileDataConnected(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = cm?.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        } catch (e: Exception) {
            false
        }
    }

    fun isAirplaneModeOn(context: Context): Boolean {
        return try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
        } catch (e: Exception) {
            false
        }
    }

    fun isDndOn(context: Context): Boolean {
        return try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val filter = nm?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL
            filter != NotificationManager.INTERRUPTION_FILTER_ALL
        } catch (e: Exception) {
            false
        }
    }

    fun getBrightnessPercent(context: Context): Int {
        return try {
            val cur = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
            (cur * 100) / 255
        } catch (e: Exception) {
            50
        }
    }

    fun getVolumePercent(context: Context): Int {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (max > 0) (cur * 100) / max else 0
        } catch (e: Exception) {
            50
        }
    }
}
