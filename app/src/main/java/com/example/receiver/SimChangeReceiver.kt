package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.manager.AntiTheftManager
import com.example.util.DebugLogger

class SimChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == "android.intent.action.SIM_STATE_CHANGED" ||
            action == "android.intent.action.SIM_CARD_STATE_CHANGED" ||
            action == "android.intent.action.SIM_APPLICATION_STATE_CHANGED") {
            Log.d(TAG, "SIM state changed broadcast received")
            DebugLogger.logInfo("SIM state change detected by broadcast")
            AntiTheftManager.checkSimState(context)
        }
    }

    companion object {
        private const val TAG = "SimChangeReceiver"
    }
}
