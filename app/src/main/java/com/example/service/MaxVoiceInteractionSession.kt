package com.example.service

import android.app.assist.AssistContent
import android.app.assist.AssistStructure
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.util.Log
import com.example.MainActivity
import com.example.manager.AppContextManager
import com.example.manager.DefaultAssistantManager
import com.example.util.DebugLogger

class MaxVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    override fun onCreate() {
        super.onCreate()
        setTheme(android.R.style.Theme_Translucent_NoTitleBar)
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        Log.i(TAG, "Digital Assistant triggered via home button long-press or assist gesture (showFlags=$showFlags)")
        
        // Required Debug Log
        DebugLogger.logInfo("ASSISTANT_TRIGGERED: source=home_long_press_or_assist_gesture")
        DefaultAssistantManager.recordAssistTrigger()

        // Launch MainActivity directly with auto-start listening flag
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_AUTO_START_LISTENING, true)
                putExtra(MainActivity.EXTRA_TRIGGER_SOURCE, "ASSIST_GESTURE")
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching MainActivity from VoiceInteractionSession", e)
        }

        // Finish/hide session
        hide()
    }

    override fun onHandleAssist(data: Bundle?, structure: AssistStructure?, content: AssistContent?) {
        super.onHandleAssist(data, structure, content)
        Log.d(TAG, "Assist structure received from foreground window")
        if (structure != null) {
            try {
                val extractedText = StringBuilder()
                val windowNodes = structure.run { (0 until windowNodeCount).map { getWindowNodeAt(it) } }
                for (windowNode in windowNodes) {
                    val rootView = windowNode.rootViewNode
                    traverseAssistNode(rootView, extractedText)
                }
                val screenContent = extractedText.toString().trim()
                if (screenContent.isNotEmpty()) {
                    AppContextManager.updateScreenContext(
                        packageName = structure.activityComponent?.packageName ?: "",
                        title = structure.activityComponent?.className ?: "",
                        extractedText = screenContent.take(1500)
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing assist structure", e)
            }
        }
    }

    private fun traverseAssistNode(node: AssistStructure.ViewNode?, out: StringBuilder) {
        if (node == null) return
        val text = node.text?.toString()?.trim()
        if (!text.isNullOrEmpty() && text.length > 2) {
            out.append(text).append(" | ")
        }
        for (i in 0 until node.childCount) {
            traverseAssistNode(node.getChildAt(i), out)
        }
    }

    companion object {
        private const val TAG = "MaxVoiceSession"
    }
}
