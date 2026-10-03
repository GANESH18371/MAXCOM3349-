package com.example.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object SecureApiKeyManager {
    private const val TAG = "SecureApiKeyManager"
    private const val PREFS_FILE = "max_secure_api_prefs"

    // Exact standardized key name across the entire app
    const val KEY_GEMINI_API = "gemini_api_key"
    private const val LEGACY_KEY_GEMINI_API = "secure_gemini_api_key"

    @Volatile
    private var cachedApiKey: String? = null

    private val _apiKeyFlow = MutableStateFlow("")
    val apiKeyFlow: StateFlow<String> = _apiKeyFlow.asStateFlow()

    private var initialized = false

    /**
     * Aggressively sanitizes candidate API keys by removing any accidental leading/trailing
     * whitespace, newlines, tabs, quotes, backticks, zero-width spaces, and invisible UTF-8 BOM characters.
     */
    fun sanitizeApiKey(rawKey: String): String {
        return rawKey
            .trim()
            .replace("\"", "")
            .replace("'", "")
            .replace("`", "")
            .replace("\uFEFF", "") // UTF-8 Byte Order Mark
            .replace("\u200B", "") // Zero-width space
            .replace("\u200C", "")
            .replace("\u200D", "")
            .replace("\r", "")
            .replace("\n", "")
            .replace("\t", "")
            .filter { it > ' ' && it <= '~' } // Retain only valid printable ASCII non-space characters
    }

    private fun getSecurePrefs(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context.applicationContext,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.w(TAG, "EncryptedSharedPreferences init fallback to private prefs: ${e.message}")
            context.applicationContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        }
    }

    /**
     * Initializes key in memory on app startup.
     */
    fun init(context: Context) {
        if (initialized) return
        val key = getApiKey(context, "AppStartup")
        _apiKeyFlow.value = key
        cachedApiKey = key
        initialized = true
        if (key.isNotBlank()) {
            DebugLogger.logInfo("CENTRAL_API_KEY: Loaded successfully (Configured)")
        } else {
            DebugLogger.logInfo("CENTRAL_API_KEY: Not yet configured")
        }
    }

    /**
     * Centralized single source of truth for the Gemini API key across the entire application.
     * Priority:
     * 1. In-memory volatile cache (fastest, immune to transient disk/keystore latency).
     * 2. Standard and Encrypted SharedPreferences with unified key name "gemini_api_key".
     * 3. Fallback to BuildConfig.GEMINI_API_KEY if present and valid.
     *
     * Emits exact debug log: "API_KEY_READ_ATTEMPT: location=<location>, found=<true/false>"
     */
    fun getApiKey(context: Context? = null, location: String = "general"): String {
        // 1. Fast in-memory cache check
        cachedApiKey?.let { cached ->
            if (cached.isNotBlank()) {
                DebugLogger.logApiKeyReadAttempt(location, true)
                return cached
            }
        }

        val flowVal = _apiKeyFlow.value.trim()
        if (flowVal.isNotBlank()) {
            cachedApiKey = flowVal
            DebugLogger.logApiKeyReadAttempt(location, true)
            return flowVal
        }

        // 2. Read from persistent centralized storage
        val ctx = context?.applicationContext ?: try { com.example.MaxApp.instance } catch (_: Throwable) { null }
        if (ctx != null) {
            val keyFromStorage = readKeyFromStorage(ctx)
            if (keyFromStorage.isNotBlank()) {
                cachedApiKey = keyFromStorage
                _apiKeyFlow.value = keyFromStorage
                DebugLogger.logApiKeyReadAttempt(location, true)
                return keyFromStorage
            }
        }

        // 3. Fallback to BuildConfig if defined in build/env
        val buildKey = try {
            sanitizeApiKey(BuildConfig.GEMINI_API_KEY)
        } catch (_: Throwable) {
            ""
        }

        val finalKey = if (buildKey.isNotBlank() && buildKey != "MY_GEMINI_API_KEY") {
            buildKey
        } else {
            ""
        }

        val found = finalKey.isNotBlank()
        if (found) {
            cachedApiKey = finalKey
            _apiKeyFlow.value = finalKey
        }
        DebugLogger.logApiKeyReadAttempt(location, found)
        return finalKey
    }

    private fun readKeyFromStorage(ctx: Context): String {
        // A. Check standard SharedPreferences with unified key name
        try {
            val stdPrefs = ctx.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            val key = sanitizeApiKey(stdPrefs.getString(KEY_GEMINI_API, "") ?: "")
            if (key.isNotBlank()) return key

            // Check legacy key and auto-migrate
            val legacy = sanitizeApiKey(stdPrefs.getString(LEGACY_KEY_GEMINI_API, "") ?: "")
            if (legacy.isNotBlank()) {
                stdPrefs.edit().putString(KEY_GEMINI_API, legacy).remove(LEGACY_KEY_GEMINI_API).apply()
                return legacy
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error reading from standard prefs: ${e.message}")
        }

        // B. Check EncryptedSharedPreferences with unified key name
        try {
            val encPrefs = getSecurePrefs(ctx)
            val key = sanitizeApiKey(encPrefs.getString(KEY_GEMINI_API, "") ?: "")
            if (key.isNotBlank()) {
                // Mirror to stdPrefs for ultra-reliable cross-service retrieval
                try {
                    ctx.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
                        .edit().putString(KEY_GEMINI_API, key).commit()
                } catch (_: Exception) {}
                return key
            }

            val legacy = sanitizeApiKey(encPrefs.getString(LEGACY_KEY_GEMINI_API, "") ?: "")
            if (legacy.isNotBlank()) {
                encPrefs.edit().putString(KEY_GEMINI_API, legacy).remove(LEGACY_KEY_GEMINI_API).apply()
                try {
                    ctx.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
                        .edit().putString(KEY_GEMINI_API, legacy).commit()
                } catch (_: Exception) {}
                return legacy
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error reading from encrypted prefs: ${e.message}")
        }

        return ""
    }

    /**
     * Returns true if user has saved a key or build key is valid.
     */
    fun isKeyConfigured(context: Context): Boolean {
        return getApiKey(context, "KeyConfigCheck").isNotBlank()
    }

    /**
     * Returns true if the active key was explicitly provided by the user in Settings.
     */
    fun isUserSuppliedKey(context: Context): Boolean {
        return try {
            val stdPrefs = context.applicationContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            val userKey = sanitizeApiKey(stdPrefs.getString(KEY_GEMINI_API, "") ?: "")
            if (userKey.isNotBlank()) return true

            val encPrefs = getSecurePrefs(context)
            val encKey = sanitizeApiKey(encPrefs.getString(KEY_GEMINI_API, "") ?: "")
            encKey.isNotBlank()
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Saves user's API key into centralized storage after sanitization.
     * Uses synchronous .commit() to ensure instant cross-thread availability.
     */
    fun saveApiKey(context: Context, rawKey: String): Boolean {
        val sanitized = sanitizeApiKey(rawKey)
        val ctx = context.applicationContext
        return try {
            cachedApiKey = sanitized
            _apiKeyFlow.value = sanitized

            // Write to standard private SharedPreferences with synchronous .commit()
            val stdPrefs = ctx.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            stdPrefs.edit()
                .putString(KEY_GEMINI_API, sanitized)
                .remove(LEGACY_KEY_GEMINI_API)
                .commit()

            // Also write to EncryptedSharedPreferences
            try {
                val encPrefs = getSecurePrefs(ctx)
                encPrefs.edit()
                    .putString(KEY_GEMINI_API, sanitized)
                    .remove(LEGACY_KEY_GEMINI_API)
                    .apply()
            } catch (e: Exception) {
                Log.w(TAG, "Encrypted prefs write warning: ${e.message}")
            }

            DebugLogger.logInfo("CENTRAL_API_KEY: User key saved securely to centralized storage (key: $KEY_GEMINI_API)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save user API key", e)
            DebugLogger.logInfo("CENTRAL_API_KEY_ERROR: Failed to save (${e.message})")
            false
        }
    }

    /**
     * Deletes user's API key from centralized storage.
     */
    fun clearApiKey(context: Context): Boolean {
        val ctx = context.applicationContext
        return try {
            cachedApiKey = null
            _apiKeyFlow.value = ""

            val stdPrefs = ctx.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            stdPrefs.edit()
                .remove(KEY_GEMINI_API)
                .remove(LEGACY_KEY_GEMINI_API)
                .commit()

            try {
                val encPrefs = getSecurePrefs(ctx)
                encPrefs.edit()
                    .remove(KEY_GEMINI_API)
                    .remove(LEGACY_KEY_GEMINI_API)
                    .apply()
            } catch (_: Exception) {}

            val fallback = getApiKey(ctx, "ClearFallback")
            _apiKeyFlow.value = fallback
            cachedApiKey = if (fallback.isNotBlank()) fallback else null
            DebugLogger.logInfo("CENTRAL_API_KEY: User key cleared from centralized storage")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear API key", e)
            false
        }
    }

    /**
     * Returns a safe masked version of the key for UI display (e.g. AIzaSy...9xYz).
     */
    fun getMaskedKey(fullKey: String): String {
        val trimmed = sanitizeApiKey(fullKey)
        if (trimmed.isBlank()) return "Not Configured"
        if (trimmed.length <= 10) return "••••••••"
        val prefix = trimmed.take(7)
        val suffix = trimmed.takeLast(4)
        return "$prefix••••••••$suffix"
    }

    /**
     * Parses the Google Generative Language API error response to extract exact status and message.
     */
    fun parseGoogleApiError(code: Int, body: String): String {
        return try {
            val json = JSONObject(body)
            val errorObj = json.optJSONObject("error")
            if (errorObj != null) {
                val status = errorObj.optString("status", "")
                val message = errorObj.optString("message", "")
                val statusText = if (status.isNotBlank()) " $status" else ""
                "[HTTP $code$statusText]: $message"
            } else {
                "[HTTP $code]: ${body.take(150)}"
            }
        } catch (_: Exception) {
            if (body.isNotBlank()) {
                "[HTTP $code]: ${body.take(150)}"
            } else {
                when (code) {
                    400 -> "[HTTP 400 Bad Request]: API key not valid"
                    401 -> "[HTTP 401 Unauthorized]: Key is not authorized by Google"
                    403 -> "[HTTP 403 Forbidden]: API key restricted or Generative Language API disabled in Google Cloud"
                    404 -> "[HTTP 404 Not Found]: Endpoint or model not found"
                    429 -> "[HTTP 429 Too Many Requests]: Quota exceeded"
                    else -> "[HTTP $code]: Validation failed"
                }
            }
        }
    }

    /**
     * Validates the provided API key by executing a real test API call to Google Generative Language API.
     * Uses current endpoints and returns detailed HTTP response status and error body.
     */
    suspend fun validateKey(candidateKey: String): Result<String> = withContext(Dispatchers.IO) {
        val key = sanitizeApiKey(candidateKey)
        if (key.isBlank()) {
            return@withContext Result.failure(Exception("Yeh API key invalid hai, sahi key daaliye - Key blank hai"))
        }

        if (key.length < 15) {
            return@withContext Result.failure(Exception("Yeh API key invalid hai, sahi key daaliye - Key bohot chhota hai (kam se kam 15-39 characters hone chahiye)"))
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()

        // 1. Primary Test: Call generateContent with standard gemini-2.5-flash model
        val jsonPayload = """{"contents":[{"parts":[{"text":"ping"}]}]}"""
        val mediaType = "application/json".toMediaType()
        val requestBody = jsonPayload.toRequestBody(mediaType)
        val generateUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$key"

        val request = Request.Builder()
            .url(generateUrl)
            .post(requestBody)
            .build()

        try {
            var response = client.newCall(request).execute()
            var code = response.code
            var body = response.body?.string() ?: ""

            // 2. Fallback: If 404 (model unavailable or restricted in this specific project/region),
            // test lightweight model discovery endpoint
            if (code == 404) {
                response.close()
                val fallbackUrl = "https://generativelanguage.googleapis.com/v1beta/models?key=$key"
                val fallbackRequest = Request.Builder().url(fallbackUrl).get().build()
                response = client.newCall(fallbackRequest).execute()
                code = response.code
                body = response.body?.string() ?: ""
            }

            response.use {
                if (response.isSuccessful) {
                    Result.success("Configured ✓")
                } else {
                    val parsedDetail = parseGoogleApiError(code, body)
                    val fullError = "Yeh API key invalid hai, sahi key daaliye - $parsedDetail"
                    Result.failure(Exception(fullError))
                }
            }
        } catch (e: Exception) {
            val netError = "Yeh API key invalid hai, sahi key daaliye - [Network Error]: ${e.localizedMessage ?: "Connection failed"}"
            Result.failure(Exception(netError))
        }
    }

    /**
     * Validates candidate key with a REAL test API call BEFORE saving.
     * Only saves if validation passes.
     */
    suspend fun validateAndSaveApiKey(context: Context, candidateKey: String): Result<String> = withContext(Dispatchers.IO) {
        val sanitized = sanitizeApiKey(candidateKey)

        // Debug Log requirement: "API_KEY_VALIDATION_ATTEMPT: true"
        DebugLogger.logApiKeyValidationAttempt()

        val validationResult = validateKey(sanitized)
        if (validationResult.isSuccess) {
            val saved = saveApiKey(context, sanitized)
            if (saved) {
                // Debug Log requirement: "API_KEY_VALIDATION_RESULT: valid"
                DebugLogger.logApiKeyValidationResult(true)
                Result.success("Configured ✓")
            } else {
                val err = "Storage error: Failed to save key"
                DebugLogger.logApiKeyValidationResult(false, err)
                Result.failure(Exception(err))
            }
        } else {
            val fullError = validationResult.exceptionOrNull()?.message ?: "Yeh API key invalid hai, sahi key daaliye"
            // Debug Log requirement: "API_KEY_VALIDATION_RESULT: invalid, error=<msg>"
            DebugLogger.logApiKeyValidationResult(false, fullError)
            Result.failure(Exception(fullError))
        }
    }
}
