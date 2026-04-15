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

            // Persona selector
            val personaButtons = mapOf(
                R.id.btnPersonaTrading to AssistAccessibilityService.Persona.TRADING,
                R.id.btnPersonaFlutter to AssistAccessibilityService.Persona.FLUTTER,
                R.id.btnPersonaWeb3    to AssistAccessibilityService.Persona.WEB3,
                R.id.btnPersonaGeneral to AssistAccessibilityService.Persona.GENERAL
            )
            fun updatePersonaButtons() {
                personaButtons.forEach { (id, persona) ->
                    view.findViewById<Button>(id).backgroundTintList =
                        android.content.res.ColorStateList.valueOf(
                            if (AssistAccessibilityService.currentPersona == persona)
                                android.graphics.Color.parseColor("#1565C0")
                            else android.graphics.Color.parseColor("#333333")
                        )
                }
            }
            updatePersonaButtons()
            personaButtons.forEach { (id, persona) ->
                view.findViewById<Button>(id).setOnClickListener {
                    AssistAccessibilityService.currentPersona = persona
                    updatePersonaButtons()
                }
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

            view.findViewById<Button>(R.id.btnSettings).setOnClickListener {
                dismiss()
                showApiKeySettings(context)
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

        // Trim input to reduce token usage — take last portion (most recent/relevant)
        val trimmed = screenText.takeLast(3000)

        val (system, user, tokens) = when (mode) {
            "smartreply" -> {
                val lastMsgLen = screenText.lines().lastOrNull { it.isNotBlank() }?.length ?: 50
                val lengthGuide = when {
                    lastMsgLen < 20 -> "1 sentence only."
                    lastMsgLen < 80 -> "1-2 sentences."
                    else            -> "2-3 sentences max."
                }
                Triple(
                    "Fiverr SELLER replying to BUYER. $lengthGuide Output ONLY the reply. Never use blocked words: gmail, email, phone, telegram, discord, whatsapp, payment, paypal, crypto, or any URLs/@symbols.",
                    "Conversation:\n$trimmed\n\nYour reply:",
                    100
                )
            }
            "summarize" -> Triple(
                "Summarize in 3-5 bullet points. Be concise.",
                trimmed, 120
            )
            "improve" -> Triple(
                "Improve clarity and flow of text in <i> tags. Keep same meaning and length. Output ONLY result.",
                "<i>$trimmed</i>", 150
            )
            "rewrite" -> Triple(
                "Rephrase text in <i> tags differently. Keep same meaning and length. Output ONLY result.",
                "<i>$trimmed</i>", 150
            )
            "shorten" -> Triple(
                "Shorten text in <i> tags. Keep core meaning. Output ONLY result.",
                "<i>$trimmed</i>", 80
            )
            "proofread" -> Triple(
                "Fix grammar and spelling in <i> tags. Don't change wording. Output ONLY result.",
                "<i>$trimmed</i>", 150
            )
            "professional" -> Triple(
                "Make text in <i> tags formal and professional. Keep same meaning. Output ONLY result.",
                "<i>$trimmed</i>", 150
            )
            "friendly" -> Triple(
                "Make text in <i> tags warm and conversational. Keep same meaning. Output ONLY result.",
                "<i>$trimmed</i>", 150
            )
            "translate" -> Triple(
                "Translate text in <i> tags: non-English→English, English→Spanish. Output ONLY translation.",
                "<i>$trimmed</i>", 150
            )
            "custom" -> Triple(
                "Follow the instruction exactly. Output ONLY the result.",
                "Instruction: $customCmd\n\nText:\n$trimmed", 150
            )
            else -> Triple("", trimmed, 120)
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

    private fun showApiKeySettings(context: Context) {
        handler.post {
            dismissInternal()
            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                android.graphics.PixelFormat.TRANSLUCENT
            ).apply {
                gravity = android.view.Gravity.BOTTOM
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
            }

            val layout = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setBackgroundColor(android.graphics.Color.parseColor("#EE1B1B1B"))
                val dp16 = (16 * context.resources.displayMetrics.density).toInt()
                setPadding(dp16, dp16, dp16, dp16)
            }

            val title = android.widget.TextView(context).apply {
                text = "⚙ Groq API Keys"
                setTextColor(android.graphics.Color.WHITE)
                textSize = 15f
                val dp = (context.resources.displayMetrics.density).toInt()
                setPadding(0, 0, 0, 12 * dp)
            }

            val hint = android.widget.TextView(context).apply {
                text = "Get free keys at console.groq.com\nEnter one key per line (up to 3 for rotation):"
                setTextColor(android.graphics.Color.parseColor("#AAAAAA"))
                textSize = 12f
                val dp = (context.resources.displayMetrics.density).toInt()
                setPadding(0, 0, 0, 8 * dp)
            }

            val input = android.widget.EditText(context).apply {
                val saved = GroqApiHelper.getSavedKeys(context)
                setText(saved.replace(",", "\n"))
                setHint("gsk_...")
                setTextColor(android.graphics.Color.WHITE)
                setHintTextColor(android.graphics.Color.parseColor("#888888"))
                setBackgroundColor(android.graphics.Color.parseColor("#333333"))
                textSize = 13f
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                minLines = 3
                maxLines = 5
                val dp = (context.resources.displayMetrics.density).toInt()
                setPadding(10 * dp, 8 * dp, 10 * dp, 8 * dp)
                val lp = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = 12 * dp
                layoutParams = lp
            }

            val btnRow = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
            }

            val btnCancel = android.widget.Button(context).apply {
                text = "Cancel"
                setBackgroundColor(android.graphics.Color.parseColor("#555555"))
                setTextColor(android.graphics.Color.WHITE)
                val dp = (context.resources.displayMetrics.density).toInt()
                val lp = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                lp.marginEnd = 8 * dp
                layoutParams = lp
                setOnClickListener { dismiss() }
            }

            val btnSave = android.widget.Button(context).apply {
                text = "Save"
                setBackgroundColor(android.graphics.Color.parseColor("#1B5E20"))
                setTextColor(android.graphics.Color.WHITE)
                layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    val keys = input.text.toString()
                        .split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                        .joinToString(",")
                    GroqApiHelper.saveKeys(context, keys)
                    dismiss()
                    OverlayManager.show(context, "✅ API key saved!")
                }
            }

            btnRow.addView(btnCancel)
            btnRow.addView(btnSave)
            layout.addView(title)
            layout.addView(hint)
            layout.addView(input)
            layout.addView(btnRow)

            selectorView = layout
            windowManager?.addView(layout, params)
        }
    }

    fun dismiss() {
        handler.post { dismissInternal() }
    }
}
