package com.teamnak.nakassist

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

object ModeSelector {

    private var windowManager: WindowManager? = null
    private var selectorView: android.view.View? = null
    private val handler = Handler(Looper.getMainLooper())

    private fun dismissInternal() {
        selectorView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        selectorView = null
    }

    fun show(context: Context, screenText: String, service: AssistAccessibilityService) {
        handler.post {
            dismissInternal()
            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val view = LayoutInflater.from(context).inflate(R.layout.overlay_mode_selector, null)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                // No FLAG_SECURE here — it breaks focusable overlays on many ROMs
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
            }

            view.findViewById<TextView>(R.id.tvCloseMode).setOnClickListener { dismiss() }
            val etCustom = view.findViewById<EditText>(R.id.etCustomCommand)

            val modes = mapOf(
                R.id.btnSmartReply   to "smartreply",
                R.id.btnSummarize    to "summarize",
                R.id.btnImprove      to "improve",
                R.id.btnRewrite      to "rewrite",
                R.id.btnShorten      to "shorten",
                R.id.btnProofread    to "proofread",
                R.id.btnProfessional to "professional",
                R.id.btnFriendly     to "friendly",
                R.id.btnTranslate    to "translate"
            )

            modes.forEach { (id, mode) ->
                view.findViewById<Button>(id).setOnClickListener {
                    dismiss()
                    runMode(context, service, screenText, mode)
                }
            }

            // Stay Online toggle
            val btnStayOnline = view.findViewById<Button>(R.id.btnAutoRefreshToggle)
            fun updateStayOnlineBtn() {
                val on = AssistAccessibilityService.stayOnlineEnabled
                val secs = AssistAccessibilityService.stayOnlineInterval
                btnStayOnline.text = if (on) "🟢 Stay Online: ON (${secs}s)" else "🟢 Stay Online: OFF"
                btnStayOnline.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    if (on) android.graphics.Color.parseColor("#1565C0")
                    else android.graphics.Color.parseColor("#555555")
                )
            }
            updateStayOnlineBtn()
            btnStayOnline.setOnClickListener {
                val svc = AssistAccessibilityService.instance
                if (AssistAccessibilityService.stayOnlineEnabled) {
                    svc?.stopStayOnline()
                } else {
                    svc?.startStayOnline()
                }
                updateStayOnlineBtn()
            }

            // Interval buttons
            val intervals = mapOf(
                R.id.btnInterval5  to 5,
                R.id.btnInterval10 to 10,
                R.id.btnInterval30 to 30,
                R.id.btnInterval60 to 60
            )
            fun updateIntervalButtons() {
                intervals.forEach { (id, secs) ->
                    view.findViewById<Button>(id).backgroundTintList =
                        android.content.res.ColorStateList.valueOf(
                            if (AssistAccessibilityService.stayOnlineInterval == secs)
                                android.graphics.Color.parseColor("#1565C0")
                            else android.graphics.Color.parseColor("#333333")
                        )
                }
            }
            updateIntervalButtons()
            intervals.forEach { (id, secs) ->
                view.findViewById<Button>(id).setOnClickListener {
                    AssistAccessibilityService.stayOnlineInterval = secs
                    if (AssistAccessibilityService.stayOnlineEnabled) {
                        AssistAccessibilityService.instance?.startStayOnline()
                    }
                    updateIntervalButtons()
                    updateStayOnlineBtn()
                }
            }

