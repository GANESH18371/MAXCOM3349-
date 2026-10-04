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

data class ComprehensionAction(
    val type: String, // "open_app", "toggle", "answer", "unclear"
    val target: String
)

data class GeminiComprehensionResult(
    val understoodIntent: String,
    val actions: List<ComprehensionAction>,
    val replyText: String
) {
    val actionNeeded: String get() = actions.firstOrNull()?.type ?: "answer"
    val target: String get() = actions.firstOrNull()?.target ?: ""
}

object GeminiReplyService {
    private const val MODEL_NAME = "gemini-2.5-flash"
    private const val FALLBACK_MODEL_NAME = "gemini-flash-latest"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent"
    private const val STREAM_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:streamGenerateContent?alt=sse"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun generateAutoReply(sender: String, messageText: String): String = withContext(Dispatchers.IO) {
        val apiKey = com.example.util.SecureApiKeyManager.getApiKey(com.example.MaxApp.instance, "WhatsAppAutoReply")

        // Context info from active app / interactions
        val currentApp = AppContextManager.getCurrentApp()?.name
        val contextInfo = if (currentApp != null) "User is currently in app: $currentApp." else ""

        if (apiKey.isBlank()) {
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

    /**
     * Multimodal scene analysis via Gemini Vision API
     */
    suspend fun analyzeSceneImage(imageBytes: ByteArray): String = withContext(Dispatchers.IO) {
        val apiKey = com.example.util.SecureApiKeyManager.getApiKey(com.example.MaxApp.instance, "CameraSceneAnalysis")

        if (apiKey.isBlank()) {
            DebugLogger.logGeminiRequestSent(false, "API key missing or blank")
            DebugLogger.logGeminiResponseReceived(false, "API key missing, returning local scene fallback")
            DebugLogger.logInfo("Gemini API key not configured, returning local scene fallback")
            return@withContext "सामने एक कमरा और वस्तुएं दिखाई दे रही हैं. स्पष्ट विवरण के लिए Gemini API Key कॉन्फ़िगर करें."
        }

        try {
            val base64Data = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP)

            val jsonBody = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val partsArray = JSONArray().apply {
                            put(JSONObject().put("text", "Is image me kya hai, short me batao. 1-2 chote sentences me naturally Hindi me bolo (jaise 'Saamne ek laptop aur kitaab rakhi hai')."))
                            val inlineData = JSONObject().apply {
                                put("mimeType", "image/jpeg")
                                put("data", base64Data)
                            }
                            put(JSONObject().put("inlineData", inlineData))
                        }
                        put("parts", partsArray)
                    }
                    put(contentObj)
                }
                put("contents", contentsArray)

