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
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return

        flashDebounce?.let { handler.removeCallbacks(it) }
        flashDebounce = Runnable {
            val screen = readScreen() ?: return@Runnable
            val hash = screen.hashCode()
            if (hash != lastScreenHash) {
                lastScreenHash = hash
                FloatingButtonManager.flash()
                if (MessageNotificationService.awayMode) {
                    MessageNotificationService.sendAwayReply(this, screen)
                }
            }
        }.also { handler.postDelayed(it, 1500) }
    }

    // ── Stay Online ──────────────────────────────────────────────────────────

    fun startStayOnline() {
        stopStayOnline()
        stayOnlineEnabled = true
        scheduleNextPing()
    }

    fun stopStayOnline() {
        stayOnlineEnabled = false
        stayOnlineRunnable?.let { handler.removeCallbacks(it) }
        stayOnlineRunnable = null
    }

    private fun scheduleNextPing() {
        if (!stayOnlineEnabled) return
        stayOnlineRunnable = Runnable {
            performStayOnlineGesture()
            if (stayOnlineEnabled) scheduleNextPing()
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
