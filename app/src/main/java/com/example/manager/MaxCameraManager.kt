package com.example.manager

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.example.service.GeminiReplyService
import com.example.util.DebugLogger
import com.example.util.TtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor
import kotlin.coroutines.resume

object MaxCameraManager {
    private const val TAG = "MaxCameraManager"
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _lastCapturedUri = MutableStateFlow<String?>(null)
    val lastCapturedUri: StateFlow<String?> = _lastCapturedUri.asStateFlow()

    private val _lastSceneAnalysis = MutableStateFlow<String?>(null)
    val lastSceneAnalysis: StateFlow<String?> = _lastSceneAnalysis.asStateFlow()

    /**
     * Checks if Camera permission is granted.
     */
    fun hasCameraPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Helper lifecycle owner for headless/background CameraX binding.
     */
    class HeadlessLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)
        init {
            registry.currentState = Lifecycle.State.RESUMED
        }
        override val lifecycle: Lifecycle get() = registry
        fun destroy() {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }

    /**
     * PART 1: Capture photo (front selfie or back camera) and save directly to DCIM/Max in gallery.
     */
    fun capturePhoto(
        context: Context,
        isFrontCamera: Boolean,
        onComplete: ((Boolean, String) -> Unit)? = null
    ) {
        val typeStr = if (isFrontCamera) "selfie" else "back"

        if (!hasCameraPermission(context)) {
            val msg = "कैमरा इस्तेमाल करने के लिए कैमरा परमिशन ज़रूरी है. कृपया स्क्रीन पर परमिशन दें."
            TtsManager.speak(msg)
            DebugLogger.logCameraCapture(type = typeStr, success = false, details = "Camera permission missing")
            onComplete?.invoke(false, "Camera permission missing")
            return
        }

        _isBusy.value = true
        val mainExecutor: Executor = ContextCompat.getMainExecutor(context)
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val headlessOwner = HeadlessLifecycleOwner()

                val cameraSelector = if (isFrontCamera) {
                    if (cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(headlessOwner, cameraSelector, imageCapture)

                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "Max_${if (isFrontCamera) "Selfie" else "Photo"}_$timeStamp.jpg"

                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/Max")
                    }
                }

                val outputOptions = ImageCapture.OutputFileOptions.Builder(
                    context.contentResolver,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                ).build()

                imageCapture.takePicture(
                    outputOptions,
                    mainExecutor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            _isBusy.value = false
                            headlessOwner.destroy()
                            cameraProvider.unbindAll()

                            val savedUri = outputFileResults.savedUri?.toString() ?: "DCIM/Max/$fileName"
                            _lastCapturedUri.value = savedUri

                            // Required debug log: "CAMERA_CAPTURE: type=<selfie/back>, result=success/fail"
                            DebugLogger.logCameraCapture(type = typeStr, success = true, details = "Saved to $savedUri")

                            // Required TTS confirmation: "फोटो ले ली गई है"
                            TtsManager.speak("फोटो ले ली गई है.")
                            onComplete?.invoke(true, savedUri)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            _isBusy.value = false
                            headlessOwner.destroy()
                            cameraProvider.unbindAll()

                            Log.e(TAG, "Camera capture error", exception)
                            DebugLogger.logCameraCapture(type = typeStr, success = false, details = exception.message ?: "Capture failed")
                            TtsManager.speak("फोटो खींचने में समस्या आई.")
                            onComplete?.invoke(false, exception.message ?: "Capture failed")
                        }
                    }
                )
            } catch (e: Exception) {
                _isBusy.value = false
                Log.e(TAG, "Failed to bind camera", e)
                DebugLogger.logCameraCapture(type = typeStr, success = false, details = e.message ?: "Camera binding failed")
                TtsManager.speak("कैमरा चालू करने में समस्या आई.")
                onComplete?.invoke(false, e.message ?: "Camera binding failed")
            }
        }, mainExecutor)
    }

    /**
     * PART 2: Scene Analysis - captures a temporary frame (NOT saved to gallery)
     * and sends to Gemini Vision API for Hindi scene description.
     */
    fun analyzeScene(
        context: Context,
        onComplete: ((Boolean, String) -> Unit)? = null
    ) {
        if (!hasCameraPermission(context)) {
            val msg = "सामने का दृश्य देखने के लिए कैमरा परमिशन ज़रूरी है."
            TtsManager.speak(msg)
            onComplete?.invoke(false, "Camera permission missing")
            return
        }

        _isBusy.value = true
        TtsManager.speak("सामने देखा जा रहा है, कृपया एक पल रुकिए...")

        val mainExecutor = ContextCompat.getMainExecutor(context)
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val headlessOwner = HeadlessLifecycleOwner()
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(headlessOwner, cameraSelector, imageCapture)

                imageCapture.takePicture(
                    mainExecutor,
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(imageProxy: ImageProxy) {
                            val jpegBytes = imageProxyToJpegBytes(imageProxy)
                            imageProxy.close()
                            headlessOwner.destroy()
                            cameraProvider.unbindAll()

                            scope.launch {
                                try {
                                    val summary = GeminiReplyService.analyzeSceneImage(jpegBytes)
                                    _isBusy.value = false
                                    _lastSceneAnalysis.value = summary

                                    // Required debug log: "SCENE_ANALYSIS: gemini_response=<summary>"
                                    DebugLogger.logSceneAnalysis(summary)

                                    // TTS speech in Hindi
                                    TtsManager.speak(summary)
                                    onComplete?.invoke(true, summary)
                                } catch (e: Exception) {
                                    _isBusy.value = false
                                    Log.e(TAG, "Error in scene analysis", e)
                                    val err = "दृश्य विश्लेषण में समस्या आई."
                                    DebugLogger.logSceneAnalysis("Error: ${e.message}")
                                    TtsManager.speak(err)
                                    onComplete?.invoke(false, err)
                                }
                            }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            _isBusy.value = false
                            headlessOwner.destroy()
                            cameraProvider.unbindAll()
                            Log.e(TAG, "Failed to capture frame for scene analysis", exception)
                            val err = "फ़्रेम कैप्चर नहीं हो सका."
                            TtsManager.speak(err)
                            onComplete?.invoke(false, err)
                        }
                    }
                )
            } catch (e: Exception) {
                _isBusy.value = false
                Log.e(TAG, "Camera provider setup error for scene analysis", e)
                val err = "कैमरा शुरू नहीं हो सका."
                TtsManager.speak(err)
                onComplete?.invoke(false, err)
            }
        }, mainExecutor)
    }

    /**
     * PART 3: SILENT / BACKGROUND PHOTO CAPTURE (Future Anti-Theft)
     * Prepared function to capture a photo in the background without UI.
     * Ready for future use; does not show any preview or alert.
     */
    suspend fun captureSilentBackgroundPhoto(
        context: Context,
        useFrontCamera: Boolean = true
    ): File? = withContext(Dispatchers.Main) {
        if (!hasCameraPermission(context)) return@withContext null

        return@withContext suspendCancellableCoroutine { continuation ->
            val mainExecutor = ContextCompat.getMainExecutor(context)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    val headlessOwner = HeadlessLifecycleOwner()
                    val selector = if (useFrontCamera && cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }

                    val imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()

                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(headlessOwner, selector, imageCapture)

                    val secretFile = File(context.cacheDir, "silent_capture_${System.currentTimeMillis()}.jpg")
                    val outputOptions = ImageCapture.OutputFileOptions.Builder(secretFile).build()

                    imageCapture.takePicture(
                        outputOptions,
                        mainExecutor,
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                headlessOwner.destroy()
                                cameraProvider.unbindAll()
                                if (continuation.isActive) {
                                    continuation.resume(secretFile)
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                headlessOwner.destroy()
                                cameraProvider.unbindAll()
                                if (continuation.isActive) {
                                    continuation.resume(null)
                                }
                            }
                        }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error in silent photo capture", e)
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
            }, mainExecutor)
        }
    }

    private fun imageProxyToJpegBytes(image: ImageProxy): ByteArray {
        val plane = image.planes[0]
        val buffer: ByteBuffer = plane.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        // If format is already JPEG
        if (image.format == android.graphics.ImageFormat.JPEG) {
            return bytes
        }

        // Fallback convert to JPEG via BitmapFactory
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        if (bitmap != null) {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            return stream.toByteArray()
        }

        return bytes
    }

    // =========================================================================
    // VOICE COMMAND MATCHERS
    // =========================================================================

    fun isSelfieCommand(lower: String): Boolean {
        val keywords = listOf(
            "selfie", "सेलफ़ी", "सेल्फी", "front camera", "front photo",
            "khud ki photo", "apni photo"
        )
        return keywords.any { lower.contains(it) }
    }

    fun isBackPhotoCommand(lower: String): Boolean {
        val keywords = listOf(
            "photo lo", "photo kheecho", "photo khicho", "peeche wali se photo",
            "peeche wale se photo", "back camera se photo", "take a photo",
            "take photo", "फोटो लो", "फोटो खींचो", "फोटो निकालो", "picture lo"
        )
        return keywords.any { lower.contains(it) }
    }

    fun isSceneAnalysisCommand(lower: String): Boolean {
        val keywords = listOf(
            "saamne kya hai", "samne kya hai", "yeh kya hai batao", "ye kya hai batao",
            "yeh kya hai", "ye kya hai", "kya dikh raha hai", "saamne dekho", "samne dekho",
            "सामने क्या है", "यह क्या है", "क्या दिख रहा है", "सामने देखो",
            "what is in front of me", "what is in front", "analyze scene", "describe scene"
        )
        return keywords.any { lower.contains(it) }
    }
}
