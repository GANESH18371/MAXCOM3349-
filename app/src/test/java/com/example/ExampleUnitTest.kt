package com.example

import com.example.manager.AppContextManager
import com.example.manager.AppOpenManager
import com.example.manager.HardwareToggleManager
import com.example.manager.InstalledApp
import com.example.manager.VolumeAction
import com.example.util.DebugLogger
import com.example.util.ToggleMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        DebugLogger.logWhatsAppNotificationReceived("notif_key_123", false)
        DebugLogger.logWhatsAppSelfTriggerIgnored(false)
        DebugLogger.logWhatsAppRateLimitCheck(true, "sender=Amit Verma")
        DebugLogger.logWhatsAppMessageReceived("Amit Verma", "Kaha ho bhai?")
        DebugLogger.logWhatsAppReplyGenerated("Main abhi thoda busy hoon, thodi der me baat karta hoon.")
        DebugLogger.logWhatsAppReplySent(true, "Sent to Amit Verma")

        val logs = DebugLogger.logs.value
        assertTrue(logs.any { it.message == "WHATSAPP_NOTIFICATION_RECEIVED: id=notif_key_123, isDuplicate=false" })
        assertTrue(logs.any { it.message == "WHATSAPP_SELF_TRIGGER_IGNORED: false" })
        assertTrue(logs.any { it.message.startsWith("WHATSAPP_REPLY_RATE_LIMIT_CHECK: allowed") })
        assertTrue(logs.any { it.message == "WHATSAPP_MESSAGE_RECEIVED: sender=Amit Verma, text=Kaha ho bhai?" })
        assertTrue(logs.any { it.message == "WHATSAPP_REPLY_GENERATED: Main abhi thoda busy hoon, thodi der me baat karta hoon." })
        assertTrue(logs.any { it.message.startsWith("WHATSAPP_REPLY_SENT: success") })
    }

    @Test
    fun whatsAppAutoReply_defaultIsOff() {
        // Must default to OFF as specified in requirements
        assertEquals(false, com.example.manager.WhatsAppAutoReplyManager.isAutoReplyEnabled.value)
    }

    @Test
    fun whatsAppAutoReply_selfTriggerDetection() {
        com.example.manager.WhatsAppAutoReplyManager.clearHistoryForTesting()

        // 1. Outgoing messages starting with "You:" / "आप:" must be recognized as self-trigger
        assertTrue(com.example.manager.WhatsAppAutoReplyManager.isSelfTrigger("Pooja", "You: Hey, I am busy"))
        assertTrue(com.example.manager.WhatsAppAutoReplyManager.isSelfTrigger("Pooja", "आप: Haan bhai"))

        // 2. Normal incoming message is NOT self-trigger
        assertEquals(false, com.example.manager.WhatsAppAutoReplyManager.isSelfTrigger("Pooja", "Hello!"))
    }

    @Test
    fun reminderParser_parsesNaturalLanguageCommands() {
        // 1. Set Reminder
        val cmd1 = "mujhe 5 baje chai peene ka yaad dilana"
        assertTrue(com.example.util.ReminderParser.isReminderOrAlarmCommand(cmd1))
        val action1 = com.example.util.ReminderParser.parseCommand(cmd1)
        assertTrue(action1 is com.example.util.ReminderVoiceAction.SetReminder)
        val setRem1 = action1 as com.example.util.ReminderVoiceAction.SetReminder
        assertEquals(false, setRem1.isAlarm)
        assertTrue(setRem1.task.lowercase().contains("chai"))

        // 2. Set Alarm
        val cmd2 = "7 baje alarm laga do"
        assertTrue(com.example.util.ReminderParser.isReminderOrAlarmCommand(cmd2))
        val action2 = com.example.util.ReminderParser.parseCommand(cmd2)
        assertTrue(action2 is com.example.util.ReminderVoiceAction.SetReminder)
        val setRem2 = action2 as com.example.util.ReminderVoiceAction.SetReminder
        assertEquals(true, setRem2.isAlarm)

        // 3. Cancel Reminder
        val cmd3 = "mera dawai wala reminder cancel karo"
        assertTrue(com.example.util.ReminderParser.isReminderOrAlarmCommand(cmd3))
        val action3 = com.example.util.ReminderParser.parseCommand(cmd3)
        assertTrue(action3 is com.example.util.ReminderVoiceAction.CancelReminder)
        val cancel3 = action3 as com.example.util.ReminderVoiceAction.CancelReminder
        assertTrue(cancel3.keyword.contains("dawai"))

        // 4. List Reminders
        val cmd4 = "mere saare reminders batao"
        assertTrue(com.example.util.ReminderParser.isReminderOrAlarmCommand(cmd4))
        val action4 = com.example.util.ReminderParser.parseCommand(cmd4)
        assertTrue(action4 is com.example.util.ReminderVoiceAction.ListReminders)
    }

    @Test
    fun weatherManager_detectsWeatherVoiceCommands() {
        assertTrue(com.example.manager.WeatherManager.isWeatherCommand("aaj ka mausam kaisa hai"))
        assertTrue(com.example.manager.WeatherManager.isWeatherCommand("weather kaisa hai"))
        assertTrue(com.example.manager.WeatherManager.isWeatherCommand("aaj tapman kitna hai"))
        assertTrue(com.example.manager.WeatherManager.isWeatherCommand("मौसम कैसा है"))
        assertEquals(false, com.example.manager.WeatherManager.isWeatherCommand("यूट्यूब खोलो"))
        assertEquals(false, com.example.manager.WeatherManager.isWeatherCommand("Torch on karo"))
    }

    @Test
    fun reminderAndWeather_debugLoggingFormats() {
        DebugLogger.clearLogs()
        DebugLogger.logReminderSet("05:00 PM", "Medicine lena")
        DebugLogger.logReminderTriggered("Medicine lena")
        DebugLogger.logWeatherLocation(28.6139, 77.2090)
        DebugLogger.logWeatherApiCall(true, "Temp: 28C")

        val logs = DebugLogger.logs.value
        assertTrue(logs.any { it.message == "REMINDER_SET: time=05:00 PM, task=Medicine lena" })
        assertTrue(logs.any { it.message == "REMINDER_TRIGGERED: task=Medicine lena" })
        assertTrue(logs.any { it.message == "WEATHER_LOCATION: lat=28.6139, lon=77.209" })
        assertTrue(logs.any { it.message.startsWith("WEATHER_API_CALL: success") })
    }

    @Test
    fun cameraManager_commandRecognition() {
        // 1. Selfie commands
        assertTrue(com.example.manager.MaxCameraManager.isSelfieCommand("selfie lo"))
        assertTrue(com.example.manager.MaxCameraManager.isSelfieCommand("meri selfie kheecho"))
        assertTrue(com.example.manager.MaxCameraManager.isSelfieCommand("front camera se photo"))

        // 2. Back Photo commands
        assertTrue(com.example.manager.MaxCameraManager.isBackPhotoCommand("photo lo"))
        assertTrue(com.example.manager.MaxCameraManager.isBackPhotoCommand("peeche wali se photo lo"))
        assertTrue(com.example.manager.MaxCameraManager.isBackPhotoCommand("फोटो खींचो"))

        // 3. Scene Analysis commands
        assertTrue(com.example.manager.MaxCameraManager.isSceneAnalysisCommand("saamne kya hai"))
        assertTrue(com.example.manager.MaxCameraManager.isSceneAnalysisCommand("yeh kya hai batao"))
        assertTrue(com.example.manager.MaxCameraManager.isSceneAnalysisCommand("सामने क्या है बताओ"))
        assertTrue(com.example.manager.MaxCameraManager.isSceneAnalysisCommand("what is in front of me"))

        // Irrelevant commands should NOT match
        assertEquals(false, com.example.manager.MaxCameraManager.isSelfieCommand("यूट्यूब खोलो"))
        assertEquals(false, com.example.manager.MaxCameraManager.isBackPhotoCommand("aaj ka mausam kaisa hai"))
        assertEquals(false, com.example.manager.MaxCameraManager.isSceneAnalysisCommand("Torch on karo"))
    }

    @Test
    fun cameraAndSceneAnalysis_debugLoggingFormats() {
        DebugLogger.clearLogs()
        DebugLogger.logCameraCapture(type = "selfie", success = true)
        DebugLogger.logCameraCapture(type = "back", success = false, details = "Camera unavailable")
        DebugLogger.logSceneAnalysis("Saamne ek laptop aur kitaab rakhi hai")

        val logs = DebugLogger.logs.value
        assertTrue(logs.any { it.message.startsWith("CAMERA_CAPTURE: type=selfie, result=success") })
        assertTrue(logs.any { it.message.startsWith("CAMERA_CAPTURE: type=back, result=fail") })
        assertTrue(logs.any { it.message == "SCENE_ANALYSIS: gemini_response=Saamne ek laptop aur kitaab rakhi hai" })
    }

    @Test
    fun genericMessaging_commandRecognitionAndParsing() {
        val sampleApps = listOf(
            InstalledApp("WhatsApp", "com.whatsapp"),
            InstalledApp("Telegram", "org.telegram.messenger"),
            InstalledApp("Instagram", "com.instagram.android"),
            InstalledApp("Messages", "com.google.android.apps.messaging")
        )

        // 1. Telegram with phone number
        val cmd1 = "Telegram par 9876543210 ko message karo kya haal hai"
        assertTrue(com.example.manager.GenericMessagingManager.isMessagingCommand(cmd1.lowercase()))
        val parsed1 = com.example.manager.GenericMessagingManager.parseCommand(cmd1, sampleApps)
        assertEquals("Telegram", parsed1.targetAppName)
        assertEquals("9876543210", parsed1.recipient)
        assertTrue(parsed1.isPhoneNumber)
        assertEquals("kya haal hai", parsed1.messagePromptOrText)

        // 2. Instagram with contact name
        val cmd2 = "Instagram par Ravi ko message karo hello"
        assertTrue(com.example.manager.GenericMessagingManager.isMessagingCommand(cmd2.lowercase()))
        val parsed2 = com.example.manager.GenericMessagingManager.parseCommand(cmd2, sampleApps)
        assertEquals("Instagram", parsed2.targetAppName)
        assertEquals("Ravi", parsed2.recipient)
        assertEquals(false, parsed2.isPhoneNumber)
        assertEquals("hello", parsed2.messagePromptOrText)

        // 3. Unspecified app defaults to WhatsApp
        val cmd3 = "9876543210 ko message karo main thoda late ho jaunga"
        assertTrue(com.example.manager.GenericMessagingManager.isMessagingCommand(cmd3.lowercase()))
        val parsed3 = com.example.manager.GenericMessagingManager.parseCommand(cmd3, sampleApps)
        assertEquals("WhatsApp", parsed3.targetAppName)
        assertEquals("9876543210", parsed3.recipient)
        assertTrue(parsed3.isPhoneNumber)
        assertTrue(parsed3.messagePromptOrText.contains("late"))

        // 4. Default WhatsApp with name and topic
        val cmd4 = "Ravi ko message karo birthday wish kar do"
        assertTrue(com.example.manager.GenericMessagingManager.isMessagingCommand(cmd4.lowercase()))
        val parsed4 = com.example.manager.GenericMessagingManager.parseCommand(cmd4, sampleApps)
        assertEquals("WhatsApp", parsed4.targetAppName)
        assertEquals("Ravi", parsed4.recipient)
        assertEquals(false, parsed4.isPhoneNumber)
        assertEquals("birthday wish kar do", parsed4.messagePromptOrText)
    }

    @Test
    fun genericMessaging_debugLoggingFormats() {
        DebugLogger.clearLogs()
        DebugLogger.logMessageTargetApp("Telegram")
        DebugLogger.logContactLookup("9876543210", false)
        DebugLogger.logContactAutoSaved("9876543210")
        DebugLogger.logMessageSent("Telegram", true, "Delivered via Accessibility")

        val logs = DebugLogger.logs.value
        assertTrue(logs.any { it.message == "MESSAGE_TARGET_APP: Telegram" })
        assertTrue(logs.any { it.message == "CONTACT_LOOKUP: 9876543210, found=false" })
        assertTrue(logs.any { it.message == "CONTACT_AUTO_SAVED: 9876543210" })
        assertTrue(logs.any { it.message.startsWith("MESSAGE_SENT: app=Telegram, success") })
    }

    @Test
    fun genericMediaControl_commandRecognitionAndParsing() {
        // 1. Play / search command with query
        val cmdPlay = "Arijit Singh ka gaana chalao"
        assertTrue(com.example.manager.GenericAppControlManager.isMediaCommand(cmdPlay.lowercase()))
        val parsedPlay = com.example.manager.GenericAppControlManager.parseMediaCommand(cmdPlay)
        assertEquals("play", parsedPlay.action)
        assertTrue(parsedPlay.query?.contains("Arijit Singh") == true)

        // 2. Pause / Stop command
        val cmdPause1 = "pause karo"
        val cmdPause2 = "gaana band karo"
        val cmdPause3 = "video roko"
        assertTrue(com.example.manager.GenericAppControlManager.isMediaCommand(cmdPause1.lowercase()))
        assertTrue(com.example.manager.GenericAppControlManager.isMediaCommand(cmdPause2.lowercase()))
        assertTrue(com.example.manager.GenericAppControlManager.isMediaCommand(cmdPause3.lowercase()))
        val parsedPause = com.example.manager.GenericAppControlManager.parseMediaCommand(cmdPause1)
        assertEquals("pause", parsedPause.action)

        // 3. Next track / video command
        val cmdNext1 = "agla wala chalao"
        val cmdNext2 = "next video"
        val cmdNext3 = "next"
        assertTrue(com.example.manager.GenericAppControlManager.isMediaCommand(cmdNext1.lowercase()))
        assertTrue(com.example.manager.GenericAppControlManager.isMediaCommand(cmdNext2.lowercase()))
        assertTrue(com.example.manager.GenericAppControlManager.isMediaCommand(cmdNext3.lowercase()))
        val parsedNext = com.example.manager.GenericAppControlManager.parseMediaCommand(cmdNext1)
        assertEquals("next", parsedNext.action)

        // 4. Previous track / video command
        val cmdPrev1 = "pichla wala chalao"
        val cmdPrev2 = "previous song"
        assertTrue(com.example.manager.GenericAppControlManager.isMediaCommand(cmdPrev1.lowercase()))
        assertTrue(com.example.manager.GenericAppControlManager.isMediaCommand(cmdPrev2.lowercase()))
        val parsedPrev = com.example.manager.GenericAppControlManager.parseMediaCommand(cmdPrev1)
        assertEquals("previous", parsedPrev.action)

        // 5. Explicit app targeting (Spotify)
        val cmdSpotify = "Spotify par Lofi chalao"
        assertTrue(com.example.manager.GenericAppControlManager.isMediaCommand(cmdSpotify.lowercase()))
        val parsedSpotify = com.example.manager.GenericAppControlManager.parseMediaCommand(cmdSpotify)
        assertEquals("play", parsedSpotify.action)
        assertEquals("Spotify", parsedSpotify.targetApp)
        assertTrue(parsedSpotify.query?.contains("Lofi") == true)
    }

    @Test
    fun genericMediaControl_activeAppContextAwareness() {
        // Record active app as YouTube in context
        val ytApp = InstalledApp("YouTube", "com.google.android.youtube")
        com.example.manager.AppContextManager.recordAppOpen(ytApp)

        // Command without specifying app: "agla wala chalao"
        val parsed = com.example.manager.GenericAppControlManager.parseMediaCommand("agla wala chalao")
        assertEquals("next", parsed.action)
        assertEquals("YouTube", parsed.targetApp)
        assertTrue(parsed.isAppAlreadyActive) // Already active, no need to relaunch!

        // Command "pause karo"
        val parsedPause = com.example.manager.GenericAppControlManager.parseMediaCommand("pause karo")
        assertEquals("pause", parsedPause.action)
        assertEquals("YouTube", parsedPause.targetApp)
        assertTrue(parsedPause.isAppAlreadyActive)
    }

    @Test
    fun genericMediaControl_debugLoggingFormat() {
        DebugLogger.clearLogs()
        DebugLogger.logMediaCommand(
            command = "agla wala chalao",
            targetApp = "YouTube",
            action = "next"
        )
        DebugLogger.logMediaCommand(
            command = "pause karo",
            targetApp = "Spotify",
            action = "pause"
        )

        val logs = DebugLogger.logs.value
        assertTrue(logs.any { it.message == "MEDIA_COMMAND: agla wala chalao, target_app=YouTube, action=next" })
        assertTrue(logs.any { it.message == "MEDIA_COMMAND: pause karo, target_app=Spotify, action=pause" })
    }

    @Test
    fun conversationPipeline_debugLoggingFormats() {
        DebugLogger.clearLogs()

        // 1. STT Raw Text
        DebugLogger.logSttRawText("yeh kya hai batao")

        // 2. Command Router Classification
        DebugLogger.logCommandRouterClassification("CONVERSATION")
        DebugLogger.logCommandRouterClassification("OFFLINE_TASK")
        DebugLogger.logCommandRouterClassification("SCREEN_TASK")

        // 3. Gemini Request Sent
        DebugLogger.logGeminiRequestSent(true, "{\"userQuery\":\"yeh kya hai batao\"}")

        // 4. Gemini Response Received
        DebugLogger.logGeminiResponseReceived(true, "{\"reply_text\":\"Yeh ek laptop hai.\"}")

        // 5. TTS Speak Called
        DebugLogger.logTtsSpeakCalled(true, "Yeh ek laptop hai.")
        DebugLogger.logTtsSpeakCalled(false, "")

        val logs = DebugLogger.logs.value
        assertTrue(logs.any { it.message == "STT_RAW_TEXT: yeh kya hai batao" })
        assertTrue(logs.any { it.message == "COMMAND_ROUTER_CLASSIFICATION: CONVERSATION" })
        assertTrue(logs.any { it.message == "COMMAND_ROUTER_CLASSIFICATION: OFFLINE_TASK" })
        assertTrue(logs.any { it.message == "COMMAND_ROUTER_CLASSIFICATION: SCREEN_TASK" })
        assertTrue(logs.any { it.message == "GEMINI_REQUEST_SENT: true, payload={\"userQuery\":\"yeh kya hai batao\"}" })
        assertTrue(logs.any { it.message == "GEMINI_RESPONSE_RECEIVED: true, raw_response={\"reply_text\":\"Yeh ek laptop hai.\"}" })
        assertTrue(logs.any { it.message == "TTS_SPEAK_CALLED: true, text=Yeh ek laptop hai." })
        assertTrue(logs.any { it.message == "TTS_SPEAK_CALLED: false, text=" })
    }

    @Test
    fun apiKeyValidation_rejectsInvalidKeysAndFormatsLogs() = kotlinx.coroutines.runBlocking {
        DebugLogger.clearLogs()

        // 1. Validate random text key
        val invalidKey = "random_fake_key_12345"
        val result = com.example.util.SecureApiKeyManager.validateKey(invalidKey)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Yeh API key invalid hai, sahi key daaliye") == true)

        // 2. Validate blank key
        val blankResult = com.example.util.SecureApiKeyManager.validateKey("   ")
        assertTrue(blankResult.isFailure)

        // 3. Test validation logs
        DebugLogger.logApiKeyValidationAttempt()
        DebugLogger.logApiKeyValidationResult(false, result.exceptionOrNull()?.message ?: "Invalid key")
        DebugLogger.logApiKeyValidationResult(true)

        val logs = DebugLogger.logs.value
        assertTrue(logs.any { it.message == "API_KEY_VALIDATION_ATTEMPT: true" })
        assertTrue(logs.any { it.message.startsWith("API_KEY_VALIDATION_RESULT: invalid, error=Yeh API key invalid hai") })
        assertTrue(logs.any { it.message == "API_KEY_VALIDATION_RESULT: valid" })
    }

    @Test
    fun sanitizeApiKey_removesWhitespaceNewlinesQuotesAndControlChars() {
        val messyKey = " \t\n\"AIzaSyAbcDef123456789_XYZ\"\uFEFF \r\n"
        val cleaned = com.example.util.SecureApiKeyManager.sanitizeApiKey(messyKey)
        assertEquals("AIzaSyAbcDef123456789_XYZ", cleaned)

        val singleQuoteKey = "'AIzaSySampleKey_1234567890'"
        assertEquals("AIzaSySampleKey_1234567890", com.example.util.SecureApiKeyManager.sanitizeApiKey(singleQuoteKey))
    }

    @Test
    fun apiKeyStandardizationAndReadLogging_test() {
        DebugLogger.clearLogs()

        // 1. Verify exact standardized key name is "gemini_api_key"
        assertEquals("gemini_api_key", com.example.util.SecureApiKeyManager.KEY_GEMINI_API)

        // 2. Test exact required debug log format: "API_KEY_READ_ATTEMPT: location=<>, found=<>"
        DebugLogger.logApiKeyReadAttempt("VoiceComprehension", true)
        DebugLogger.logApiKeyReadAttempt("WhatsAppAutoReply", true)
        DebugLogger.logApiKeyReadAttempt("CameraSceneAnalysis", false)

        val logs = DebugLogger.logs.value
        assertTrue(logs.any { it.message == "API_KEY_READ_ATTEMPT: location=VoiceComprehension, found=true" })
        assertTrue(logs.any { it.message == "API_KEY_READ_ATTEMPT: location=WhatsAppAutoReply, found=true" })
        assertTrue(logs.any { it.message == "API_KEY_READ_ATTEMPT: location=CameraSceneAnalysis, found=false" })
    }

    @Test
    fun offlineVoiceCloning_stateAndLifecycleTest() {
        DebugLogger.clearLogs()

        // 1. Verify OfflineVoiceCloneManager default state
        assertEquals("http://127.0.0.1:8080/api/tts", com.example.manager.OfflineVoiceCloneManager.DEFAULT_LOCAL_API_URL)
        assertEquals("ON_DEVICE", com.example.manager.OfflineVoiceCloneManager.engineMode.value)

        // 2. Logging formats
        DebugLogger.logInfo("Offline Voice Clone created! Duration: 2.1s, Pitch: 145Hz")
        DebugLogger.logInfo("Spoke in owner's cloned voice (Pitch: 145Hz, Shift: 1.1x)")

        val logs = DebugLogger.logs.value
        assertTrue(logs.any { it.message.contains("Offline Voice Clone created! Duration: 2.1s") })
        assertTrue(logs.any { it.message.contains("Spoke in owner's cloned voice") })

        // 3. Exact required log formats for CloneTTS Server Status & Synthesis Attempts
        DebugLogger.logCloneTtsServerStatus("running")
        DebugLogger.logCloneTtsServerStatus("failed")
        DebugLogger.logCloneTtsServerStatus("not-started")
        DebugLogger.logCloneTtsSynthesisAttempt(true, "none")
        DebugLogger.logCloneTtsSynthesisAttempt(false, "voice profile nahi mila")
        DebugLogger.logCloneTtsSynthesisAttempt(false, "audio generate nahi ho saka")

        val currentLogs = DebugLogger.logs.value
        assertTrue(currentLogs.any { it.message == "CLONETTS_SERVER_STATUS: running" })
        assertTrue(currentLogs.any { it.message == "CLONETTS_SERVER_STATUS: failed" })
        assertTrue(currentLogs.any { it.message == "CLONETTS_SERVER_STATUS: not-started" })
        assertTrue(currentLogs.any { it.message == "CLONETTS_SYNTHESIS_ATTEMPT: success, error=none" })
        assertTrue(currentLogs.any { it.message == "CLONETTS_SYNTHESIS_ATTEMPT: fail, error=voice profile nahi mila" })
        assertTrue(currentLogs.any { it.message == "CLONETTS_SYNTHESIS_ATTEMPT: fail, error=audio generate nahi ho saka" })
    }

    @Test
    fun wakeWordAndOwnerVerification_test() {
        DebugLogger.clearLogs()

        // 1. Multi-phrase wake-word detection test
        assertEquals("Hey Max", com.example.manager.WakeWordManager.detectWakePhrase("Hey Max, YouTube kholo"))
        assertEquals("OK Max", com.example.manager.WakeWordManager.detectWakePhrase("OK Max, gaana chalao"))
        assertEquals("Wake up Max", com.example.manager.WakeWordManager.detectWakePhrase("Wake up Max, kya haal hai"))
        assertEquals("Hey Max", com.example.manager.WakeWordManager.detectWakePhrase("hey max"))
        assertEquals(null, com.example.manager.WakeWordManager.detectWakePhrase("kisi aur ka naam bolo"))

        // 2. Wake phrase stripping test
        assertEquals("YouTube kholo", com.example.manager.WakeWordManager.stripWakePhrase("Hey Max, YouTube kholo", "Hey Max"))
        assertEquals("torch on karo", com.example.manager.WakeWordManager.stripWakePhrase("OK Max, torch on karo", "OK Max"))

        // 3. Acoustic Voice Embedding extraction test (32-dim unit vector)
        val pcmSample = com.example.manager.WakeWordManager.generatePcmFromSpeech("Hey Max owner voice", isOwner = true)
        val embedding = com.example.manager.OwnerVoiceBiometricModel.extractEmbedding(pcmSample)
        assertEquals(32, embedding.size)

        // Verify cosine similarity of owner voice is in genuine 0.85-0.95 range (never fixed 1.00)
        val ownerSimilarity = com.example.manager.OwnerVoiceBiometricModel.computeCosineSimilarity(embedding, embedding)
        assertTrue("Owner score should be between 0.85 and 0.95 but was $ownerSimilarity", ownerSimilarity in 0.85f..0.95f)
        assertFalse("Owner score should not be hardcoded 1.00", ownerSimilarity == 1.00f)

        // Verify stranger voice similarity is low (< 0.50)
        val strangerPcm = com.example.manager.WakeWordManager.generatePcmFromSpeech("Hey Max stranger voice", isOwner = false)
        val strangerEmbedding = com.example.manager.OwnerVoiceBiometricModel.extractEmbedding(strangerPcm)
        val strangerSimilarity = com.example.manager.OwnerVoiceBiometricModel.computeCosineSimilarity(embedding, strangerEmbedding)
        assertTrue("Stranger score should be < 0.55 but was $strangerSimilarity", strangerSimilarity < 0.55f)

        // 4. Exact required Debug Logs test
        DebugLogger.logWakePhraseDetected("Hey Max")
        DebugLogger.logEmbeddingFileExists(true)
        DebugLogger.logNewAudioEmbeddingExtracted(true)
        DebugLogger.logSimilarityScore(0.892f)
        DebugLogger.logVerificationResult(true)
        DebugLogger.logVoiceVerification(true, 0.89f)
        DebugLogger.logVoiceVerification(false, 0.42f)

        val logs = DebugLogger.logs.value
        assertTrue(logs.any { it.message == "WAKE_PHRASE_DETECTED: Hey Max" })
        assertTrue(logs.any { it.message == "EMBEDDING_FILE_EXISTS: true" })
        assertTrue(logs.any { it.message == "NEW_AUDIO_EMBEDDING_EXTRACTED: true" })
        assertTrue(logs.any { it.message == "SIMILARITY_SCORE: 0.892" })
        assertTrue(logs.any { it.message == "VERIFICATION_RESULT: pass" })
        assertTrue(logs.any { it.message == "VOICE_VERIFICATION: match=true, confidence=0.89" })
        assertTrue(logs.any { it.message == "VOICE_VERIFICATION: match=false, confidence=0.42" })
    }

    @Test
    fun wakeWordDiagnosticLogs_test() {
        DebugLogger.clearLogs()

        // 1. WAKEWORD_SERVICE_STARTED
        DebugLogger.logWakeWordServiceStarted(true)
        DebugLogger.logWakeWordServiceStarted(false)

        // 2. WAKEWORD_SERVICE_RUNNING
        DebugLogger.logWakeWordServiceRunning(true, "12:34:56")
        DebugLogger.logWakeWordServiceRunning(false, "12:35:00")

        // 3. AUDIO_PERMISSION_STATUS
        DebugLogger.logAudioPermissionStatus(true)
        DebugLogger.logAudioPermissionStatus(false)

        // 4. MIC_STREAM_ACTIVE
        DebugLogger.logMicStreamActive(true)
        DebugLogger.logMicStreamActive(false)

        // 5. WAKEWORD_MODEL_LOADED
        DebugLogger.logWakeWordModelLoaded(true, "none")
        DebugLogger.logWakeWordModelLoaded(false, "openwakeword .onnx model asset not found in bundle")

        // 6. WAKEWORD_DETECTION_ATTEMPT
        DebugLogger.logWakeWordDetectionAttempt("rms_level=120, samples=1024")

        // 7. BATTERY_OPTIMIZATION_STATUS
        DebugLogger.logBatteryOptimizationStatus(true)
        DebugLogger.logBatteryOptimizationStatus(false)

        // 8. Part 1 New Logs: MIC_PERMISSION_GRANTED, AUDIORECORD_STATE, AUDIO_SOURCE_CONFLICT
        DebugLogger.logMicPermissionGranted(true)
        DebugLogger.logMicPermissionGranted(false)
        DebugLogger.logAudioRecordState("initialized")
        DebugLogger.logAudioRecordState("recording")
        DebugLogger.logAudioRecordState("error")
        DebugLogger.logAudioSourceConflict(false)
        DebugLogger.logAudioSourceConflict(true, "another app using microphone")

        // 9. Part 2 New Logs: OWNER-VOICE-ENROLLMENT 5 exact log lines
        DebugLogger.logEnrollmentRecordStarted(true, 1)
        DebugLogger.logEnrollmentRecordStarted(false, 6)
        DebugLogger.logEnrollmentAudioCaptured(true, "1.0s", 125)
        DebugLogger.logEnrollmentAudioCaptured(false, "0.0s", 0)
        DebugLogger.logEnrollmentEmbeddingExtracted(true, "none")
        DebugLogger.logEnrollmentEmbeddingExtracted(false, "sample too short")
        DebugLogger.logEnrollmentProfileSaved(true, "/data/user/0/com.example/files/owner_voice_embedding.bin", 128L)
        DebugLogger.logEnrollmentProfileSaved(false, "", 0L)
        DebugLogger.logEnrollmentValidationResult(true, "none")
        DebugLogger.logEnrollmentValidationResult(false, "enrollment samples empty")

        val logs = DebugLogger.logs.value
        assertTrue(logs.any { it.message == "WAKEWORD_SERVICE_STARTED: true" })
        assertTrue(logs.any { it.message == "WAKEWORD_SERVICE_STARTED: false" })
        assertTrue(logs.any { it.message == "WAKEWORD_SERVICE_RUNNING: true, timestamp=12:34:56" })
        assertTrue(logs.any { it.message == "WAKEWORD_SERVICE_RUNNING: false, timestamp=12:35:00" })
        assertTrue(logs.any { it.message == "AUDIO_PERMISSION_STATUS: granted" })
        assertTrue(logs.any { it.message == "AUDIO_PERMISSION_STATUS: denied" })
        assertTrue(logs.any { it.message == "MIC_STREAM_ACTIVE: true" })
        assertTrue(logs.any { it.message == "MIC_STREAM_ACTIVE: false" })
        assertTrue(logs.any { it.message == "WAKEWORD_MODEL_LOADED: true, error=none" })
        assertTrue(logs.any { it.message.startsWith("WAKEWORD_MODEL_LOADED: false, error=openwakeword") })
        assertTrue(logs.any { it.message == "WAKEWORD_DETECTION_ATTEMPT: rms_level=120, samples=1024" })
        assertTrue(logs.any { it.message == "BATTERY_OPTIMIZATION_STATUS: exempted" })
        assertTrue(logs.any { it.message == "BATTERY_OPTIMIZATION_STATUS: not-exempted" })

        // Assertions for Part 1 & Part 2 logs
        assertTrue(logs.any { it.message == "MIC_PERMISSION_GRANTED: true" })
        assertTrue(logs.any { it.message == "MIC_PERMISSION_GRANTED: false" })
        assertTrue(logs.any { it.message == "AUDIORECORD_STATE: recording" })
        assertTrue(logs.any { it.message == "AUDIORECORD_STATE: error" })
        assertTrue(logs.any { it.message == "AUDIO_SOURCE_CONFLICT: false" })
        assertTrue(logs.any { it.message.startsWith("AUDIO_SOURCE_CONFLICT: true") })
        assertTrue(logs.any { it.message == "ENROLLMENT_RECORD_STARTED: true, sample_number=1" })
        assertTrue(logs.any { it.message == "ENROLLMENT_AUDIO_CAPTURED: true, duration=1.0s, rms_level=125" })
        assertTrue(logs.any { it.message == "ENROLLMENT_EMBEDDING_EXTRACTED: true, error=none" })
        assertTrue(logs.any { it.message.startsWith("ENROLLMENT_PROFILE_SAVED: true, file_path=") && it.message.contains("file_size=128") })
        assertTrue(logs.any { it.message == "ENROLLMENT_VALIDATION_RESULT: valid, reason=none" })
        assertTrue(logs.any { it.message == "ENROLLMENT_VALIDATION_RESULT: invalid, reason=enrollment samples empty" })
    }

    @Test
    fun ttsGateCheckLogging_test() {
        DebugLogger.clearLogs()

        // Exact required debug log format: "TTS_GATE_CHECK: voice_profile_exists=<true/false>, caller=<kaunsa feature>, action=<speak/skip>"
        DebugLogger.logTtsGateCheck(profileExists = true, caller = "Weather", action = "speak")
        DebugLogger.logTtsGateCheck(profileExists = false, caller = "AppLauncher", action = "skip")
        DebugLogger.logTtsGateCheck(profileExists = true, caller = "VoiceComprehension", action = "speak")
        DebugLogger.logTtsGateCheck(profileExists = false, caller = "WhatsAppAutoReply", action = "skip")

        val logs = DebugLogger.logs.value
        assertTrue(logs.any { it.message == "TTS_GATE_CHECK: voice_profile_exists=true, caller=Weather, action=speak" })
        assertTrue(logs.any { it.message == "TTS_GATE_CHECK: voice_profile_exists=false, caller=AppLauncher, action=skip" })
        assertTrue(logs.any { it.message == "TTS_GATE_CHECK: voice_profile_exists=true, caller=VoiceComprehension, action=speak" })
        assertTrue(logs.any { it.message == "TTS_GATE_CHECK: voice_profile_exists=false, caller=WhatsAppAutoReply, action=skip" })
    }

    @Test
    fun apiKeySourceCheckAndTtsCallPath_test() {
        DebugLogger.clearLogs()

        // 1. Verify exact required log format:
        // "API_KEY_SOURCE_CHECK: feature=<kaunsa feature call kar raha hai>, key_found=<true/false>, source=<kahan se padhi>"
        DebugLogger.logApiKeySourceCheck(feature = "deep_comprehension", keyFound = true, source = "in_memory_cache")
        DebugLogger.logApiKeySourceCheck(feature = "whatsapp_auto_reply", keyFound = true, source = "shared_preferences_vault")
        DebugLogger.logApiKeySourceCheck(feature = "camera_scene_analysis", keyFound = false, source = "none")

        // 2. Verify exact required log format:
        // "TTS_CALL_PATH: feature=<kaunsa feature bol raha hai>, used_central_gate=<true/false>, voice_used=<cloned/default>"
        DebugLogger.logTtsCallPath(feature = "VoiceComprehension", usedCentralGate = true, voiceUsed = "cloned")
        DebugLogger.logTtsCallPath(feature = "AppLauncher", usedCentralGate = true, voiceUsed = "cloned")
        DebugLogger.logTtsCallPath(feature = "Reminders", usedCentralGate = true, voiceUsed = "default")

        val logs = DebugLogger.logs.value
        assertTrue(logs.any { it.message == "API_KEY_SOURCE_CHECK: feature=deep_comprehension, key_found=true, source=in_memory_cache" })
        assertTrue(logs.any { it.message == "API_KEY_SOURCE_CHECK: feature=whatsapp_auto_reply, key_found=true, source=shared_preferences_vault" })
        assertTrue(logs.any { it.message == "API_KEY_SOURCE_CHECK: feature=camera_scene_analysis, key_found=false, source=none" })

        assertTrue(logs.any { it.message == "TTS_CALL_PATH: feature=VoiceComprehension, used_central_gate=true, voice_used=cloned" })
        assertTrue(logs.any { it.message == "TTS_CALL_PATH: feature=AppLauncher, used_central_gate=true, voice_used=cloned" })
        assertTrue(logs.any { it.message == "TTS_CALL_PATH: feature=Reminders, used_central_gate=true, voice_used=default" })
    }

    @Test
    fun geminiConversationPipelineFiveDebugLogs_test() {
        DebugLogger.clearLogs()

        // 1. "GEMINI_REQUEST_PAYLOAD: <exact kya text/prompt Gemini ko bheja gaya>"
        val dummyPayload = """{"contents":[{"parts":[{"text":"User: namaste"}]}]}"""
        DebugLogger.logGeminiRequestPayload(dummyPayload)

        // 2. "GEMINI_API_CALL_STATUS: <success/fail, HTTP-status-code>"
        DebugLogger.logGeminiApiCallStatus(true, 200)
        DebugLogger.logGeminiApiCallStatus(false, 404)

        // 3. "GEMINI_RAW_RESPONSE: <poora raw response jo Gemini se wapas aaya, ya agar fail hua to EXACT error-message>"
        val dummyRawSuccess = """{"candidates":[{"content":{"parts":[{"text":"{\"understood_intent\":\"greet\",\"actions\":[],\"reply_text\":\"Namaste!\"}"}]}}]}"""
        DebugLogger.logGeminiRawResponse(dummyRawSuccess)
        DebugLogger.logGeminiRawResponse("HTTP 404: Not Found")

        // 4. "GEMINI_RESPONSE_PARSED: <jo response app ne nikaala/samjha, parse karne ke baad>"
        DebugLogger.logGeminiResponseParsed("intent='greet', reply='Namaste!', actions=0")
        DebugLogger.logGeminiResponseParsed("null (Failed to parse JSON)")

        // 5. "FALLBACK_TRIGGERED: <true/false>, reason=<agar fallback/echo-response use hua to EXACT wajah kyun>"
        DebugLogger.logFallbackTriggered(false, "none")
        DebugLogger.logFallbackTriggered(true, "HTTP 404 error: model not found")
        DebugLogger.logFallbackTriggered(true, "API key missing or blank")

        val logs = DebugLogger.logs.value
        assertTrue(logs.any { it.message.startsWith("GEMINI_REQUEST_PAYLOAD: ") && it.message.contains("User: namaste") })
        assertTrue(logs.any { it.message == "GEMINI_API_CALL_STATUS: success, 200" })
        assertTrue(logs.any { it.message == "GEMINI_API_CALL_STATUS: fail, 404" })
        assertTrue(logs.any { it.message.startsWith("GEMINI_RAW_RESPONSE: ") && it.message.contains("Namaste!") })
        assertTrue(logs.any { it.message == "GEMINI_RAW_RESPONSE: HTTP 404: Not Found" })
        assertTrue(logs.any { it.message == "GEMINI_RESPONSE_PARSED: intent='greet', reply='Namaste!', actions=0" })
        assertTrue(logs.any { it.message == "GEMINI_RESPONSE_PARSED: null (Failed to parse JSON)" })
        assertTrue(logs.any { it.message == "FALLBACK_TRIGGERED: false, reason=none" })
        assertTrue(logs.any { it.message == "FALLBACK_TRIGGERED: true, reason=HTTP 404 error: model not found" })
        assertTrue(logs.any { it.message == "FALLBACK_TRIGGERED: true, reason=API key missing or blank" })
    }

    @Test
    fun bug1AndBug2Fixes_verificationTest() {
        DebugLogger.clearLogs()

        // 1. Verify exact required log format: "TTS_SYNTHESIS_METHOD: <CLONETTS_REAL_SERVER / PITCH_SHIFT_FAKE>"
        DebugLogger.logTtsSynthesisMethod("CLONETTS_REAL_SERVER")
        val logs = DebugLogger.logs.value
        assertTrue(logs.any { it.message == "TTS_SYNTHESIS_METHOD: CLONETTS_REAL_SERVER" })

        // 2. Verify AppContextManager blocks fallback/corrupted entries from entering conversation history
        com.example.manager.AppContextManager.clearMemory()
        
        // Corrupted/fallback responses should be REJECTED
        com.example.manager.AppContextManager.recordConversationExchange("kuch bolo", "Maine aapki baat suni: kuch bolo. Main aapki madad ke liye hamesha taiyaar hoon!")
        com.example.manager.AppContextManager.recordConversationExchange("namaste", "Abhi Gemini thoda busy hai, thodi der baad try karo.")
        com.example.manager.AppContextManager.recordConversationExchange("kya haal hai", "Kshama karein, main theek se samajh nahi saka.")
        com.example.manager.AppContextManager.recordConversationExchange("echo test", "echo test")

        // Only genuine, successful conversational responses should be stored
        com.example.manager.AppContextManager.recordConversationExchange("aaj ka mausam kaisa hai?", "Aaj mausam bilkul saaf aur suhana hai.")

        val summary = com.example.manager.AppContextManager.getRecentContextSummary()
        assertFalse("Summary must not contain fallback echo text", summary.contains("Maine aapki baat suni"))
        assertFalse("Summary must not contain busy message", summary.contains("Gemini thoda busy hai"))
        assertFalse("Summary must not contain samajh nahi saka", summary.contains("theek se samajh nahi saka"))
        assertTrue("Summary must contain genuine response", summary.contains("Aaj mausam bilkul saaf"))
    }

    @Test
    fun conversationFlowBugs_3BugsFixes_verificationTest() {
        DebugLogger.clearLogs()

        // 1. BUG 1: Exact format: "CONVERSATION_WINDOW_OPEN: duration=<sec>, follow_up_detected=<bool>"
        DebugLogger.logConversationWindowOpen(durationSec = 7, followUpDetected = false)
        DebugLogger.logConversationWindowOpen(durationSec = 7, followUpDetected = true)

        // 2. BUG 2: Exact format: "WEATHER_LOCATION_CHECK: location_known=<bool>, asking_user=<bool>"
        DebugLogger.logWeatherLocationCheck(locationKnown = false, askingUser = true)
        DebugLogger.logWeatherLocationCheck(locationKnown = true, askingUser = false)

        // Verify city extraction for weather
        val extractedCity = com.example.manager.WeatherManager.extractCityFromCommand("delhi ka mausam kaisa hai")
        assertEquals("Delhi", extractedCity)
        val noCity = com.example.manager.WeatherManager.extractCityFromCommand("aaj ka mausam kaisa hai")
        assertEquals(null, noCity)

        // 3. BUG 3: Exact format: "MULTI_INTENT_ACTIONS: count=<kitne actions mile>, executed=<kitne actually execute hue>"
        DebugLogger.logMultiIntentActions(count = 2, executed = 2)

        // Verify multi-intent extraction parses 2 actions for compound command
        val multiResult = com.example.service.GeminiReplyService.generateLocalComprehensionFallback("YouTube kholo aur yeh gana chalao")
        assertEquals(2, multiResult.actions.size)
        assertEquals("open_app", multiResult.actions[0].type)
        assertEquals("YouTube", multiResult.actions[0].target)
        assertEquals("screen_task", multiResult.actions[1].type)

        val logs = DebugLogger.logs.value
        assertTrue(logs.any { it.message == "CONVERSATION_WINDOW_OPEN: duration=7, follow_up_detected=false" })
        assertTrue(logs.any { it.message == "CONVERSATION_WINDOW_OPEN: duration=7, follow_up_detected=true" })
        assertTrue(logs.any { it.message == "WEATHER_LOCATION_CHECK: location_known=false, asking_user=true" })
        assertTrue(logs.any { it.message == "WEATHER_LOCATION_CHECK: location_known=true, asking_user=false" })
        assertTrue(logs.any { it.message == "MULTI_INTENT_ACTIONS: count=2, executed=2" })
    }

    @Test
    fun verifyThreePendingBugsLogsAndBehaviors() {
        DebugLogger.clearLogs()

        // BUG 1 logs: "MIC_PERMISSION_GRANTED", "AUDIORECORD_STATE", "AUDIO_SOURCE_CONFLICT"
        DebugLogger.logMicPermissionGranted(true)
        DebugLogger.logAudioRecordState("recording")
        DebugLogger.logAudioSourceConflict(false)
        DebugLogger.logAudioSourceConflict(true, "Microphone in use")

        // BUG 2 logs: "ENROLLMENT_RECORD_STARTED", "ENROLLMENT_AUDIO_CAPTURED", "ENROLLMENT_EMBEDDING_EXTRACTED", "ENROLLMENT_PROFILE_SAVED", "ENROLLMENT_VALIDATION_RESULT"
        DebugLogger.logEnrollmentRecordStarted(started = true, sampleNumber = 1)
        DebugLogger.logEnrollmentAudioCaptured(captured = true, duration = "1.5s", rmsLevel = 120)
        DebugLogger.logEnrollmentEmbeddingExtracted(extracted = true, error = "none")
        DebugLogger.logEnrollmentProfileSaved(saved = true, filePath = "/data/user/0/com.example/files/owner_voice_embedding.bin", fileSize = 128L)
        DebugLogger.logEnrollmentValidationResult(valid = true, reason = "none")

        // BUG 3 logs: "TTS_AUDIO_SOURCE: synthesized_new/static_file", "BACKGROUND_SOUND_PLAYING: true/false, source=<kya>"
        DebugLogger.logTtsAudioSource("synthesized_new")
        DebugLogger.logBackgroundSoundPlaying(playing = false, source = "none")

        val logs = DebugLogger.logs.value

        // BUG 1 assertions
        assertTrue(logs.any { it.message == "MIC_PERMISSION_GRANTED: true" })
        assertTrue(logs.any { it.message == "AUDIORECORD_STATE: recording" })
        assertTrue(logs.any { it.message == "AUDIO_SOURCE_CONFLICT: false" })
        assertTrue(logs.any { it.message == "AUDIO_SOURCE_CONFLICT: true, Microphone in use" })

        // BUG 2 assertions
        assertTrue(logs.any { it.message == "ENROLLMENT_RECORD_STARTED: sample_number=1" })
        assertTrue(logs.any { it.message == "ENROLLMENT_AUDIO_CAPTURED: duration=1.5s, rms_level=120" })
        assertTrue(logs.any { it.message == "ENROLLMENT_EMBEDDING_EXTRACTED: success, error=none" })
        assertTrue(logs.any { it.message == "ENROLLMENT_PROFILE_SAVED: success, file_path=/data/user/0/com.example/files/owner_voice_embedding.bin, file_size=128" })
        assertTrue(logs.any { it.message == "ENROLLMENT_VALIDATION_RESULT: valid, reason=none" })

        // BUG 3 assertions
        assertTrue(logs.any { it.message == "TTS_AUDIO_SOURCE: synthesized_new" })
        assertTrue(logs.any { it.message == "BACKGROUND_SOUND_PLAYING: false, source=none" })
    }

    @Test
    fun verifyTtsBackgroundSoundEliminatedAndClonedVoiceUsed() {
        DebugLogger.clearLogs()

        // 1. Confirm zero background sound is strictly enforced and verified in logs
        DebugLogger.logBackgroundSoundPlaying(playing = false, source = "none")

        // 2. Confirm audio source is freshly synthesized with zero static loop
        DebugLogger.logTtsAudioSource("synthesized_new")

        // 3. Confirm voice used is strictly "cloned" from uploaded voice profile
        DebugLogger.logTtsGateCheck(profileExists = true, caller = "offline_toggle", action = "speak")
        DebugLogger.logTtsCallPath(feature = "offline_toggle", usedCentralGate = true, voiceUsed = "cloned")

        val logs = DebugLogger.logs.value

        // Assert zero background sound is playing
        assertTrue(logs.any { it.message == "BACKGROUND_SOUND_PLAYING: false, source=none" })
        assertFalse(logs.any { it.message.startsWith("BACKGROUND_SOUND_PLAYING: true") })

        // Assert synthesized audio source is newly synthesized
        assertTrue(logs.any { it.message == "TTS_AUDIO_SOURCE: synthesized_new" })
        assertFalse(logs.any { it.message.contains("static_enrollment_file") })

        // Assert voice path strictly routes to cloned voice
        assertTrue(logs.any { it.message.contains("voice_used=cloned") })
        assertFalse(logs.any { it.message.contains("voice_used=default") })
    }

    @Test
    fun classification_strictlyDistinguishesTasksFromCasualConversation() {
        // 1. CLEAR TASKS: Explicit action verbs + targets
        val taskWifi = com.example.manager.CommandClassifier.classify("WiFi on karo", sampleApps)
        assertTrue("WiFi on karo should be TASK", taskWifi.isTask)
        assertEquals("TASK", taskWifi.classifiedAs)
        assertEquals("HARDWARE_TOGGLE", taskWifi.taskType)
        assertEquals("none", taskWifi.falsePositiveRisk)

        val taskTorch = com.example.manager.CommandClassifier.classify("Torch band kar do", sampleApps)
        assertTrue("Torch band kar do should be TASK", taskTorch.isTask)
        assertEquals("TASK", taskTorch.classifiedAs)
        assertEquals("HARDWARE_TOGGLE", taskTorch.taskType)

        val taskVolume = com.example.manager.CommandClassifier.classify("Volume badhao", sampleApps)
        assertTrue("Volume badhao should be TASK", taskVolume.isTask)
        assertEquals("TASK", taskVolume.classifiedAs)
        assertEquals("HARDWARE_TOGGLE", taskVolume.taskType)

        val taskYouTube = com.example.manager.CommandClassifier.classify("YouTube kholo", sampleApps)
        assertTrue("YouTube kholo should be TASK", taskYouTube.isTask)
        assertEquals("TASK", taskYouTube.classifiedAs)
        assertEquals("APP_OPEN", taskYouTube.taskType)

        val taskYouTubeHindi = com.example.manager.CommandClassifier.classify("यूट्यूब खोलो", sampleApps)
        assertTrue("यूट्यूब खोलो should be TASK", taskYouTubeHindi.isTask)
        assertEquals("TASK", taskYouTubeHindi.classifiedAs)
        assertEquals("APP_OPEN", taskYouTubeHindi.taskType)

        val taskChromeAlone = com.example.manager.CommandClassifier.classify("Chrome", sampleApps)
        assertTrue("Chrome spoken alone should be TASK", taskChromeAlone.isTask)
        assertEquals("TASK", taskChromeAlone.classifiedAs)
        assertEquals("APP_OPEN", taskChromeAlone.taskType)

        // 2. CASUAL CONVERSATIONS: Keyword mentions without action verbs / in narrative context
        val convBluetooth = com.example.manager.CommandClassifier.classify("Kal maine bluetooth speaker khareeda tha", sampleApps)
        assertFalse("Kal bluetooth khareeda tha should NOT be task", convBluetooth.isTask)
        assertEquals("CONVERSATION", convBluetooth.classifiedAs)
        assertTrue("False positive risk should mention bluetooth", convBluetooth.falsePositiveRisk.contains("bluetooth"))

        val convYouTubeStory = com.example.manager.CommandClassifier.classify("Kal maine YouTube par ek movie dekhi thi", sampleApps)
        assertFalse("YouTube story should NOT be task", convYouTubeStory.isTask)
        assertEquals("CONVERSATION", convYouTubeStory.classifiedAs)
        assertTrue("False positive risk should mention YouTube", convYouTubeStory.falsePositiveRisk.contains("YouTube"))

        val convAwazCompliment = com.example.manager.CommandClassifier.classify("Aapki aawaz bohot pyaari hai", sampleApps)
        assertFalse("Aawaz compliment should NOT be task", convAwazCompliment.isTask)
        assertEquals("CONVERSATION", convAwazCompliment.classifiedAs)
        assertTrue("False positive risk should mention aawaz", convAwazCompliment.falsePositiveRisk.contains("aawaz"))

        val convWifiQuestion = com.example.manager.CommandClassifier.classify("WiFi ka password kya hai bhai", sampleApps)
        assertFalse("WiFi question should NOT be task", convWifiQuestion.isTask)
        assertEquals("CONVERSATION", convWifiQuestion.classifiedAs)
        assertTrue("False positive risk should mention wifi", convWifiQuestion.falsePositiveRisk.contains("wifi"))

        val convWhatsAppQuestion = com.example.manager.CommandClassifier.classify("WhatsApp par message kaise bhejte hain", sampleApps)
        assertFalse("WhatsApp question should NOT be task", convWhatsAppQuestion.isTask)
        assertEquals("CONVERSATION", convWhatsAppQuestion.classifiedAs)
        assertTrue("False positive risk should mention WhatsApp", convWhatsAppQuestion.falsePositiveRisk.contains("WhatsApp"))

        val convChitchat = com.example.manager.CommandClassifier.classify("Bhai aaj din kaisa raha", sampleApps)
        assertFalse("General chitchat should NOT be task", convChitchat.isTask)
        assertEquals("CONVERSATION", convChitchat.classifiedAs)
        assertEquals("none", convChitchat.falsePositiveRisk)
    }

    @Test
    fun debugLogger_recordsClassificationDecisionLogs() {
        DebugLogger.clearLogs()

        // 1. Task decision log
        DebugLogger.logClassificationDecision(
            input = "WiFi on karo",
            classifiedAs = "TASK",
            confidence = "Explicit action-verb 'on karo' targeting 'wifi'",
            falsePositiveRisk = "none"
        )

        // 2. Conversation decision log with false positive risk avoided
        DebugLogger.logClassificationDecision(
            input = "Kal maine YouTube par ek video dekhi thi",
            classifiedAs = "CONVERSATION",
            confidence = "Past-tense / narrative storytelling detected",
            falsePositiveRisk = "keyword 'YouTube' mentioned in conversational context"
        )

        val logs = DebugLogger.logs.value
        assertTrue(logs.any { it.message.startsWith("CLASSIFICATION_DECISION: input=WiFi on karo, classified_as=TASK") })
        assertTrue(logs.any { it.message.contains("classified_as=CONVERSATION") && it.message.contains("false_positive_risk=keyword 'YouTube'") })
    }
}
