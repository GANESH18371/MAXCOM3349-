package com.example.manager

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.util.DebugLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Manages Voice Cloning (Owner's Voice) for Max Assistant TTS output:
 * 1. Audio Sample Recording & Storage (3 to 5 clean samples, 10-15s each)
 * 2. Instant Voice Cloning API integration (ElevenLabs / Custom / OpenVoice)
 * 3. Seamless Audio Playback via MediaPlayer
 * 4. Automatic zero-delay Fallback to local Android TTS if offline or API error
 */
object ClonedVoiceManager {
    private const val TAG = "ClonedVoiceManager"
    private const val PREFS_NAME = "max_voice_cloning_prefs"
    private const val KEY_ENABLED = "cloned_voice_enabled"
    private const val KEY_PROVIDER = "cloned_voice_provider"
    private const val KEY_API_KEY = "cloned_voice_api_key"
    private const val KEY_VOICE_ID = "cloned_voice_id"
    private const val KEY_CUSTOM_ENDPOINT = "cloned_voice_custom_endpoint"

    val SAMPLE_PROMPTS = listOf(
        "नमस्ते! मैं मैक्स हूँ, आपका पर्सनल वॉयस असिस्टेंट। आज मैं आपकी क्या सहायता करूँ?",
        "आज का मौसम काफी सुहाना है, तापमान 28 डिग्री सेल्सियस है और आसमान साफ रहेगा।",
        "वाई-फाई और टॉर्च ऑन कर दिया गया है। आपके फ़ोन का वॉल्यूम 70 प्रतिशत पर सेट है।",
        "मैंने आपके दोस्त का व्हाट्सएप मैसेज ऑटो-रिप्लाई कर दिया है और जरूरी रिमाइंडर सेव कर लिया है।",
        "आपकी पसंदीदा यूट्यूब वीडियो प्ले हो रही है और बैटरी 85 प्रतिशत बची है, ऑल सिस्टम्स नॉर्मल।"
    )

    private val mainDispatcher by lazy {
        try {
            Dispatchers.Main
        } catch (_: Throwable) {
            Dispatchers.Default
        }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    // Observable States
    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _provider = MutableStateFlow("ELEVEN_LABS")
    val provider: StateFlow<String> = _provider.asStateFlow()

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _voiceId = MutableStateFlow("")
    val voiceId: StateFlow<String> = _voiceId.asStateFlow()

    private val _customEndpoint = MutableStateFlow("")
    val customEndpoint: StateFlow<String> = _customEndpoint.asStateFlow()

    private val _samplesCount = MutableStateFlow(0)
    val samplesCount: StateFlow<Int> = _samplesCount.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingIndex = MutableStateFlow<Int?>(null)
    val recordingIndex: StateFlow<Int?> = _recordingIndex.asStateFlow()

    private val _playingSampleIndex = MutableStateFlow<Int?>(null)
    val playingSampleIndex: StateFlow<Int?> = _playingSampleIndex.asStateFlow()

    private val _isSynthesizing = MutableStateFlow(false)
    val isSynthesizing: StateFlow<Boolean> = _isSynthesizing.asStateFlow()

    private var activeRecorder: MediaRecorder? = null
    private var activePlayer: MediaPlayer? = null
    private var activePlaybackPlayer: MediaPlayer? = null

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _isEnabled.value = prefs.getBoolean(KEY_ENABLED, false)
        _provider.value = prefs.getString(KEY_PROVIDER, "ELEVEN_LABS") ?: "ELEVEN_LABS"
        _apiKey.value = prefs.getString(KEY_API_KEY, "") ?: ""
        _voiceId.value = prefs.getString(KEY_VOICE_ID, "") ?: ""
        _customEndpoint.value = prefs.getString(KEY_CUSTOM_ENDPOINT, "") ?: ""
        updateSamplesCount(context)
        DebugLogger.logInfo("ClonedVoiceManager Initialized: enabled=${_isEnabled.value}, voiceId=${_voiceId.value.take(6)}..., samples=${_samplesCount.value}")
    }

    private fun savePrefs(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, _isEnabled.value)
            .putString(KEY_PROVIDER, _provider.value)
            .putString(KEY_API_KEY, _apiKey.value)
            .putString(KEY_VOICE_ID, _voiceId.value)
            .putString(KEY_CUSTOM_ENDPOINT, _customEndpoint.value)
            .apply()
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        _isEnabled.value = enabled
        savePrefs(context)
        DebugLogger.logClonedVoiceStatus(enabled, _voiceId.value)
    }

    fun setCredentials(context: Context, apiKey: String, voiceId: String, provider: String = "ELEVEN_LABS", customEndpoint: String = "") {
        _apiKey.value = apiKey.trim()
        _voiceId.value = voiceId.trim()
        _provider.value = provider.trim()
        _customEndpoint.value = customEndpoint.trim()
        savePrefs(context)
        DebugLogger.logInfo("Cloned Voice credentials updated: provider=$provider, voiceId=${_voiceId.value}")
    }

    fun isClonedVoiceActive(): Boolean {
        return _isEnabled.value && _apiKey.value.isNotBlank() && _voiceId.value.isNotBlank()
    }

    // =========================================================================
    // VOICE SAMPLES MANAGEMENT (Recording, Playback, Deletion, Import)
    // =========================================================================

    private fun getSamplesDir(context: Context): File {
        val dir = File(context.filesDir, "voice_samples")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getSampleFile(context: Context, index: Int): File {
        return File(getSamplesDir(context), "sample_$index.m4a")
    }

    fun doesSampleExist(context: Context, index: Int): Boolean {
        val file = getSampleFile(context, index)
        return file.exists() && file.length() > 1024L
    }

    fun updateSamplesCount(context: Context) {
        var count = 0
        for (i in 0 until 5) {
            if (doesSampleExist(context, i)) {
                count++
            }
        }
        _samplesCount.value = count
    }

    fun startRecording(context: Context, index: Int): Boolean {
        if (index !in 0..4) return false
        stopPlayback()
        stopRecording()

        val outputFile = getSampleFile(context, index)
        if (outputFile.exists()) {
            outputFile.delete()
        }

        return try {
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(128000)
            recorder.setAudioSamplingRate(44100)
            recorder.setOutputFile(outputFile.absolutePath)
            recorder.prepare()
            recorder.start()

            activeRecorder = recorder
            _isRecording.value = true
            _recordingIndex.value = index
            DebugLogger.logInfo("Voice sample recording started for slot $index")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording voice sample", e)
            _isRecording.value = false
            _recordingIndex.value = null
            activeRecorder = null
            false
        }
    }

    fun stopRecording(context: Context? = null): Boolean {
        if (!_isRecording.value) return false
        val idx = _recordingIndex.value
        return try {
            activeRecorder?.apply {
                try {
                    stop()
                } catch (e: Exception) {
                    Log.w(TAG, "Error stopping recorder", e)
                }
                release()
            }
            activeRecorder = null
            _isRecording.value = false
            _recordingIndex.value = null
            context?.let { updateSamplesCount(it) }
            DebugLogger.logInfo("Voice sample recording stopped successfully for slot $idx")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error finalizing recording", e)
            activeRecorder = null
            _isRecording.value = false
            _recordingIndex.value = null
            false
        }
    }

    fun deleteSample(context: Context, index: Int): Boolean {
        stopPlayback()
        val file = getSampleFile(context, index)
        val deleted = file.exists() && file.delete()
        updateSamplesCount(context)
        DebugLogger.logInfo("Voice sample $index deleted: $deleted")
        return deleted
    }

    fun playSample(context: Context, index: Int, onDone: (() -> Unit)? = null) {
        stopPlayback()
        val file = getSampleFile(context, index)
        if (!file.exists()) return

        try {
            val player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    _playingSampleIndex.value = null
                    onDone?.invoke()
                }
                setOnErrorListener { _, _, _ ->
                    _playingSampleIndex.value = null
                    onDone?.invoke()
                    true
                }
                start()
            }
            activePlayer = player
            _playingSampleIndex.value = index
        } catch (e: Exception) {
            Log.e(TAG, "Error playing voice sample $index", e)
            _playingSampleIndex.value = null
            onDone?.invoke()
        }
    }

    fun stopPlayback() {
        try {
            activePlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (_: Exception) {}
        activePlayer = null
        _playingSampleIndex.value = null

        try {
            activePlaybackPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (_: Exception) {}
        activePlaybackPlayer = null
    }

    fun importSampleFromUri(context: Context, index: Int, uri: Uri): Boolean {
        return try {
            val targetFile = getSampleFile(context, index)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            updateSamplesCount(context)
            DebugLogger.logInfo("Imported custom audio sample for slot $index")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error importing audio sample", e)
            false
        }
    }

    // =========================================================================
    // CLONED VOICE SYNTHESIS & TTS INTEGRATION
    // =========================================================================

    /**
     * Synthesizes text using the Cloned Voice API.
     * If successful, streams & plays audio, then triggers onDone.
     * If failed (offline, quota limit, HTTP error), immediately calls onFallback().
     */
    fun speakWithClonedVoice(
        text: String,
        onDone: (() -> Unit)? = null,
        onFallback: () -> Unit
    ): Boolean {
        if (!isClonedVoiceActive()) {
            return false
        }

        _isSynthesizing.value = true

        scope.launch(Dispatchers.IO) {
            try {
                val voiceKey = _apiKey.value
                val currentVoiceId = _voiceId.value
                val prov = _provider.value

                val url = if (prov == "CUSTOM" && _customEndpoint.value.isNotBlank()) {
                    _customEndpoint.value
                } else {
                    "https://api.elevenlabs.io/v1/text-to-speech/$currentVoiceId"
                }

                val jsonPayload = JSONObject().apply {
                    put("text", text)
                    put("model_id", "eleven_multilingual_v2")
                    val settings = JSONObject().apply {
                        put("stability", 0.5)
                        put("similarity_boost", 0.8)
                    }
                    put("voice_settings", settings)
                }

                val requestBody = jsonPayload.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(url)
                    .addHeader("xi-api-key", voiceKey)
                    .addHeader("Accept", "audio/mpeg")
                    .post(requestBody)
                    .build()

                val response = httpClient.newCall(request).execute()

                if (response.isSuccessful) {
                    val audioBytes = response.body?.bytes()
                    if (audioBytes != null && audioBytes.isNotEmpty()) {
                        withContext(mainDispatcher) {
                            playSynthesizedAudioBytes(audioBytes, onDone = {
                                _isSynthesizing.value = false
                                onDone?.invoke()
                            }, onError = {
                                _isSynthesizing.value = false
                                DebugLogger.logClonedVoiceSynthesis(false, "Audio player error, falling back")
                                onFallback()
                            })
                        }
                        DebugLogger.logClonedVoiceSynthesis(true, "TTS in owner's cloned voice")
                        return@launch
                    }
                }

                // If non-200 or empty audio body
                val errCode = response.code
                DebugLogger.logClonedVoiceSynthesis(false, "HTTP $errCode, fallback to Android TTS")
                withContext(mainDispatcher) {
                    _isSynthesizing.value = false
                    onFallback()
                }

            } catch (e: Exception) {
                Log.w(TAG, "Cloned voice synthesis error: ${e.message}, falling back to Android TTS")
                DebugLogger.logClonedVoiceSynthesis(false, "Exception: ${e.message}")
                withContext(mainDispatcher) {
                    _isSynthesizing.value = false
                    onFallback()
                }
            }
        }

        return true
    }

    private fun playSynthesizedAudioBytes(
        audioBytes: ByteArray,
        onDone: () -> Unit,
        onError: () -> Unit
    ) {
        try {
            stopPlayback()
            val tempFile = File.createTempFile("max_cloned_tts", ".mp3")
            tempFile.deleteOnExit()
            FileOutputStream(tempFile).use { it.write(audioBytes) }

            val player = MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                prepare()
                setOnCompletionListener {
                    tempFile.delete()
                    onDone()
                }
                setOnErrorListener { _, _, _ ->
                    tempFile.delete()
                    onError()
                    true
                }
                start()
            }
            activePlaybackPlayer = player
        } catch (e: Exception) {
            Log.e(TAG, "Error playing cloned voice audio", e)
            onError()
        }
    }

    /**
     * Uploads the recorded samples to ElevenLabs to auto-create an Instant Voice Clone.
     */
    suspend fun createVoiceFromSamples(context: Context, voiceName: String = "My Cloned Voice"): Result<String> = withContext(Dispatchers.IO) {
        val key = _apiKey.value.trim()
        if (key.isBlank()) {
            return@withContext Result.failure(Exception("ElevenLabs API Key required to create voice clone"))
        }

        val sampleFiles = mutableListOf<File>()
        for (i in 0 until 5) {
            val file = getSampleFile(context, i)
            if (file.exists() && file.length() > 1024L) {
                sampleFiles.add(file)
            }
        }

        if (sampleFiles.isEmpty()) {
            return@withContext Result.failure(Exception("At least 1-3 voice samples must be recorded first"))
        }

        try {
            val multipartBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("name", voiceName)
                .addFormDataPart("description", "Cloned voice of Max assistant owner created on Android")

            for (file in sampleFiles) {
                val body = file.asRequestBody("audio/mp4".toMediaType())
                multipartBuilder.addFormDataPart("files", file.name, body)
            }

            val request = Request.Builder()
                .url("https://api.elevenlabs.io/v1/voices/add")
                .addHeader("xi-api-key", key)
                .post(multipartBuilder.build())
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful && responseBody.isNotBlank()) {
                val json = JSONObject(responseBody)
                val newVoiceId = json.optString("voice_id", "")
                if (newVoiceId.isNotBlank()) {
                    withContext(mainDispatcher) {
                        setCredentials(context, key, newVoiceId)
                        setEnabled(context, true)
                    }
                    DebugLogger.logInfo("Cloned Voice created successfully! VoiceId: $newVoiceId")
                    return@withContext Result.success(newVoiceId)
                }
            }

            val err = "API Error (${response.code}): ${responseBody.take(120)}"
            DebugLogger.logInfo("Cloned Voice creation failed: $err")
            Result.failure(Exception(err))

        } catch (e: Exception) {
            Result.failure(Exception("Failed to upload samples: ${e.localizedMessage ?: "Network error"}"))
        }
    }

    fun stop() {
        stopPlayback()
    }
}
