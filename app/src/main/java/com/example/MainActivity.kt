package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.manager.DefaultAssistantManager
import com.example.manager.HardwareToggleManager
import com.example.ui.MainScreen
import com.example.ui.theme.AppTheme
import com.example.util.DebugLogger

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        const val EXTRA_AUTO_START_LISTENING = "extra_auto_start_listening"
        const val EXTRA_TRIGGER_SOURCE = "extra_trigger_source"
    }
}
