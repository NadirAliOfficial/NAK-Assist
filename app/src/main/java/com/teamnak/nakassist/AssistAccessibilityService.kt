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

        var keepAwakeEnabled = false

        // Tracks when a notification arrived — used for stats response-time calculation
        var lastNotificationTimestamp = 0L
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

        // Show the button immediately if Fiverr is already the foreground app
        if (rootInActiveWindow?.packageName?.toString() in FIVERR_PACKAGES) {
            FloatingButtonManager.show(this)
        }

        if (PersistenceHelper.loadKeepAwake(this)) startKeepAwake()

        // Re-register the daily-stats alarm
        DailyStatsReceiver.registerAlarms(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return

        // Show the floating button only while Fiverr is the foreground app —
        // hide it the instant the user switches to anything else.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (pkg in FIVERR_PACKAGES) FloatingButtonManager.show(this)
            else FloatingButtonManager.dismiss()
        }

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

    // ── Banned-word safety filter ────────────────────────────────────────────
    // Secondary defence after the prompt — catches any AI slip-through before the
    // user pastes it. The user still reviews and sends every reply manually.

    private val bannedPatterns = listOf(
        // Platform / contact names
        Regex("""\bgmail\b""", RegexOption.IGNORE_CASE),
        Regex("""\byahoo\b""", RegexOption.IGNORE_CASE),
        Regex("""\bhotmail\b""", RegexOption.IGNORE_CASE),
        Regex("""\be-?mail\b""", RegexOption.IGNORE_CASE),
        Regex("""\bphone\b""", RegexOption.IGNORE_CASE),
        Regex("""\bmobile\b""", RegexOption.IGNORE_CASE),
        Regex("""\bskype\b""", RegexOption.IGNORE_CASE),
        Regex("""\bzoom\b""", RegexOption.IGNORE_CASE),
        Regex("""\btelegram\b""", RegexOption.IGNORE_CASE),
        Regex("""\bdiscord\b""", RegexOption.IGNORE_CASE),
        Regex("""\bwhatsapp\b""", RegexOption.IGNORE_CASE),
        Regex("""\bslack\b""", RegexOption.IGNORE_CASE),
        Regex("""\bsignal\b""", RegexOption.IGNORE_CASE),
        Regex("""\binstagram\b""", RegexOption.IGNORE_CASE),
        Regex("""\btwitter\b""", RegexOption.IGNORE_CASE),
        Regex("""\bfacebook\b""", RegexOption.IGNORE_CASE),
        Regex("""\blinkedin\b""", RegexOption.IGNORE_CASE),
        // Off-platform payment methods (not pricing — pricing is allowed)
        Regex("""\bpayment\b""", RegexOption.IGNORE_CASE),
        Regex("""\bpaypal\b""", RegexOption.IGNORE_CASE),
        Regex("""\binvoice\b""", RegexOption.IGNORE_CASE),
        Regex("""\bcrypto\b""", RegexOption.IGNORE_CASE),
        Regex("""\bbitcoin\b""", RegexOption.IGNORE_CASE),
        Regex("""\bwallet\b""", RegexOption.IGNORE_CASE),
        // @ symbol (email addresses)
        Regex("""@"""),
    )

    fun containsBannedContent(text: String): Boolean =
        bannedPatterns.any { it.containsMatchIn(text) }

    fun fixLinks(text: String): String {
        // Strip any existing prefix first, then re-add — prevents https://https:// doubles
        return text
            .replace(Regex("https?://(?:www\\.)?github\\.com/NadirAliOfficial"), "__GITHUB__")
            .replace(Regex("github\\.com/NadirAliOfficial"), "__GITHUB__")
            .replace("__GITHUB__", "https://github.com/NadirAliOfficial")
            .replace(Regex("https?://(?:www\\.)?theteamnak\\.com"), "__TEAMNAK__")
            .replace(Regex("theteamnak\\.com"), "__TEAMNAK__")
            .replace("__TEAMNAK__", "https://www.theteamnak.com")
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
        ScreenContext.FIVERR_CHAT -> personaPrompt()
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

    fun personaPrompt(): String {
        return """You are Nadir Ali Khan — Fiverr Level 2 seller and full-stack developer.
Expertise: trading bots (IBKR, MT5, TradingView), Flutter mobile apps (iOS & Android), Web3/Solana/DeFi, backend APIs, AI automation, crypto bots.

⛔ FIVERR BANNED — NEVER use ANY of these, not even once:
gmail, yahoo, hotmail, email, e-mail, phone, number, mobile, call, skype, zoom, meet, teams,
telegram, discord, whatsapp, slack, signal, instagram, twitter, facebook, linkedin,
paypal, crypto, bitcoin, invoice, wallet, @ symbol
Using any of these will get Nadir's account flagged or banned. There are NO exceptions.
Instead of contact info → say "we can coordinate here on Fiverr"
Never use the word "payment" — say "I'll send a custom offer" instead

Pricing and timelines ARE allowed — quote $ amounts and delivery days when relevant.
When you quote a price, follow with "I'll send you a custom offer" so it's formal on Fiverr.

MOST IMPORTANT — Reading requirements:
- The screen may show order requirements or a client brief at the top — READ IT before replying
- If client has listed what they need (features, deadlines, tech stack, details), acknowledge those specifics in your reply
- Never give a generic answer when the client already shared their requirements — address them directly

Style:
- Casual, confident — like a developer texting a client
- 1-2 sentences MAX — never more than 2
- ONLY reply to the buyer's LAST message — use the rest for context
- Never start with "I", "Thanks", "Hi", "Hello"
- No filler phrases

Reply rules:
- ALWAYS answer the buyer's actual question first — never dodge or redirect when they ask something directly
- If buyer asks "how will you do the work" / "what's the plan" / "what do I get" → explain the actual deliverable and process in 1-2 sentences, then mention price/timeline
- If buyer asks about cost/price → give a $ amount + timeline, then say "I'll send you a custom offer"
- Be specific — mention timelines, tech, or deliverables when relevant
- Only say "I'll send an offer" when the client has clearly described everything and is ready to proceed — never as a default ending
- End with a question or next step based on where the conversation actually is
- hi/hello/hey → warm 1-sentence opener + ask what they need
- ok/thanks/got it/noted/sounds good → 2-3 words only ("Sounds great!", "Perfect!")
- bye/goodbye/see you → short farewell only, no pitching

Portfolio — ALWAYS write full URLs, never just repo names:
Website: https://www.theteamnak.com
https://github.com/NadirAliOfficial/ninabot
https://github.com/NadirAliOfficial/ibkr-copytrade-engine
https://github.com/NadirAliOfficial/tv-ibkr-v3
https://github.com/NadirAliOfficial/STAR-EA-v11.20
https://github.com/NadirAliOfficial/eurusd-scalper-ea
https://github.com/NadirAliOfficial/tradingview-ibkr-auto-bridge
https://github.com/NadirAliOfficial/teller-solana-dapp
https://github.com/NadirAliOfficial/flash-loan-arbitrage-bot
All projects: https://github.com/NadirAliOfficial
Each URL on its own line.

Context note: conversation history may be partial — if something is unclear, ask ONE short clarifying question instead of assuming.

Output ONLY the reply."""
    }

    // ── Keep Screen Awake ───────────────────────────────────────────────────
    // Just prevents the screen from locking while you're working — no synthetic
    // input, no interaction with the Fiverr app. Purely a screen-timeout override.

    fun startKeepAwake() {
        keepAwakeEnabled = true
        PersistenceHelper.saveKeepAwake(this, true)
        FloatingButtonManager.setKeepScreenOn(true)
    }

    fun stopKeepAwake() {
        keepAwakeEnabled = false
        PersistenceHelper.saveKeepAwake(this, false)
        FloatingButtonManager.setKeepScreenOn(false)
    }

    // ── AI modes ─────────────────────────────────────────────────────────────
    // Every mode below only drafts text into an on-screen box. Nothing is ever
    // sent automatically — the user edits and pastes it themselves, then taps
    // Fiverr's own Send button.

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

        val requestStarted = System.currentTimeMillis()

        GroqApiHelper.ask(
            systemPrompt = systemPrompt,
            userContent = userContent,
            maxTokens = 150,
            onResult = { reply ->
                val cleaned = fixLinks(reply.trim())
                if (containsBannedContent(cleaned)) {
                    android.util.Log.w("NAK", "Smart reply contains banned content — showing anyway for user review: '$cleaned'")
                }
                OverlayManager.show(
                    this, cleaned, showPaste = true,
                    onRetry = { smartReply(0) }
                ) { text ->
                    TextInjector.inject(this, text)
                    buyerName?.let { ConversationCache.addReply(it, text) }
                    if (context == ScreenContext.FIVERR_CHAT && lastNotificationTimestamp > 0) {
                        StatsTracker.recordReply(System.currentTimeMillis() - lastNotificationTimestamp)
                    } else {
                        StatsTracker.recordReply(System.currentTimeMillis() - requestStarted)
                    }
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
        ModeSelector.show(this, this)
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

    private fun findEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val result = findEditableNode(node.getChild(i) ?: continue)
            if (result != null) return result
        }
        return null
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
        stopKeepAwake()
        OverlayManager.dismiss()
        ModeSelector.dismiss()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopKeepAwake()
        instance = null
        FloatingButtonManager.dismiss()
        OverlayManager.dismiss()
        ModeSelector.dismiss()
    }
}
