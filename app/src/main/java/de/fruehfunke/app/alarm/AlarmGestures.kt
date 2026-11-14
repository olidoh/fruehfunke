package de.fruehfunke.app.alarm

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

enum class Gesture(val label: String) { FLIP("Umgedreht"), SHAKE("Geschüttelt"), PROXIMITY("Annäherung") }

/** Live-Werte für den Sensortest. */
data class SensorSample(val motion: Float, val faceDown: Boolean, val near: Boolean?)

/**
 * Erkennt Umdrehen (Display nach unten), Schütteln und Annäherung.
 * Wird vom Weckdienst und vom Sensortest in den Einstellungen benutzt.
 */
class AlarmGestures(
    context: Context,
    var shakeThreshold: Float,
    private val onGesture: (Gesture) -> Unit,
) : SensorEventListener {
    private val sm = context.getSystemService(SensorManager::class.java)
    private val accel: Sensor? = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val prox: Sensor? = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    var onSample: ((SensorSample) -> Unit)? = null

    private var armedAt = 0L
    private var sawFaceUp = false
    private var faceDownSince = 0L
    private var flipFired = false
    private var lastPeak = 0L
    private var peaks = 0
    private var wasFar = false
    private var near: Boolean? = null

    val hasAccelerometer get() = accel != null
    val hasProximity get() = prox != null

    fun start(useMotion: Boolean, useProximity: Boolean, armDelayMs: Long = 1_500L) {
        armedAt = SystemClock.elapsedRealtime() + armDelayMs
        if (useMotion) accel?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        if (useProximity) prox?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    fun stop() = sm.unregisterListener(this)

    override fun onSensorChanged(e: SensorEvent) {
        val now = SystemClock.elapsedRealtime()
        when (e.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val x = e.values[0]
                val y = e.values[1]
                val z = e.values[2]
                val motion = abs(sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH)
                val faceDown = z < -8f
                onSample?.invoke(SensorSample(motion, faceDown, near))
                if (now < armedAt) return

                // Umdrehen: erst Display oben gesehen, dann 0,4 s Display unten
                when {
                    z > 3f -> {
                        sawFaceUp = true
                        faceDownSince = 0L
                        flipFired = false
                    }
                    faceDown && sawFaceUp -> {
                        if (faceDownSince == 0L) faceDownSince = now
                        else if (!flipFired && now - faceDownSince > 400L) {
                            flipFired = true
                            onGesture(Gesture.FLIP)
                        }
                    }
                    else -> faceDownSince = 0L
                }

                // Schütteln: zwei kräftige Ausschläge innerhalb von 0,8 s
                if (motion > shakeThreshold && now - lastPeak > 150L) {
                    peaks = if (now - lastPeak < 800L) peaks + 1 else 1
                    lastPeak = now
                    if (peaks >= 2) {
                        peaks = 0
                        onGesture(Gesture.SHAKE)
                    }
                }
            }
            Sensor.TYPE_PROXIMITY -> {
                val isNear = e.values[0] < min(e.sensor.maximumRange, 5f)
                near = isNear
                onSample?.invoke(SensorSample(-1f, false, isNear))
                if (!isNear) {
                    wasFar = true
                } else if (wasFar && now >= armedAt) {
                    wasFar = false
                    onGesture(Gesture.PROXIMITY)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
