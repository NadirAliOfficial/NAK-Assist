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

        // Tracks when we last sent a reply — used to ignore notification echoes of our own messages
        var lastSentTimestamp = 0L

        // Tracks when notification arrived — used for stats response time calculation
        var lastNotificationTimestamp = 0L
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
        StatsTracker.init(this)
        ConversationCache.init(this)

        // Restore persisted state
        val awayOn = PersistenceHelper.loadAwayMode(this)
        MessageNotificationService.awayMode = awayOn
        stayOnlineInterval = PersistenceHelper.loadStayOnlineInterval(this)
        val stayOn = PersistenceHelper.loadStayOnline(this)

        FloatingButtonManager.show(this)
        FloatingButtonManager.setAwayMode(awayOn)

        if (stayOn) startStayOnline()

        // Re-register scheduled alarms
        AwayScheduleReceiver.registerAlarms(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in FIVERR_PACKAGES) return

        // Fast path: Fiverr was not open — STATE_CHANGED fires when it opens fresh
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

    // ── Banned-word safety filter ────────────────────────────────────────────
    // Secondary defence after the prompt — catches any AI slip-through before send.

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

    fun startAwayReply() {
        android.util.Log.e("NAK", "startAwayReply called — isGenerating=$isGeneratingAwayReply")
        waitForConversationAndReply(0)
    }

    private fun waitForConversationAndReply(attempt: Int) {
        if (isGeneratingAwayReply) return
        val screen = readScreen()
        android.util.Log.e("NAK", "waitForConversation attempt=$attempt screen=${if (screen != null) "${screen.length} chars" else "null"}")

        when {
            screen != null && isConversationScreen(screen) -> generateAwayReplyFromScreen(screen)
            attempt < 8 -> handler.postDelayed({ waitForConversationAndReply(attempt + 1) }, 1000)
            else -> {
                // Timed out — use cached history + notification text as fallback context
                val cachedBuyer = ConversationCache.lastBuyerName()
                val cached = cachedBuyer?.let { ConversationCache.getContext(it) }
                val fallback = cached ?: MessageNotificationService.pendingMessage
                if (fallback.isNotBlank()) generateAwayReplyFromScreen(fallback)
            }
        }
    }

    // Returns true if screen content looks like a conversation (not the inbox list)
    private fun isConversationScreen(screen: String): Boolean {
        val pending = MessageNotificationService.pendingMessage
        if (pending.isBlank()) return true
        val keywords = pending.split(" ").filter { it.length > 4 }
        return keywords.isEmpty() || keywords.any { screen.contains(it, ignoreCase = true) }
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

    private fun generateAwayReplyFromScreen(screen: String, retryCount: Int = 0) {
        isGeneratingAwayReply = true
        android.util.Log.e("NAK", "generateAwayReply — calling Groq API (retry=$retryCount)")

        GroqApiHelper.ask(
            systemPrompt = personaPrompt(), // away mode always uses Fiverr chat persona
            userContent = "Conversation:\n$screen\n\nWrite Nadir's reply:",
            maxTokens = 120,
            onResult = { reply ->
                val clean = reply.trim()
                android.util.Log.e("NAK", "Groq reply: '$clean'")
                when {
                    clean.isBlank() || clean.length < 3 -> {
                        android.util.Log.e("NAK", "Reply too short/blank — skipping send")
                        isGeneratingAwayReply = false
                    }
                    containsBannedContent(clean) && retryCount < 2 -> {
                        android.util.Log.e("NAK", "Banned content in reply — regenerating (attempt ${retryCount + 1})")
                        generateAwayReplyFromScreen(screen, retryCount + 1)
                    }
                    containsBannedContent(clean) -> {
                        android.util.Log.e("NAK", "Still banned after 2 retries — skipping send: '$clean'")
                        isGeneratingAwayReply = false
                    }
                    else -> {
                        val linkedClean = fixLinks(clean)
                        ConversationCache.lastBuyerName()?.let { ConversationCache.addReply(it, linkedClean) }
                        lastSentTimestamp = System.currentTimeMillis()
                        StatsTracker.recordReply(lastSentTimestamp - lastNotificationTimestamp)
                        injectAndSend(linkedClean) { isGeneratingAwayReply = false }
                    }
                }
            },
            onError = { err ->
                isGeneratingAwayReply = false
                android.util.Log.e("NAK", "Away reply error: $err")
            }
        )
    }

    // ── Urgency Detection ────────────────────────────────────────────────

    private fun isUrgent(message: String): Boolean {
        val lower = message.lowercase()
        // Keyword signals
        val urgentKeywords = listOf(
            "urgent", "asap", "deadline", "rush", "quickly", "hurry",
            "immediately", "time sensitive", "right now", "as soon as"
        )
        if (urgentKeywords.any { it in lower }) return true

        // Impatient patterns
        val impatient = listOf("hello?", "anyone?", "are you there", "you there?", "??")
        if (impatient.any { it in lower }) return true

        // Excessive punctuation: ???, !!!
        if (Regex("[?!]{3,}").containsMatchIn(message)) return true

        // Mostly CAPS (>50% uppercase letters, min 10 chars to avoid false positives on short msgs)
        val letters = message.filter { it.isLetter() }
        if (letters.length >= 10 && letters.count { it.isUpperCase() } > letters.length * 0.5) return true

        return false
    }

    private fun humanDelay(screen: String): Long {
        val lastMsg = screen.lines().lastOrNull { it.isNotBlank() } ?: ""
        if (isUrgent(lastMsg)) return (3..5).random().toLong()
        return (5..12).random().toLong()
    }

    private fun injectAndSend(text: String, attempt: Int = 0, onDone: (() -> Unit)? = null) {
        val root = rootInActiveWindow ?: run {
            if (attempt < 5) handler.postDelayed({ injectAndSend(text, attempt + 1, onDone) }, 1000)
            else onDone?.invoke()
            return
        }

        val inputNode = findEditableNode(root)
        if (inputNode == null) {
            root.recycle()
            if (attempt < 5) handler.postDelayed({ injectAndSend(text, attempt + 1, onDone) }, 1000)
            else onDone?.invoke()
            return
        }

        val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("", text))

        // Try paste without focusing — avoids keyboard opening and layout shift.
        // Clipboard paste triggers RN onChange even without focus.
        val pastedDirect = inputNode.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_PASTE)
        inputNode.recycle()
        root.recycle()

        if (pastedDirect) {
            // 3s: enough for RN onChange to fire and enable the send button
            clickSendAfterDelay(3000, onDone)
        } else {
            // Fallback: focus first, then paste after keyboard appears
            handler.postDelayed({
                val r2 = rootInActiveWindow
                val input2 = r2?.let { findEditableNode(it) }
                input2?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                input2?.recycle()
                r2?.recycle()
                handler.postDelayed({
                    val r3 = rootInActiveWindow
                    val input3 = r3?.let { findEditableNode(it) }
                    input3?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_PASTE)
                    input3?.recycle()
                    r3?.recycle()
                    clickSendAfterDelay(2000, onDone)
                }, 600)
            }, 200)
        }
    }

    private fun clickSendAfterDelay(delayMs: Long, onDone: (() -> Unit)?, retryCount: Int = 0) {
        handler.postDelayed({
            val r2 = rootInActiveWindow
            var sent = false
            if (r2 != null) {
                val freshInput = findEditableNode(r2)
                if (freshInput != null) {
                    val btn = findSendButton(r2, freshInput)
                    if (btn != null) {
                        if (!btn.isEnabled && retryCount < 4) {
                            // Button found but RN hasn't enabled it yet — wait and retry
                            btn.recycle()
                            freshInput.recycle()
                            r2.recycle()
                            android.util.Log.w("NAK", "Send button disabled, retry ${retryCount + 1}")
                            clickSendAfterDelay(800, onDone, retryCount + 1)
                            return@postDelayed
                        }
                        val rect = android.graphics.Rect()
                        btn.getBoundsInScreen(rect)
                        btn.recycle()
                        val cx = rect.centerX().toFloat()
                        val cy = rect.centerY().toFloat()
                        val path = android.graphics.Path().apply { moveTo(cx, cy); lineTo(cx + 1f, cy) }
                        val gesture = GestureDescription.Builder()
                            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
                            .build()
                        dispatchGesture(gesture, null, null)
                        sent = true
                        android.util.Log.d("NAK", "Send gesture dispatched at ($cx, $cy)")
                    } else {
                        android.util.Log.w("NAK", "Send button not found (retry=$retryCount)")
                    }
                    freshInput.recycle()
                }
                r2.recycle()
            }
            onDone?.invoke()
            if (sent) {
                handler.postDelayed({
                    val wasOnline = stayOnlineEnabled
                    if (wasOnline) stopStayOnline()
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    handler.postDelayed({
                        performGlobalAction(GLOBAL_ACTION_BACK)
                        if (wasOnline) handler.postDelayed({ startStayOnline() }, 1000)
                    }, 500)
                }, 2000)
            }
        }, delayMs)
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
        val inputRect = android.graphics.Rect()
        inputNode.getBoundsInScreen(inputRect)

        // Primary: text search for "Send". We only need the bounds for a gesture tap —
        // isClickable doesn't matter since dispatchGesture works on screen coordinates.
        val byText = root.findAccessibilityNodeInfosByText("Send")
        for (node in byText) {
            val r = android.graphics.Rect()
            node.getBoundsInScreen(r)
            // Must be to the right of centre and not the input itself
            if (!r.isEmpty && r.left > inputRect.centerX()) {
                byText.filter { it !== node }.forEach { it.recycle() }
                android.util.Log.d("NAK", "findSendButton: by text at $r")
                return node
            }
            node.recycle()
        }

        // Fallback: walk tree for any node (clickable or not) whose text is "Send" or
        // whose bounds sit to the right of the input in the toolbar row below it.
        val inputBottom = inputRect.bottom
        var best: AccessibilityNodeInfo? = null
        var bestLeft = Int.MAX_VALUE

        fun traverse(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > 60) return
            val r = android.graphics.Rect()
            node.getBoundsInScreen(r)
            val text = node.text?.toString() ?: node.contentDescription?.toString() ?: ""
            val isSendLabel = text.equals("send", ignoreCase = true)
            val isRightOfInput = r.left > inputRect.centerX() && !r.isEmpty
            val isBelowInput = r.top >= inputBottom - 20  // toolbar is just below or touching input bottom
            if (isRightOfInput && (isSendLabel || (isBelowInput && r.left < bestLeft))) {
                best?.recycle()
                best = AccessibilityNodeInfo.obtain(node)
                bestLeft = r.left
                if (isSendLabel) return // exact match, stop
            }
            for (i in 0 until node.childCount) traverse(node.getChild(i) ?: continue, depth + 1)
        }
        traverse(root, 0)

        if (best != null) android.util.Log.d("NAK", "findSendButton: fallback found at left=$bestLeft")
        else android.util.Log.w("NAK", "findSendButton: not found (inputRect=$inputRect)")
        return best
    }

    // ── Stay Online ──────────────────────────────────────────────────────────

    fun startStayOnline() {
        stopStayOnline()
        stayOnlineEnabled = true
        PersistenceHelper.saveStayOnline(this, true)
        FloatingButtonManager.setKeepScreenOn(true)
        FloatingButtonManager.startCountdown(stayOnlineInterval)
        scheduleNextPing()
    }

    fun stopStayOnline() {
        stayOnlineEnabled = false
        PersistenceHelper.saveStayOnline(this, false)
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
        // Far-right edge, 15% from top — avoids notification shade (top), nav bar (bottom),
        // and all Fiverr UI buttons (center). This area has no clickable elements.
        val cx = metrics.widthPixels * 0.99f
        val cy = metrics.heightPixels * 0.15f

        val path = Path().apply { moveTo(cx, cy); lineTo(cx, cy + 2f) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()

        dispatchGesture(gesture, null, null)
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
        isGeneratingAwayReply = false
        stopStayOnline()
        OverlayManager.dismiss()
        ModeSelector.dismiss()
    }

    override fun onDestroy() {
        super.onDestroy()
        isGeneratingAwayReply = false
        stopStayOnline()
        instance = null
        FloatingButtonManager.dismiss()
        OverlayManager.dismiss()
        ModeSelector.dismiss()
    }
}
