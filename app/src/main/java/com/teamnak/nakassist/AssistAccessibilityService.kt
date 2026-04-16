package com.teamnak.nakassist

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AssistAccessibilityService : AccessibilityService() {

    enum class Persona { TRADING, FLUTTER, WEB3, GENERAL }

    companion object {
        var instance: AssistAccessibilityService? = null
        private val FIVERR_PACKAGES = setOf("com.fiverr.fiverr", "com.fiverr.android")

        var stayOnlineEnabled = false
        var stayOnlineInterval = 21 // seconds
        var currentPersona = Persona.TRADING
    }

    private val handler = Handler(Looper.getMainLooper())
    private var flashDebounce: Runnable? = null
    private var lastScreenHash = 0
    private var stayOnlineRunnable: Runnable? = null
    private var isGeneratingAwayReply = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        GroqApiHelper.init(this)
        FloatingButtonManager.show(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in FIVERR_PACKAGES) return

        // When Fiverr conversation screen opens and Away Mode triggered
        // Only on STATE_CHANGED (real screen nav) — CONTENT_CHANGED fires on inbox too early
        if (MessageNotificationService.pendingAwayTrigger &&
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            MessageNotificationService.pendingAwayTrigger = false
            handler.postDelayed({ waitForConversationAndReply(0) }, 1500)
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
        // Strip any existing prefix first, then re-add — prevents https://https:// doubles
        return text
            .replace(Regex("https?://(?:www\\.)?github\\.com/NadirAliOfficial"), "__GITHUB__")
            .replace(Regex("github\\.com/NadirAliOfficial"), "__GITHUB__")
            .replace("__GITHUB__", "https://github.com/NadirAliOfficial")
            .replace(Regex("https?://(?:www\\.)?theteamnak\\.com"), "__TEAMNAK__")
            .replace(Regex("theteamnak\\.com"), "__TEAMNAK__")
            .replace("__TEAMNAK__", "https://www.theteamnak.com")
    }

    private fun waitForConversationAndReply(attempt: Int) {
        if (isGeneratingAwayReply) return
        val screen = readScreen()

        if (screen != null) {
            generateAwayReplyFromScreen(screen)
        } else if (attempt < 15) {
            handler.postDelayed({ waitForConversationAndReply(attempt + 1) }, 1000)
        }
    }

    private fun personaPrompt(): String {
        val blocked = """
IMPORTANT — Fiverr blocks these, never use them:
- gmail, yahoo, email, phone, number, call, skype, zoom, telegram, discord, whatsapp, slack
- payment, pay, paypal, crypto, bitcoin, transfer, invoice
- @ symbols"""

        val style = """
Style:
- Casual, confident, human — like a developer texting a client
- 2-3 sentences max
- Stay strictly on the topic of the conversation
- Never start with "I", "Thanks", "Hi", "Hello"
- No filler phrases
- ONLY reply to the buyer's LAST message — ignore the rest of the history except for context
- If the buyer's last message is very short (?, !!, one word with no clear meaning), ask ONE simple clarifying question

Effective communication rules (critical for Fiverr score):
- Always acknowledge what the buyer just said before answering
- Be specific — mention timelines, process, or deliverables where relevant
- If the buyer's requirement is unclear, ask ONE short clarifying question
- Always end with a clear next step (e.g. "share your requirements", "I'll send an offer", "let me know X")
- If buyer says hi/hello/hey/good morning/good evening or any greeting, reply with a warm 1-sentence opener + ask what they need (e.g. "Hey! What project can I help you with?")
- If buyer says ok/thanks/got it/sure/noted/alright/sounds good, reply with 2-3 words only (e.g. "Sounds great!", "Anytime!", "Perfect!")
- If buyer says bye/goodbye/see you/take care/bye for now/not now, reply with ONLY a short farewell like "Talk soon!" — no pitching, no next steps"""

        val portfolio = "Portfolio: if buyer asks for past work, put EACH URL on its own line:\nWebsite: https://www.theteamnak.com"

        return when (currentPersona) {
            Persona.TRADING -> """You are Nadir Ali Khan — Fiverr Level 2 seller specializing in trading bots (IBKR, MT5, TradingView), algo trading, and financial automation.
$style
$portfolio
IBKR bots: https://github.com/NadirAliOfficial/ninabot
https://github.com/NadirAliOfficial/ibkr-copytrade-engine
https://github.com/NadirAliOfficial/tv-ibkr-v3
MT5 bots: https://github.com/NadirAliOfficial/STAR-EA-v11.20
https://github.com/NadirAliOfficial/eurusd-scalper-ea
TradingView: https://github.com/NadirAliOfficial/tradingview-ibkr-auto-bridge
$blocked
Output ONLY the reply or SKIP."""

            Persona.FLUTTER -> """You are Nadir Ali Khan — Fiverr Level 2 seller specializing in Flutter mobile app development, cross-platform apps (iOS & Android), and Firebase/backend integration.
$style
$portfolio
Flutter/Mobile work: https://github.com/NadirAliOfficial
$blocked
Output ONLY the reply or SKIP."""

            Persona.WEB3 -> """You are Nadir Ali Khan — Fiverr Level 2 seller specializing in Web3, blockchain, Solana, smart contracts, DeFi, and crypto bots.
$style
$portfolio
Web3 projects: https://github.com/NadirAliOfficial/teller-solana-dapp
https://github.com/NadirAliOfficial/flash-loan-arbitrage-bot
$blocked
Output ONLY the reply or SKIP."""

            Persona.GENERAL -> """You are Nadir Ali Khan — Fiverr Level 2 seller and full-stack developer. Expertise: APIs, backend systems, AI automation, bots, web apps.
$style
$portfolio
All projects: https://github.com/NadirAliOfficial
$blocked
Output ONLY the reply or SKIP."""
        }
    }

    private fun generateAwayReplyFromScreen(screen: String) {
        isGeneratingAwayReply = true

        GroqApiHelper.ask(
            systemPrompt = personaPrompt(),
            userContent = "Conversation:\n$screen\n\nWrite Nadir's reply:",
            maxTokens = 120,
            onResult = { reply ->
                val clean = reply.trim()
                if (clean.isBlank() || clean.length < 3) {
                    isGeneratingAwayReply = false
                } else {
                    val delaySec = humanDelay(screen)
                    handler.postDelayed({
                        injectAndSend(fixLinks(clean)) { isGeneratingAwayReply = false }
                    }, delaySec * 1000L)
                }
            },
            onError = { err ->
                isGeneratingAwayReply = false
                android.util.Log.e("NAKAssist", "Away reply error: $err")
            }
        )
    }

    private fun humanDelay(screen: String): Long {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val lastMsgLen = screen.lines().lastOrNull { it.isNotBlank() }?.length ?: 30

        // Night hours (11pm–7am) — slow way down, like waking up to check phone
        if (hour in 0..6 || hour >= 23) return (180..420).random().toLong()

        // Delay scales with how long buyer's message was — longer = more "thinking" time
        return when {
            lastMsgLen < 20  -> (20..45).random().toLong()   // quick "ok/sure" → quick reply
            lastMsgLen < 80  -> (40..90).random().toLong()   // normal message
            lastMsgLen < 200 -> (60..150).random().toLong()  // detailed message → longer think
            else             -> (90..210).random().toLong()  // long message → takes time to read
        }
    }

    private fun injectAndSend(text: String, attempt: Int = 0, onDone: (() -> Unit)? = null) {
        val root = rootInActiveWindow ?: run {
            if (attempt < 5) handler.postDelayed({ injectAndSend(text, attempt + 1, onDone) }, 1000)
            else onDone?.invoke()
            return
        }

        // Find message input field
        val inputNode = findEditableNode(root)
        if (inputNode == null) {
            root.recycle()
            if (attempt < 5) handler.postDelayed({ injectAndSend(text, attempt + 1, onDone) }, 1000)
            else onDone?.invoke()
            return
        }

        val args = android.os.Bundle()
        args.putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        inputNode.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        inputNode.recycle()
        root.recycle()

        // Click Send — use fresh nodes so nothing is stale
        handler.postDelayed({
            val r2 = rootInActiveWindow
            if (r2 != null) {
                val freshInput = findEditableNode(r2)
                if (freshInput != null) {
                    val btn = findSendButton(r2, freshInput)
                    if (btn != null) {
                        btn.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                        btn.recycle()
                    } else {
                        // Fallback: simulate Enter key on the input field
                        val bundle = android.os.Bundle()
                        bundle.putInt(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT, 0)
                        freshInput.performAction(0x00000200) // ACTION_IME_ACTION
                    }
                    freshInput.recycle()
                }
                r2.recycle()
            }
            onDone?.invoke()

            handler.postDelayed({
                val wasOnline = stayOnlineEnabled
                if (wasOnline) stopStayOnline()
                performGlobalAction(GLOBAL_ACTION_BACK)
                if (wasOnline) handler.postDelayed({ startStayOnline() }, 1000)
            }, 800)
        }, 500)
    }

    private fun findEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val result = findEditableNode(node.getChild(i) ?: continue)
            if (result != null) return result
        }
        return null
    }

    private fun findSendButton(root: AccessibilityNodeInfo, inputNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val nodes = root.findAccessibilityNodeInfosByText("Send")
        var found: AccessibilityNodeInfo? = null
        for (node in nodes) {
            if (found == null && node.isClickable) found = node
            else node.recycle()
        }
        if (found != null) return found
        // Fallback: last clickable non-editable sibling of input
        val parent = inputNode.parent ?: return null
        for (i in parent.childCount - 1 downTo 0) {
            val child = parent.getChild(i) ?: continue
            if (child.isClickable && !child.isEditable) {
                parent.recycle()
                return child
            }
            child.recycle()
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
        // Top-right corner: battery/signal icons area — nothing clickable there
        val cx = metrics.widthPixels * 0.95f
        val cy = metrics.heightPixels * 0.01f

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
        return if (text.isBlank()) null else text.takeLast(4000) // enough to cover full conversation context
    }

    private fun extractText(node: AccessibilityNodeInfo): String {
        val parts = mutableListOf<String>()
        val ownPkg = packageName
        fun traverse(n: AccessibilityNodeInfo?, depth: Int) {
            n ?: return
            if (depth > 30) return // prevent ANR on deep trees
            if (n.packageName?.toString() == ownPkg) return
            val t = n.text?.toString()?.trim()
            if (!t.isNullOrEmpty()) parts.add(t)
            for (i in 0 until n.childCount) traverse(n.getChild(i), depth + 1)
        }
        traverse(node, 0)
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
