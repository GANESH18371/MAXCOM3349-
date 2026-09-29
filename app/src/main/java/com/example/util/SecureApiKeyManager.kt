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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object SecureApiKeyManager {
    private const val TAG = "SecureApiKeyManager"
    private const val PREFS_FILE = "max_secure_api_prefs"
    private const val KEY_GEMINI_API = "secure_gemini_api_key"

    private val _apiKeyFlow = MutableStateFlow("")
    val apiKeyFlow: StateFlow<String> = _apiKeyFlow.asStateFlow()

    private var initialized = false

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
        val key = getApiKey(context)
        _apiKeyFlow.value = key
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
     * 1. User explicitly saved key in EncryptedSharedPreferences (Settings Screen).
     * 2. Fallback to BuildConfig.GEMINI_API_KEY if present and valid.
     */
    fun getApiKey(context: Context): String {
        try {
            val prefs = getSecurePrefs(context)
            val userKey = prefs.getString(KEY_GEMINI_API, "")?.trim() ?: ""
            if (userKey.isNotBlank()) {
                return userKey
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading user API key from secure storage", e)
        }

        // Fallback to BuildConfig if defined in build/env
        val buildKey = try {
            BuildConfig.GEMINI_API_KEY.trim()
        } catch (_: Throwable) {
            ""
        }

        return if (buildKey.isNotBlank() && buildKey != "MY_GEMINI_API_KEY") {
            buildKey
        } else {
            ""
        }
    }

    /**
     * Returns true if user has saved a key or build key is valid.
     */
    fun isKeyConfigured(context: Context): Boolean {
        return getApiKey(context).isNotBlank()
    }

    /**
     * Returns true if the active key was explicitly provided by the user in Settings.
     */
    fun isUserSuppliedKey(context: Context): Boolean {
        return try {
            val prefs = getSecurePrefs(context)
            val userKey = prefs.getString(KEY_GEMINI_API, "")?.trim() ?: ""
            userKey.isNotBlank()
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Saves user's API key into EncryptedSharedPreferences.
     */
    fun saveApiKey(context: Context, rawKey: String): Boolean {
        val trimmed = rawKey.trim()
        return try {
            val prefs = getSecurePrefs(context)
            prefs.edit().putString(KEY_GEMINI_API, trimmed).apply()
            _apiKeyFlow.value = trimmed
            DebugLogger.logInfo("CENTRAL_API_KEY: User key saved securely to EncryptedSharedPreferences")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save user API key", e)
            DebugLogger.logInfo("CENTRAL_API_KEY_ERROR: Failed to save (${e.message})")
            false
        }
    }

    /**
     * Deletes user's API key from EncryptedSharedPreferences.
     */
    fun clearApiKey(context: Context): Boolean {
        return try {
            val prefs = getSecurePrefs(context)
            prefs.edit().remove(KEY_GEMINI_API).apply()
            val fallback = getApiKey(context)
            _apiKeyFlow.value = fallback
            DebugLogger.logInfo("CENTRAL_API_KEY: User key cleared from secure storage")
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
        val trimmed = fullKey.trim()
        if (trimmed.isBlank()) return "Not Configured"
        if (trimmed.length <= 10) return "••••••••"
        val prefix = trimmed.take(7)
        val suffix = trimmed.takeLast(4)
        return "$prefix••••••••$suffix"
    }

    /**
     * Validates the provided API key by calling the models list endpoint.
     */
    suspend fun validateKey(candidateKey: String): Result<String> = withContext(Dispatchers.IO) {
        val key = candidateKey.trim()
        if (key.isBlank()) {
            return@withContext Result.failure(Exception("API Key cannot be blank"))
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .build()

        val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$key"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success("API Key is VALID and ACTIVE!")
                } else {
                    val code = response.code
                    val errorBody = response.body?.string() ?: ""
                    val msg = when (code) {
                        400 -> "Invalid API Key format or parameter"
                        403 -> "API Key expired, disabled, or unauthorized"
                        429 -> "API Key quota exceeded"
                        else -> "API Error: HTTP $code ($errorBody)"
                    }
                    Result.failure(Exception(msg))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("Connection error: ${e.localizedMessage ?: "Unable to reach Google servers"}"))
        }
    }
}
