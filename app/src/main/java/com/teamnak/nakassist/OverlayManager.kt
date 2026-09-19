package com.teamnak.nakassist

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
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

            val etResponse = view.findViewById<EditText>(R.id.etResponse)
            etResponse.setText(text)
            view.findViewById<TextView>(R.id.tvClose).setOnClickListener { dismiss() }

            val tvRetry = view.findViewById<TextView>(R.id.tvRetry)
            if (onRetry != null) {
                tvRetry.visibility = android.view.View.VISIBLE
                tvRetry.setOnClickListener { dismiss(); onRetry() }
            } else {
                tvRetry.visibility = android.view.View.GONE
            }

            val btnSend = view.findViewById<Button>(R.id.btnPaste)
            val editable = showPaste && onPaste != null
            if (editable) {
                btnSend.visibility = android.view.View.VISIBLE
                btnSend.setOnClickListener {
                    onPaste?.invoke(etResponse.text.toString())
                    dismiss()
                }
                etResponse.isFocusable = true
                etResponse.isFocusableInTouchMode = true
                etResponse.inputType = InputType.TYPE_CLASS_TEXT or
                        InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                        InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            } else {
                btnSend.visibility = android.view.View.GONE
                etResponse.isFocusable = false
                etResponse.isFocusableInTouchMode = false
                etResponse.inputType = InputType.TYPE_NULL
            }

            // Editable overlays need window focus to accept typing; read-only
            // banners (loading/notification previews) stay non-focusable so they
            // never steal input from the Fiverr app underneath.
            val baseFlags = WindowManager.LayoutParams.FLAG_SECURE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            val flags = if (editable) baseFlags else baseFlags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                flags,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM
                y = 0
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            }

            overlayView = view
            try { windowManager?.addView(view, params) } catch (_: Exception) {}

            // Don't auto-dismiss while the user might still be editing the draft.
            if (!editable) {
                hideRunnable = Runnable { dismiss() }
                handler.postDelayed(hideRunnable!!, 30000)
            }
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
