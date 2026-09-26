package com.example.manager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import com.example.util.DebugLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

data class BatteryHealthStatus(
    val batteryPercentage: Int = 100,
    val isCharging: Boolean = false,
    val isPowerSaveMode: Boolean = false,
    val isIgnoringBatteryOptimizations: Boolean = true,
    val isDeviceInIdleMode: Boolean = false,
    val activeWakeLockCount: Int = 0,
    val voiceListeningState: String = "IDLE (Sleep Mode)",
    val accessibilityState: String = "CONNECTED (Passive/Throttled)",
    val notificationListenerState: String = "STANDBY (Event-Driven)",
    val antiTheftGuardState: String = "STANDBY (Broadcast-Driven)",
    val callAnnouncerState: String = "STANDBY (Telecom-Event)"
)

object BatteryOptimizationManager {
    private const val TAG = "BatteryOptimizationMgr"
    private val activeWakeLocks = AtomicInteger(0)

    private val _batteryStatus = MutableStateFlow(BatteryHealthStatus())
    val batteryStatus: StateFlow<BatteryHealthStatus> = _batteryStatus.asStateFlow()

    private var batteryReceiver: BroadcastReceiver? = null

    fun init(context: Context) {
        registerBatteryReceiver(context)
        refreshStatus(context)
        DebugLogger.logInfo("BatteryOptimizationManager initialized (Zero Background Drain Architecture)")
    }

    private fun registerBatteryReceiver(context: Context) {
        if (batteryReceiver != null) return
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                c?.let { refreshStatus(it) }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
            }
        }
        try {
            context.registerReceiver(batteryReceiver, filter)
        } catch (e: Exception) {
            Log.w(TAG, "Error registering battery receiver", e)
        }
    }

    fun refreshStatus(context: Context) {
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

            val batteryPct = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
            val isPowerSave = powerManager?.isPowerSaveMode ?: false
            val isDeviceIdle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                powerManager?.isDeviceIdleMode ?: false
            } else false

            val isIgnoringOptimizations = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
            } else true

            _batteryStatus.value = _batteryStatus.value.copy(
                batteryPercentage = batteryPct,
                isPowerSaveMode = isPowerSave,
                isDeviceInIdleMode = isDeviceIdle,
                isIgnoringBatteryOptimizations = isIgnoringOptimizations,
                activeWakeLockCount = activeWakeLocks.get().coerceAtLeast(0)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error querying battery status", e)
        }
    }

    fun updateSubsystemState(
        voiceState: String? = null,
        accessibilityState: String? = null,
        notificationState: String? = null,
        antiTheftState: String? = null,
        callState: String? = null
    ) {
        val current = _batteryStatus.value
        _batteryStatus.value = current.copy(
            voiceListeningState = voiceState ?: current.voiceListeningState,
            accessibilityState = accessibilityState ?: current.accessibilityState,
            notificationListenerState = notificationState ?: current.notificationListenerState,
            antiTheftGuardState = antiTheftState ?: current.antiTheftGuardState,
            callAnnouncerState = callState ?: current.callAnnouncerState
        )
    }

    /**
     * Executes block holding a safe, auto-releasing WakeLock with strict timeout.
     * Guarantees WakeLock is released immediately in finally block with 0 leaks.
     */
    fun <T> runWithSafeWakeLock(
        context: Context,
        tag: String,
        timeoutMs: Long = 8000L,
        block: () -> T
    ): T {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MaxApp:$tag")
        return try {
            wakeLock?.acquire(timeoutMs)
            activeWakeLocks.incrementAndGet()
            _batteryStatus.value = _batteryStatus.value.copy(activeWakeLockCount = activeWakeLocks.get())
            block()
        } finally {
            try {
                if (wakeLock?.isHeld == true) {
                    wakeLock.release()
                }
            } catch (_: Exception) {}
            val count = activeWakeLocks.decrementAndGet()
            _batteryStatus.value = _batteryStatus.value.copy(activeWakeLockCount = count.coerceAtLeast(0))
        }
    }

    fun requestIgnoreBatteryOptimization(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error opening battery optimization settings", e)
                try {
                    val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(fallbackIntent)
                } catch (ex: Exception) {
                    Log.e(TAG, "Error opening fallback battery settings", ex)
                }
            }
        }
    }
}
