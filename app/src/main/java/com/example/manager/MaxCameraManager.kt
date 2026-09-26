package com.example.manager

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Log
import android.util.Size
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
     * PART 3: SILENT / BACKGROUND PHOTO CAPTURE (Anti-Theft Guard)
     * Uses low-level Camera2 API for completely headless background capture without UI or Lifecycle.
     * Includes automatic fallback to CameraX if needed.
     */
    suspend fun captureSilentBackgroundPhoto(
        context: Context,
        useFrontCamera: Boolean = true
    ): File? {
        if (!hasCameraPermission(context)) {
            Log.w(TAG, "Cannot capture silent photo: CAMERA permission not granted")
            return null
        }

        // Try Camera2 low-level API first (reliable in background & lock screen)
        try {
            val camera2File = captureSilentWithCamera2(context, useFrontCamera)
            if (camera2File != null && camera2File.exists() && camera2File.length() > 0) {
                Log.i(TAG, "Silent photo captured successfully via Camera2: ${camera2File.absolutePath}")
                return camera2File
            }
        } catch (e: Exception) {
            Log.w(TAG, "Camera2 silent capture attempt failed, trying CameraX fallback", e)
        }

        // Fallback to CameraX if Camera2 fails
        return try {
            captureSilentWithCameraX(context, useFrontCamera)
        } catch (e: Exception) {
            Log.e(TAG, "Both Camera2 and CameraX silent capture failed", e)
            null
        }
    }

    /**
     * Low-level Camera2 silent photo capture:
     * Directly interacts with CameraManager, CameraDevice, and ImageReader.
     * Does NOT require any active Activity, Fragment, or LifecycleOwner.
     */
    private suspend fun captureSilentWithCamera2(
        context: Context,
        useFrontCamera: Boolean
    ): File? = withContext(Dispatchers.IO) {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return@withContext null

        try {
            var targetCameraId: String? = null
            var targetCharacteristics: CameraCharacteristics? = null

            val targetFacing = if (useFrontCamera) {
                CameraCharacteristics.LENS_FACING_FRONT
            } else {
                CameraCharacteristics.LENS_FACING_BACK
            }

            for (id in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing == targetFacing) {
                    targetCameraId = id
                    targetCharacteristics = characteristics
                    break
                }
            }

            if (targetCameraId == null && cameraManager.cameraIdList.isNotEmpty()) {
                targetCameraId = cameraManager.cameraIdList[0]
                targetCharacteristics = cameraManager.getCameraCharacteristics(targetCameraId)
            }

            if (targetCameraId == null || targetCharacteristics == null) {
                Log.e(TAG, "No suitable camera ID found for Camera2")
                return@withContext null
            }

            // Pick optimal resolution for JPEG
            val map = targetCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val jpegSizes = map?.getOutputSizes(ImageFormat.JPEG) ?: emptyArray()
            val selectedSize = jpegSizes.filter { it.width in 640..1920 }
                .minByOrNull { it.width * it.height }
                ?: jpegSizes.firstOrNull()
                ?: Size(1280, 720)

            val imageReader = ImageReader.newInstance(
                selectedSize.width,
                selectedSize.height,
                ImageFormat.JPEG,
                2
            )

            val thread = HandlerThread("AntiTheftCamera2Thread").apply { start() }
            val handler = Handler(thread.looper)

            val outputFile = File(
                context.cacheDir,
                "theft_capture_${System.currentTimeMillis()}.jpg"
            )

            return@withContext suspendCancellableCoroutine { continuation ->
                var cameraDevice: CameraDevice? = null
                var captureSession: CameraCaptureSession? = null
                var isCompleted = false

                fun cleanup() {
                    try {
                        captureSession?.close()
                    } catch (_: Exception) {}
                    try {
                        cameraDevice?.close()
                    } catch (_: Exception) {}
                    try {
                        imageReader.close()
                    } catch (_: Exception) {}
                    try {
                        thread.quitSafely()
                    } catch (_: Exception) {}
                }

                fun finish(result: File?) {
                    if (!isCompleted) {
                        isCompleted = true
                        cleanup()
                        if (continuation.isActive) {
                            continuation.resume(result)
                        }
                    }
                }

                imageReader.setOnImageAvailableListener({ reader ->
                    try {
                        val image = reader.acquireLatestImage()
                        if (image != null) {
                            val planes = image.planes
                            if (planes.isNotEmpty()) {
                                val buffer = planes[0].buffer
                                val bytes = ByteArray(buffer.remaining())
                                buffer.get(bytes)
                                FileOutputStream(outputFile).use { fos ->
                                    fos.write(bytes)
                                    fos.flush()
                                }
                            }
                            image.close()
                            finish(outputFile)
                        } else {
                            finish(null)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error writing image buffer from Camera2", e)
                        finish(null)
                    }
                }, handler)

                val sessionCallback = object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        try {
                            val dev = cameraDevice
                            if (dev == null) {
                                finish(null)
                                return
                            }
                            val captureBuilder = dev.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                                addTarget(imageReader.surface)
                                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                                val sensorOrientation = targetCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 270
                                set(CaptureRequest.JPEG_ORIENTATION, sensorOrientation)
                            }

                            session.capture(
                                captureBuilder.build(),
                                object : CameraCaptureSession.CaptureCallback() {
                                    override fun onCaptureFailed(
                                        session: CameraCaptureSession,
                                        request: CaptureRequest,
                                        failure: CaptureFailure
                                    ) {
                                        super.onCaptureFailed(session, request, failure)
                                        Log.e(TAG, "Camera2 session capture failed")
                                        finish(null)
                                    }
                                },
                                handler
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Error sending Camera2 capture request", e)
                            finish(null)
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "Camera2 capture session configuration failed")
                        finish(null)
                    }
                }

                val deviceCallback = object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        cameraDevice = camera
                        try {
                            @Suppress("DEPRECATION")
                            camera.createCaptureSession(
                                listOf(imageReader.surface),
                                sessionCallback,
                                handler
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Error creating Camera2 capture session", e)
                            finish(null)
                        }
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        camera.close()
                        finish(null)
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        Log.e(TAG, "Camera2 device callback error: $error")
                        camera.close()
                        finish(null)
                    }
                }

                try {
                    cameraManager.openCamera(targetCameraId, deviceCallback, handler)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to open camera via CameraManager", e)
                    finish(null)
                }

                // Safety timeout after 5.5 seconds
                handler.postDelayed({
                    if (!isCompleted) {
                        Log.w(TAG, "Camera2 capture timed out")
                        finish(null)
                    }
                }, 5500)

                continuation.invokeOnCancellation {
                    cleanup()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Camera2 execution error", e)
            return@withContext null
        }
    }

    /**
     * Fallback headless CameraX capture.
     */
    private suspend fun captureSilentWithCameraX(
        context: Context,
        useFrontCamera: Boolean
    ): File? = withContext(Dispatchers.Main) {
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
                    Log.e(TAG, "Error in CameraX fallback capture", e)
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
