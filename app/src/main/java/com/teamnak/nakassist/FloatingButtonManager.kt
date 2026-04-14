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
            var pressStart = 0L

            button.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x; initialY = params.y
                        touchX = event.rawX; touchY = event.rawY
                        moved = false
                        pressStart = System.currentTimeMillis()
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
                            val holdDuration = System.currentTimeMillis() - pressStart
                            val now = System.currentTimeMillis()

                            when {
                                holdDuration > 600 -> {
                                    // Long press → mode selector
                                    AssistAccessibilityService.instance?.openModeSelector()
                                }
                                now - lastTapTime < 400 -> {
                                    // Double tap → summarize
                                    lastTapTime = 0
                                    AssistAccessibilityService.instance?.summarize()
                                }
                                else -> {
                                    // Single tap → smart reply
                                    lastTapTime = now
                                    handler.postDelayed({
                                        if (lastTapTime == now) {
                                            AssistAccessibilityService.instance?.smartReply()
                                        }
                                    }, 350)
                                }
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

    fun dismiss() {
        handler.post {
            buttonView?.let {
                try { windowManager?.removeView(it) } catch (_: Exception) {}
            }
            buttonView = null
        }
    }
}
