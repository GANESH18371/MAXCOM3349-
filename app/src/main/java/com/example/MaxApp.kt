package com.example

import android.app.Application
import com.example.util.SecureApiKeyManager

class MaxApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        SecureApiKeyManager.init(this)
        com.example.manager.OfflineVoiceCloneManager.init(this)
        com.example.manager.WakeWordManager.init(this)
    }

    companion object {
        lateinit var instance: MaxApp
            private set
    }
}
