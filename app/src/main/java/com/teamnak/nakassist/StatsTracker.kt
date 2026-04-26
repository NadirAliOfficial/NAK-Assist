package com.teamnak.nakassist

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.*

/**
 * Tracks daily messaging stats. Persists across restarts via SharedPreferences.
 * Auto-resets when the date changes.
 */
object StatsTracker {

    private const val PREFS_NAME = "nak_stats"
    private var context: Context? = null

    // In-memory counters (loaded from prefs on init)
    private var messagesReceived = 0
    private var autoRepliesSent = 0
    private var totalResponseTimeMs = 0L
    private var currentDate = ""

    fun init(ctx: Context) {
        context = ctx.applicationContext
        load()
    }

    private fun todayStr(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun load() {
        val prefs = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) ?: return
        currentDate = prefs.getString("date", "") ?: ""
        if (currentDate != todayStr()) {
            // Date changed — archive yesterday's stats and reset
            val yesterdayMessages = prefs.getInt("messages", 0)
            val yesterdayReplies = prefs.getInt("replies", 0)
            val yesterdayResponseTime = prefs.getLong("response_time", 0L)
            // Save as "yesterday" for the daily summary notification
            prefs.edit()
                .putInt("yesterday_messages", yesterdayMessages)
                .putInt("yesterday_replies", yesterdayReplies)
                .putLong("yesterday_response_time", yesterdayResponseTime)
                .apply()
            reset()
        } else {
            messagesReceived = prefs.getInt("messages", 0)
            autoRepliesSent = prefs.getInt("replies", 0)
            totalResponseTimeMs = prefs.getLong("response_time", 0L)
        }
    }

    private fun save() {
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)?.edit()
            ?.putString("date", currentDate)
            ?.putInt("messages", messagesReceived)
            ?.putInt("replies", autoRepliesSent)
            ?.putLong("response_time", totalResponseTimeMs)
            ?.apply()
    }

    private fun reset() {
        currentDate = todayStr()
        messagesReceived = 0
        autoRepliesSent = 0
        totalResponseTimeMs = 0L
        save()
    }

    fun recordMessage() {
        checkDateRollover()
        messagesReceived++
        save()
    }

    fun recordReply(responseTimeMs: Long) {
        checkDateRollover()
        autoRepliesSent++
        totalResponseTimeMs += responseTimeMs
        save()
    }

    private fun checkDateRollover() {
        if (currentDate != todayStr()) reset()
    }

    /** Get today's stats as a short summary string */
    fun getTodaySummary(): String {
        checkDateRollover()
        val avgSec = if (autoRepliesSent > 0) (totalResponseTimeMs / autoRepliesSent / 1000) else 0
        return "Today — $messagesReceived msgs received, $autoRepliesSent auto-replied" +
                if (autoRepliesSent > 0) ", avg response ${avgSec}s" else ""
    }

    /** Get yesterday's stats summary */
    fun getYesterdaySummary(): String? {
        val prefs = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) ?: return null
        val msgs = prefs.getInt("yesterday_messages", 0)
        val replies = prefs.getInt("yesterday_replies", 0)
        val responseTime = prefs.getLong("yesterday_response_time", 0L)
        if (msgs == 0 && replies == 0) return null
        val avgSec = if (replies > 0) (responseTime / replies / 1000) else 0
        return "Yesterday — $msgs msgs, $replies auto-replied" +
                if (replies > 0) ", avg response ${avgSec}s" else ""
    }

    /** Show a notification with yesterday's stats (called once in the morning) */
    fun showDailySummaryNotification() {
        val ctx = context ?: return
        val summary = getYesterdaySummary() ?: return

        val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "nak_assist_stats"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "NAK Assist Daily Stats", NotificationManager.IMPORTANCE_LOW)
            )
        }

        val notification = NotificationCompat.Builder(ctx, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("📊 NAK Assist — Daily Report")
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        manager.notify(9001, notification)
    }
}
