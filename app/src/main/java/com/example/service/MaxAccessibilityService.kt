package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.util.DebugLogger
import com.example.util.ToggleMethod
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class MaxAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var pendingTargetKeywords: List<String>? = null
    private var pendingTargetName: String? = null
    private var onActionResult: ((Boolean) -> Unit)? = null
    private var isSearching = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceEnabled.value = true
        DebugLogger.logInfo("MaxAccessibilityService connected successfully (Zero Background Drain Throttling).")
        com.example.manager.BatteryOptimizationManager.updateSubsystemState(accessibilityState = "CONNECTED (Throttled/Passive)")

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_CLICKED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 200 // 200ms throttle to prevent CPU flooding
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Track current foreground app context
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString()
            if (!pkg.isNullOrBlank() && pkg != packageName) {
                com.example.manager.AppContextManager.recordCurrentPackage(pkg)
            }
        }

        // Zero Background Overhead: Only inspect screen nodes when an active command is waiting for tile matching
        if (!isSearching || pendingTargetKeywords.isNullOrEmpty()) return

        // If Quick Settings window is active, scan for target tile
        serviceScope.launch {
            checkAndClickTile()
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility Service Interrupted")
        resetSearch()
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        _isServiceEnabled.value = false
        com.example.manager.BatteryOptimizationManager.updateSubsystemState(accessibilityState = "OFF (Disconnected)")
        DebugLogger.logInfo("MaxAccessibilityService destroyed.")
    }

    fun triggerQuickSettingTile(
        toggleName: String,
        keywords: List<String>,
        onComplete: (Boolean) -> Unit
    ) {
        pendingTargetName = toggleName
        pendingTargetKeywords = keywords
        onActionResult = onComplete
        isSearching = true
        com.example.manager.BatteryOptimizationManager.updateSubsystemState(accessibilityState = "SCANNING QS TILE ($toggleName)")

        DebugLogger.logToggleAttempt(toggleName, ToggleMethod.ACCESSIBILITY)

        // Step 1: Open Quick Settings
        val opened = performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
        if (!opened) {
            DebugLogger.logToggleResult(false, "Could not open Quick Settings shade")
            onComplete(false)
            resetSearch()
            return
        }

        // Step 2: Search after short delay to allow QS panel expansion
        serviceScope.launch {
            var clicked = false
            for (attempt in 1..8) {
                delay(250)
                if (!isSearching) break
                clicked = checkAndClickTile()
                if (clicked) break
            }

            if (!clicked && isSearching) {
                DebugLogger.logToggleResult(false, "Tile matching '$toggleName' not found in Quick Settings")
                onActionResult?.invoke(false)
                resetSearch()
                // Collapse shade
                delay(300)
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        }
    }

    private fun checkAndClickTile(): Boolean {
        if (!isSearching || pendingTargetKeywords == null) return false

        val rootNode = rootInActiveWindow ?: return false
        val keywords = pendingTargetKeywords ?: return false
        val targetName = pendingTargetName ?: "Unknown"

        val matchedNode = findMatchingNode(rootNode, keywords)
        if (matchedNode != null) {
            val clickableNode = findClickableAncestorOrSelf(matchedNode) ?: matchedNode
            val clicked = clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)

            if (clicked) {
                isSearching = false
                DebugLogger.logToggleResult(true, "Clicked tile '$targetName'")
                onActionResult?.invoke(true)
                resetSearch()

                // Collapse notification panel after brief pause
                serviceScope.launch {
                    delay(700)
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }
                return true
            }
        }
        return false
    }

    private fun findMatchingNode(node: AccessibilityNodeInfo?, keywords: List<String>): AccessibilityNodeInfo? {
        if (node == null) return null

        val text = node.text?.toString()?.lowercase(Locale.getDefault()) ?: ""
        val contentDesc = node.contentDescription?.toString()?.lowercase(Locale.getDefault()) ?: ""
        val viewId = node.viewIdResourceName?.lowercase(Locale.getDefault()) ?: ""

        for (kw in keywords) {
            val keyword = kw.lowercase(Locale.getDefault())
            if (text.contains(keyword) || contentDesc.contains(keyword) || viewId.contains(keyword)) {
                return node
            }
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i)
            val match = findMatchingNode(child, keywords)
            if (match != null) {
                return match
            }
        }
        return null
    }

    private fun findClickableAncestorOrSelf(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var current = node
        while (current != null) {
            if (current.isClickable) {
                return current
            }
            current = current.parent
        }
        return null
    }

    /**
     * Finds WhatsApp chat input, sets the generated reply text, and clicks Send.
     */
    fun sendWhatsAppMessage(targetSender: String, replyText: String): Boolean {
        try {
            val root = rootInActiveWindow ?: return false

            // 1. Look for EditText node to type into
            val editTextNode = findEditTextNode(root)
            if (editTextNode != null) {
                val args = android.os.Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, replyText)
                }
                val textSet = editTextNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                if (textSet) {
                    // Look for send button
                    serviceScope.launch {
                        delay(200)
                        val sendBtn = findSendButton(rootInActiveWindow ?: root)
                        if (sendBtn != null) {
                            sendBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            DebugLogger.logInfo("Accessibility: Clicked Send in WhatsApp for $targetSender")
                        }
                    }
                    return true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error in sendWhatsAppMessage via accessibility", e)
        }
        return false
    }

    /**
     * Generic App-Control: Automates finding contact/chat, setting message text, and clicking Send
     * across ANY messaging application (WhatsApp, Telegram, Instagram, Signal, Messages, etc.)
     */
    fun sendGenericMessage(
        recipient: String,
        messageText: String,
        onFinished: (Boolean) -> Unit
    ) {
        serviceScope.launch {
            try {
                // Allow target app activity to settle
                delay(600)
                var root = rootInActiveWindow

                // Check 1: Already inside chat view (input edit text is already present)
                val chatInput = findMessageInputNode(root)
                if (chatInput != null) {
                    val typed = typeTextIntoNode(chatInput, messageText)
                    if (typed) {
                        delay(250)
                        val sendBtn = findSendButton(rootInActiveWindow ?: root)
                        if (sendBtn != null) {
                            clickNodeSafely(sendBtn)
                            DebugLogger.logInfo("GenericAccessibility: Typed and clicked Send in active chat")
                            onFinished(true)
                            return@launch
                        }
                    }
                }

                // Check 2: In conversations list - look for recipient directly on screen
                val recipientNode = findNodeWithTextOrDesc(root, recipient)
                if (recipientNode != null) {
                    clickNodeSafely(recipientNode)
                    delay(700)
                    root = rootInActiveWindow
                    val inputNode = findMessageInputNode(root)
                    if (inputNode != null) {
                        typeTextIntoNode(inputNode, messageText)
                        delay(250)
                        val sendBtn = findSendButton(rootInActiveWindow ?: root)
                        if (sendBtn != null) {
                            clickNodeSafely(sendBtn)
                            DebugLogger.logInfo("GenericAccessibility: Selected recipient from list, typed and sent")
                            onFinished(true)
                            return@launch
                        }
                    }
                }

                // Check 3: Look for Search or Compose/New Chat button
                val searchOrCompose = findSearchOrComposeButton(root)
                if (searchOrCompose != null) {
                    clickNodeSafely(searchOrCompose)
                    delay(500)
                    root = rootInActiveWindow
                    val searchInput = findEditTextNode(root)
                    if (searchInput != null) {
                        typeTextIntoNode(searchInput, recipient)
                        delay(800) // Wait for search results
                        root = rootInActiveWindow
                        val matchedResult = findNodeWithTextOrDesc(root, recipient) ?: findFirstClickableItem(root)
                        if (matchedResult != null) {
                            clickNodeSafely(matchedResult)
                            delay(700)
                            root = rootInActiveWindow
                            val inputNode = findMessageInputNode(root)
                            if (inputNode != null) {
                                typeTextIntoNode(inputNode, messageText)
                                delay(250)
                                val sendBtn = findSendButton(rootInActiveWindow ?: root)
                                if (sendBtn != null) {
                                    clickNodeSafely(sendBtn)
                                    DebugLogger.logInfo("GenericAccessibility: Searched for $recipient, opened chat, and sent")
                                    onFinished(true)
                                    return@launch
                                }
                            }
                        }
                    }
                }

                // Check 4: Fallback input node discovery
                val anyInput = findMessageInputNode(rootInActiveWindow ?: root)
                if (anyInput != null) {
                    typeTextIntoNode(anyInput, messageText)
                    delay(250)
                    val sendBtn = findSendButton(rootInActiveWindow ?: root)
                    if (sendBtn != null) {
                        clickNodeSafely(sendBtn)
                        DebugLogger.logInfo("GenericAccessibility: Sent via fallback input check")
                        onFinished(true)
                        return@launch
                    }
                }

                DebugLogger.logInfo("GenericAccessibility: Reached end without finding send button")
                onFinished(false)
            } catch (e: Exception) {
                Log.w(TAG, "Error in sendGenericMessage via accessibility", e)
                onFinished(false)
            }
        }
    }

    private fun findMessageInputNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        val cls = node.className?.toString() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase(Locale.getDefault()) ?: ""
        val text = node.text?.toString()?.lowercase(Locale.getDefault()) ?: ""
        val id = node.viewIdResourceName?.lowercase(Locale.getDefault()) ?: ""

        if (cls.contains("EditText", ignoreCase = true) ||
            desc.contains("message") || desc.contains("type a message") || desc.contains("chat") ||
            id.contains("entry") || id.contains("input") || id.contains("message_edit") || id.contains("text_input")
        ) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val res = findMessageInputNode(child)
            if (res != null) return res
        }
        return null
    }

    private fun findSearchOrComposeButton(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        val desc = node.contentDescription?.toString()?.lowercase(Locale.getDefault()) ?: ""
        val text = node.text?.toString()?.lowercase(Locale.getDefault()) ?: ""
        val id = node.viewIdResourceName?.lowercase(Locale.getDefault()) ?: ""

        val isSearchOrCompose = desc.contains("search") || desc.contains("खोजें") || desc.contains("find") ||
                desc.contains("compose") || desc.contains("new message") || desc.contains("new chat") ||
                desc.contains("start chat") || id.contains("search") || id.contains("compose") || id.contains("fab")

        if (isSearchOrCompose && (node.isClickable || node.parent?.isClickable == true)) {
            return findClickableAncestorOrSelf(node) ?: node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val res = findSearchOrComposeButton(child)
            if (res != null) return res
        }
        return null
    }

    private fun findNodeWithTextOrDesc(node: AccessibilityNodeInfo?, target: String): AccessibilityNodeInfo? {
        if (node == null || target.isBlank()) return null
        val query = target.lowercase(Locale.getDefault())
        val text = node.text?.toString()?.lowercase(Locale.getDefault()) ?: ""
        val desc = node.contentDescription?.toString()?.lowercase(Locale.getDefault()) ?: ""

        if (text.contains(query) || desc.contains(query)) {
            return findClickableAncestorOrSelf(node) ?: node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val res = findNodeWithTextOrDesc(child, target)
            if (res != null) return res
        }
        return null
    }

    private fun findFirstClickableItem(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isClickable && node.className?.toString()?.contains("EditText", ignoreCase = true) == false) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val res = findFirstClickableItem(child)
            if (res != null) return res
        }
        return null
    }

    private fun clickNodeSafely(node: AccessibilityNodeInfo?): Boolean {
        val clickable = findClickableAncestorOrSelf(node) ?: node ?: return false
        return clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun typeTextIntoNode(node: AccessibilityNodeInfo?, text: String): Boolean {
        if (node == null) return false
        val args = android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun findEditTextNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.className?.toString()?.contains("EditText", ignoreCase = true) == true) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val result = findEditTextNode(child)
            if (result != null) return result
        }
        return null
    }

    private fun findSendButton(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        val desc = node.contentDescription?.toString()?.lowercase(Locale.getDefault()) ?: ""
        val id = node.viewIdResourceName?.lowercase(Locale.getDefault()) ?: ""
        if (desc.contains("send") || desc.contains("भेजें") || id.contains("send")) {
            return findClickableAncestorOrSelf(node) ?: node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val result = findSendButton(child)
            if (result != null) return result
        }
        return null
    }

    /**
     * Generic App-Control: Automates media controls (play, pause, next, previous, in-app volume)
     * across any media player (YouTube, Spotify, Gaana, JioSaavn, Wynk, etc.)
     * Uses screen-reading, element detection, and Gemini screen-understanding.
     */
    fun executeGenericMediaAction(
        action: String,
        query: String? = null,
        onFinished: (Boolean, String) -> Unit
    ) {
        serviceScope.launch {
            try {
                delay(350)
                var root = rootInActiveWindow

                when (action.lowercase(Locale.getDefault())) {
                    "next" -> {
                        var nextBtn = findMediaNextButton(root)
                        if (nextBtn == null) {
                            clickPlayerCenter(root)
                            delay(350)
                            root = rootInActiveWindow
                            nextBtn = findMediaNextButton(root)
                        }
                        if (nextBtn != null && clickNodeSafely(nextBtn)) {
                            DebugLogger.logInfo("GenericAccessibility: Clicked Next button")
                            onFinished(true, "Agla content chala diya")
                            return@launch
                        }

                        // Gemini screen-understanding fallback
                        val elements = collectClickableElements(root)
                        val resolved = GeminiReplyService.resolveScreenActionTarget("Play next video or song", elements)
                        if (resolved.isNotBlank()) {
                            val node = findNodeWithTextOrDesc(root, resolved)
                            if (node != null && clickNodeSafely(node)) {
                                DebugLogger.logInfo("GenericAccessibility: Clicked Gemini-resolved Next node: $resolved")
                                onFinished(true, "Agla content chala diya")
                                return@launch
                            }
                        }
                        onFinished(false, "Next button nahi mila")
                    }

                    "previous" -> {
                        var prevBtn = findMediaPrevButton(root)
                        if (prevBtn == null) {
                            clickPlayerCenter(root)
                            delay(350)
                            root = rootInActiveWindow
                            prevBtn = findMediaPrevButton(root)
                        }
                        if (prevBtn != null && clickNodeSafely(prevBtn)) {
                            DebugLogger.logInfo("GenericAccessibility: Clicked Previous button")
                            onFinished(true, "Pichla content chala diya")
                            return@launch
                        }

                        val elements = collectClickableElements(root)
                        val resolved = GeminiReplyService.resolveScreenActionTarget("Play previous video or song", elements)
                        if (resolved.isNotBlank()) {
                            val node = findNodeWithTextOrDesc(root, resolved)
                            if (node != null && clickNodeSafely(node)) {
                                DebugLogger.logInfo("GenericAccessibility: Clicked Gemini-resolved Previous node: $resolved")
                                onFinished(true, "Pichla content chala diya")
                                return@launch
                            }
                        }
                        onFinished(false, "Previous button nahi mila")
                    }

                    "pause" -> {
                        var pauseBtn = findMediaPauseButton(root)
                        if (pauseBtn == null) {
                            clickPlayerCenter(root)
                            delay(350)
                            root = rootInActiveWindow
                            pauseBtn = findMediaPauseButton(root)
                        }
                        if (pauseBtn != null && clickNodeSafely(pauseBtn)) {
                            DebugLogger.logInfo("GenericAccessibility: Clicked Pause button")
                            onFinished(true, "Pause kar diya")
                            return@launch
                        }

                        val elements = collectClickableElements(root)
                        val resolved = GeminiReplyService.resolveScreenActionTarget("Pause video or song", elements)
                        if (resolved.isNotBlank()) {
                            val node = findNodeWithTextOrDesc(root, resolved)
                            if (node != null && clickNodeSafely(node)) {
                                DebugLogger.logInfo("GenericAccessibility: Clicked Gemini-resolved Pause node: $resolved")
                                onFinished(true, "Pause kar diya")
                                return@launch
                            }
                        }

                        // Toggle player center tap to pause
                        val tapped = clickPlayerCenter(root)
                        onFinished(tapped, if (tapped) "Pause kar diya" else "Pause button nahi mila")
                    }

                    "play" -> {
                        if (!query.isNullOrBlank()) {
                            val searchBtn = findSearchOrComposeButton(root)
                            if (searchBtn != null) {
                                clickNodeSafely(searchBtn)
                                delay(450)
                                root = rootInActiveWindow
                                val searchInput = findEditTextNode(root)
                                if (searchInput != null) {
                                    typeTextIntoNode(searchInput, query)
                                    delay(650)
                                    root = rootInActiveWindow
                                    val firstItem = findFirstPlayableResult(root, query) ?: findFirstClickableItem(root)
                                    if (firstItem != null && clickNodeSafely(firstItem)) {
                                        DebugLogger.logInfo("GenericAccessibility: Searched '$query' and played result")
                                        onFinished(true, "$query chala diya")
                                        return@launch
                                    }
                                }
                            }
                            val directMatch = findNodeWithTextOrDesc(root, query)
                            if (directMatch != null && clickNodeSafely(directMatch)) {
                                onFinished(true, "$query chala diya")
                                return@launch
                            }
                            onFinished(false, "$query nahi mila")
                        } else {
                            var playBtn = findMediaPlayButton(root)
                            if (playBtn == null) {
                                clickPlayerCenter(root)
                                delay(350)
                                root = rootInActiveWindow
                                playBtn = findMediaPlayButton(root)
                            }
                            if (playBtn != null && clickNodeSafely(playBtn)) {
                                DebugLogger.logInfo("GenericAccessibility: Clicked Play button")
                                onFinished(true, "Play kar diya")
                                return@launch
                            }
                            val tapped = clickPlayerCenter(root)
                            onFinished(tapped, if (tapped) "Play kar diya" else "Play button nahi mila")
                        }
                    }

                    "volume_up", "volume_down" -> {
                        val volNode = findInAppVolumeNode(root)
                        if (volNode != null && clickNodeSafely(volNode)) {
                            DebugLogger.logInfo("GenericAccessibility: Adjusted in-app volume node")
                            onFinished(true, "In-app volume adjust kar diya")
                            return@launch
                        }
                        onFinished(false, "No in-app volume slider; fallback to system volume")
                    }

                    else -> onFinished(false, "Unknown media action: $action")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error in executeGenericMediaAction via accessibility", e)
                onFinished(false, e.message ?: "Execution error")
            }
        }
    }

    private fun findMediaNextButton(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        val desc = node.contentDescription?.toString()?.lowercase(Locale.getDefault()) ?: ""
        val text = node.text?.toString()?.lowercase(Locale.getDefault()) ?: ""
        val id = node.viewIdResourceName?.lowercase(Locale.getDefault()) ?: ""

        val isNext = desc.contains("next") || desc.contains("skip next") || desc.contains("अगला") ||
                text.contains("next") || text.contains("अगला") ||
                id.contains("exo_next") || id.contains("next_button") || id.contains("skip_next")

        if (isNext && (node.isClickable || node.parent?.isClickable == true)) {
            return findClickableAncestorOrSelf(node) ?: node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val res = findMediaNextButton(child)
            if (res != null) return res
        }
        return null
    }

    private fun findMediaPrevButton(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        val desc = node.contentDescription?.toString()?.lowercase(Locale.getDefault()) ?: ""
        val text = node.text?.toString()?.lowercase(Locale.getDefault()) ?: ""
        val id = node.viewIdResourceName?.lowercase(Locale.getDefault()) ?: ""

        val isPrev = desc.contains("previous") || desc.contains("prev") || desc.contains("skip prev") || desc.contains("पिछला") ||
                text.contains("prev") || text.contains("पिछला") ||
                id.contains("exo_prev") || id.contains("prev_button") || id.contains("skip_prev")

        if (isPrev && (node.isClickable || node.parent?.isClickable == true)) {
            return findClickableAncestorOrSelf(node) ?: node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val res = findMediaPrevButton(child)
            if (res != null) return res
        }
        return null
    }

    private fun findMediaPauseButton(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        val desc = node.contentDescription?.toString()?.lowercase(Locale.getDefault()) ?: ""
        val text = node.text?.toString()?.lowercase(Locale.getDefault()) ?: ""
        val id = node.viewIdResourceName?.lowercase(Locale.getDefault()) ?: ""

        val isPause = desc.contains("pause") || desc.contains("पॉज़") || desc.contains("रोकें") ||
                text.contains("pause") || id.contains("exo_pause") || id.contains("pause_button")

        if (isPause && (node.isClickable || node.parent?.isClickable == true)) {
            return findClickableAncestorOrSelf(node) ?: node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val res = findMediaPauseButton(child)
            if (res != null) return res
        }
        return null
    }

    private fun findMediaPlayButton(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        val desc = node.contentDescription?.toString()?.lowercase(Locale.getDefault()) ?: ""
        val text = node.text?.toString()?.lowercase(Locale.getDefault()) ?: ""
        val id = node.viewIdResourceName?.lowercase(Locale.getDefault()) ?: ""

        val isPlay = desc.contains("play") || desc.contains("resume") || desc.contains("प्ले") || desc.contains("चलाएं") ||
                text.contains("play") || id.contains("exo_play") || id.contains("play_button")

        if (isPlay && (node.isClickable || node.parent?.isClickable == true)) {
            return findClickableAncestorOrSelf(node) ?: node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val res = findMediaPlayButton(child)
            if (res != null) return res
        }
        return null
    }

    private fun findInAppVolumeNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        val desc = node.contentDescription?.toString()?.lowercase(Locale.getDefault()) ?: ""
        val id = node.viewIdResourceName?.lowercase(Locale.getDefault()) ?: ""

        val isVol = desc.contains("volume") || desc.contains("mute") || desc.contains("unmute") ||
                id.contains("volume") || id.contains("sound")

        if (isVol && (node.isClickable || node.parent?.isClickable == true)) {
            return findClickableAncestorOrSelf(node) ?: node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val res = findInAppVolumeNode(child)
            if (res != null) return res
        }
        return null
    }

    private fun clickPlayerCenter(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val cls = node.className?.toString() ?: ""
        val id = node.viewIdResourceName?.lowercase(Locale.getDefault()) ?: ""
        val desc = node.contentDescription?.toString()?.lowercase(Locale.getDefault()) ?: ""

        if (id.contains("player") || id.contains("video") || id.contains("surface") ||
            desc.contains("player") || desc.contains("video") ||
            cls.contains("SurfaceView") || cls.contains("TextureView") || cls.contains("PlayerView")
        ) {
            if (node.isClickable) return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            val clickable = findClickableAncestorOrSelf(node)
            if (clickable != null) return clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }

        for (i in 0 until node.childCount) {
            if (clickPlayerCenter(node.getChild(i))) return true
        }
        return false
    }

    private fun collectClickableElements(node: AccessibilityNodeInfo?, list: MutableList<String> = mutableListOf()): List<String> {
        if (node == null) return list
        val text = node.text?.toString()?.trim() ?: ""
        val desc = node.contentDescription?.toString()?.trim() ?: ""
        val id = node.viewIdResourceName?.substringAfterLast('/') ?: ""

        if (node.isClickable || node.parent?.isClickable == true) {
            val label = when {
                text.isNotBlank() -> text
                desc.isNotBlank() -> desc
                id.isNotBlank() -> id
                else -> ""
            }
            if (label.isNotBlank() && !list.contains(label)) {
                list.add(label)
            }
        }

        for (i in 0 until node.childCount) {
            collectClickableElements(node.getChild(i), list)
        }
        return list
    }

    private fun findFirstPlayableResult(node: AccessibilityNodeInfo?, query: String): AccessibilityNodeInfo? {
        if (node == null) return null
        val queryLower = query.lowercase(Locale.getDefault())
        val text = node.text?.toString()?.lowercase(Locale.getDefault()) ?: ""
        val desc = node.contentDescription?.toString()?.lowercase(Locale.getDefault()) ?: ""

        if ((text.contains(queryLower) || desc.contains(queryLower)) && (node.isClickable || node.parent?.isClickable == true)) {
            return findClickableAncestorOrSelf(node) ?: node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val res = findFirstPlayableResult(child, query)
            if (res != null) return res
        }
        return null
    }

    /**
     * GENERIC MULTI-STEP IN-APP ACTION AUTOMATION:
     * Reads current screen via Accessibility Node Tree, determines UI elements,
     * uses Gemini Screen Understanding + heuristics to execute multi-step in-app operations
     * sequentially (e.g. search-icon -> type query -> submit/enter/first-result -> click/play/subscribe/follow).
     *
     * Emits Debug Log: "MULTI_STEP_ACTION: steps_identified=<count>, current_step=<kaunsa>, step_result=<success/fail, error=<reason>>"
     */
    fun executeGenericInAppAction(
        actionDescription: String,
        onFinished: (Boolean, String) -> Unit
    ) {
        serviceScope.launch {
            try {
                val lowerAction = actionDescription.lowercase(Locale.getDefault())
                val isSearchAction = lowerAction.contains("search") || lowerAction.contains("khoj") ||
                        lowerAction.contains("dhoondo") || lowerAction.contains("lagao") ||
                        lowerAction.contains("play") || lowerAction.contains("chalao") ||
                        lowerAction.contains("bajao")

                val stepsIdentified = if (isSearchAction) 4 else 3
                var stepsExecuted = 0

                // Step 1: UI Settle & Screen Read
                var root: AccessibilityNodeInfo? = null
                for (attempt in 1..6) {
                    delay(300L)
                    root = rootInActiveWindow
                    if (root != null && root.childCount > 0) break
                }

                if (root == null) {
                    DebugLogger.logMultiStepActionStep(stepsIdentified, "screen_read", false, "rootInActiveWindow is null")
                    DebugLogger.logMultiStepAction(stepsIdentified, 0)
                    onFinished(false, "Screen read failed: window root is null")
                    return@launch
                }

                DebugLogger.logMultiStepActionStep(stepsIdentified, "screen_read", true, "none")
                stepsExecuted++

                if (isSearchAction) {
                    val query = extractQueryFromAction(actionDescription)
                    if (query.isNotBlank()) {
                        // Step 2: Find search button or edit text
                        var searchInput = findEditTextNode(root)
                        if (searchInput == null) {
                            val searchBtn = findSearchOrComposeButton(root)
                                ?: findNodeWithTextOrDesc(root, "Search")
                                ?: findNodeWithTextOrDesc(root, "खोजें")
                                ?: findNodeWithIdContaining(root, "search")

                            if (searchBtn != null && clickNodeSafely(searchBtn)) {
                                DebugLogger.logMultiStepActionStep(stepsIdentified, "find_element", true, "none")
                                stepsExecuted++
                                delay(500L)
                                root = rootInActiveWindow
                                searchInput = findEditTextNode(root)
                            } else {
                                DebugLogger.logMultiStepActionStep(stepsIdentified, "find_element", false, "Search button not found")
                            }
                        } else {
                            DebugLogger.logMultiStepActionStep(stepsIdentified, "find_element", true, "none")
                            stepsExecuted++
                        }

                        // Step 3: Type query into search field
                        if (searchInput != null) {
                            val typed = typeTextIntoNode(searchInput, query)
                            if (typed) {
                                DebugLogger.logMultiStepActionStep(stepsIdentified, "tap_type", true, "none")
                                stepsExecuted++
                                delay(600L)
                                root = rootInActiveWindow

                                // Step 4: Submit search or click suggestion / play first result
                                val suggestion = findFirstPlayableResult(root, query)
                                if (suggestion != null && clickNodeSafely(suggestion)) {
                                    delay(800L)
                                    root = rootInActiveWindow
                                } else {
                                    val searchActionBtn = findNodeWithTextOrDesc(root, "Search")
                                        ?: findNodeWithIdContaining(root, "search")
                                    if (searchActionBtn != null) {
                                        clickNodeSafely(searchActionBtn)
                                        delay(800L)
                                        root = rootInActiveWindow
                                    }
                                }

                                val shouldPlay = lowerAction.contains("play") || lowerAction.contains("chalao") ||
                                        lowerAction.contains("lagao") || lowerAction.contains("bajao")
                                if (shouldPlay) {
                                    val playableItem = findFirstPlayableResult(root, query) ?: findFirstClickableItem(root)
                                    if (playableItem != null && clickNodeSafely(playableItem)) {
                                        DebugLogger.logMultiStepActionStep(stepsIdentified, "submit_or_play", true, "none")
                                        stepsExecuted++
                                        DebugLogger.logMultiStepAction(stepsIdentified, stepsExecuted)
                                        onFinished(true, "Searched and played '$query'")
                                        return@launch
                                    } else {
                                        DebugLogger.logMultiStepActionStep(stepsIdentified, "submit_or_play", false, "No playable result found")
                                    }
                                } else {
                                    DebugLogger.logMultiStepActionStep(stepsIdentified, "submit_or_play", true, "none")
                                    stepsExecuted++
                                    DebugLogger.logMultiStepAction(stepsIdentified, stepsExecuted)
                                    onFinished(true, "Searched for '$query'")
                                    return@launch
                                }
                            } else {
                                DebugLogger.logMultiStepActionStep(stepsIdentified, "tap_type", false, "Failed to type into search field")
                            }
                        }
                    }
                }

                // If not search or generic element action (subscribe, follow, etc.)
                val visibleElements = collectClickableElements(root)
                var targetNode: AccessibilityNodeInfo? = null
                var resolvedName = ""

                if (visibleElements.isNotEmpty()) {
                    val resolvedTarget = GeminiReplyService.resolveScreenActionTarget(
                        actionIntent = actionDescription,
                        screenElements = visibleElements
                    )
                    if (resolvedTarget.isNotBlank() && !resolvedTarget.equals("NONE", ignoreCase = true)) {
                        targetNode = findNodeWithTextOrDesc(root, resolvedTarget)
                        resolvedName = resolvedTarget
                    }
                }

                if (targetNode == null) {
                    val actionKeywords = extractKeyTargetWords(lowerAction)
                    for (kw in actionKeywords) {
                        val match = findNodeWithTextOrDesc(root, kw)
                        if (match != null) {
                            targetNode = match
                            resolvedName = kw
                            break
                        }
                    }
                }

                if (targetNode != null) {
                    DebugLogger.logMultiStepActionStep(stepsIdentified, "find_element", true, "none")
                    stepsExecuted++

                    val clicked = clickNodeSafely(targetNode)
                    if (clicked) {
                        DebugLogger.logMultiStepActionStep(stepsIdentified, "tap_type", true, "none")
                        stepsExecuted++
                        DebugLogger.logMultiStepAction(stepsIdentified, stepsExecuted)
                        onFinished(true, "Clicked $resolvedName")
                        return@launch
                    } else {
                        DebugLogger.logMultiStepActionStep(stepsIdentified, "tap_type", false, "Click failed on $resolvedName")
                    }
                } else {
                    DebugLogger.logMultiStepActionStep(stepsIdentified, "find_element", false, "Element not found on screen")
                }

                DebugLogger.logMultiStepAction(stepsIdentified, stepsExecuted)
                onFinished(stepsExecuted > 1, if (stepsExecuted > 1) "Steps executed partially" else "Target element not found on screen")

            } catch (e: Exception) {
                Log.w(TAG, "Error in executeGenericInAppAction", e)
                DebugLogger.logMultiStepActionStep(3, "exception", false, e.message ?: "Unknown error")
                DebugLogger.logMultiStepAction(3, 0)
                onFinished(false, e.message ?: "Screen action error")
            }
        }
    }

    suspend fun executeGenericInAppActionSuspend(actionDescription: String): Pair<Boolean, String> =
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            executeGenericInAppAction(actionDescription) { success, details ->
                if (cont.isActive) {
                    cont.resumeWith(Result.success(Pair(success, details)))
                }
            }
        }

    private fun findNodeWithIdContaining(node: AccessibilityNodeInfo?, idSubstring: String): AccessibilityNodeInfo? {
        if (node == null) return null
        val id = node.viewIdResourceName?.lowercase(Locale.getDefault()) ?: ""
        if (id.contains(idSubstring.lowercase(Locale.getDefault()))) {
            return findClickableAncestorOrSelf(node) ?: node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val res = findNodeWithIdContaining(child, idSubstring)
            if (res != null) return res
        }
        return null
    }

    fun extractQueryFromAction(action: String): String {
        var clean = action
        val appNames = listOf("youtube", "instagram", "spotify", "chrome", "gaana", "wynk", "jiosaavn")
        for (app in appNames) {
            clean = clean.replace(Regex("(?i)\\b$app\\b"), " ")
        }
        val removePhrases = listOf(
            "par search karo aur play karo", "pe search karo aur play karo",
            "par search karo aur lagao", "pe search karo aur lagao",
            "search karo aur play karo", "search karo aur lagao", "search karke play karo",
            "search karo", "khojo", "dhoondo", "search",
            "par chalao", "pe chalao", "par lagao", "pe lagao",
            "play karo", "chala do", "chalao", "lagao", "bajao", "baja do",
            "kholo aur", "open karo aur", "khol ke", "kholke", "khol kar", "kholkar", "open karke", "open kar",
            "par", "pe", "mein", "me", "mai", "yeh", "woh",
            "gana", "gaana", "song", "video", "audio", "track",
            "aur", "and", "phir", "fir"
        )
        for (p in removePhrases) {
            clean = clean.replace(Regex("(?i)\\b$p\\b"), " ")
        }
        return clean.replace(Regex("\\s+"), " ").trim()
    }

    private fun extractKeyTargetWords(lowerAction: String): List<String> {
        val keywords = mutableListOf<String>()
        if (lowerAction.contains("subscribe")) keywords.add("subscribe")
        if (lowerAction.contains("unsubscribe")) keywords.add("unsubscribe")
        if (lowerAction.contains("follow")) keywords.add("follow")
        if (lowerAction.contains("unfollow")) keywords.add("unfollow")
        if (lowerAction.contains("play") || lowerAction.contains("chalao")) keywords.add("play")
        if (lowerAction.contains("pause") || lowerAction.contains("roko")) keywords.add("pause")
        if (lowerAction.contains("like")) keywords.add("like")
        return keywords
    }

    private fun resetSearch() {
        isSearching = false
        pendingTargetKeywords = null
        pendingTargetName = null
        onActionResult = null
        com.example.manager.BatteryOptimizationManager.updateSubsystemState(accessibilityState = "CONNECTED (Throttled/Passive)")
    }

    companion object {
        private const val TAG = "MaxAccessibilityService"
        var instance: MaxAccessibilityService? = null
            private set

        private val _isServiceEnabled = MutableStateFlow(false)
        val isServiceEnabled: StateFlow<Boolean> = _isServiceEnabled.asStateFlow()

        fun isRunning(): Boolean = instance != null

        fun openAccessibilitySettings(context: Context) {
            try {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error opening accessibility settings", e)
            }
        }

        fun checkAccessibilityPermission(context: Context): Boolean {
            val expectedServiceName = "${context.packageName}/${MaxAccessibilityService::class.java.name}"
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServices)
            while (colonSplitter.hasNext()) {
                val componentName = colonSplitter.next()
                if (componentName.equals(expectedServiceName, ignoreCase = true) ||
                    componentName.contains(MaxAccessibilityService::class.java.simpleName)
                ) {
                    return true
                }
            }
            return false
        }
    }
}
