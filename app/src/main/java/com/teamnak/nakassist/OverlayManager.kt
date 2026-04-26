package com.teamnak.nakassist

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView

object OverlayManager {

    private var windowManager: WindowManager? = null
    private var overlayView: android.view.View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var hideRunnable: Runnable? = null

    fun show(
        context: Context,
        text: String,
        showPaste: Boolean = false,
        onRetry: (() -> Unit)? = null,
        onPaste: ((String) -> Unit)? = null
    ) {
        handler.post {
            dismiss()

            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val view = LayoutInflater.from(context).inflate(R.layout.overlay_response, null)

            view.findViewById<TextView>(R.id.tvResponse).text = text
            view.findViewById<TextView>(R.id.tvClose).setOnClickListener { dismiss() }

            val tvRetry = view.findViewById<TextView>(R.id.tvRetry)
            if (onRetry != null) {
                tvRetry.visibility = android.view.View.VISIBLE
                tvRetry.setOnClickListener { dismiss(); onRetry() }
            } else {
                tvRetry.visibility = android.view.View.GONE
            }

            val btnSend = view.findViewById<Button>(R.id.btnPaste)
            if (showPaste && onPaste != null) {
                btnSend.visibility = android.view.View.VISIBLE
                btnSend.setOnClickListener { onPaste(text); dismiss() }
            } else {
                btnSend.visibility = android.view.View.GONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_SECURE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM
                y = 0
            }

            overlayView = view
            try { windowManager?.addView(view, params) } catch (_: Exception) {}

            hideRunnable = Runnable { dismiss() }
            handler.postDelayed(hideRunnable!!, 30000)
        }
    }

    fun showLoading(context: Context, message: String = "Thinking...") {
        show(context, message)
    }

    fun dismiss() {
        hideRunnable?.let { handler.removeCallbacks(it) }
        overlayView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        overlayView = null
    }
}
