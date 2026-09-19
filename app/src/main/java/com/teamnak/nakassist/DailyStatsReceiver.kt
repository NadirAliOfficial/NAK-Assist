package com.teamnak.nakassist

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.*

/**
 * Fires the daily stats notification at 9:00 AM and re-registers itself on boot.
 */
class DailyStatsReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_DAILY_STATS = "com.teamnak.nakassist.DAILY_STATS"

        fun registerAlarms(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextAlarmTimeMs(9 * 60),
                pendingIntent(context)
            )
        }

        private fun nextAlarmTimeMs(minutesSinceMidnight: Int): Long {
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, minutesSinceMidnight / 60)
                set(Calendar.MINUTE, minutesSinceMidnight % 60)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            // If time already passed today, schedule for tomorrow
            if (cal.timeInMillis <= System.currentTimeMillis()) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            return cal.timeInMillis
        }

        private fun pendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, DailyStatsReceiver::class.java).apply {
                action = ACTION_DAILY_STATS
            }
            return PendingIntent.getBroadcast(
                context, ACTION_DAILY_STATS.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_DAILY_STATS -> {
                StatsTracker.init(context)
                StatsTracker.showDailySummaryNotification()
                registerAlarms(context)
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                registerAlarms(context)
            }
        }
    }
}
