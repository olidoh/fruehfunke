package de.fruehfunke.app.util

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

enum class Perm(val title: String, val description: String, val critical: Boolean) {
    NOTIFICATIONS("Benachrichtigungen", "Ohne sie kann der klingelnde Wecker nicht angezeigt werden.", true),
    FULL_SCREEN("Vollbild beim Wecken", "Zeigt den Weckbildschirm auch auf dem Sperrbildschirm.", true),
    EXACT_ALARMS("Exakte Wecker", "Damit der Wecker auf die Minute genau klingelt.", true),
    BATTERY("Akku-Optimierung aus", "Empfohlen, damit Android die App nachts nicht einschränkt.", false),
}

object Permissions {
    fun granted(context: Context, p: Perm): Boolean = when (p) {
        Perm.NOTIFICATIONS -> context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        Perm.FULL_SCREEN -> if (Build.VERSION.SDK_INT >= 34)
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent() else true
        Perm.EXACT_ALARMS -> context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        Perm.BATTERY -> context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName)
    }

    fun missingCritical(context: Context): List<Perm> =
        Perm.values().filter { it.critical && !granted(context, it) }

    fun settingsIntent(context: Context, p: Perm): Intent {
        val pkg = Uri.parse("package:" + context.packageName)
        return when (p) {
            Perm.NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            Perm.FULL_SCREEN -> if (Build.VERSION.SDK_INT >= 34)
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg)
            else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
            Perm.EXACT_ALARMS -> Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg)
            Perm.BATTERY -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg)
        }
    }
}
