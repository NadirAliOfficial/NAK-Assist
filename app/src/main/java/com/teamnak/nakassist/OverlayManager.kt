package com.teamnak.nakassist

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView

object OverlayManager {

    private var windowManager: WindowManager? = null
    private var overlayView: android.view.View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var hideRunnable: Runnable? = null
    private var onPasteCallback: ((String) -> Unit)? = null

    fun show(context: Context, text: String, showPaste: Boolean = false, onPaste: ((String) -> Unit)? = null) {
        handler.post {
            dismiss()
            onPasteCallback = onPaste

            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val view = LayoutInflater.from(context).inflate(R.layout.overlay_response, null)

            view.findViewById<TextView>(R.id.tvResponse).text = text
            view.findViewById<TextView>(R.id.tvClose).setOnClickListener { dismiss() }

            val btnPaste = view.findViewById<Button>(R.id.btnPaste)
            if (showPaste && onPaste != null) {
                btnPaste.visibility = android.view.View.VISIBLE
                btnPaste.setOnClickListener {
                    onPaste(text)
                    dismiss()
                }
            } else {
                btnPaste.visibility = android.view.View.GONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_SECURE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM
                y = 120
            }

            overlayView = view
            windowManager?.addView(view, params)

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
