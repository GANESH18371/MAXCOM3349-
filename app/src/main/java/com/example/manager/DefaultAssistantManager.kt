package com.example.manager

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.example.service.MaxVoiceInteractionService
import com.example.util.DebugLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DefaultAssistantManager {
    private const val TAG = "DefaultAssistantMgr"

    private val _isDefaultAssistant = MutableStateFlow(false)
    val isDefaultAssistant: StateFlow<Boolean> = _isDefaultAssistant.asStateFlow()

    private val _totalAssistTriggers = MutableStateFlow(0)
    val totalAssistTriggers: StateFlow<Int> = _totalAssistTriggers.asStateFlow()

    private val _lastTriggerTime = MutableStateFlow<String?>(null)
    val lastTriggerTime: StateFlow<String?> = _lastTriggerTime.asStateFlow()

    private val _autoListenRequest = MutableStateFlow(0L)
    val autoListenRequest: StateFlow<Long> = _autoListenRequest.asStateFlow()

    fun triggerAutoListen() {
        _autoListenRequest.value = System.currentTimeMillis()
    }

    fun init(context: Context) {
        checkAssistantStatus(context)
        DebugLogger.logInfo("DefaultAssistantManager initialized (Default Assistant status: ${_isDefaultAssistant.value})")
    }

    fun checkAssistantStatus(context: Context): Boolean {
        var isCurrent = false
        try {
            // Check via VoiceInteractionService active state
            if (MaxVoiceInteractionService.isCurrentActiveService(context)) {
                isCurrent = true
            }

            // Fallback check via Settings.Secure
            if (!isCurrent) {
                val assistantSetting = Settings.Secure.getString(context.contentResolver, "voice_interaction_service")
                    ?: Settings.Secure.getString(context.contentResolver, "assistant")
                if (assistantSetting != null && assistantSetting.contains(context.packageName)) {
                    isCurrent = true
                }
            }

            // RoleManager check on Android 10+ (Q)
            if (!isCurrent && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
                if (roleManager?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true) {
                    isCurrent = true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking default assistant status", e)
        }

        _isDefaultAssistant.value = isCurrent
        return isCurrent
    }

    fun notifyAssistantStateChanged(isActive: Boolean) {
        _isDefaultAssistant.value = isActive
        DebugLogger.logInfo("DEFAULT_ASSISTANT_STATUS: ${if (isActive) "enabled" else "disabled"}")
    }

    fun recordAssistTrigger() {
        _totalAssistTriggers.value += 1
        val timeString = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(Date())
        _lastTriggerTime.value = timeString
    }

    /**
     * Opens Android Settings directly to Digital Assistant & Voice Input settings page.
     */
    fun openDigitalAssistantSettings(context: Context): Boolean {
        DebugLogger.logInfo("Opening Default Digital Assistant Settings...")
        val intents = mutableListOf<Intent>()

        // 1. Primary: Direct Voice Input / Assistant settings
        intents.add(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })

        // 2. Secondary: Default Apps settings on Android 7.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            intents.add(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        }

        // 3. Fallback: RoleManager Request Assistant Role on Android Q+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
                try {
                    val roleIntent = roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    intents.add(0, roleIntent) // Prioritize role popup on Android 10+
                } catch (e: Exception) {
                    Log.w(TAG, "RoleManager intent creation failed", e)
                }
            }
        }

        // 4. Fallback: General Application Settings
        intents.add(Intent(Settings.ACTION_APPLICATION_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })

        for (intent in intents) {
            try {
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Failed launching intent: ${intent.action}", e)
            }
        }
        return false
    }
}
