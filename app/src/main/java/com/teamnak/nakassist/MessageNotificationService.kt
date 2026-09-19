package com.teamnak.nakassist

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat

class MessageNotificationService : NotificationListenerService() {

    companion object {
        private val FIVERR_PACKAGES = setOf("com.fiverr.fiverr", "com.fiverr.android")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in FIVERR_PACKAGES) return

        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text  = extras.getCharSequence("android.text")?.toString() ?: ""
        if (text.isBlank()) return

        android.util.Log.e("NAK", "Notification: pkg=${sbn.packageName} title=$title text=$text")

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
