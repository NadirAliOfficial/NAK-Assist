package com.teamnak.nakassist

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat

class MessageNotificationService : NotificationListenerService() {

    companion object {
        var awayMode = false
        private val FIVERR_PACKAGES = setOf("com.fiverr.fiverr", "com.fiverr.android")
        private var lastAwayReplyTime = 0L
        private const val AWAY_REPLY_COOLDOWN_MS = 8_000L

        // Set when Away Mode triggers — tells accessibility service to read & reply once Fiverr opens
        var pendingAwayTrigger = false
        var pendingMessage = ""   // last buyer message from notification
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in FIVERR_PACKAGES) return

        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text  = extras.getCharSequence("android.text")?.toString() ?: ""
        if (text.isBlank()) return

        // Ignore notification echoes of our own sent replies (prevents infinite reply loop)
        val sinceLastSent = System.currentTimeMillis() - AssistAccessibilityService.lastSentTimestamp
        if (sinceLastSent < 15_000) {
            android.util.Log.e("NAK", "Ignoring notification — likely echo of our own reply (${sinceLastSent}ms ago)")
            return
        }

        android.util.Log.e("NAK", "Notification: pkg=${sbn.packageName} title=$title text=$text awayMode=$awayMode")

        // Track stats & cache conversation
        StatsTracker.recordMessage()
        ConversationCache.addMessage(title, text)
        AssistAccessibilityService.lastNotificationTimestamp = System.currentTimeMillis()

        // Increment unreplied badge
        FloatingButtonManager.incrementUnreplied()
        FloatingButtonManager.flash()

        // Show a brief overlay so the user knows a message arrived
        AssistAccessibilityService.instance?.let { service ->
            OverlayManager.show(service, "💬 $title: $text", showPaste = false)
        }

        if (awayMode) {
            val now = System.currentTimeMillis()
            val sinceLastReply = now - lastAwayReplyTime
            android.util.Log.e("NAK", "Away mode ON — sinceLastReply=${sinceLastReply}ms cooldown=${AWAY_REPLY_COOLDOWN_MS}ms")
            if (sinceLastReply < AWAY_REPLY_COOLDOWN_MS) {
                android.util.Log.e("NAK", "Cooldown active — skipping")
                return
            }
            lastAwayReplyTime = now

            pendingAwayTrigger = true
            pendingMessage = text
            android.util.Log.e("NAK", "pendingAwayTrigger set, opening Fiverr. svcInstance=${AssistAccessibilityService.instance != null}")
            try {
                sbn.notification.contentIntent?.send(applicationContext, 0, null)
                android.util.Log.e("NAK", "contentIntent sent")
            } catch (e: Exception) {
                android.util.Log.e("NAK", "contentIntent failed: ${e.message}, trying launchIntent")
                applicationContext.packageManager
                    .getLaunchIntentForPackage(sbn.packageName)
                    ?.apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
                    ?.let { applicationContext.startActivity(it) }
            }

            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                android.util.Log.e("NAK", "Fallback fired — pendingAwayTrigger=$pendingAwayTrigger svc=${AssistAccessibilityService.instance != null}")
                if (pendingAwayTrigger) {
                    pendingAwayTrigger = false
                    AssistAccessibilityService.instance?.startAwayReply()
                        ?: android.util.Log.e("NAK", "startAwayReply FAILED — accessibility service is null!")
                }
            }, 2500)
        }

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
            .setGroup("nak_fiverr_messages")
            .build()

        // Use title hashCode so each buyer gets one notification (updated, not stacked)
        manager.notify(title.hashCode(), notification)
    }
}
