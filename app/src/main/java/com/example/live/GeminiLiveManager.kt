package com.example.live

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.manager.AppContextManager
import com.example.manager.VoiceCommandManager
import com.example.util.DebugLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class LiveConnectionState {
    DISCONNECTED,
    CONNECTING,
    LISTENING,
    SPEAKING,
    ERROR
}

data class LiveMessage(
    val sender: String, // "User" or "Max"
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

object GeminiLiveManager {
    private const val TAG = "GeminiLiveManager"

    // MANDATORY REQUIREMENT: EXACTLY "gemini-3.8-live" in a single constant
    const val GEMINI_LIVE_MODEL = "gemini-3.8-live"
    private const val LIVE_BIDI_WS_URL = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var webSocket: WebSocket? = null
    private var audioRecorder: LiveAudioRecorder? = null
    private var audioPlayer: LiveAudioPlayer? = null

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Indefinite for live WebSocket stream
        .writeTimeout(30, TimeUnit.SECONDS)
        .pingInterval(0, TimeUnit.SECONDS) // Ping disabled: prevents SocketTimeoutException from missing pongs
        .retryOnConnectionFailure(true)
        .build()

    // Public StateFlows
    private val _connectionState = MutableStateFlow(LiveConnectionState.DISCONNECTED)
    val connectionState: StateFlow<LiveConnectionState> = _connectionState.asStateFlow()

    private val _micAmplitude = MutableStateFlow(0f)
    val micAmplitude: StateFlow<Float> = _micAmplitude.asStateFlow()

    private val _speakerAmplitude = MutableStateFlow(0f)
    val speakerAmplitude: StateFlow<Float> = _speakerAmplitude.asStateFlow()

    private val _liveMessages = MutableStateFlow<List<LiveMessage>>(emptyList())
    val liveMessages: StateFlow<List<LiveMessage>> = _liveMessages.asStateFlow()

    private val _lastErrorMessage = MutableStateFlow<String?>(null)
    val lastErrorMessage: StateFlow<String?> = _lastErrorMessage.asStateFlow()

    private val _isBargeInActive = MutableStateFlow(false)
    val isBargeInActive: StateFlow<Boolean> = _isBargeInActive.asStateFlow()

    private var currentSpeakingModelText = StringBuilder()
    private var commandRouterCallback: ((String) -> Unit)? = null

    fun setCommandRouter(callback: (String) -> Unit) {
        commandRouterCallback = callback
    }

    /**
     * Starts the real-time Gemini Live audio streaming session.
     */
    fun startLiveSession(context: Context) {
        if (_connectionState.value == LiveConnectionState.CONNECTING ||
            _connectionState.value == LiveConnectionState.LISTENING ||
            _connectionState.value == LiveConnectionState.SPEAKING
        ) {
            return
        }

        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (_: Throwable) {
            ""
        }

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            val err = "Gemini API Key missing! Please set GEMINI_API_KEY in Secrets / Settings."
            _lastErrorMessage.value = err
            _connectionState.value = LiveConnectionState.ERROR
            DebugLogger.logInfo("GEMINI_LIVE_ERROR: $err")
            return
        }

        _lastErrorMessage.value = null
        _connectionState.value = LiveConnectionState.CONNECTING
        DebugLogger.logInfo("GEMINI_LIVE_CONNECTING: Initializing Live Audio Session with model '$GEMINI_LIVE_MODEL'...")

        // 1. Initialize Audio Player
        audioPlayer = LiveAudioPlayer(sampleRate = 24000) { amp ->
            _speakerAmplitude.value = amp
        }
        audioPlayer?.start()

        // 2. Connect to WebSocket
        val wsUrl = "$LIVE_BIDI_WS_URL?key=$apiKey"
        val request = Request.Builder().url(wsUrl).build()

        webSocket = okHttpClient.newWebSocket(request, createWebSocketListener())

        // 3. Initialize Audio Recorder (PCM 16kHz)
        audioRecorder = LiveAudioRecorder { chunk, amp ->
            _micAmplitude.value = amp

            // Handle Barge-In: If user speaks loudly (RMS > 0.08) while model is speaking, interrupt playback immediately
            if (_connectionState.value == LiveConnectionState.SPEAKING && amp > 0.08f) {
                triggerBargeIn()
            }

            // Stream PCM chunk to WebSocket as base64
            sendAudioChunk(chunk)
        }

        val recordSuccess = audioRecorder?.start(scope) ?: false
        if (!recordSuccess) {
            val err = "Microphone record permission or hardware initialization failed"
            _lastErrorMessage.value = err
            _connectionState.value = LiveConnectionState.ERROR
            DebugLogger.logInfo("GEMINI_LIVE_ERROR: $err")
            stopLiveSession()
        }
    }

    /**
     * Stops the active live streaming session and cleans up resources.
     */
    fun stopLiveSession() {
        DebugLogger.logInfo("GEMINI_LIVE_DISCONNECTED: Session ended")
        audioRecorder?.stop()
        audioRecorder = null

        audioPlayer?.release()
        audioPlayer = null

        try {
            webSocket?.close(1000, "Session ended by user")
        } catch (e: Exception) {
            Log.w(TAG, "Error closing WebSocket", e)
        }
        webSocket = null

        _micAmplitude.value = 0f
        _speakerAmplitude.value = 0f
        _isBargeInActive.value = false
        _connectionState.value = LiveConnectionState.DISCONNECTED
    }

    /**
     * Immediate Barge-in / Interruption: Stops speaker output and listens to user input.
     */
    fun triggerBargeIn() {
        if (_connectionState.value == LiveConnectionState.SPEAKING) {
            _isBargeInActive.value = true
            audioPlayer?.stopAndFlush()
            _connectionState.value = LiveConnectionState.LISTENING
            DebugLogger.logInfo("GEMINI_LIVE_BARGE_IN: User interrupted assistant, listening to new voice input...")

            // Send client barge-in signal to model stream
            scope.launch {
                try {
                    val clientContentObj = JSONObject().apply {
                        val turnCompleteObj = JSONObject().put("turnComplete", false)
                        put("clientContent", turnCompleteObj)
                    }
                    webSocket?.send(clientContentObj.toString())
                } catch (e: Exception) {
                    Log.w(TAG, "Error sending barge-in frame", e)
                }
            }
        }
    }

    private fun sendAudioChunk(pcmChunk: ByteArray) {
        val ws = webSocket ?: return
        if (_connectionState.value == LiveConnectionState.DISCONNECTED ||
            _connectionState.value == LiveConnectionState.ERROR
        ) return

        try {
            val base64Pcm = Base64.encodeToString(pcmChunk, Base64.NO_WRAP)
            val json = JSONObject().apply {
                val realtimeInputObj = JSONObject().apply {
                    val mediaChunksArr = JSONArray().apply {
                        val chunkObj = JSONObject().apply {
                            put("mimeType", "audio/pcm;rate=16000")
                            put("data", base64Pcm)
                        }
                        put(chunkObj)
                    }
                    put("mediaChunks", mediaChunksArr)
                }
                put("realtimeInput", realtimeInputObj)
            }
            ws.send(json.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Error sending realtime audio chunk", e)
        }
    }

    private fun sendSetupMessage(ws: WebSocket) {
        try {
            val contextApp = AppContextManager.getCurrentApp()?.name
            val contextInfo = if (contextApp != null) "The user is currently using app: $contextApp." else ""

            val setupJson = JSONObject().apply {
                val setupObj = JSONObject().apply {
                    put("model", "models/$GEMINI_LIVE_MODEL")
                    
                    val generationConfig = JSONObject().apply {
                        val responseModalities = JSONArray().apply {
                            put("AUDIO")
                            put("TEXT")
                        }
                        put("responseModalities", responseModalities)
                        
                        val speechConfig = JSONObject().apply {
                            val voiceConfig = JSONObject().apply {
                                val prebuiltVoiceConfig = JSONObject().apply {
                                    put("voiceName", "Puck")
                                }
                                put("prebuiltVoiceConfig", prebuiltVoiceConfig)
                            }
                            put("voiceConfig", voiceConfig)
                        }
                        put("speechConfig", speechConfig)
                    }
                    put("generationConfig", generationConfig)

                    val systemInstruction = JSONObject().apply {
                        val partsArr = JSONArray().apply {
                            val partObj = JSONObject().put(
                                "text",
                                "You are Max, a hyper-fast intelligent bilingual assistant (Hindi & English). " +
                                        "Speak naturally, briefly, and helpfully in 1-2 sentences. " +
                                        "$contextInfo " +
                                        "If the user asks to open an app (e.g. YouTube, Camera), toggle hardware (Torch, Wifi, Bluetooth), check weather, or set alarms, state your response concisely."
                            )
                            put(partObj)
                        }
                        put("parts", partsArr)
                    }
                    put("systemInstruction", systemInstruction)
                }
                put("setup", setupObj)
            }

            ws.send(setupJson.toString())
            DebugLogger.logInfo("GEMINI_LIVE_SETUP: Sent configuration for model '$GEMINI_LIVE_MODEL'")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending setup message", e)
        }
    }

    private fun createWebSocketListener(): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket connected successfully to Gemini Live endpoint")
                _connectionState.value = LiveConnectionState.LISTENING
                DebugLogger.logInfo("GEMINI_LIVE_STATE: LISTENING (Connected to $GEMINI_LIVE_MODEL)")
                sendSetupMessage(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingServerMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleIncomingServerMessage(bytes.utf8())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closing: code=$code, reason=$reason")
                _connectionState.value = LiveConnectionState.DISCONNECTED
                audioRecorder?.stop()
                audioPlayer?.stopAndFlush()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val errorMsg = "Gemini Live Connection Failed: ${t.message ?: "Unknown socket error"}"
                Log.e(TAG, errorMsg, t)
                _lastErrorMessage.value = errorMsg
                _connectionState.value = LiveConnectionState.ERROR
                audioRecorder?.stop()
                audioPlayer?.stopAndFlush()

                // Exact requirement: Log error for model debugging
                DebugLogger.logInfo("GEMINI_LIVE_ERROR (Model: $GEMINI_LIVE_MODEL): $errorMsg ${response?.message ?: ""}")
            }
        }
    }

    private fun handleIncomingServerMessage(rawJson: String) {
        try {
            val root = JSONObject(rawJson)

            // Check for server errors (e.g. "model not found")
            if (root.has("error")) {
                val errObj = root.getJSONObject("error")
                val errMsg = errObj.optString("message", "Model error occurred")
                val errCode = errObj.optInt("code", 0)
                val fullErr = "Gemini Live API Error ($errCode): $errMsg (Model: $GEMINI_LIVE_MODEL)"
                _lastErrorMessage.value = fullErr
                _connectionState.value = LiveConnectionState.ERROR
                DebugLogger.logInfo("GEMINI_LIVE_ERROR: $fullErr")
                return
            }

            // Check for serverContent
            val serverContent = root.optJSONObject("serverContent")
            if (serverContent != null) {
                val interrupted = serverContent.optBoolean("interrupted", false)
                if (interrupted) {
                    _isBargeInActive.value = true
                    audioPlayer?.stopAndFlush()
                    _connectionState.value = LiveConnectionState.LISTENING
                    DebugLogger.logInfo("GEMINI_LIVE_BARGE_IN: Server signalled interruption")
                }

                val modelTurn = serverContent.optJSONObject("modelTurn")
                if (modelTurn != null) {
                    val parts = modelTurn.optJSONArray("parts")
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)

                            // 1. Text Transcript
                            val textChunk = part.optString("text", "")
                            if (textChunk.isNotBlank()) {
                                currentSpeakingModelText.append(textChunk)
                                _connectionState.value = LiveConnectionState.SPEAKING
                            }

                            // 2. Audio PCM Chunks (24kHz)
                            val inlineData = part.optJSONObject("inlineData")
                            if (inlineData != null) {
                                val mimeType = inlineData.optString("mimeType", "")
                                val base64Data = inlineData.optString("data", "")
                                if (base64Data.isNotBlank()) {
                                    val pcmBytes = Base64.decode(base64Data, Base64.NO_WRAP)
                                    _connectionState.value = LiveConnectionState.SPEAKING
                                    _isBargeInActive.value = false
                                    audioPlayer?.playChunk(pcmBytes)
                                }
                            }
                        }
                    }
                }

                val turnComplete = serverContent.optBoolean("turnComplete", false)
                if (turnComplete) {
                    _connectionState.value = LiveConnectionState.LISTENING
                    val completeResponse = currentSpeakingModelText.toString().trim()
                    if (completeResponse.isNotBlank()) {
                        addLiveMessage("Max", completeResponse)
                        DebugLogger.logInfo("GEMINI_LIVE_RESPONSE: \"$completeResponse\"")

                        // Route to existing command router if task detected
                        routeCommandIfActionable(completeResponse)
                        currentSpeakingModelText.clear()
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing incoming Gemini Live frame: ${e.message}")
        }
    }

    private fun addLiveMessage(sender: String, text: String) {
        val newMsg = LiveMessage(sender = sender, text = text)
        _liveMessages.value = (listOf(newMsg) + _liveMessages.value).take(30)
    }

    /**
     * Bridges Live AI understanding with existing reliable local command execution.
     */
    private fun routeCommandIfActionable(text: String) {
        commandRouterCallback?.invoke(text)
    }
}
