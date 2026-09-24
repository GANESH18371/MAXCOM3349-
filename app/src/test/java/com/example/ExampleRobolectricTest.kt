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
}
