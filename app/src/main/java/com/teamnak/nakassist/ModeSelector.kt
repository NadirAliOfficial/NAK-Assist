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

    fun show(context: Context) {
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

            // Away Mode toggle
            val btnAway = view.findViewById<Button>(R.id.btnAwayModeToggle)
            fun updateAwayBtn() {
                val on = MessageNotificationService.awayMode
                btnAway.text = if (on) "💤 Away Mode: ON" else "💤 Away Mode: OFF"
                btnAway.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor(if (on) "#2F5FCC" else "#2C2C2E")
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

            // Keep Screen On toggle — just prevents the screen from sleeping
            val btnKeepScreenOn = view.findViewById<Button>(R.id.btnKeepScreenOn)
            fun updateKeepScreenOnBtn() {
                val on = FloatingButtonManager.isKeepScreenOnEnabled()
                btnKeepScreenOn.text = if (on) "🔆 Keep Screen On: ON" else "🔆 Keep Screen On: OFF"
                btnKeepScreenOn.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor(if (on) "#2F5FCC" else "#2C2C2E")
                )
            }
            updateKeepScreenOnBtn()
            btnKeepScreenOn.setOnClickListener {
                val on = !FloatingButtonManager.isKeepScreenOnEnabled()
                FloatingButtonManager.setKeepScreenOn(on)
                PersistenceHelper.saveKeepScreenOn(context, on)
                updateKeepScreenOnBtn()
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
