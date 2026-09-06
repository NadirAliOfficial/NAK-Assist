package com.teamnak.nakassist

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat

/**
 * Reads Fiverr message notifications only — never touches Fiverr's UI or sends anything.
 * Feeds the client-message aggregator (ConversationCache) and, when Away Mode is on,
 * asks Groq for a draft reply and posts it as a notification with a "Copy reply" action
 * so Nadir reviews and sends it himself from the Fiverr app.
 */
class MessageNotificationService : NotificationListenerService() {

    companion object {
        var awayMode = false
        private val FIVERR_PACKAGES = setOf("com.fiverr.fiverr", "com.fiverr.android")
        private var lastDraftTime = 0L
        private const val DRAFT_COOLDOWN_MS = 8_000L
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        // Independent of the accessibility service so the notification-only
        // (read-only aggregator + draft-only Away Mode) setup works on its own.
        ConversationCache.init(applicationContext)
        StatsTracker.init(applicationContext)
        GroqApiHelper.init(applicationContext)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in FIVERR_PACKAGES) return

        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text = extractFullText(extras)
        if (text.isBlank()) return

        // Track stats & cache conversation
        StatsTracker.recordMessage()
        ConversationCache.addMessage(title, text)

        // Increment unreplied badge
        FloatingButtonManager.incrementUnreplied()
        FloatingButtonManager.flash()

        // Show a brief overlay so the user knows a message arrived (only if the
        // accessibility service — and therefore the overlay — is running)
        AssistAccessibilityService.instance?.let { service ->
            OverlayManager.show(service, "💬 $title: $text", showPaste = false)
        }

        if (awayMode) {
            val now = System.currentTimeMillis()
            if (now - lastDraftTime >= DRAFT_COOLDOWN_MS) {
                lastDraftTime = now
                generateDraftAndNotify(title, text)
            }
        }

        showSystemNotification(title, text)
    }

    /**
     * "android.text" is just the collapsed one-line preview and gets cut off for long
     * messages. Prefer the untruncated versions Android/Fiverr also attach:
     * "android.textLines" (multiple stacked messages, e.g. two arrived close together)
     * and "android.bigText" (the full expanded single message), falling back to the
     * short preview only if neither is present.
     */
    private fun extractFullText(extras: android.os.Bundle): String {
        val lines = extras.getCharSequenceArray("android.textLines")
        if (lines != null && lines.isNotEmpty()) {
            return lines.joinToString("\n") { it.toString() }
        }
        val bigText = extras.getCharSequence("android.bigText")?.toString()
        if (!bigText.isNullOrBlank()) return bigText
        return extras.getCharSequence("android.text")?.toString() ?: ""
    }

    /** Away Mode: draft a reply and notify Nadir to review & send — never sends anything itself. */
    private fun generateDraftAndNotify(buyerName: String, message: String, retryCount: Int = 0) {
        val history = ConversationCache.getContext(buyerName)
        val userContent = buildString {
            if (!history.isNullOrBlank()) {
                append("Conversation history:\n")
                append(history)
                append("\n\n")
            }
            append("Client's latest message: \"$message\"\n\nWrite Nadir's reply:")
        }

        GroqApiHelper.ask(
            systemPrompt = ReplyComposer.personaPrompt(),
            userContent = userContent,
            maxTokens = 120,
            onResult = { reply ->
                val clean = reply.trim()
                when {
                    clean.isBlank() || clean.length < 3 -> {}
                    ReplyComposer.containsBannedContent(clean) && retryCount < 2 ->
                        generateDraftAndNotify(buyerName, message, retryCount + 1)
                    ReplyComposer.containsBannedContent(clean) -> {}
                    else -> {
                        val finalText = ReplyComposer.fixLinks(clean)
                        StatsTracker.recordReply(0L)
                        ConversationCache.setDraft(buyerName, finalText)
                        showDraftNotification(buyerName, finalText)
                    }
                }
            },
            onError = { }
        )
    }

    private fun showDraftNotification(buyerName: String, draft: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "nak_assist_drafts"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "NAK Assist Draft Replies", NotificationManager.IMPORTANCE_HIGH)
            )
        }

        val copyIntent = Intent(this, CopyDraftReceiver::class.java).apply {
            putExtra(CopyDraftReceiver.EXTRA_TEXT, draft)
        }
        val copyPending = PendingIntent.getBroadcast(
            this, buyerName.hashCode(), copyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("✍️ Draft reply for $buyerName")
            .setContentText(draft)
            .setStyle(NotificationCompat.BigTextStyle().bigText(draft))
            .addAction(0, "Copy reply", copyPending)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify(("draft_$buyerName").hashCode(), notification)
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
            .setGroup("nak_fiverr_messages")
            .build()

        // Use title hashCode so each buyer gets one notification (updated, not stacked)
        manager.notify(title.hashCode(), notification)
    }
}
