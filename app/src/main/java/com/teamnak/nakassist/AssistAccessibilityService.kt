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

        // When Fiverr conversation screen opens, inject pending away reply
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val pending = MessageNotificationService.pendingAwayReply
            if (pending != null) {
                MessageNotificationService.pendingAwayReply = null
                // Wait 1.5s for Fiverr's UI to fully load, then inject + send
                handler.postDelayed({ injectAndSend(pending) }, 1500)
                return
            }
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

        // Try IME action first (most reliable — same as pressing Send on keyboard)
        // Fall back to finding the send button by searching siblings
        handler.postDelayed({
            val sent = inputNode.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_IME_ACTION)
            if (!sent) {
                val r = rootInActiveWindow
                if (r != null) {
                    findSendButton(r, inputNode)?.let { btn ->
                        btn.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                        btn.recycle()
                    }
                    r.recycle()
                }
            }
            inputNode.recycle()
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
        val cy = metrics.heightPixels / 2f

        // Tiny 4px swipe down then back up — invisible but counts as activity
        val down = Path().apply { moveTo(cx, cy); lineTo(cx, cy + 4f) }
        val up   = Path().apply { moveTo(cx, cy + 4f); lineTo(cx, cy) }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(down, 0,   80))
            .addStroke(GestureDescription.StrokeDescription(up,   100, 80))
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
