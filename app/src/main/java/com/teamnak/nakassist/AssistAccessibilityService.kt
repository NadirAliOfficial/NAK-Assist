package com.teamnak.nakassist

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AssistAccessibilityService : AccessibilityService() {

    companion object {
        var instance: AssistAccessibilityService? = null
        private val FIVERR_PACKAGES = setOf("com.fiverr.fiverr", "com.fiverr.android")

        var stayOnlineEnabled = false
        var stayOnlineInterval = 21 // seconds
    }

    private val handler = Handler(Looper.getMainLooper())
    private var flashDebounce: Runnable? = null
    private var lastScreenHash = 0
    private var stayOnlineRunnable: Runnable? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        FloatingButtonManager.show(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in FIVERR_PACKAGES) return

        // When Fiverr conversation screen opens and Away Mode triggered
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            MessageNotificationService.pendingAwayTrigger) {
            MessageNotificationService.pendingAwayTrigger = false
            // Wait for conversation to load, then generate reply
            handler.postDelayed({ waitForConversationAndReply(0) }, 2000)
            return
        }

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

    private fun fixLinks(text: String): String {
        return text
            .replace(Regex("(?<!https?://)(?<!/)\\b(github\\.com)")) { "https://${it.value}" }
            .replace(Regex("(?<!https?://)(?<!/)\\b(theteamnak\\.com)")) { "https://www.${it.value}" }
    }

    private fun waitForConversationAndReply(attempt: Int) {
        val screen = readScreen()
        // Check that screen has actual conversation content (messages visible)
        val hasMessages = screen != null &&
            screen.lines().count { it.isNotBlank() } >= 3 &&
            !screen.contains("Type a message") == false || screen?.contains("Type a message") == true

        if (screen != null && screen.contains("Type a message") && attempt < 8) {
            generateAwayReplyFromScreen(screen)
        } else if (attempt < 8) {
            // Not loaded yet — wait another second and retry
            handler.postDelayed({ waitForConversationAndReply(attempt + 1) }, 1000)
        }
    }

    private fun generateAwayReplyFromScreen(screen: String) {
        val screen = readScreen() ?: return

        GroqApiHelper.ask(
            systemPrompt = """You are Nadir Ali Khan — a Fiverr Level 2 seller and software developer. Reply to the buyer based on the conversation context.

Your expertise: trading bots (IBKR, MT5, TradingView), AI automation, Web3/blockchain, Flutter apps, backend systems/APIs.

Style:
- Casual, confident, human — like a developer texting a client
- 1-2 sentences max
- Stay strictly on the topic of the conversation — do NOT mention technologies unrelated to what buyer asked
- Never start with "I", "Thanks", "Hi", "Hello"
- No filler phrases

Portfolio: if buyer asks for past work/examples, share the website AND the relevant GitHub repos based on the conversation topic:
- Website: https://www.theteamnak.com
- IBKR topic → github.com/NadirAliOfficial/ninabot, github.com/NadirAliOfficial/ibkr-copytrade-engine, github.com/NadirAliOfficial/tv-ibkr-v3
- MT5 topic → github.com/NadirAliOfficial/STAR-EA-v11.20, github.com/NadirAliOfficial/eurusd-scalper-ea
- Web3/Solana topic → github.com/NadirAliOfficial/teller-solana-dapp, github.com/NadirAliOfficial/flash-loan-arbitrage-bot
- TradingView topic → github.com/NadirAliOfficial/tradingview-ibkr-auto-bridge, github.com/NadirAliOfficial/tradingview-capitalcom-bot
- General → github.com/NadirAliOfficial

IMPORTANT — Fiverr blocks these, never use them:
- gmail, yahoo, email, phone, number, call, skype, zoom, telegram, discord, whatsapp, slack
- payment, pay, paypal, crypto, bitcoin, transfer, invoice
- @ symbols

Output ONLY the reply text, nothing else.""",
            userContent = "Conversation:\n$screen\n\nWrite Nadir's reply:",
            maxTokens = 80,
            onResult = { reply -> injectAndSend(fixLinks(reply)) },
            onError = {}
        )
    }

    private fun injectAndSend(text: String, attempt: Int = 0) {
        val root = rootInActiveWindow ?: run {
            if (attempt < 5) handler.postDelayed({ injectAndSend(text, attempt + 1) }, 1000)
            return
        }

        // Find message input field
        val inputNode = findEditableNode(root)
        if (inputNode == null) {
            root.recycle()
            if (attempt < 5) handler.postDelayed({ injectAndSend(text, attempt + 1) }, 1000)
            return
        }

        val args = android.os.Bundle()
        args.putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        inputNode.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, args)

        // Click Send button
        handler.postDelayed({
            val r = rootInActiveWindow
            if (r != null) {
                findSendButton(r, inputNode)?.let { btn ->
                    btn.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                    btn.recycle()
                }
                r.recycle()
            }
            inputNode.recycle()

            // Go back once: conversation → Fiverr inbox
            // Inbox is enough — Fiverr sends notifications when you're not inside the conversation
            handler.postDelayed({
                val wasOnline = stayOnlineEnabled
                if (wasOnline) stopStayOnline()
                performGlobalAction(GLOBAL_ACTION_BACK)
                if (wasOnline) handler.postDelayed({ startStayOnline() }, 1000)
            }, 1500)
        }, 600)

        root.recycle()
    }

    private fun findEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val result = findEditableNode(node.getChild(i) ?: continue)
            if (result != null) return result
        }
        return null
    }

    // Find the Send button — Fiverr shows a "Send" text button bottom-right of input
    private fun findSendButton(root: AccessibilityNodeInfo, inputNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // Search by text "Send", only return if clickable (avoids matching message bubbles)
        val nodes = root.findAccessibilityNodeInfosByText("Send")
        for (node in nodes) {
            if (node.isClickable) return node
            node.recycle()
        }
        // Fallback: get input's parent row, take last clickable non-editable child
        val parent = inputNode.parent ?: return null
        for (i in parent.childCount - 1 downTo 0) {
            val child = parent.getChild(i) ?: continue
            if (child.isClickable && !child.isEditable) return child
        }
        parent.recycle()
        return null
    }

    // ── Stay Online ──────────────────────────────────────────────────────────

    fun startStayOnline() {
        stopStayOnline()
        stayOnlineEnabled = true
        FloatingButtonManager.setKeepScreenOn(true)
        FloatingButtonManager.startCountdown(stayOnlineInterval)
        scheduleNextPing()
    }

    fun stopStayOnline() {
        stayOnlineEnabled = false
        stayOnlineRunnable?.let { handler.removeCallbacks(it) }
        stayOnlineRunnable = null
        FloatingButtonManager.setKeepScreenOn(false)
        FloatingButtonManager.stopCountdown()
    }

    private fun scheduleNextPing() {
        if (!stayOnlineEnabled) return
        stayOnlineRunnable = Runnable {
            flashDebounce?.let { handler.removeCallbacks(it) }
            performStayOnlineGesture()
            if (stayOnlineEnabled) {
                FloatingButtonManager.startCountdown(stayOnlineInterval)
                scheduleNextPing()
            }
        }.also {
            handler.postDelayed(it, stayOnlineInterval * 1000L)
        }
    }

    private fun performStayOnlineGesture() {
        val metrics = resources.displayMetrics
        val cx = metrics.widthPixels / 2f
        // Use top 8% of screen — status bar / title area, nothing clickable there
        val cy = metrics.heightPixels * 0.08f

        val path = Path().apply { moveTo(cx, cy); lineTo(cx, cy + 2f) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()

        dispatchGesture(gesture, null, null)
    }

    // ── AI modes ─────────────────────────────────────────────────────────────

    fun smartReply() {
        val screen = readScreen() ?: return
        val lastMsgLen = screen.lines().lastOrNull { it.isNotBlank() }?.length ?: 50
        val lengthGuide = when {
            lastMsgLen < 20 -> "Reply with 1 short sentence only."
            lastMsgLen < 80 -> "Keep the reply to 1–2 sentences."
            else            -> "Keep the reply to 2–3 sentences max."
        }
        OverlayManager.showLoading(this, "Writing reply...")
        GroqApiHelper.ask(
            systemPrompt = "You are a professional Fiverr SELLER (freelancer). The person messaging you is the BUYER (client). " +
                "Write the next message FROM YOU (the seller) in response to the buyer's last message. " +
                "$lengthGuide Stay on the topics discussed. Output ONLY the reply text — no labels, no explanations.",
            userContent = "Fiverr conversation:\n$screen\n\nWrite your reply as the seller:",
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
        return if (text.isBlank()) null else text.takeLast(1200) // last 1200 chars = most recent messages
    }

    private fun extractText(node: AccessibilityNodeInfo): String {
        val parts = mutableListOf<String>()
        // Skip our own overlay package to avoid contaminating screen text
        val ownPkg = packageName
        fun traverse(n: AccessibilityNodeInfo?) {
            n ?: return
            if (n.packageName?.toString() == ownPkg) return
            val t = n.text?.toString()?.trim()
            if (!t.isNullOrEmpty()) parts.add(t)
            for (i in 0 until n.childCount) traverse(n.getChild(i))
        }
        traverse(node)
        return parts.joinToString("\n")
    }

    override fun onInterrupt() {
        stopStayOnline()
        OverlayManager.dismiss()
        ModeSelector.dismiss()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopStayOnline()
        instance = null
        FloatingButtonManager.dismiss()
        OverlayManager.dismiss()
        ModeSelector.dismiss()
    }
}
