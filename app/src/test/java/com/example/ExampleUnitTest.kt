package com.example

import com.example.manager.AppContextManager
import com.example.manager.AppOpenManager
import com.example.manager.HardwareToggleManager
import com.example.manager.InstalledApp
import com.example.manager.VolumeAction
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
    fun parseVolumeCommand_correctlyParsesActionsAndPercentages() {
        // 1. "वॉल्यूम बढ़ाओ" -> INCREASE
        val v1 = HardwareToggleManager.parseVolumeCommand("वॉल्यूम बढ़ाओ")
        assertEquals(com.example.manager.VolumeAction.UP, v1.action)
        assertEquals(null, v1.explicitPercent)

        // 2. "वॉल्यूम कम करो" -> DECREASE
        val v2 = HardwareToggleManager.parseVolumeCommand("वॉल्यूम कम करो")
        assertEquals(com.example.manager.VolumeAction.DOWN, v2.action)
        assertEquals(null, v2.explicitPercent)

        // 3. "volume up" / "volume badhao" -> INCREASE
        val v3 = HardwareToggleManager.parseVolumeCommand("volume up")
        assertEquals(com.example.manager.VolumeAction.UP, v3.action)
        assertEquals(null, v3.explicitPercent)

        val v4 = HardwareToggleManager.parseVolumeCommand("volume badhao")
        assertEquals(com.example.manager.VolumeAction.UP, v4.action)
        assertEquals(null, v4.explicitPercent)

        // 4. "volume down" / "volume kam karo" -> DECREASE
        val v5 = HardwareToggleManager.parseVolumeCommand("volume down")
        assertEquals(com.example.manager.VolumeAction.DOWN, v5.action)
        assertEquals(null, v5.explicitPercent)

        val v6 = HardwareToggleManager.parseVolumeCommand("volume kam karo")
        assertEquals(com.example.manager.VolumeAction.DOWN, v6.action)
        assertEquals(null, v6.explicitPercent)

        // 5. "आवाज़ बढ़ाओ" / "आवाज़ कम करो"
        val v7 = HardwareToggleManager.parseVolumeCommand("आवाज़ बढ़ाओ")
        assertEquals(com.example.manager.VolumeAction.UP, v7.action)

        val v8 = HardwareToggleManager.parseVolumeCommand("आवाज़ कम करो")
        assertEquals(com.example.manager.VolumeAction.DOWN, v8.action)

        // 6. "वॉल्यूम म्यूट करो" / "volume mute" / "chup karo" -> MUTE
        val v9 = HardwareToggleManager.parseVolumeCommand("वॉल्यूम म्यूट करो")
        assertEquals(com.example.manager.VolumeAction.MUTE, v9.action)

        val v10 = HardwareToggleManager.parseVolumeCommand("volume mute")
        assertEquals(com.example.manager.VolumeAction.MUTE, v10.action)

        val v11 = HardwareToggleManager.parseVolumeCommand("chup karo")
        assertEquals(com.example.manager.VolumeAction.MUTE, v11.action)

        // 7. Explicit percentage: "वॉल्यूम बढ़ाओ 60%" / "volume 80 percent" / "आवाज 50%"
        val v12 = HardwareToggleManager.parseVolumeCommand("वॉल्यूम बढ़ाओ 60%")
        assertEquals(60, v12.explicitPercent)

        val v13 = HardwareToggleManager.parseVolumeCommand("volume 80 percent")
        assertEquals(80, v13.explicitPercent)

        val v14 = HardwareToggleManager.parseVolumeCommand("आवाज 50%")
        assertEquals(50, v14.explicitPercent)

        val v15 = HardwareToggleManager.parseVolumeCommand("वॉल्यूम 75 प्रतिशत")
        assertEquals(75, v15.explicitPercent)
    }

    @Test
    fun debugLogger_recordsLogsProperly() {
        DebugLogger.clearLogs()
        DebugLogger.logMatch(true, "YouTube (com.google.android.youtube)")
        DebugLogger.logLaunch(true, "YouTube")
        DebugLogger.logToggleAttempt("WiFi", ToggleMethod.ACCESSIBILITY)
        DebugLogger.logToggleResult(true)
        DebugLogger.logContextCurrentApp("YouTube")
        DebugLogger.logContextUsed(true, "Resolved iska -> YouTube")

        val logs = DebugLogger.logs.value
        assertTrue(logs.any { it.message.startsWith("APP_OPEN_MATCH: found") })
        assertTrue(logs.any { it.message.startsWith("APP_OPEN_LAUNCH: success") })
        assertTrue(logs.any { it.message.startsWith("TOGGLE_ATTEMPT: WiFi, method=ACCESSIBILITY") })
        assertTrue(logs.any { it.message.startsWith("TOGGLE_RESULT: success") })
        assertTrue(logs.any { it.message.startsWith("CONTEXT_CURRENT_APP: YouTube") })
        assertTrue(logs.any { it.message.startsWith("CONTEXT_USED: true") })
    }

    @Test
    fun appContextManager_tracksAppOpenAndHardwareToggle() {
        AppContextManager.clearMemory()
        val app = InstalledApp("YouTube", "com.google.android.youtube")
        AppContextManager.recordAppOpen(app)

        assertEquals("YouTube", AppContextManager.getCurrentApp()?.name)
        assertEquals(1, AppContextManager.getRecentInteractions().size)

        AppContextManager.recordHardwareToggle(com.example.manager.HardwareFeature.TORCH, "ON", true)
        assertEquals(com.example.manager.HardwareFeature.TORCH, AppContextManager.getLastHardwareAction()?.feature)
        assertEquals(2, AppContextManager.getRecentInteractions().size)
    }

    @Test
    fun contextResolution_resolvesReferringWordsWithContext() {
        AppContextManager.clearMemory()
        val app = InstalledApp("YouTube", "com.google.android.youtube")
        AppContextManager.recordAppOpen(app)

        // 1. "YouTube kholo" ke baad "इसका वॉल्यूम बढ़ाओ" / "iska volume badhao"
        val res1 = AppContextManager.resolveContext("इसका वॉल्यूम बढ़ाओ", isVolume = true, hasHardwareName = false)
        assertTrue(res1 is com.example.manager.ContextResolutionResult.ResolvedVolume)

        val res2 = AppContextManager.resolveContext("iska volume badhao", isVolume = true, hasHardwareName = false)
        assertTrue(res2 is com.example.manager.ContextResolutionResult.ResolvedVolume)

        // 2. Hardware toggle followed by "isko band karo"
        AppContextManager.recordHardwareToggle(com.example.manager.HardwareFeature.TORCH, "ON", true)
        val res3 = AppContextManager.resolveContext("isko band karo", isVolume = false, hasHardwareName = false)
        assertTrue(res3 is com.example.manager.ContextResolutionResult.ResolvedHardware)
        val hwRes = res3 as com.example.manager.ContextResolutionResult.ResolvedHardware
        assertEquals(com.example.manager.HardwareFeature.TORCH, hwRes.feature)
        assertEquals(false, hwRes.targetState)

        // 3. "wahi kholo" / "yeh wala open karo" after app was opened
        AppContextManager.recordAppOpen(app)
        val res4 = AppContextManager.resolveContext("wahi kholo", isVolume = false, hasHardwareName = false)
        assertTrue(res4 is com.example.manager.ContextResolutionResult.ResolvedAppOpen)
        val appRes = res4 as com.example.manager.ContextResolutionResult.ResolvedAppOpen
        assertEquals("YouTube", appRes.app.name)

        val res5 = AppContextManager.resolveContext("yeh wala open karo", isVolume = false, hasHardwareName = false)
        assertTrue(res5 is com.example.manager.ContextResolutionResult.ResolvedAppOpen)
    }

    @Test
    fun contextResolution_clarifiesWhenNoContextAvailable() {
        AppContextManager.clearMemory()

        // With no context, ambiguous command "isko band karo" or "wahi chalao" should ask "kiska matlab hai?"
        val resAmbiguous = AppContextManager.resolveContext("isko band karo", isVolume = false, hasHardwareName = false)
        assertTrue(resAmbiguous is com.example.manager.ContextResolutionResult.Ambiguous)
        val amb = resAmbiguous as com.example.manager.ContextResolutionResult.Ambiguous
        assertTrue(amb.message.contains("kiska matlab hai"))
    }

    @Test
    fun whatsAppAutoReply_logsCorrectly() {
        DebugLogger.clearLogs()
        DebugLogger.logWhatsAppMessageReceived("Amit Verma", "Kaha ho bhai?")
        DebugLogger.logWhatsAppReplyGenerated("Main abhi thoda busy hoon, thodi der me baat karta hoon.")
        DebugLogger.logWhatsAppReplySent(true, "Sent to Amit Verma")

        val logs = DebugLogger.logs.value
        assertTrue(logs.any { it.message == "WHATSAPP_MESSAGE_RECEIVED: sender=Amit Verma, text=Kaha ho bhai?" })
        assertTrue(logs.any { it.message == "WHATSAPP_REPLY_GENERATED: Main abhi thoda busy hoon, thodi der me baat karta hoon." })
        assertTrue(logs.any { it.message.startsWith("WHATSAPP_REPLY_SENT: success") })
    }

    @Test
    fun whatsAppAutoReply_defaultIsOff() {
        // Must default to OFF as specified in requirements
        assertEquals(false, com.example.manager.WhatsAppAutoReplyManager.isAutoReplyEnabled.value)
    }
}
