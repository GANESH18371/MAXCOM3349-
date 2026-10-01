package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.manager.DefaultAssistantManager
import com.example.manager.HardwareToggleManager
import com.example.ui.MainScreen
import com.example.ui.theme.AppTheme
import com.example.util.DebugLogger

class MainActivity : ComponentActivity() {

    private var pendingCameraAction: (() -> Unit)? = null

    val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            DebugLogger.logInfo("Camera permission granted by user")
            val action = pendingCameraAction
            pendingCameraAction = null
            action?.invoke()
        } else {
            DebugLogger.logInfo("Camera permission denied by user")
            pendingCameraAction = null
        }
    }

    fun requestCameraPermission(onGranted: (() -> Unit)? = null) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            onGranted?.invoke()
            return
        }
        pendingCameraAction = onGranted
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentActivity = this
        enableEdgeToEdge()

        // Initialize local hardware managers
        HardwareToggleManager.initTorch(this)
        com.example.util.TtsManager.init(this)
        com.example.manager.AntiTheftManager.init(this)
        com.example.manager.CallManager.init(this)
        com.example.manager.BatteryOptimizationManager.init(this)
        DefaultAssistantManager.init(this)
        DebugLogger.logInfo("Max Assistant Initialized (100% Local / Offline)")

        handleAssistIntent(intent)

        setContent {
            AppTheme {
                MainScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        currentActivity = this
    }

    override fun onDestroy() {
        super.onDestroy()
        if (currentActivity == this) {
            currentActivity = null
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAssistIntent(intent)
    }

    private fun handleAssistIntent(intent: Intent?) {
        if (intent == null) return
        val isAssistAction = intent.action == Intent.ACTION_ASSIST ||
                intent.action == "android.intent.action.VOICE_ASSIST" ||
                intent.action == "android.intent.action.VOICE_COMMAND"
        val isAutoListenExtra = intent.getBooleanExtra(EXTRA_AUTO_START_LISTENING, false)

        if (isAssistAction || isAutoListenExtra) {
            val source = intent.getStringExtra(EXTRA_TRIGGER_SOURCE) ?: "ASSIST_GESTURE"
            DebugLogger.logInfo("ASSISTANT_TRIGGERED: source=$source")
            DefaultAssistantManager.recordAssistTrigger()
            DefaultAssistantManager.triggerAutoListen()
        }
    }

    companion object {
        var currentActivity: MainActivity? = null
            private set

        const val EXTRA_AUTO_START_LISTENING = "extra_auto_start_listening"
        const val EXTRA_TRIGGER_SOURCE = "extra_trigger_source"
    }
}
