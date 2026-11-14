package de.fruehfunke.app.ui

import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.fruehfunke.app.alarm.AlarmGestures
import de.fruehfunke.app.data.AppSettings
import de.fruehfunke.app.data.GestureAction
import de.fruehfunke.app.data.RingControl
import de.fruehfunke.app.data.VolumeKeyAction
import kotlin.math.roundToInt

/** Auswahl Nichts / Schlummern / Stopp für eine Geste. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestureRow(
    title: String,
    subtitle: String? = null,
    value: GestureAction,
    enabled: Boolean = true,
    onChange: (GestureAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val options = GestureAction.values()
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, a ->
                SegmentedButton(
                    selected = value == a,
                    enabled = enabled,
                    onClick = { onChange(a) },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                ) { Text(a.label) }
            }
        }
    }
}

/** Einstellungsbereich „Steuerung beim Klingeln“. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlsSection() {
    val context = LocalContext.current
    val s by AppSettings.state.collectAsStateWithLifecycle()
    val probe = remember { AlarmGestures(context, 0f) {} }
    var showTest by remember { mutableStateOf(false) }

    Section("Steuerung beim Klingeln") {
        Text("Bedienung auf dem Weckbildschirm", style = MaterialTheme.typography.bodyLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = s.ringControl == RingControl.SLIDE,
                onClick = { AppSettings.update { it.copy(ringControl = RingControl.SLIDE) } },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text("Wischen") }
            SegmentedButton(
                selected = s.ringControl == RingControl.BUTTONS,
                onClick = { AppSettings.update { it.copy(ringControl = RingControl.BUTTONS) } },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text("Tasten (lange drücken)") }
        }

        GestureRow(
            title = "Umdrehen",
            subtitle = if (probe.hasAccelerometer) "Gerät mit dem Display nach unten hinlegen" else "Kein Bewegungssensor vorhanden",
            value = s.flipAction,
            enabled = probe.hasAccelerometer,
        ) { a -> AppSettings.update { it.copy(flipAction = a) } }
        GestureRow(
            title = "Schütteln",
            subtitle = if (probe.hasAccelerometer) null else "Kein Bewegungssensor vorhanden",
            value = s.shakeAction,
            enabled = probe.hasAccelerometer,
        ) { a -> AppSettings.update { it.copy(shakeAction = a) } }
        GestureRow(
            title = "Power-Knopf",
            subtitle = "Bildschirm während des Klingelns ausschalten",
            value = s.powerAction,
        ) { a -> AppSettings.update { it.copy(powerAction = a) } }
        GestureRow(
            title = "Annähern",
            subtitle = if (probe.hasProximity) "Hand über die Oberseite halten" else "Dieses Gerät hat keinen Näherungssensor",
            value = s.proximityAction,
            enabled = probe.hasProximity,
        ) { a -> AppSettings.update { it.copy(proximityAction = a) } }

        Text("Lautstärketasten", style = MaterialTheme.typography.bodyLarge)
        val volOptions = VolumeKeyAction.values()
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            volOptions.forEachIndexed { i, a ->
                SegmentedButton(
                    selected = s.volumeKeyAction == a,
                    onClick = { AppSettings.update { it.copy(volumeKeyAction = a) } },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = volOptions.size),
                ) { Text(a.label) }
            }
        }

        SliderRow(
            title = "Schüttel-Stärke",
            valueText = sensitivityLabel(s.shakeThreshold),
            value = s.shakeThreshold,
            range = 6f..30f,
            onChange = { v -> AppSettings.update { it.copy(shakeThreshold = (v * 2).roundToInt() / 2f) } },
        )
        SwitchRow(
            title = "Kurz vibrieren",
            subtitle = "Rückmeldung, wenn eine Geste erkannt wurde",
            checked = s.gestureHaptics,
            onChange = { v -> AppSettings.update { it.copy(gestureHaptics = v) } },
        )
        TextButton(onClick = { showTest = true }) { Text("Sensoren testen") }
    }

    if (showTest) SensorTestDialog(onDismiss = { showTest = false })
}

private fun sensitivityLabel(t: Float): String = when {
    t < 10f -> "leicht"
    t < 18f -> "mittel"
    t < 24f -> "kräftig"
    else -> "sehr kräftig"
} + " (${t.roundToInt()} m/s²)"

/** Live-Anzeige der Sensoren zum Einstellen der Schüttel-Stärke. */
@Composable
fun SensorTestDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val s by AppSettings.state.collectAsStateWithLifecycle()
    var motion by remember { mutableFloatStateOf(0f) }
    var peak by remember { mutableFloatStateOf(0f) }
    var faceDown by remember { mutableStateOf(false) }
    var near by remember { mutableStateOf<Boolean?>(null) }
    var lastGesture by remember { mutableStateOf("–") }

    val detector = remember {
        AlarmGestures(context, s.shakeThreshold) { g ->
            lastGesture = g.label
            context.getSystemService(VibratorManager::class.java).defaultVibrator
                .vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
    detector.shakeThreshold = s.shakeThreshold
    DisposableEffect(detector) {
        detector.onSample = { sample ->
            if (sample.motion >= 0f) {
                motion = sample.motion
                if (sample.motion > peak) peak = sample.motion
                faceDown = sample.faceDown
            }
            near = sample.near
        }
        detector.start(useMotion = true, useProximity = true, armDelayMs = 0L)
        onDispose { detector.stop() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sensoren testen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Bewegung: ${motion.roundToInt()} m/s² · Höchstwert ${peak.roundToInt()} m/s²")
                LinearProgressIndicator(
                    progress = { (motion / 30f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(10.dp),
                )
                Text(
                    "Schütteln wird erkannt ab ${s.shakeThreshold.roundToInt()} m/s² (zweimal kurz hintereinander).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SliderRow(
                    title = "Schüttel-Stärke",
                    valueText = sensitivityLabel(s.shakeThreshold),
                    value = s.shakeThreshold,
                    range = 6f..30f,
                    onChange = { v -> AppSettings.update { it.copy(shakeThreshold = (v * 2).roundToInt() / 2f) } },
                )
                Text("Lage: " + if (faceDown) "Display unten" else "Display oben / aufrecht")
                Text(
                    "Nähe: " + when (near) {
                        null -> if (detector.hasProximity) "–" else "kein Sensor"
                        true -> "nah"
                        false -> "fern"
                    }
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Zuletzt erkannt: ", style = MaterialTheme.typography.titleMedium)
                    Text(lastGesture, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fertig") } },
    )
}
