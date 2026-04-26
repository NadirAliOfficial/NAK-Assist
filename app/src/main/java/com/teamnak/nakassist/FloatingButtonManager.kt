package com.teamnak.nakassist

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView

object FloatingButtonManager {

    private var windowManager: WindowManager? = null
    private var buttonView: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var countdownRunnable: Runnable? = null
    private var longPressRunnable: Runnable? = null
    private var countdownSeconds = 0
    private var unrepliedCount = 0

    fun show(context: Context) {
        if (buttonView != null) return
        handler.post {
            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

            val button = TextView(context).apply {
                text = "⚡"
                textSize = 20f
                setTextColor(android.graphics.Color.WHITE)
                gravity = Gravity.CENTER
                setSingleLine(true)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(android.graphics.Color.parseColor("#DD1B5E20"))
                }
            }

            val density = context.resources.displayMetrics.density
            val sizePx = (52 * density).toInt()

            val params = WindowManager.LayoutParams(
                sizePx, sizePx,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        or WindowManager.LayoutParams.FLAG_SECURE
                        or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 16
                y = 300
            }

            var initialX = 0; var initialY = 0
            var touchX = 0f; var touchY = 0f
            var moved = false
            var longPressed = false

            button.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x; initialY = params.y
                        touchX = event.rawX; touchY = event.rawY
                        moved = false
                        longPressed = false
                        button.alpha = 1f
                        longPressRunnable = Runnable {
                            if (!moved) {
                                longPressed = true
                                button.alpha = 0.4f
                                AssistAccessibilityService.instance?.openModeSelector()
                            }
                        }
                        handler.postDelayed(longPressRunnable!!, 500)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - touchX).toInt()
                        val dy = (event.rawY - touchY).toInt()
                        if (Math.abs(dx) > 8 || Math.abs(dy) > 8) {
                            moved = true
                            longPressRunnable?.let { handler.removeCallbacks(it) }
                        }
                        params.x = initialX + dx
                        params.y = initialY + dy
                        windowManager?.updateViewLayout(button, params)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        longPressRunnable?.let { handler.removeCallbacks(it) }
                        button.alpha = 0.4f
                        if (!moved && !longPressed) {
                            AssistAccessibilityService.instance?.handleTap()
                        }
                        true
                    }
                    else -> false
                }
            }

            button.alpha = 0.4f
            buttonView = button
            try {
                windowManager?.addView(button, params)
            } catch (e: Exception) {
                android.util.Log.e("NAK", "Failed to add floating button: ${e.message}")
            }
        }
    }

    fun flash() {
        handler.post {
            val btn = buttonView ?: return@post
            btn.alpha = 1f
            btn.setBackgroundColor(android.graphics.Color.parseColor("#DD4CAF50"))
            handler.postDelayed({
                btn.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                btn.background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(android.graphics.Color.parseColor(
                        if (MessageNotificationService.awayMode) "#DD9C27B0" else "#DD1B5E20"
                    ))
                }
                btn.alpha = 0.4f
            }, 1000)
        }
    }

    fun setAwayMode(on: Boolean) {
        handler.post {
            buttonView?.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(android.graphics.Color.parseColor(if (on) "#DD9C27B0" else "#DD1B5E20"))
            }
            updateButtonText()
        }
    }

    // ── Unreplied Badge ──────────────────────────────────────────────────

    fun incrementUnreplied() {
        handler.post {
            unrepliedCount++
            updateButtonText()
        }
    }

    fun resetUnreplied() {
        handler.post {
            unrepliedCount = 0
            updateButtonText()
        }
    }

    private fun updateButtonText() {
        val btn = buttonView ?: return
        // Don't override countdown text
        if (countdownRunnable != null) return
        val icon = if (MessageNotificationService.awayMode) "💤" else "⚡"
        btn.text = if (unrepliedCount > 0) "$icon$unrepliedCount" else icon
    }

    fun startCountdown(intervalSeconds: Int) {
        stopCountdown()
        countdownSeconds = intervalSeconds
        countdownRunnable = object : Runnable {
            override fun run() {
                if (countdownSeconds <= 0) {
                    countdownSeconds = intervalSeconds
                }
                buttonView?.text = "$countdownSeconds"
                countdownSeconds--
                handler.postDelayed(this, 1000)
            }
        }.also { handler.post(it) }
    }

    fun stopCountdown() {
        countdownRunnable?.let { handler.removeCallbacks(it) }
        countdownRunnable = null
        updateButtonText()
    }

    fun setKeepScreenOn(on: Boolean) {
        handler.post {
            val btn = buttonView ?: return@post
            val wm = windowManager ?: return@post
            val params = btn.layoutParams as? WindowManager.LayoutParams ?: return@post
            if (on) {
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            } else {
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON.inv()
            }
            try { wm.updateViewLayout(btn, params) } catch (_: Exception) {}
        }
    }

    fun dismiss() {
        handler.post {
            buttonView?.let {
                try { windowManager?.removeView(it) } catch (_: Exception) {}
            }
            buttonView = null
        }
    }
}
