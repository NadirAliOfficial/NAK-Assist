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
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in FIVERR_PACKAGES) return

        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text  = extras.getCharSequence("android.text")?.toString() ?: ""
        if (text.isBlank()) return

        FloatingButtonManager.flash()

        // Show a brief overlay so the user knows a message arrived
        AssistAccessibilityService.instance?.let { service ->
            OverlayManager.show(service, "💬 $title: $text", showPaste = false)
        }

        if (awayMode) {
            val now = System.currentTimeMillis()
            if (now - lastAwayReplyTime < AWAY_REPLY_COOLDOWN_MS) return
            lastAwayReplyTime = now

            pendingAwayTrigger = true
            try {
                sbn.notification.contentIntent?.send(applicationContext, 0, null)
            } catch (_: Exception) {
                applicationContext.packageManager
                    .getLaunchIntentForPackage(sbn.packageName)
                    ?.apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
                    ?.let { applicationContext.startActivity(it) }
            }

            // Fallback: if Fiverr was already open, STATE_CHANGED never fires.
            // Directly trigger after 2.5s to cover both cases.
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (pendingAwayTrigger) {
                    pendingAwayTrigger = false
                    AssistAccessibilityService.instance?.startAwayReply()
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
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
