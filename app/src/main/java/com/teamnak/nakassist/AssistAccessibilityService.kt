package com.teamnak.nakassist

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AssistAccessibilityService : AccessibilityService() {

    companion object {
        var instance: AssistAccessibilityService? = null
        private val FIVERR_PACKAGES = setOf("com.fiverr.fiverr", "com.fiverr.android")
    }

    private val handler = Handler(Looper.getMainLooper())
    private var flashDebounce: Runnable? = null
    private var lastScreenHash = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        GroqApiHelper.init(this)
        StatsTracker.init(this)
        ConversationCache.init(this)

        // Restore persisted state
        val awayOn = PersistenceHelper.loadAwayMode(this)
        MessageNotificationService.awayMode = awayOn

        FloatingButtonManager.show(this)
        FloatingButtonManager.setAwayMode(awayOn)

        // Re-register scheduled alarms
        AwayScheduleReceiver.registerAlarms(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in FIVERR_PACKAGES) return

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return

        flashDebounce?.let { handler.removeCallbacks(it) }
        flashDebounce = Runnable {
            val screen = readScreen() ?: return@Runnable
            val hash = screen.hashCode()
            if (hash != lastScreenHash) {
                lastScreenHash = hash
                FloatingButtonManager.flash()
            }
        }.also { handler.postDelayed(it, 1500) }
    }

    private enum class ScreenContext { FIVERR_CHAT, FIVERR_ORDER, GENERAL }

    private fun detectContext(screen: String): ScreenContext {
        val pkg = rootInActiveWindow?.packageName?.toString() ?: ""
        val isFiverr = pkg in FIVERR_PACKAGES
        if (!isFiverr) return ScreenContext.GENERAL
        val lower = screen.lowercase()
        // Only trigger ORDER mode for UI strings that only appear on the actual order page
        val orderSignals = listOf(
            "order requirements", "order details", "order placed",
            "mark as complete", "request revision", "order #", "in progress"
        )
        return if (orderSignals.count { it in lower } >= 2) ScreenContext.FIVERR_ORDER
        else ScreenContext.FIVERR_CHAT
    }

    private fun promptFor(context: ScreenContext): String = when (context) {
        ScreenContext.FIVERR_CHAT -> ReplyComposer.personaPrompt()
        ScreenContext.FIVERR_ORDER -> orderPrompt()
        ScreenContext.GENERAL -> generalPrompt()
    }

    private fun orderPrompt(): String = """You are Nadir Ali Khan — Fiverr Level 2 seller and full-stack developer.
You are looking at a Fiverr ORDER page, not a chat. Your job is to read the order requirements and summarize:
- What the client wants built
- Key details (deadline, tech, features mentioned)
- Any missing information you should ask about

Be concise. Use bullet points. Don't write a reply — write a summary for Nadir to review.
Output ONLY the summary."""

    private fun generalPrompt(): String = """You are a professional writing assistant helping Nadir Ali Khan reply to a message.
Read the conversation on screen and write a clear, professional, human reply.

Rules:
- 2-3 sentences max
- Match the tone of the conversation (casual if casual, formal if formal)
- Address exactly what was asked or said
- End with a clear next step if needed
- Output ONLY the reply, nothing else."""

    private fun findEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val result = findEditableNode(node.getChild(i) ?: continue)
            if (result != null) return result
        }
        return null
    }

    // ── AI modes ─────────────────────────────────────────────────────────────

    fun handleTap() {
        val typed = readInputFieldText()
        if (typed != null && typed.length > 3) polishText(typed) else smartReply(0)
    }

    fun polishText(text: String) {
        OverlayManager.showLoading(this, "Polishing...")
        GroqApiHelper.ask(
            systemPrompt = """You are a writing assistant for a freelancer on Fiverr.
Fix any grammar or spelling mistakes. If the text can be made clearer or more professional, improve it.
Keep the same tone, meaning, and approximate length.
Output ONLY the corrected text — nothing else.""",
            userContent = text,
            maxTokens = 200,
            onResult = { result ->
                OverlayManager.show(
                    this, result.trim(), showPaste = true,
                    onRetry = { polishText(text) }
                ) { polished -> TextInjector.inject(this, polished) }
            },
            onError = { error -> OverlayManager.show(this, error) }
        )
    }

    fun smartReply(attempt: Int = 0) {
        if (attempt == 0) {
            FloatingButtonManager.resetUnreplied()
            OverlayManager.showLoading(this, "Writing reply...")
        }
        val screen = readScreen()
        if (screen == null) {
            if (attempt < 4) {
                handler.postDelayed({ smartReply(attempt + 1) }, 500)
            } else {
                OverlayManager.show(this, "Open a Fiverr conversation first")
            }
            return
        }

        val context = detectContext(screen)
        val systemPrompt = promptFor(context)

        val buyerName = ConversationCache.lastBuyerName()
        val cachedHistory = if (context == ScreenContext.FIVERR_CHAT)
            buyerName?.let { ConversationCache.getContext(it) } else null

        val userContent = buildString {
            if (!cachedHistory.isNullOrBlank()) {
                append("Conversation history:\n")
                append(cachedHistory)
                append("\n\n")
            }
            when (context) {
                ScreenContext.FIVERR_CHAT -> {
                    append("Current screen (requirements at top, messages below):\n")
                    append(screen)
                    append("\n\nRead the client's requirements and latest message. Write Nadir's reply:")
                }
                ScreenContext.FIVERR_ORDER -> {
                    append("Order page content:\n")
                    append(screen)
                    append("\n\nSummarize this order for Nadir:")
                }
                ScreenContext.GENERAL -> {
                    append("Screen content:\n")
                    append(screen)
                    append("\n\nWrite a reply:")
                }
            }
        }

        GroqApiHelper.ask(
            systemPrompt = systemPrompt,
            userContent = userContent,
            maxTokens = 150,
            onResult = { reply ->
                val cleaned = ReplyComposer.fixLinks(reply.trim())
                if (ReplyComposer.containsBannedContent(cleaned)) {
                    android.util.Log.w("NAK", "Smart reply contains banned content — showing anyway for user review: '$cleaned'")
                }
                OverlayManager.show(
                    this, cleaned, showPaste = true,
                    onRetry = { smartReply(0) }
                ) { text ->
                    TextInjector.inject(this, text)
                    buyerName?.let { ConversationCache.addReply(it, text) }
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
        ModeSelector.show(this)
    }

    fun readScreen(): String? {
        val root = rootInActiveWindow ?: return null
        val text = extractText(root)
        root.recycle()
        if (text.isBlank()) return null
        // Keep top (requirements/brief) + recent conversation — don't drop either end
        return if (text.length > 5000) text.take(1500) + "\n---\n" + text.takeLast(3500)
        else text
    }

    fun readInputFieldText(): String? {
        val root = rootInActiveWindow ?: return null
        val node = findEditableNode(root)
        val text = node?.text?.toString()?.trim()
        node?.recycle()
        root.recycle()
        if (text.isNullOrBlank() || text.length <= 3) return null
        val lower = text.lowercase()
        val placeholders = setOf(
            "type a message", "type a message...", "message", "message...",
            "write a message", "write something", "reply", "."
        )
        if (lower in placeholders || placeholders.any { lower.startsWith(it) }) return null
        return text
    }

    private fun extractText(node: AccessibilityNodeInfo): String {
        val parts = mutableListOf<String>()
        val ownPkg = packageName
        fun traverse(n: AccessibilityNodeInfo?, depth: Int) {
            n ?: return
            if (depth > 60) return // prevent ANR on deep trees (React Native apps can be 50+ deep)
            if (n.packageName?.toString() == ownPkg) return
            val t = n.text?.toString()?.trim()
            if (!t.isNullOrEmpty()) parts.add(t)
            for (i in 0 until n.childCount) traverse(n.getChild(i), depth + 1)
        }
        traverse(node, 0)
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
