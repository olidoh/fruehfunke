package de.fruehfunke.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Plant alle Wecker nach Neustart, Update oder Zeitänderung neu ein. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmScheduler.rescheduleAll(context)
    }
}
