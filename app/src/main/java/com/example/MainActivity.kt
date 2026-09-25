package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
        DebugLogger.logInfo("Max Assistant Initialized (100% Local / Offline)")

        setContent {
            AppTheme {
                MainScreen()
            }
        }
    }
}
