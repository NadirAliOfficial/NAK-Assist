package com.teamnak.nakassist

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.ContentResolver
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
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
        // Per client, so two clients messaging within seconds both get a draft.
        private val lastDraftTime = mutableMapOf<String, Long>()
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

        // Only react to genuinely new messages — Fiverr re-posts notifications and stacks
        // earlier lines into later ones, which used to duplicate the thread, re-bump the
        // unread badge and burn an AI draft each time.
        val newParts = extractMessages(extras).filter { ConversationCache.addMessage(title, it) }
        if (newParts.isEmpty()) return
        val text = newParts.joinToString("\n")

        StatsTracker.recordMessage()

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
            val key = title.trim().lowercase()
            if (now - (lastDraftTime[key] ?: 0L) >= DRAFT_COOLDOWN_MS) {
                lastDraftTime[key] = now
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
     * short preview only if neither is present. Stacked lines are returned separately so
     * already-captured ones can be skipped.
     */
    private fun extractMessages(extras: android.os.Bundle): List<String> {
        val lines = extras.getCharSequenceArray("android.textLines")
        if (lines != null && lines.isNotEmpty()) {
            return lines.map { it.toString() }.filter { it.isNotBlank() }
        }
        val bigText = extras.getCharSequence("android.bigText")?.toString()
        if (!bigText.isNullOrBlank()) return listOf(bigText)
        val text = extras.getCharSequence("android.text")?.toString()
        return if (text.isNullOrBlank()) emptyList() else listOf(text)
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
            .setContentIntent(openThreadIntent(buyerName))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify(("draft_$buyerName").hashCode(), notification)
    }

    /** Tapping a notification opens that client's thread, with the client list behind it on Back. */
    private fun openThreadIntent(buyerName: String): PendingIntent? {
        val threadIntent = Intent(this, ThreadActivity::class.java)
            .putExtra(ThreadActivity.EXTRA_BUYER_KEY, buyerName.trim().lowercase())
        return TaskStackBuilder.create(this)
            .addNextIntent(Intent(this, ClientsActivity::class.java))
            .addNextIntent(threadIntent)
            .getPendingIntent(
                ("open_$buyerName").hashCode(),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
    }

    private fun showSystemNotification(title: String, text: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        // New channel id: a channel's sound can't be changed once created, so the custom
        // Fiverr chime needs a fresh channel. The old silent-default one is removed.
        val channelId = "nak_fiverr_chime"
        val chime = Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://$packageName/${R.raw.fiverr_chime}")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.deleteNotificationChannel("nak_assist_messages")
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Fiverr Messages", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "New Fiverr client messages — plays the NAK Fiverr chime"
                    setSound(
                        chime,
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 120, 80, 220)
                }
            )
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("⚡ Fiverr — $title")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openThreadIntent(title))
            .setSound(chime)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setGroup("nak_fiverr_messages")
            .build()

        // Use title hashCode so each buyer gets one notification (updated, not stacked)
        manager.notify(title.hashCode(), notification)
    }
}
