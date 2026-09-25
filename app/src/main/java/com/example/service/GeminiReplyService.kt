package com.example.service

import com.example.BuildConfig
import com.example.manager.AppContextManager
import com.example.util.DebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiReplyService {
    private const val MODEL_NAME = "gemini-3.5-flash"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun generateAutoReply(sender: String, messageText: String): String = withContext(Dispatchers.IO) {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (_: Throwable) {
            ""
        }

        // Context info from active app / interactions
        val currentApp = AppContextManager.getCurrentApp()?.name
        val contextInfo = if (currentApp != null) "User is currently in app: $currentApp." else ""

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            DebugLogger.logInfo("Gemini API key not configured, generating contextual offline smart reply")
            return@withContext generateFallbackReply(sender, messageText)
        }

        try {
            val systemPrompt = "You are Max AI WhatsApp Assistant. Generate a short, natural, polite auto-reply for an incoming WhatsApp message. " +
                    "Respond in the same language as the incoming message (Hindi, Hinglish, or English). " +
                    "Keep the reply concise (1-2 sentences maximum), appropriate for a chat reply. Do not add quotes."

            val userPrompt = "Incoming WhatsApp message from $sender: \"$messageText\". $contextInfo Generate an appropriate auto-reply."

            val jsonBody = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val partsArray = JSONArray().apply {
                            put(JSONObject().put("text", userPrompt))
                        }
                        put("parts", partsArray)
                    }
                    put(contentObj)
                }
                put("contents", contentsArray)

                val systemInstructionObj = JSONObject().apply {
                    val partsArray = JSONArray().apply {
                        put(JSONObject().put("text", systemPrompt))
                    }
                    put("parts", partsArray)
                }
                put("systemInstruction", systemInstructionObj)

                val genConfig = JSONObject().apply {
                    put("temperature", 0.7)
                    put("maxOutputTokens", 100)
                }
                put("generationConfig", genConfig)
            }

            val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$BASE_URL?key=$apiKey")
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && !responseBody.isNullOrBlank()) {
                val jsonResponse = JSONObject(responseBody)
                val candidates = jsonResponse.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val content = candidates.getJSONObject(0).optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        val reply = parts.getJSONObject(0).optString("text", "").trim()
                        if (reply.isNotBlank()) {
                            return@withContext reply
                        }
                    }
                }
            } else {
                DebugLogger.logInfo("Gemini API returned ${response.code}: ${responseBody?.take(100)}")
            }
        } catch (e: Exception) {
            DebugLogger.logInfo("Gemini request exception: ${e.message}")
        }

        return@withContext generateFallbackReply(sender, messageText)
    }

    private fun generateFallbackReply(sender: String, messageText: String): String {
        val lower = messageText.lowercase()
        return when {
            lower.contains("kaha") || lower.contains("kidhar") || lower.contains("where") -> {
                "Hey $sender, thoda busy hoon abhi. Free hokar call/reply karta hoon!"
            }
            lower.contains("hi") || lower.contains("hello") || lower.contains("hey") || lower.contains("नमस्ते") || lower.contains("हेलो") -> {
                "Hello $sender! Main abhi thoda busy hoon, thodi der me message karta hoon."
            }
            lower.contains("urgent") || lower.contains("call") || lower.contains("phone") -> {
                "Hi $sender, abhi call nahi utha sakta. Urgent ho to text me likh do please."
            }
            lower.contains("thanks") || lower.contains("shukriya") || lower.contains("धन्यवाद") -> {
                "You're welcome!"
            }
            else -> {
                "Hey $sender, message mil gaya. Thodi der me reply karta hoon!"
            }
        }
    }
}
