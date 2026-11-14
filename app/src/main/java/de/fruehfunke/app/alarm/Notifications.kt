package de.fruehfunke.app.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import de.fruehfunke.app.R
import de.fruehfunke.app.data.Alarm
import de.fruehfunke.app.ui.MainActivity
import de.fruehfunke.app.ui.RingingActivity
import de.fruehfunke.app.util.TimeText

object Notifications {
    const val CH_RINGING = "ringing"
    const val CH_STATUS = "status"
    const val RINGING_ID = 42
    private const val SNOOZE_BASE = 10_000

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val ringing = NotificationChannel(CH_RINGING, "Klingelnder Wecker", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Zeigt den Weckbildschirm, wenn ein Wecker klingelt"
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
        }
        val status = NotificationChannel(CH_STATUS, "Schlummern", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Hinweis, solange ein Wecker schlummert"
        }
        nm.createNotificationChannels(listOf(ringing, status))
    }

    fun ringing(context: Context, alarm: Alarm?): Notification {
        val fullScreen = PendingIntent.getActivity(
            context, 0,
            Intent(context, RingingActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val b = NotificationCompat.Builder(context, CH_RINGING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(alarm?.label?.ifBlank { null } ?: "Frühfunke")
            .setContentText("Wecker – " + TimeText.time(context, System.currentTimeMillis()))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
        if (alarm != null) {
            if (alarm.canSnoozeAgain) {
                b.addAction(0, "Schlummern", action(context, AlarmReceiver.ACTION_SNOOZE, alarm.id))
            }
            b.addAction(0, "Stopp", action(context, AlarmReceiver.ACTION_STOP, alarm.id))
        }
        return b.build()
    }

    fun showSnoozed(context: Context, alarm: Alarm, until: Long) {
        val n = NotificationCompat.Builder(context, CH_STATUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Schlummert bis " + TimeText.time(context, until))
            .setContentText(alarm.label.ifBlank { "Wecker" })
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setWhen(until)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context, 1, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .addAction(0, "Wecker beenden", action(context, AlarmReceiver.ACTION_DISMISS, alarm.id))
            .build()
        context.getSystemService(NotificationManager::class.java).notify(SNOOZE_BASE + alarm.id, n)
    }

    fun cancelSnoozed(context: Context, id: Int) {
        context.getSystemService(NotificationManager::class.java).cancel(SNOOZE_BASE + id)
    }

    private fun action(context: Context, action: String, id: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, action.hashCode() * 31 + id,
            Intent(context, AlarmReceiver::class.java)
                .setAction(action)
                .putExtra(AlarmScheduler.EXTRA_ALARM_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
