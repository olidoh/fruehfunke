package de.fruehfunke.app.alarm

import android.content.Context
import android.content.Intent
import de.fruehfunke.app.data.AlarmRepository

/** Schlummern und Stoppen – aufrufbar vom Weckbildschirm, der Benachrichtigung und dem Dienst. */
object AlarmActions {

    fun snooze(context: Context, id: Int) {
        val alarm = AlarmRepository.get(id)
        if (alarm == null) {
            silence(context, id)
            return
        }
        if (!alarm.canSnoozeAgain) {
            stop(context, id)
            return
        }
        val until = System.currentTimeMillis() + alarm.snoozeMinutes * 60_000L
        val updated = AlarmRepository.update(id) {
            it.copy(snoozeUntil = until, snoozeCount = it.snoozeCount + 1)
        }
        AlarmScheduler.rescheduleAll(context)
        silence(context, id)
        if (updated != null) Notifications.showSnoozed(context, updated, until)
    }

    fun stop(context: Context, id: Int) {
        val alarm = AlarmRepository.get(id)
        if (alarm != null) {
            if (alarm.isCountdown) {
                AlarmRepository.delete(id)
                AlarmScheduler.cancel(context, id)
            } else {
                AlarmRepository.update(id) {
                    it.copy(
                        snoozeUntil = 0L,
                        snoozeCount = 0,
                        enabled = if (it.isRepeating) it.enabled else false,
                        onceDate = if (it.isRepeating) it.onceDate else null,
                    )
                }
            }
        }
        AlarmScheduler.rescheduleAll(context)
        Notifications.cancelSnoozed(context, id)
        silence(context, id)
    }

    private fun silence(context: Context, id: Int) {
        if (RingingState.current.value?.alarmId == id) {
            context.startService(
                Intent(context, AlarmService::class.java).setAction(AlarmService.ACTION_SILENCE)
            )
        }
    }
}
