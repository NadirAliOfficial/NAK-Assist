package com.teamnak.nakassist

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.Button

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

    fun show(context: Context, service: AssistAccessibilityService) {
        handler.post {
            dismissInternal()
            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val view = LayoutInflater.from(context).inflate(R.layout.overlay_mode_selector, null)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.BOTTOM }

            view.findViewById<android.widget.TextView>(R.id.tvCloseMode).setOnClickListener { dismiss() }

            // Stay Online toggle
            val btnStayOnline = view.findViewById<Button>(R.id.btnAutoRefreshToggle)
            fun updateStayOnlineBtn() {
                val on = AssistAccessibilityService.stayOnlineEnabled
                val secs = AssistAccessibilityService.stayOnlineInterval
                btnStayOnline.text = if (on) "🟢 Stay Online: ON (${secs}s)" else "🟢 Stay Online: OFF"
                btnStayOnline.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor(if (on) "#0A3A0A" else "#1A2A1A")
                )
            }
            updateStayOnlineBtn()
            btnStayOnline.setOnClickListener {
                if (AssistAccessibilityService.stayOnlineEnabled) service.stopStayOnline()
                else service.startStayOnline()
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
                            android.graphics.Color.parseColor(
                                if (AssistAccessibilityService.stayOnlineInterval == secs) "#1565C0"
                                else "#2C2C2E"
                            )
                        )
                }
            }
            updateIntervalButtons()
            intervals.forEach { (id, secs) ->
                view.findViewById<Button>(id).setOnClickListener {
                    AssistAccessibilityService.stayOnlineInterval = secs
                    PersistenceHelper.saveStayOnlineInterval(context, secs)
                    if (AssistAccessibilityService.stayOnlineEnabled) service.startStayOnline()
                    updateIntervalButtons()
                    updateStayOnlineBtn()
                }
            }

            // Away Mode toggle
            val btnAway = view.findViewById<Button>(R.id.btnAwayModeToggle)
            fun updateAwayBtn() {
                val on = MessageNotificationService.awayMode
                btnAway.text = if (on) "💤 Away Mode: ON" else "💤 Away Mode: OFF"
                btnAway.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor(if (on) "#4A1060" else "#2C1F3A")
                )
            }
            updateAwayBtn()
            btnAway.setOnClickListener {
                MessageNotificationService.awayMode = !MessageNotificationService.awayMode
                val on = MessageNotificationService.awayMode
                PersistenceHelper.saveAwayMode(context, on)
                FloatingButtonManager.setAwayMode(on)
                updateAwayBtn()
            }

            selectorView = view
            try {
                windowManager?.addView(view, params)
            } catch (e: Exception) {
                android.util.Log.e("NAK", "Failed to add mode selector: ${e.message}")
            }
        }
    }

    fun dismiss() {
        handler.post { dismissInternal() }
    }
}
