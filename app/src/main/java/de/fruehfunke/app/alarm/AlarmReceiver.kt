package de.fruehfunke.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_SNOOZE = "de.fruehfunke.app.action.SNOOZE"
        const val ACTION_STOP = "de.fruehfunke.app.action.STOP"
        const val ACTION_DISMISS = "de.fruehfunke.app.action.DISMISS"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, -1)
        when (intent.action) {
            AlarmScheduler.ACTION_FIRE -> ContextCompat.startForegroundService(
                context,
                Intent(context, AlarmService::class.java)
                    .setAction(AlarmService.ACTION_RING)
                    .putExtra(AlarmScheduler.EXTRA_ALARM_ID, id),
            )
            ACTION_SNOOZE -> AlarmActions.snooze(context, id)
            ACTION_STOP, ACTION_DISMISS -> AlarmActions.stop(context, id)
        }
    }
}
