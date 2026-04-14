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

    fun show(context: Context, screenText: String, service: AssistAccessibilityService) {
        handler.post {
            dismiss()
            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val view = LayoutInflater.from(context).inflate(R.layout.overlay_mode_selector, null)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_SECURE,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.BOTTOM }

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
            "smartreply" -> Triple(
                "You are a professional Fiverr seller. Write a professional, friendly and concise reply to the buyer's latest message. Return only the reply text.",
                "Conversation:\n$screenText", 150
            )
            "summarize" -> Triple(
                "Summarize in 3-5 short bullet points. Be brief.",
                screenText, 150
            )
            "improve" -> Triple(
                "Improve this text to be clearer and more professional. Return only the improved text.",
                screenText, 200
            )
            "rewrite" -> Triple(
                "Rewrite this text in a more polished way while keeping the same meaning. Return only the rewritten text.",
                screenText, 200
            )
            "shorten" -> Triple(
                "Shorten this text while keeping the key meaning. Return only the shortened text.",
                screenText, 100
            )
            "proofread" -> Triple(
                "Fix all grammar, spelling, and punctuation errors. Return only the corrected text.",
                screenText, 200
            )
            "professional" -> Triple(
                "Rewrite this text in a formal, professional tone. Return only the rewritten text.",
                screenText, 200
            )
            "friendly" -> Triple(
                "Rewrite this text in a warm, friendly, conversational tone. Return only the rewritten text.",
                screenText, 200
            )
            "translate" -> Triple(
                "Translate this text to English. Return only the translated text.",
                screenText, 200
            )
            "custom" -> Triple(
                "You are a smart AI assistant. Follow the user's instruction exactly. Return only the result.",
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
            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val view = LayoutInflater.from(context).inflate(R.layout.overlay_templates, null)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_SECURE,
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
        handler.post {
            selectorView?.let {
                try { windowManager?.removeView(it) } catch (_: Exception) {}
            }
            selectorView = null
        }
    }
}
