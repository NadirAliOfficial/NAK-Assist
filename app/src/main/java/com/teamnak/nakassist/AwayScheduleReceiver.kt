package com.teamnak.nakassist

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.*

/**
 * Handles scheduled Away Mode toggles and daily stats notifications.
 * Triggered by AlarmManager at user-configured times.
 * Also re-registers alarms on device boot.
 */
class AwayScheduleReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_AWAY_ON = "com.teamnak.nakassist.AWAY_ON"
        const val ACTION_AWAY_OFF = "com.teamnak.nakassist.AWAY_OFF"
        const val ACTION_DAILY_STATS = "com.teamnak.nakassist.DAILY_STATS"
        private const val PREFS = "nak_settings"

        fun scheduleAway(context: Context, startMinutes: Int, endMinutes: Int) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.edit()
                .putInt("away_schedule_start", startMinutes)
                .putInt("away_schedule_end", endMinutes)
                .putBoolean("away_schedule_enabled", true)
                .apply()
            registerAlarms(context)
        }

        fun cancelSchedule(context: Context) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.edit().putBoolean("away_schedule_enabled", false).apply()
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(pendingIntent(context, ACTION_AWAY_ON))
            am.cancel(pendingIntent(context, ACTION_AWAY_OFF))
        }

        fun isScheduleEnabled(context: Context): Boolean {
            return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean("away_schedule_enabled", false)
        }

        fun getScheduleTimes(context: Context): Pair<Int, Int> {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return Pair(
                prefs.getInt("away_schedule_start", 23 * 60),  // default 11:00 PM
                prefs.getInt("away_schedule_end", 8 * 60)      // default 8:00 AM
            )
        }

        fun registerAlarms(context: Context) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (!prefs.getBoolean("away_schedule_enabled", false)) return

            val startMinutes = prefs.getInt("away_schedule_start", 23 * 60)
            val endMinutes = prefs.getInt("away_schedule_end", 8 * 60)
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            // Schedule "Away ON" alarm
            am.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextAlarmTimeMs(startMinutes),
                pendingIntent(context, ACTION_AWAY_ON)
            )

            // Schedule "Away OFF" alarm
            am.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextAlarmTimeMs(endMinutes),
                pendingIntent(context, ACTION_AWAY_OFF)
            )

            // Daily stats at 9:00 AM
            am.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextAlarmTimeMs(9 * 60),
                pendingIntent(context, ACTION_DAILY_STATS)
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

        private fun pendingIntent(context: Context, action: String): PendingIntent {
            val intent = Intent(context, AwayScheduleReceiver::class.java).apply {
                this.action = action
            }
            return PendingIntent.getBroadcast(
                context, action.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_AWAY_ON -> {
                MessageNotificationService.awayMode = true
                PersistenceHelper.saveAwayMode(context, true)
                FloatingButtonManager.setAwayMode(true)
                android.util.Log.e("NAK", "Scheduled Away Mode ON")
                // Re-register so it fires again tomorrow
                registerAlarms(context)
            }
            ACTION_AWAY_OFF -> {
                MessageNotificationService.awayMode = false
                PersistenceHelper.saveAwayMode(context, false)
                FloatingButtonManager.setAwayMode(false)
                android.util.Log.e("NAK", "Scheduled Away Mode OFF")
                registerAlarms(context)
            }
            ACTION_DAILY_STATS -> {
                StatsTracker.init(context)
                StatsTracker.showDailySummaryNotification()
                registerAlarms(context)
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                // Re-register all alarms after reboot
                registerAlarms(context)
            }
        }
    }
}
