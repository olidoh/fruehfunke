package de.fruehfunke.app.util

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns

object Sounds {
    fun defaultUri(): Uri =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

    fun ringtoneTitle(context: Context, uri: Uri): String? =
        runCatching { RingtoneManager.getRingtone(context, uri)?.getTitle(context) }.getOrNull()

    fun fileName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()
}
