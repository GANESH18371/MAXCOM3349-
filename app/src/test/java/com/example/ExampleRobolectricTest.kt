package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.manager.AppOpenManager
import com.example.util.DebugLogger
import com.example.util.ToggleMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Max", appName)
    }

    @Test
    fun `test sanitize and fuzzy matcher logic`() {
        val raw = "Max hello suno Chrome kholo"
        val clean = AppOpenManager.sanitizeCommand(raw)
        assertEquals("chrome", clean)
    }

    @Test
    fun `test debug logger entries`() {
        DebugLogger.clearLogs()
        DebugLogger.logMatch(true, "YouTube")
        DebugLogger.logLaunch(true, "YouTube")
        DebugLogger.logToggleAttempt("Torch", ToggleMethod.DIRECT)
        DebugLogger.logToggleResult(true)

        val logs = DebugLogger.logs.value
        assertEquals(4, logs.size)
        assertTrue(logs.any { it.message == "APP_OPEN_MATCH: found (YouTube)" })
        assertTrue(logs.any { it.message == "APP_OPEN_LAUNCH: success (YouTube)" })
        assertTrue(logs.any { it.message == "TOGGLE_ATTEMPT: Torch, method=DIRECT" })
        assertTrue(logs.any { it.message == "TOGGLE_RESULT: success" })
    }

    @Test
    fun `test bug 1 and bug 2 debug log formats`() {
        DebugLogger.clearLogs()
        DebugLogger.logGeminiCallResult(true, "none")
        DebugLogger.logGeminiCallResult(false, "HTTP 503: Service Unavailable")
        DebugLogger.logCommandUnderstanding("YouTube khol kar yeh gana search karo", "TASK", 2)
        DebugLogger.logTtsCallSource("SettingsTest", "cloned")
        DebugLogger.logTtsCallSource("ConversationResponse", "cloned")

        val logs = DebugLogger.logs.value
        assertTrue(logs.any { it.message == "GEMINI_CALL_RESULT: success, error=none" })
        assertTrue(logs.any { it.message == "GEMINI_CALL_RESULT: fail, error=HTTP 503: Service Unavailable" })
        assertTrue(logs.any { it.message == "COMMAND_UNDERSTANDING: input=YouTube khol kar yeh gana search karo, classified_as=TASK, actions_parsed=2" })
        assertTrue(logs.any { it.message == "TTS_CALL_SOURCE: SettingsTest, voice_used=cloned" })
        assertTrue(logs.any { it.message == "TTS_CALL_SOURCE: ConversationResponse, voice_used=cloned" })
    }

    @Test
    fun `test multi-step search command classification and local comprehension fallback`() {
        val query = "YouTube khol kar yeh gana search karo"
        val classification = com.example.manager.CommandClassifier.classify(query, emptyList())
        assertTrue(classification.isTask)

        val fallback = com.example.service.GeminiReplyService.generateLocalComprehensionFallback(query, listOf("YouTube"))
        assertEquals(2, fallback.actions.size)
        assertEquals("open_app", fallback.actions[0].type)
        assertEquals("YouTube", fallback.actions[0].target)
        assertTrue(fallback.actions[1].type == "in_app_action" || fallback.actions[1].type == "screen_task")
    }
}
