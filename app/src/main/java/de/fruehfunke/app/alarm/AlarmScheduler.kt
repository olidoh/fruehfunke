package de.fruehfunke.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import de.fruehfunke.app.data.Alarm
import de.fruehfunke.app.data.AlarmRepository
import de.fruehfunke.app.data.WeekParity
import de.fruehfunke.app.ui.MainActivity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.IsoFields

object AlarmScheduler {
    const val ACTION_FIRE = "de.fruehfunke.app.action.FIRE"
    const val EXTRA_ALARM_ID = "alarm_id"

    fun zoned(epochMs: Long): ZonedDateTime =
        Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())

    /** Nächster Klingelzeitpunkt inkl. Schlummern, oder null. */
    fun nextTrigger(alarm: Alarm, now: ZonedDateTime = ZonedDateTime.now()): Long? {
        val nowMs = now.toInstant().toEpochMilli()
        if (alarm.snoozeUntil > nowMs) return alarm.snoozeUntil
        if (!alarm.enabled) return null
        return regularTrigger(alarm, now)
    }

    /** Nächste reguläre Weckzeit (ohne Schlummern, Aktiv-Status ignoriert). */
    fun regularTrigger(alarm: Alarm, now: ZonedDateTime = ZonedDateTime.now()): Long? {
        val nowMs = now.toInstant().toEpochMilli()
        if (alarm.isCountdown) return alarm.exactAt.takeIf { it > nowMs }

        val zone = now.zone
        fun at(date: LocalDate) = date.atTime(alarm.hour, alarm.minute).atZone(zone).toInstant().toEpochMilli()

        if (!alarm.isRepeating && alarm.onceDate != null) {
            val t = at(alarm.onceDate)
            return t.takeIf { it > nowMs && it > alarm.skipUntil }
        }

        val today = now.toLocalDate()
        for (offset in 0L..28L) {
            val date = today.plusDays(offset)
            if (alarm.isRepeating) {
                if (date.dayOfWeek !in alarm.days) continue
                if (!parityMatches(alarm.weekParity, date)) continue
            }
            val t = at(date)
            if (t <= nowMs || t <= alarm.skipUntil) continue
            return t
        }
        return null
    }

    private fun parityMatches(parity: WeekParity, date: LocalDate): Boolean {
        val week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        return when (parity) {
            WeekParity.ALL -> true
            WeekParity.EVEN -> week % 2 == 0
            WeekParity.ODD -> week % 2 == 1
        }
    }

    fun nextAlarm(alarms: List<Alarm>, nowMs: Long = System.currentTimeMillis()): Pair<Alarm, Long>? {
        val now = zoned(nowMs)
        return alarms.mapNotNull { a -> nextTrigger(a, now)?.let { a to it } }.minByOrNull { it.second }
    }

    fun rescheduleAll(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        AlarmRepository.alarms.value.forEach { schedule(context, am, it) }
    }

    fun cancel(context: Context, id: Int) {
        context.getSystemService(AlarmManager::class.java).cancel(firePendingIntent(context, id))
    }

    private fun schedule(context: Context, am: AlarmManager, alarm: Alarm) {
        val fire = firePendingIntent(context, alarm.id)
        val trigger = nextTrigger(alarm)
        if (trigger == null) {
            am.cancel(fire)
            return
        }
        val show = PendingIntent.getActivity(
            context, alarm.id,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(trigger, show), fire)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, fire)
        }
    }

    private fun firePendingIntent(context: Context, id: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, id,
            Intent(context, AlarmReceiver::class.java)
                .setAction(ACTION_FIRE)
                .putExtra(EXTRA_ALARM_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
