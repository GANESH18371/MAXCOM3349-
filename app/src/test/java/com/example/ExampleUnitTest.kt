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

    private val sampleApps = listOf(
        InstalledApp("YouTube", "com.google.android.youtube"),
        InstalledApp("WhatsApp Messenger", "com.whatsapp"),
        InstalledApp("Google Chrome", "com.android.chrome"),
        InstalledApp("Camera", "com.android.camera2"),
        InstalledApp("Settings", "com.android.settings"),
        InstalledApp("Instagram", "com.instagram.android"),
        InstalledApp("Facebook", "com.facebook.katana"),
        InstalledApp("Calculator", "com.google.android.calculator")
    )

    @Test
    fun sanitizeCommand_stripsGreetingAndTriggerWords_englishAndHindi() {
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

        // Devanagari wake-words and action words stripping
        val input5 = "मैक्स हेलो यूट्यूब खोलो"
        val output5 = AppOpenManager.sanitizeCommand(input5)
        assertEquals("यूट्यूब", output5)

        val input6 = "युटुब ओपन करो"
        val output6 = AppOpenManager.sanitizeCommand(input6)
        assertEquals("युटुब", output6)

        val input7 = "सुनो क्रोम चला दो"
        val output7 = AppOpenManager.sanitizeCommand(input7)
        assertEquals("क्रोम", output7)
    }

    @Test
    fun fuzzyMatchApp_findsInstalledApp_english() {
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
    fun fuzzyMatchApp_devanagariPhoneticMapping_matchesCorrectly() {
        // 1. "यूट्यूब खोलो" -> YouTube open ho
        val matchYouTube1 = AppOpenManager.fuzzyMatchApp("यूट्यूब खोलो", sampleApps)
        assertNotNull("YouTube should match for 'यूट्यूब खोलो'", matchYouTube1)
        assertEquals("com.google.android.youtube", matchYouTube1?.packageName)

        // 2. "युटुब ओपन करो" -> YouTube open ho (spelling variation)
        val matchYouTube2 = AppOpenManager.fuzzyMatchApp("युटुब ओपन करो", sampleApps)
        assertNotNull("YouTube should match for 'युटुब ओपन करो'", matchYouTube2)
        assertEquals("com.google.android.youtube", matchYouTube2?.packageName)

        // 3. "क्रोम खोलो" -> Chrome open ho
        val matchChrome = AppOpenManager.fuzzyMatchApp("क्रोम खोलो", sampleApps)
        assertNotNull("Chrome should match for 'क्रोम खोलो'", matchChrome)
        assertEquals("com.android.chrome", matchChrome?.packageName)

        // 4. "व्हाट्सएप चालू करो" / "वॉट्सएप" -> WhatsApp
        val matchWhatsApp = AppOpenManager.fuzzyMatchApp("व्हाट्सएप खोलो", sampleApps)
        assertNotNull("WhatsApp should match for 'व्हाट्सएप खोलो'", matchWhatsApp)
        assertEquals("com.whatsapp", matchWhatsApp?.packageName)

        // 5. "कैमरा खोलो" -> Camera
        val matchCamera = AppOpenManager.fuzzyMatchApp("कैमरा चालू करो", sampleApps)
        assertNotNull("Camera should match for 'कैमरा चालू करो'", matchCamera)
        assertEquals("com.android.camera2", matchCamera?.packageName)

        // 6. "सेटिंग्स" -> Settings
        val matchSettings = AppOpenManager.fuzzyMatchApp("मैक्स सेटिंग्स खोलो", sampleApps)
        assertNotNull("Settings should match for 'मैक्स सेटिंग्स खोलो'", matchSettings)
        assertEquals("com.android.settings", matchSettings?.packageName)

        // 7. "इंस्टाग्राम" -> Instagram
        val matchInstagram = AppOpenManager.fuzzyMatchApp("इंस्टाग्राम चलाओ", sampleApps)
        assertNotNull("Instagram should match for 'इंस्टाग्राम चलाओ'", matchInstagram)
        assertEquals("com.instagram.android", matchInstagram?.packageName)

        // 8. "फेसबुक" -> Facebook
        val matchFacebook = AppOpenManager.fuzzyMatchApp("फेसबुक ओपन करो", sampleApps)
        assertNotNull("Facebook should match for 'फेसबुक ओपन करो'", matchFacebook)
        assertEquals("com.facebook.katana", matchFacebook?.packageName)
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