            // Away Mode toggle
            val btnAway = view.findViewById<Button>(R.id.btnAwayModeToggle)
            btnAway.text = if (MessageNotificationService.awayMode) "💤 Away Mode: ON" else "💤 Away Mode: OFF"
            btnAway.backgroundTintList = android.content.res.ColorStateList.valueOf(
                if (MessageNotificationService.awayMode) android.graphics.Color.parseColor("#9C27B0")
                else android.graphics.Color.parseColor("#555555")
            )
            btnAway.setOnClickListener {
                MessageNotificationService.awayMode = !MessageNotificationService.awayMode
                val on = MessageNotificationService.awayMode
                btnAway.text = if (on) "💤 Away Mode: ON" else "💤 Away Mode: OFF"
                btnAway.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    if (on) android.graphics.Color.parseColor("#9C27B0")
                    else android.graphics.Color.parseColor("#555555")
                )
                FloatingButtonManager.setAwayMode(on)
            }

            view.findViewById<Button>(R.id.btnTemplates).setOnClickListener {
                dismiss()
                showTemplates(context, service)
            }

            view.findViewById<Button>(R.id.btnCustomSend).setOnClickListener {
                val cmd = etCustom.text.toString().trim()
                if (cmd.isNotEmpty()) {
                    dismiss()
                    runMode(context, service, screenText, "custom", cmd)
                }
            }

            selectorView = view
            windowManager?.addView(view, params)
        }
    }

    fun runMode(
        context: Context,
        service: AssistAccessibilityService,
        screenText: String,
        mode: String,
        customCmd: String = ""
    ) {
        val canPaste = mode !in listOf("summarize")

        val (system, user, tokens) = when (mode) {
            "smartreply" -> {
                val lastMsgLen = screenText.lines().lastOrNull { it.isNotBlank() }?.length ?: 50
                val lengthGuide = when {
                    lastMsgLen < 20 -> "Reply with 1 short sentence only."
                    lastMsgLen < 80 -> "Keep the reply to 1–2 sentences."
                    else            -> "Keep the reply to 2–3 sentences max."
                }
                Triple(
                    "You are a professional Fiverr SELLER (freelancer). The person messaging you is the BUYER (client). " +
                    "Write the next message FROM YOU (the seller) in response to the buyer's last message. " +
                    "$lengthGuide Stay on the topics discussed. Output ONLY the reply text — no labels, no explanations.",
                    "Fiverr conversation:\n$screenText\n\nWrite your reply as the seller:",
                    150
                )
            }
            "summarize" -> Triple(
                "Summarize in 3–5 short bullet points. Be brief and clear.",
                screenText, 150
            )
            "improve" -> Triple(
                "Improve the clarity, grammar, and flow of the text in <input> tags. Keep the same meaning, tone, length, and speaker perspective. Output ONLY the improved text.",
                "<input>$screenText</input>", 200
            )
            "rewrite" -> Triple(
                "Rephrase the text in <input> tags using different wording. Keep the same meaning, length, and speaker perspective. Output ONLY the rewritten text.",
                "<input>$screenText</input>", 200
            )
            "shorten" -> Triple(
                "Shorten the text in <input> tags. Keep the core meaning and speaker's voice. Output ONLY the shortened text.",
                "<input>$screenText</input>", 100
            )
            "proofread" -> Triple(
                "Fix all grammar, spelling, and punctuation in the text in <input> tags. Do not change wording or style. Output ONLY the corrected text.",
                "<input>$screenText</input>", 200
            )
            "professional" -> Triple(
                "Rewrite the text in <input> tags to sound formal and professional. Keep the same meaning and length. Output ONLY the rewritten text.",
                "<input>$screenText</input>", 200
            )
            "friendly" -> Triple(
                "Rewrite the text in <input> tags to sound warm and conversational. Keep the same meaning and length. Output ONLY the rewritten text.",
                "<input>$screenText</input>", 200
            )
            "translate" -> Triple(
                "Detect the language of the text in <input> tags. If it is not English, translate it to English. If it is already English, translate it to Spanish. Output ONLY the translation.",
                "<input>$screenText</input>", 200
            )
            "custom" -> Triple(
                "You are a smart AI assistant. Follow the user's instruction exactly. Output ONLY the result, no explanation.",
                "Instruction: $customCmd\n\nContent:\n$screenText", 200
            )
            else -> Triple("", screenText, 150)
        }

        OverlayManager.showLoading(context, "Working...")

        GroqApiHelper.ask(
            systemPrompt = system,
            userContent = user,
            maxTokens = tokens,
            onResult = { result ->
                OverlayManager.show(context, result, showPaste = canPaste) { text ->
                    TextInjector.inject(service, text)
                }
            },
            onError = { error -> OverlayManager.show(context, error) }
        )
    }

    private fun showTemplates(context: Context, service: AssistAccessibilityService) {
        handler.post {
            dismissInternal()
            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val view = LayoutInflater.from(context).inflate(R.layout.overlay_templates, null)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.BOTTOM }

            view.findViewById<TextView>(R.id.tvCloseTemplates).setOnClickListener { dismiss() }

            val container = view.findViewById<LinearLayout>(R.id.templateContainer)
            Templates.list.forEach { template ->
                val btn = Button(context).apply {
                    text = template.title
                    setBackgroundColor(android.graphics.Color.parseColor("#2E7D32"))
                    setTextColor(android.graphics.Color.WHITE)
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    lp.setMargins(0, 0, 0, 8)
                    layoutParams = lp
                    setOnClickListener {
                        dismiss()
                        OverlayManager.show(context, template.text, showPaste = true) { text ->
                            TextInjector.inject(service, text)
                        }
                    }
                }
                container.addView(btn)
            }

            selectorView = view
            windowManager?.addView(view, params)
        }
    }

    fun dismiss() {
        handler.post { dismissInternal() }
    }
}
