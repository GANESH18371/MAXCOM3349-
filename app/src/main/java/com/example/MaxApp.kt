package com.example

import android.app.Application

class MaxApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: MaxApp
            private set
    }
}
