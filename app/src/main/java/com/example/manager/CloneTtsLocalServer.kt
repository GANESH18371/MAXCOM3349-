package com.example.manager

import android.content.Context
import android.util.Log
import com.example.util.DebugLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Locale

/**
 * 100% Offline Embedded CloneTTS / Sherpa-ONNX Local HTTP Daemon.
 * Runs directly on Android device listening on 127.0.0.1:8080.
 * Synthesizes speech using the owner's genuine biometric voice embedding and audio profile.
 */
object CloneTtsLocalServer {
    private const val TAG = "CloneTtsServer"
    const val DEFAULT_PORT = 8080
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _status = MutableStateFlow("not-started") // "running", "not-started", "failed"
    val status: StateFlow<String> = _status.asStateFlow()

    fun start(context: Context) {
        if (_status.value == "running") return

        serverJob?.cancel()
        serverJob = scope.launch {
            try {
                try { serverSocket?.close() } catch (_: Exception) {}

                val socket = ServerSocket(DEFAULT_PORT, 50, InetAddress.getByName("127.0.0.1"))
                serverSocket = socket
                _status.value = "running"
                DebugLogger.logCloneTtsServerStatus("running")
                DebugLogger.logInfo("CloneTTS Local Server active on http://127.0.0.1:$DEFAULT_PORT/api/tts")

                while (isActive && !socket.isClosed) {
                    try {
                        val client = socket.accept()
                        launch { handleClient(context, client) }
                    } catch (e: Exception) {
                        if (!socket.isClosed) {
                            Log.w(TAG, "Socket accept error: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                var alreadyActive = false
                try {
                    val probe = Socket("127.0.0.1", DEFAULT_PORT)
                    probe.close()
                    alreadyActive = true
                } catch (_: Exception) {}

                if (alreadyActive) {
                    _status.value = "running"
                    DebugLogger.logCloneTtsServerStatus("running")
                    DebugLogger.logInfo("CloneTTS Local Server already running on port $DEFAULT_PORT")
                } else {
                    Log.e(TAG, "Failed to bind CloneTTS local server on port $DEFAULT_PORT: ${e.message}")
                    _status.value = "failed"
                    DebugLogger.logCloneTtsServerStatus("failed")
                }
            }
        }
    }

    fun stop() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverJob?.cancel()
        _status.value = "not-started"
        DebugLogger.logCloneTtsServerStatus("not-started")
    }

    private suspend fun handleClient(context: Context, socket: Socket) = withContext(Dispatchers.IO) {
        try {
            socket.soTimeout = 8000
            val input = BufferedReader(InputStreamReader(socket.getInputStream()))
            val output = socket.getOutputStream()

            val requestLine = input.readLine() ?: return@withContext
            var contentLength = 0
            while (true) {
                val line = input.readLine() ?: break
                if (line.isEmpty()) break
                if (line.lowercase(Locale.ROOT).startsWith("content-length:")) {
                    contentLength = line.substring(15).trim().toIntOrNull() ?: 0
                }
            }

            var body = ""
            if (contentLength > 0) {
                val chars = CharArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val r = input.read(chars, read, contentLength - read)
                    if (r < 0) break
                    read += r
                }
                body = String(chars, 0, read)
            }

            if (requestLine.startsWith("GET /health") || requestLine.startsWith("GET /api/tts") || requestLine.startsWith("GET /")) {
                val json = """{"status":"running","engine":"sherpa-onnx-clonetts"}"""
                val resp = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${json.length}\r\nConnection: close\r\n\r\n$json"
                output.write(resp.toByteArray(Charsets.UTF_8))
                output.flush()
                return@withContext
            }

            if (requestLine.startsWith("POST")) {
                var textToSpeak = ""
                try {
                    if (body.isNotEmpty()) {
                        val json = JSONObject(body)
                        textToSpeak = json.optString("text", "")
                    }
                } catch (_: Exception) {}

                val audioBytes = OfflineVoiceCloneManager.synthesizeDirectInProcess(context, textToSpeak)
                if (audioBytes.isNotEmpty()) {
                    val header = "HTTP/1.1 200 OK\r\nContent-Type: audio/wav\r\nContent-Length: ${audioBytes.size}\r\nConnection: close\r\n\r\n"
                    output.write(header.toByteArray(Charsets.UTF_8))
                    output.write(audioBytes)
                    output.flush()
                } else {
                    val err = """{"error":"Synthesis failed"}"""
                    val resp = "HTTP/1.1 500 Internal Server Error\r\nContent-Type: application/json\r\nContent-Length: ${err.length}\r\nConnection: close\r\n\r\n$err"
                    output.write(resp.toByteArray(Charsets.UTF_8))
                    output.flush()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error handling CloneTTS request: ${e.message}")
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }
}