                val genConfig = JSONObject().apply {
                    put("temperature", 0.4)
                    put("maxOutputTokens", 120)
                }
                put("generationConfig", genConfig)
            }

            val payloadStr = jsonBody.toString()
            DebugLogger.logGeminiRequestSent(true, "multimodal_scene_analysis payload length=${payloadStr.length}")

            val requestBody = payloadStr.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$BASE_URL?key=$apiKey")
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && !responseBody.isNullOrBlank()) {
                DebugLogger.logGeminiResponseReceived(true, responseBody)
                val jsonResponse = JSONObject(responseBody)
                val candidates = jsonResponse.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val content = candidates.getJSONObject(0).optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        val analysisText = parts.getJSONObject(0).optString("text", "").trim()
                        if (analysisText.isNotBlank()) {
                            return@withContext analysisText
                        }
                    }
                }
            } else {
                val errorDetails = responseBody ?: "HTTP ${response.code}: ${response.message}"
                DebugLogger.logGeminiResponseReceived(false, errorDetails)
                DebugLogger.logInfo("Gemini Scene Analysis API error: ${response.code} ${responseBody?.take(100)}")
            }
        } catch (e: Exception) {
            DebugLogger.logGeminiResponseReceived(false, "Exception: ${e.message}")
            DebugLogger.logInfo("Gemini scene analysis exception: ${e.message}")
        }

        return@withContext "सामने का दृश्य स्पष्ट नहीं हो सका. कृपया दोबारा प्रयास करें."
    }

    /**
     * Deep Human-Like Comprehension via Gemini 2.5 Flash JSON Output.
     * Understands casual, colloquial, indirect Hindi/Hinglish phrasing, idioms, and multi-sentence thought flows.
     * Supports MULTIPLE INTENTS in a single command executed sequentially.
     * Returns structured intent, actions array, target, and a warm friendly reply.
     */
    suspend fun deepUnderstandCommand(
        userQuery: String,
        contextSummary: String,
        knownApps: List<String>,
        context: android.content.Context? = null
    ): GeminiComprehensionResult = withContext(Dispatchers.IO) {
        val ctx = context ?: try { com.example.MaxApp.instance } catch (_: Throwable) { null }
        val apiKey = com.example.util.SecureApiKeyManager.getApiKey(ctx, "deep_comprehension")

        if (apiKey.isBlank()) {
            DebugLogger.logGeminiRequestSent(false, "API key missing or blank")
            DebugLogger.logGeminiResponseReceived(false, "API key missing, returning local comprehension fallback")
            return@withContext generateLocalComprehensionFallback(userQuery)
        }

        try {
            val appsSample = knownApps.take(20).joinToString(", ")
            val systemPrompt = """
                You are Max, a perceptive, warm, and intuitive Android personal voice assistant.
                The user speaks in Hindi, Hinglish, or casual English. Users often speak casually, colloquially, with idioms, or across MULTIPLE SENTENCES (e.g. "yaar mujhe bhookh lagi hai... kuch order kar do", "kuch dekhne ka mann hai", "thoda andhera hai", "YouTube kholo aur volume badha do").

                RULES:
                1. MULTI-SENTENCE: Read the entire statement together as ONE unified narrative. Do not break or truncate early.
                2. MULTIPLE INTENTS: If the user requests multiple actions (e.g. "YouTube kholo aur volume bhi badha do", "torch on karo aur wifi band karo"), identify ALL actions sequentially in the "actions" array in order!
                3. IDIOMS & INDIRECT INTENTS:
                   - "bhookh lagi hai" / "kuch khana hai" / "order karna hai" -> type: "open_app", target: "Zomato" (or Swiggy)
                   - "kuch dekhne ka mann hai" / "bore ho raha hu" / "timepass" -> type: "open_app", target: "YouTube"
                   - "thoda andhera hai" / "kuch dikh nahi raha" / "roshni chahiye" -> type: "toggle", target: "torch_on"
                   - "screen chub rahi hai" / "aankh dukh rahi hai" / "tez roshni" -> type: "toggle", target: "brightness_toggle"
                   - "aawaz sunai nahi de rahi" -> type: "toggle", target: "volume_up"
                   - "bohot shor hai" / "aawaz kam karo" -> type: "toggle", target: "volume_down"
                   - "shanti chahiye" / "disturb mat karo" / "sone ja raha hu" -> type: "toggle", target: "dnd_on"
                   - "kisi se baat karni hai" / "message karna hai" -> type: "open_app", target: "WhatsApp"
                   - "paise bhejne hain" -> type: "open_app", target: "GPay"
                   - "ghoomne jana hai" / "cab book karni hai" -> type: "open_app", target: "Maps"
                4. TARGET APPS: Known apps include: $appsSample.
                5. TOGGLE TARGETS: torch_on, torch_off, wifi_on, wifi_off, bluetooth_on, bluetooth_off, volume_up, volume_down, volume_mute, brightness_toggle, dnd_on, dnd_off, hotspot_on, hotspot_off, mobile_data_toggle.
                6. CONVERSATION: If user is chatting or asking questions, action type is "answer".
                7. UNCLEAR: If genuinely ambiguous, set type to "unclear" and formulate a warm clarifying question in reply_text.
                8. WARM TONE: Keep reply_text short (1-2 sentences), warm, and natural in Hindi/Hinglish like a helpful friend.

                Return ONLY JSON:
                {
                  "understood_intent": "brief intent summary",
                  "actions": [
                    {"type": "open_app | toggle | answer | unclear", "target": "target name"}
                  ],
                  "reply_text": "warm spoken reply or clarifying question"
                }
            """.trimIndent()

            val userContent = """
                User: "$userQuery"
                Context: $contextSummary
            """.trimIndent()

            val jsonBody = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val partsArray = JSONArray().apply {
                            put(JSONObject().put("text", userContent))
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
                    put("temperature", 0.3)
                    put("maxOutputTokens", 180)
                    put("responseMimeType", "application/json")
                }
                put("generationConfig", genConfig)
            }

            val payloadStr = jsonBody.toString()
            DebugLogger.logGeminiRequestSent(true, payloadStr)

            val requestBody = payloadStr.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$BASE_URL?key=$apiKey")
                .post(requestBody)
                .build()

            var response = okHttpClient.newCall(request).execute()
            var responseBody = response.body?.string()

            // Automatic fallback if primary model returns 404 (endpoint not available in this project/region)
            if (response.code == 404) {
                response.close()
                val fallbackUrl = "https://generativelanguage.googleapis.com/v1beta/models/$FALLBACK_MODEL_NAME:generateContent?key=$apiKey"
                val fallbackRequest = Request.Builder()
                    .url(fallbackUrl)
                    .post(payloadStr.toRequestBody("application/json".toMediaType()))
                    .build()
                response = okHttpClient.newCall(fallbackRequest).execute()
                responseBody = response.body?.string()
            }

            if (response.isSuccessful && !responseBody.isNullOrBlank()) {
                DebugLogger.logGeminiResponseReceived(true, responseBody)
                val jsonResponse = JSONObject(responseBody)
                val candidates = jsonResponse.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val content = candidates.getJSONObject(0).optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        val rawText = parts.getJSONObject(0).optString("text", "").trim()
                        val parsed = parseComprehensionJson(rawText)
                        if (parsed != null) {
                            return@withContext parsed
                        }
                    }
                }
            } else {
                val errorDetails = responseBody ?: "HTTP ${response.code}: ${response.message}"
                DebugLogger.logGeminiResponseReceived(false, errorDetails)
            }
        } catch (e: Exception) {
            DebugLogger.logGeminiResponseReceived(false, "Exception: ${e.message}")
            DebugLogger.logInfo("Gemini deep comprehension exception: ${e.message}")
        }

        return@withContext generateLocalComprehensionFallback(userQuery)
    }

    private fun parseComprehensionJson(rawText: String): GeminiComprehensionResult? {
        return try {
            val cleaned = rawText
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            val obj = JSONObject(cleaned)
            val intent = obj.optString("understood_intent", "Understood command")
            val actionsList = mutableListOf<ComprehensionAction>()
            val actionsArr = obj.optJSONArray("actions")
            if (actionsArr != null) {
                for (i in 0 until actionsArr.length()) {
                    val actObj = actionsArr.getJSONObject(i)
                    val t = actObj.optString("type", "answer").trim().lowercase()
                    val targ = actObj.optString("target", "").trim()
                    actionsList.add(ComprehensionAction(t, targ))
                }
            }
            if (actionsList.isEmpty()) {
                val action = obj.optString("action_needed", "answer").trim().lowercase()
                val target = obj.optString("target", "").trim()
                actionsList.add(ComprehensionAction(action, target))
            }
            val reply = obj.optString("reply_text", "").trim()
            if (reply.isNotBlank()) {
                GeminiComprehensionResult(
                    understoodIntent = intent,
                    actions = actionsList,
                    replyText = reply
                )
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Streams conversational response chunk-by-chunk directly into TTS.
     * Speaks each sentence as soon as it arrives without waiting for full generation.
     */
    suspend fun streamConversationalReply(
        userQuery: String,
        contextSummary: String,
        onSentenceChunk: (chunk: String, isFirstChunk: Boolean) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val apiKey = com.example.util.SecureApiKeyManager.getApiKey(com.example.MaxApp.instance, "ContinuousConversation")
        if (apiKey.isBlank()) {
            val fallback = generateLocalConversationalFallback(userQuery)
            onSentenceChunk(fallback, true)
            return@withContext fallback
        }

        val systemPrompt = "You are Max, a warm, intelligent, and natural Hindi/Hinglish personal voice assistant. " +
                "Respond like a helpful friend in 1 to 2 short conversational sentences. " +
                "Do not use markdown, bullets, or robotic formatting."

        val fullTextBuilder = StringBuilder()
        val sentenceBuffer = StringBuilder()
        var isFirstChunk = true

        try {
            val jsonBody = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val partsArray = JSONArray().apply {
                            put(JSONObject().put("text", "Context: $contextSummary\nUser: $userQuery"))
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
                    put("temperature", 0.6)
                    put("maxOutputTokens", 120)
                }
                put("generationConfig", genConfig)
            }

            val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$STREAM_URL&key=$apiKey")
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val source = response.body?.source()

            if (response.isSuccessful && source != null) {
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.startsWith("data: ")) {
                        val dataPayload = line.removePrefix("data: ").trim()
                        if (dataPayload == "[DONE]" || dataPayload.isBlank()) continue
                        try {
                            val chunkJson = JSONObject(dataPayload)
                            val candidates = chunkJson.optJSONArray("candidates")
                            if (candidates != null && candidates.length() > 0) {
                                val content = candidates.getJSONObject(0).optJSONObject("content")
                                val parts = content?.optJSONArray("parts")
                                if (parts != null && parts.length() > 0) {
                                    val textPart = parts.getJSONObject(0).optString("text", "")
                                    if (textPart.isNotEmpty()) {
                                        fullTextBuilder.append(textPart)
                                        sentenceBuffer.append(textPart)

                                        // Check for sentence boundary: '.', '!', '?', '\n', '।'
                                        val buf = sentenceBuffer.toString()
                                        val matchIndex = buf.indexOfAny(charArrayOf('.', '!', '?', '\n', '।'))
                                        if (matchIndex != -1 && matchIndex >= 8) {
                                            val completeSentence = buf.substring(0, matchIndex + 1).trim()
                                            sentenceBuffer.delete(0, matchIndex + 1)
                                            if (completeSentence.isNotBlank()) {
                                                onSentenceChunk(completeSentence, isFirstChunk)
                                                isFirstChunk = false
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
                val remainder = sentenceBuffer.toString().trim()
                if (remainder.isNotBlank()) {
                    onSentenceChunk(remainder, isFirstChunk)
                }
                if (fullTextBuilder.isNotBlank()) {
                    return@withContext fullTextBuilder.toString().trim()
                }
            }
        } catch (e: Exception) {
            DebugLogger.logInfo("Streaming conversational reply exception: ${e.message}")
        }

        val fallback = generateLocalConversationalFallback(userQuery)
        if (fullTextBuilder.isEmpty()) {
            onSentenceChunk(fallback, true)
            return@withContext fallback
        }
        return@withContext fullTextBuilder.toString().trim()
    }

    /**
     * General conversational AI response for voice queries (Gemini 2.5 Flash text API)
     * Responds in concise, spoken natural language (Hindi, Hinglish, or English).
     */
    suspend fun generateAssistantVoiceResponse(userQuery: String): String = withContext(Dispatchers.IO) {
        val result = deepUnderstandCommand(userQuery, AppContextManager.getRecentContextSummary(), emptyList())
        return@withContext result.replyText
    }

    fun generateLocalComprehensionFallback(userQuery: String): GeminiComprehensionResult {
        val lower = userQuery.lowercase()

        // 1. Compound multi-intent local detection ("YouTube kholo aur volume badha do", "torch aur wifi", etc.)
        if (lower.contains(" aur ") || lower.contains(" and ") || lower.contains(" phir ")) {
            val parts = lower.split(Regex(" aur | and | phir "))
            if (parts.size >= 2) {
                val act1 = extractFallbackAction(parts[0].trim())
                val act2 = extractFallbackAction(parts[1].trim())
                if (act1 != null && act2 != null) {
                    return GeminiComprehensionResult(
                        understoodIntent = "Multiple sequential actions",
                        actions = listOf(act1, act2),
                        replyText = "Bilkul! Main dono kaam ek saath kar raha hoon."
                    )
                }
            }
        }

        // 2. Idiomatic & Single Intent Fallbacks
        return when {
            lower.contains("bhookh") || lower.contains("khana") || lower.contains("order kar") || lower.contains("swiggy") || lower.contains("zomato") -> {
                GeminiComprehensionResult(
                    understoodIntent = "Order food / open delivery app",
                    actions = listOf(ComprehensionAction("open_app", "Zomato")),
                    replyText = "Bhookh lagi hai to chaliye Zomato khol deta hoon, kuch swadisht order kar lijiye!"
                )
            }
            lower.contains("gaana") || lower.contains("song") || lower.contains("music") || lower.contains("bore") || lower.contains("bajao") || lower.contains("chalao") || lower.contains("dekhne ka mann") -> {
                GeminiComprehensionResult(
                    understoodIntent = "Play music or video for entertainment",
                    actions = listOf(ComprehensionAction("open_app", "YouTube")),
                    replyText = "Haan bilkul! Main aapke liye YouTube chala raha hoon, thoda mood refresh ho jayega!"
                )
            }
            lower.contains("andhera") || lower.contains("dark") || lower.contains("roshni") || lower.contains("light") || lower.contains("dikh nahi") || lower.contains("batti") -> {
                GeminiComprehensionResult(
                    understoodIntent = "Turn on torch for visibility",
                    actions = listOf(ComprehensionAction("toggle", "torch_on")),
                    replyText = "Rukiye, main torch chalu kar deta hoon taaki aapko saaf dikhe."
                )
            }
            lower.contains("chamak") || lower.contains("aankh") || lower.contains("tez") || lower.contains("bright") || lower.contains("chub") -> {
                GeminiComprehensionResult(
                    understoodIntent = "Adjust screen brightness",
                    actions = listOf(ComprehensionAction("toggle", "brightness_toggle")),
                    replyText = "Maine screen ki brightness adjust kar di hai, ab aankhon ko aaram milega."
                )
            }
            lower.contains("shor") || lower.contains("dheere") || lower.contains("dheeme") || lower.contains("awaz kam") || lower.contains("aawaz kam") -> {
                GeminiComprehensionResult(
                    understoodIntent = "Lower volume",
                    actions = listOf(ComprehensionAction("toggle", "volume_down")),
                    replyText = "Main aawaz thodi dheere kar deta hoon."
                )
            }
            lower.contains("sunai nahi") || lower.contains("awaz badhao") || lower.contains("aawaz badhao") || lower.contains("tez aawaz") -> {
                GeminiComprehensionResult(
                    understoodIntent = "Increase volume",
                    actions = listOf(ComprehensionAction("toggle", "volume_up")),
                    replyText = "Main aawaz badha deta hoon taaki saaf sunai de."
                )
            }
            lower.contains("shanti") || lower.contains("disturb") || lower.contains("silent") || lower.contains("padhai") || lower.contains("sone") -> {
                GeminiComprehensionResult(
                    understoodIntent = "Activate Do Not Disturb",
                    actions = listOf(ComprehensionAction("toggle", "dnd_on")),
                    replyText = "Main Do Not Disturb chalu kar raha hoon taaki koi shanti kharab na kare."
                )
            }
            lower.contains("baat karni") || lower.contains("message") || lower.contains("chat") -> {
                GeminiComprehensionResult(
                    understoodIntent = "Open WhatsApp for communication",
                    actions = listOf(ComprehensionAction("open_app", "WhatsApp")),
                    replyText = "Main WhatsApp open kar raha hoon, baat kar lijiye."
                )
            }
            lower.contains("paise") || lower.contains("payment") || lower.contains("bhejne") -> {
                GeminiComprehensionResult(
                    understoodIntent = "Open payment app",
                    actions = listOf(ComprehensionAction("open_app", "GPay")),
                    replyText = "Main payment ke liye app open kar deta hoon."
                )
            }
            lower.contains("ghoomne") || lower.contains("cab") || lower.contains("rasta") -> {
                GeminiComprehensionResult(
                    understoodIntent = "Open navigation / maps",
                    actions = listOf(ComprehensionAction("open_app", "Maps")),
                    replyText = "Main Maps open kar raha hoon, rasta dekh lijiye."
                )
            }
            lower.contains("wahi") || lower.contains("pichla") || lower.contains("pehle jaisa") -> {
                val lastApp = AppContextManager.getCurrentApp()
                if (lastApp != null) {
                    GeminiComprehensionResult(
                        understoodIntent = "Reopen last active app",
                        actions = listOf(ComprehensionAction("open_app", lastApp.name)),
                        replyText = "Main ${lastApp.name} wapas khol raha hoon."
                    )
                } else {
                    GeminiComprehensionResult(
                        understoodIntent = "Clarify previous reference",
                        actions = listOf(ComprehensionAction("unclear", "")),
                        replyText = "Aap pichle kis app ya setting ki baat kar rahe hain? Ek baar thoda saaf batayein na!"
                    )
                }
            }
            else -> {
                val spoken = generateLocalConversationalFallback(userQuery)
                GeminiComprehensionResult(
                    understoodIntent = "Friendly conversational reply",
                    actions = listOf(ComprehensionAction("answer", "")),
                    replyText = spoken
                )
            }
        }
    }

    private fun extractFallbackAction(part: String): ComprehensionAction? {
        val p = part.lowercase()
        return when {
            p.contains("youtube") -> ComprehensionAction("open_app", "YouTube")
            p.contains("whatsapp") -> ComprehensionAction("open_app", "WhatsApp")
            p.contains("zomato") || p.contains("swiggy") || p.contains("khana") || p.contains("bhookh") -> ComprehensionAction("open_app", "Zomato")
            p.contains("volume") && (p.contains("badha") || p.contains("up") || p.contains("tez")) -> ComprehensionAction("toggle", "volume_up")
            p.contains("volume") && (p.contains("kam") || p.contains("down") || p.contains("dheere")) -> ComprehensionAction("toggle", "volume_down")
            p.contains("torch") && (p.contains("on") || p.contains("chalu") || p.contains("jalao")) -> ComprehensionAction("toggle", "torch_on")
            p.contains("torch") && (p.contains("off") || p.contains("band") || p.contains("bujhao")) -> ComprehensionAction("toggle", "torch_off")
            p.contains("wifi") && (p.contains("on") || p.contains("chalu")) -> ComprehensionAction("toggle", "wifi_on")
            p.contains("wifi") && (p.contains("off") || p.contains("band")) -> ComprehensionAction("toggle", "wifi_off")
            p.contains("bluetooth") && (p.contains("on") || p.contains("chalu")) -> ComprehensionAction("toggle", "bluetooth_on")
            p.contains("bluetooth") && (p.contains("off") || p.contains("band")) -> ComprehensionAction("toggle", "bluetooth_off")
            p.contains("brightness") -> ComprehensionAction("toggle", "brightness_toggle")
            p.contains("dnd") -> ComprehensionAction("toggle", "dnd_on")
            else -> null
        }
    }

    private fun generateLocalConversationalFallback(query: String): String {
        val lower = query.lowercase()
        return when {
            lower.contains("kaun ho") || lower.contains("who are you") || lower.contains("naam kya") || lower.contains("kya naam") -> {
                "Main Max hoon, aapka dost aur personal AI assistant. Main aapke phone ke kaam aasan karne me madad karta hoon."
            }
            lower.contains("kya kar sakte ho") || lower.contains("help") || lower.contains("madad") || lower.contains("features") -> {
                "Main apps open kar sakta hoon, WiFi, Torch, Volume adjust kar sakta hoon, reminders set karta hoon, aur kisi bhi baat par dosti se jawab deta hoon."
            }
            lower.contains("kaise ho") || lower.contains("how are you") || lower.contains("kya haal") -> {
                "Main bilkul zabardast hoon! Aap bataiye, aaj aapka din kaisa chal raha hai?"
            }
            lower.contains("namaste") || lower.contains("hello") || lower.contains("hi") || lower.contains("hey") || lower.contains("नमस्ते") -> {
                "नमस्ते! कहिए, आज मैं अपने दोस्त की क्या मदद करूँ?"
            }
            lower.contains("shukriya") || lower.contains("dhanyawad") || lower.contains("thanks") || lower.contains("thank you") -> {
                "Arrey dost me shukriya kaisa! Main hamesha yahin hoon."
            }
            else -> {
                val hasKey = com.example.util.SecureApiKeyManager.getApiKey(null, "LocalFallbackCheck").isNotBlank()
                if (!hasKey) {
                    "Maine aapki baat suni: $query. Online aur gehraai se uttar paane ke liye Settings me Gemini API key save kar lijiye."
                } else {
                    "Maine aapki baat suni: $query. Main aapki madad ke liye hamesha taiyaar hoon!"
                }
            }
        }
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

    /**
     * Generates a warm, natural outgoing message for any messaging app based on topic or prompt.
     */
    suspend fun generateGenericOutgoingMessage(recipient: String, topic: String): String = withContext(Dispatchers.IO) {
        val apiKey = com.example.util.SecureApiKeyManager.getApiKey(com.example.MaxApp.instance, "GenericMessaging")
        if (apiKey.isBlank()) {
            return@withContext generateLocalOutgoingMessageFallback(recipient, topic)
        }

        try {
            val systemPrompt = "You are Max, drafting a natural, polite, short (1 sentence) message to send to $recipient via a messaging app. " +
                    "Draft it in warm casual Hindi/Hinglish or English matching the user's request. Output only the message text without quotes or explanation."

            val jsonBody = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val partsArray = JSONArray().apply {
                            put(JSONObject().put("text", "Draft a message for $recipient based on: $topic"))
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
                    put("maxOutputTokens", 60)
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
                        val reply = parts.getJSONObject(0).optString("text", "").trim().removeSurrounding("\"")
                        if (reply.isNotBlank()) {
                            return@withContext reply
                        }
                    }
                }
            }
        } catch (e: Exception) {
            DebugLogger.logInfo("Gemini outgoing message generation exception: ${e.message}")
        }

        return@withContext generateLocalOutgoingMessageFallback(recipient, topic)
    }

    private fun generateLocalOutgoingMessageFallback(recipient: String, topic: String): String {
        val lower = topic.lowercase()
        return when {
            lower.contains("birthday") || lower.contains("wish") -> {
                "Happy Birthday $recipient! Wishing you a wonderful year ahead!"
            }
            lower.contains("late") || lower.contains("der") -> {
                "Hey $recipient, main thoda late ho jaunga. Thodi der me milte hain."
            }
            lower.contains("pahunch") || lower.contains("aa gaya") -> {
                "Hey $recipient, main pahunch gaya hoon!"
            }
            lower.contains("meeting") -> {
                "Hey $recipient, kya hum meeting ke liye connect kar sakte hain?"
            }
            lower.contains("kaha") || lower.contains("kaha ho") -> {
                "Hey $recipient, kahan ho abhi? Free ho to batana."
            }
            else -> {
                topic.removePrefix("ki ").removePrefix("kaho ").removePrefix("bolo ").trim()
            }
        }
    }

    /**
     * Generic App-Control: Screen understanding via Gemini.
     * Takes user action intent and visible UI elements from screen-read, returns matching element identifier.
     */
    suspend fun resolveScreenActionTarget(
        actionIntent: String,
        screenElements: List<String>
    ): String = withContext(Dispatchers.IO) {
        val apiKey = com.example.util.SecureApiKeyManager.getApiKey(com.example.MaxApp.instance, "ScreenActionResolution")
        if (apiKey.isBlank() || screenElements.isEmpty()) {
            return@withContext ""
        }

        try {
            val elementsSample = screenElements.take(30).joinToString("\n- ")
            val systemPrompt = "You are Max App-Control screen analyzer. The user wants to: '$actionIntent'.\n" +
                    "Here are the clickable UI elements detected on the current Android screen:\n- $elementsSample\n" +
                    "Identify the single most accurate element text or description that performs this action. Return ONLY the exact element text, or 'NONE' if no element matches."

            val jsonBody = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val partsArray = JSONArray().apply {
                            put(JSONObject().put("text", "Which element should be tapped for: $actionIntent?"))
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
                    put("temperature", 0.1)
                    put("maxOutputTokens", 30)
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
                        val chosen = parts.getJSONObject(0).optString("text", "").trim().removeSurrounding("\"")
                        if (chosen.isNotBlank() && !chosen.equals("NONE", ignoreCase = true)) {
                            return@withContext chosen
                        }
                    }
                }
            }
        } catch (e: Exception) {
            DebugLogger.logInfo("Screen action target resolution exception: ${e.message}")
        }
        return@withContext ""
    }
}
