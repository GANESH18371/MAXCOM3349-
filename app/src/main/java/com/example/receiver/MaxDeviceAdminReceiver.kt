package com.example.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.manager.AntiTheftManager
import com.example.util.DebugLogger

class MaxDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Device Admin enabled")
        // Required log: "DEVICE_ADMIN_STATUS: enabled"
        DebugLogger.logDeviceAdminStatus(true)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.w(TAG, "Device Admin disabled")
        // Required log: "DEVICE_ADMIN_STATUS: disabled"
        DebugLogger.logDeviceAdminStatus(false)
    }

    override fun onPasswordFailed(context: Context, intent: Intent) {
        super.onPasswordFailed(context, intent)
        Log.w(TAG, "Device unlock password/PIN/pattern failed!")
        AntiTheftManager.onWrongPasswordAttempt(context)
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent) {
        super.onPasswordSucceeded(context, intent)
        Log.d(TAG, "Device unlock password succeeded")
    }

    companion object {
        private const val TAG = "MaxDeviceAdminReceiver"
    }
}
