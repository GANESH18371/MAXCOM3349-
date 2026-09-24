package com.example

import com.example.manager.AppOpenManager
import com.example.manager.InstalledApp
import com.example.util.DebugLogger
import com.example.util.ToggleMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun sanitizeCommand_stripsGreetingAndTriggerWords() {
        val input1 = "Max hello suno YouTube kholo"
        val output1 = AppOpenManager.sanitizeCommand(input1)
        assertEquals("youtube", output1)

        val input2 = "Hey Max open WhatsApp please"
        val output2 = AppOpenManager.sanitizeCommand(input2)
        assertEquals("whatsapp", output2)

        val input3 = "Chrome chala do bhai"
        val output3 = AppOpenManager.sanitizeCommand(input3)
        assertEquals("chrome", output3)

        val input4 = "suno max camera start karo"
        val output4 = AppOpenManager.sanitizeCommand(input4)
        assertEquals("camera", output4)
    }

    @Test
    fun fuzzyMatchApp_findsInstalledApp() {
        val sampleApps = listOf(
            InstalledApp("YouTube", "com.google.android.youtube"),
            InstalledApp("WhatsApp Messenger", "com.whatsapp"),
            InstalledApp("Google Chrome", "com.android.chrome"),
            InstalledApp("Camera", "com.android.camera2"),
            InstalledApp("Calculator", "com.google.android.calculator")
        )

        val match1 = AppOpenManager.fuzzyMatchApp("Max hello suno YouTube kholo", sampleApps)
        assertNotNull(match1)
        assertEquals("com.google.android.youtube", match1?.packageName)

        val match2 = AppOpenManager.fuzzyMatchApp("hey whatsapp chalao", sampleApps)
        assertNotNull(match2)
        assertEquals("com.whatsapp", match2?.packageName)

        val match3 = AppOpenManager.fuzzyMatchApp("chrome", sampleApps)
        assertNotNull(match3)
        assertEquals("com.android.chrome", match3?.packageName)
    }

    @Test
    fun debugLogger_recordsLogsProperly() {
        DebugLogger.clearLogs()
        DebugLogger.logMatch(true, "YouTube (com.google.android.youtube)")
        DebugLogger.logLaunch(true, "YouTube")
        DebugLogger.logToggleAttempt("WiFi", ToggleMethod.ACCESSIBILITY)
        DebugLogger.logToggleResult(true)

        val logs = DebugLogger.logs.value
        assertTrue(logs.any { it.message.startsWith("APP_OPEN_MATCH: found") })
        assertTrue(logs.any { it.message.startsWith("APP_OPEN_LAUNCH: success") })
        assertTrue(logs.any { it.message.startsWith("TOGGLE_ATTEMPT: WiFi, method=ACCESSIBILITY") })
        assertTrue(logs.any { it.message.startsWith("TOGGLE_RESULT: success") })
    }
}
