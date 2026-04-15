package com.teamnak.nakassist

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat

class MessageNotificationService : NotificationListenerService() {

    companion object {
        var awayMode = false
        private val FIVERR_PACKAGES = setOf(
            "com.fiverr.fiverr",
            "com.fiverr.android"
        )
        private var lastAwayReplyTime = 0L
        private const val AWAY_REPLY_COOLDOWN_MS = 30_000L // 30s between auto-replies

        fun sendAwayReply(service: AssistAccessibilityService, screenText: String) {
            val now = System.currentTimeMillis()
            if (now - lastAwayReplyTime < AWAY_REPLY_COOLDOWN_MS) return
            lastAwayReplyTime = now
            val lastMsg = screenText.lines().lastOrNull { it.isNotBlank() } ?: return
            GroqApiHelper.ask(
                systemPrompt = "You are a professional Fiverr SELLER. Write a brief, friendly holding reply (1–2 sentences) to acknowledge you received the buyer's message and will get back to them soon. Output ONLY the reply text.",
                userContent = "Conversation:\n$screenText\n\nWrite a holding reply as the seller:",
                maxTokens = 80,
                onResult = { reply ->
                    OverlayManager.show(service, "⚡ Away Reply Ready:\n\n$reply", showPaste = true) { t ->
                        TextInjector.inject(service, t)
                    }
                },
                onError = {}
            )
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in FIVERR_PACKAGES) return

        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text  = extras.getCharSequence("android.text")?.toString() ?: ""

        if (text.isBlank()) return

        // Flash the floating button
        FloatingButtonManager.flash()

        // Show notification overlay
        AssistAccessibilityService.instance?.let { service ->
            OverlayManager.show(service, "💬 New message: $text", showPaste = false)

            // Away mode — auto generate reply
            if (awayMode) {
                GroqApiHelper.ask(
                    systemPrompt = "You are a professional Fiverr SELLER. A buyer just sent you a message. Write a brief, friendly holding reply (1–2 sentences) to acknowledge you received their message and will get back to them soon. Output ONLY the reply text.",
                    userContent = "Buyer's message: $text",
                    maxTokens = 80,
                    onResult = { reply ->
                        OverlayManager.show(service, "⚡ Away Reply Ready:\n\n$reply", showPaste = true) { t ->
                            TextInjector.inject(service, t)
                        }
                    },
                    onError = {}
                )
            }
        }

        // Also show a system notification as backup
        showSystemNotification(title, text)
    }

    private fun showSystemNotification(title: String, text: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "nak_assist_messages"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "NAK Assist Messages", NotificationManager.IMPORTANCE_HIGH)
            )
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("⚡ Fiverr — $title")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
