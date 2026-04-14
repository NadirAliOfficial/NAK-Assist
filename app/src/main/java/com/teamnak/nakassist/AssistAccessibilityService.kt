package com.teamnak.nakassist

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AssistAccessibilityService : AccessibilityService() {

    companion object {
        var instance: AssistAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        FloatingButtonManager.show(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Manual trigger only
    }

    fun smartReply() {
        val screen = readScreen() ?: return
        OverlayManager.showLoading(this, "Writing reply...")
        GroqApiHelper.ask(
            systemPrompt = "You are a professional Fiverr seller assistant. Write a professional, friendly and concise reply to the buyer's latest message. Reply with only the message text, no extra words.",
            userContent = "Conversation on screen:\n$screen",
            maxTokens = 150,
            onResult = { reply ->
                OverlayManager.show(this, reply, showPaste = true) { text ->
                    TextInjector.inject(this, text)
                }
            },
            onError = { error -> OverlayManager.show(this, error) }
        )
    }

    fun summarize() {
        val screen = readScreen() ?: return
        OverlayManager.showLoading(this, "Summarizing...")
        GroqApiHelper.ask(
            systemPrompt = "Summarize the content in 3-5 short bullet points. Be brief and clear.",
            userContent = screen,
            maxTokens = 150,
            onResult = { summary -> OverlayManager.show(this, summary) },
            onError = { error -> OverlayManager.show(this, error) }
        )
    }

    fun openModeSelector() {
        val screen = readScreen() ?: return
        ModeSelector.show(this, screen, this)
    }

    fun readScreen(): String? {
        val root = rootInActiveWindow ?: return null
        val text = extractText(root)
        root.recycle()
        return if (text.isBlank()) null else text.take(2000)
    }

    private fun extractText(node: AccessibilityNodeInfo): String {
        val parts = mutableListOf<String>()
        fun traverse(n: AccessibilityNodeInfo?) {
            n ?: return
            val t = n.text?.toString()?.trim()
            if (!t.isNullOrEmpty()) parts.add(t)
            for (i in 0 until n.childCount) traverse(n.getChild(i))
        }
        traverse(node)
        return parts.joinToString("\n")
    }

    override fun onInterrupt() {
        OverlayManager.dismiss()
        ModeSelector.dismiss()
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        FloatingButtonManager.dismiss()
        OverlayManager.dismiss()
        ModeSelector.dismiss()
    }
}
