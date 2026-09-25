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
        DebugLogger.logInfo("MaxAccessibilityService connected successfully.")

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_CLICKED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isSearching || pendingTargetKeywords.isNullOrEmpty()) return

        // If Quick Settings window is active, scan for target tile
        serviceScope.launch {
            checkAndClickTile()
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility Service Interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        _isServiceEnabled.value = false
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

    private fun resetSearch() {
        isSearching = false
        pendingTargetKeywords = null
        pendingTargetName = null
        onActionResult = null
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
