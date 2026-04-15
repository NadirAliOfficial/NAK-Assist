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
    private var lastTapTime = 0L

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

            button.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x; initialY = params.y
                        touchX = event.rawX; touchY = event.rawY
                        moved = false
                        button.alpha = 1f
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - touchX).toInt()
                        val dy = (event.rawY - touchY).toInt()
                        if (Math.abs(dx) > 8 || Math.abs(dy) > 8) moved = true
                        params.x = initialX + dx
                        params.y = initialY + dy
                        windowManager?.updateViewLayout(button, params)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        button.alpha = 0.4f
                        if (!moved) {
                            val now = System.currentTimeMillis()
                            if (now - lastTapTime < 400) {
                                // Double tap → mode selector (delay lets touch events settle)
                                lastTapTime = 0
                                handler.removeCallbacksAndMessages(null)
                                handler.postDelayed({
                                    AssistAccessibilityService.instance?.openModeSelector()
                                }, 150)
                            } else {
                                // Single tap → smart reply
                                lastTapTime = now
                                handler.postDelayed({
                                    if (lastTapTime == now) {
                                        AssistAccessibilityService.instance?.smartReply()
                                    }
                                }, 350)
                            }
                        }
                        true
                    }
                    else -> false
                }
            }

            button.alpha = 0.4f
            buttonView = button
            windowManager?.addView(button, params)
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
            buttonView?.text = if (on) "💤" else "⚡"
        }
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
