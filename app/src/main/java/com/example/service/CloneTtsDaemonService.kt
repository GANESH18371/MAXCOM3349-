package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.example.manager.CloneTtsLocalServer
import com.example.util.DebugLogger

/**
 * Background Service ensuring the embedded CloneTTS / Sherpa-ONNX daemon
 * (127.0.0.1:8080) runs continuously as a background process inside the phone.
 */
class CloneTtsDaemonService : Service() {

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "CloneTtsDaemonService created. Starting local CloneTTS server daemon on 127.0.0.1:8080...")
        CloneTtsLocalServer.start(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "CloneTtsDaemonService onStartCommand")
        CloneTtsLocalServer.start(applicationContext)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "CloneTtsDaemonService onDestroy")
        CloneTtsLocalServer.stop()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "CloneTtsDaemonService"

        fun startDaemon(context: Context) {
            try {
                val intent = Intent(context, CloneTtsDaemonService::class.java)
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Could not start CloneTtsDaemonService: ${e.message}")
            }
        }
    }
}
