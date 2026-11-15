package de.fruehfunke.app.util

import android.content.Context
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager

object Haptics {
    /** Deutlich spürbarer kurzer Impuls; als Wecker-Vibration, damit ihn System-Einstellungen nicht unterdrücken. */
    fun pulse(context: Context) {
        val v = context.getSystemService(VibratorManager::class.java)?.defaultVibrator ?: return
        if (!v.hasVibrator()) return
        val effect = if (v.hasAmplitudeControl()) VibrationEffect.createOneShot(200, 255)
        else VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE)
        v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
    }

    fun available(context: Context): Boolean =
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator?.hasVibrator() == true
}
