package de.fruehfunke.app.alarm

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.min
import kotlin.math.sqrt

enum class Gesture(val label: String) { FLIP("Umgedreht"), SHAKE("Geschüttelt"), PROXIMITY("Annäherung") }

enum class Posture(val label: String) { FACE_UP("Display oben"), UPRIGHT("aufrecht / schräg"), FACE_DOWN("Display unten") }

/** Live-Werte für den Sensortest. motion < 0 = kein Bewegungswert in diesem Ereignis. */
data class SensorSample(val motion: Float, val gravityZ: Float, val posture: Posture?, val near: Boolean?)

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
    // geglättete Schwerkraft (Tiefpass), damit Schütteln die Lage nicht verfälscht
    private val gravity = FloatArray(3)
    private var gravityReady = false
    private var sawNotFaceDown = false
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
        gravityReady = false
        sawNotFaceDown = false
        faceDownSince = 0L
        flipFired = false
        if (useMotion) accel?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        if (useProximity) prox?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    fun stop() = sm.unregisterListener(this)

    override fun onSensorChanged(e: SensorEvent) {
        val now = SystemClock.elapsedRealtime()
        when (e.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> onAccel(e.values, now)
            Sensor.TYPE_PROXIMITY -> {
                val isNear = e.values[0] < min(e.sensor.maximumRange, 5f)
                near = isNear
                onSample?.invoke(SensorSample(-1f, gravity[2], null, isNear))
                if (!isNear) {
                    wasFar = true
                } else if (wasFar && now >= armedAt) {
                    wasFar = false
                    onGesture(Gesture.PROXIMITY)
                }
            }
        }
    }

    private fun onAccel(v: FloatArray, now: Long) {
        if (!gravityReady) {
            gravity[0] = v[0]; gravity[1] = v[1]; gravity[2] = v[2]
            gravityReady = true
        } else {
            val a = 0.85f
            for (i in 0..2) gravity[i] = a * gravity[i] + (1 - a) * v[i]
        }
        val lx = v[0] - gravity[0]
        val ly = v[1] - gravity[1]
        val lz = v[2] - gravity[2]
        val motion = sqrt(lx * lx + ly * ly + lz * lz)

        // Display unten: Schwerkraft zeigt deutlich aus der Rückseite heraus (bis ca. 50° Neigung)
        val gz = gravity[2]
        val posture = when {
            gz < -6f -> Posture.FACE_DOWN
            gz > 6f -> Posture.FACE_UP
            else -> Posture.UPRIGHT
        }
        onSample?.invoke(SensorSample(motion, gz, posture, near))
        if (now < armedAt) return

        // Umdrehen: vorher nicht nach unten, dann 0,3 s nach unten
        if (posture == Posture.FACE_DOWN) {
            if (sawNotFaceDown) {
                if (faceDownSince == 0L) faceDownSince = now
                else if (!flipFired && now - faceDownSince > 300L) {
                    flipFired = true
                    onGesture(Gesture.FLIP)
                }
            }
        } else if (gz > -2f) {
            sawNotFaceDown = true
            faceDownSince = 0L
            flipFired = false
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

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
