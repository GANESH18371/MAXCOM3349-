package com.example.service

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.util.Log
import com.example.manager.DefaultAssistantManager
import com.example.util.DebugLogger

class MaxVoiceInteractionService : VoiceInteractionService() {

    override fun onReady() {
        super.onReady()
        Log.i(TAG, "MaxVoiceInteractionService is ready and registered as Digital Assistant")
        DebugLogger.logInfo("DEFAULT_ASSISTANT_STATUS: enabled (MaxVoiceInteractionService ready)")
        DefaultAssistantManager.notifyAssistantStateChanged(true)
    }

    override fun onShutdown() {
        super.onShutdown()
        Log.i(TAG, "MaxVoiceInteractionService shutdown")
        DefaultAssistantManager.notifyAssistantStateChanged(false)
    }

    companion object {
        private const val TAG = "MaxVoiceInteractionSvc"

        fun isCurrentActiveService(context: Context): Boolean {
            return try {
                isActiveService(context, ComponentName(context, MaxVoiceInteractionService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "Error checking isActiveService", e)
                false
            }
        }
    }
}
