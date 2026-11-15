package de.fruehfunke.app.ui

import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.fruehfunke.app.data.AppSettings
import de.fruehfunke.app.util.SpeechText
import de.fruehfunke.app.util.TimeText
import de.fruehfunke.app.util.Weather
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

/** Kurzform fürs Display, z. B. „Bonn · bewölkt · 4° (2–9°)“. */
fun weatherLine(w: Weather.Report): String =
    "${w.place} · ${w.condNow} · ${w.tempNow.roundToInt()}° " +
        "(${w.min.roundToInt()}–${w.max.roundToInt()}°)"

/** Einstellungsbereich „Sprechende Uhr & Wetter“. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SpeechSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by AppSettings.state.collectAsStateWithLifecycle()
    val weather by Weather.latest.collectAsStateWithLifecycle()
    var showPlaceSearch by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }

    // Vorschau-Stimme
    val tts = remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.setLanguage(Locale.GERMANY)
                engine?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                tts.value = engine
            }
        }
        onDispose { engine?.shutdown() }
    }

    Section("Sprechende Uhr & Wetter") {
        Text(
            "Beim Wecken liest eine Stimme diesen Text vor. Einschalten pro Wecker unter „Zeitansage“.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Ort
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.weatherName ?: "Kein Ort für das Wetter gewählt", style = MaterialTheme.typography.bodyLarge)
                val w = weather
                if (w != null && s.weatherName != null && w.place == s.weatherName) {
                    Text(
                        weatherLine(w) + " · Stand " + TimeText.time(context, w.fetchedAt) +
                            (if (w.station.isNotBlank()) "\nDWD-Station: ${w.station}" else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (s.weatherName != null) {
                IconButton(onClick = {
                    refreshing = true
                    scope.launch {
                        val r = Weather.refresh(maxAgeMs = 0L)
                        refreshing = false
                        if (r == null) Toast.makeText(context, "Wetter konnte nicht geladen werden", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    if (refreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.Refresh, contentDescription = "Wetter aktualisieren")
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { showPlaceSearch = true }) {
                Icon(Icons.Outlined.Search, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (s.weatherName == null) "Ort suchen" else "Ort ändern")
            }
            if (s.weatherName != null) {
                TextButton(onClick = {
                    AppSettings.update { it.copy(weatherName = null, weatherLat = null, weatherLon = null) }
                }) { Text("Entfernen") }
            }
        }
        Text(
            "Wetterdaten: Deutscher Wetterdienst (offene Daten über Bright Sky) · Ortssuche: Open-Meteo",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider()

        OutlinedTextField(
            value = s.speakText,
            onValueChange = { v -> AppSettings.update { it.copy(speakText = v.take(400)) } },
            label = { Text("Ansagetext") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
        Text("Textbaustein einfügen:", style = MaterialTheme.typography.bodySmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            SpeechText.tokens.forEach { t ->
                AssistChip(
                    onClick = {
                        AppSettings.update { it.copy(speakText = (it.speakText.trimEnd() + " " + t.key).trim()) }
                    },
                    label = { Text("${t.label} ${t.key}") },
                )
            }
        }
        OutlinedTextField(
            value = s.speakFallback,
            onValueChange = { v -> AppSettings.update { it.copy(speakFallback = v.take(400)) } },
            label = { Text("Text ohne Wetter") },
            supportingText = { Text("Wird benutzt, wenn kein Ort gewählt ist oder das Wetter nicht geladen werden kann.") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )

        SliderRow(
            title = "Ansage startet nach",
            valueText = if (s.speakDelaySec == 0) "sofort" else "${s.speakDelaySec} Sek.",
            value = s.speakDelaySec.toFloat(),
            range = 0f..60f,
            onChange = { v -> AppSettings.update { it.copy(speakDelaySec = (v / 5f).roundToInt() * 5) } },
        )
        Text("Ansage wiederholen", style = MaterialTheme.typography.bodyLarge)
        val repeats = listOf(0 to "Einmal", 1 to "jede Min.", 2 to "alle 2 Min.", 5 to "alle 5 Min.")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            repeats.forEachIndexed { i, (m, label) ->
                SegmentedButton(
                    selected = s.speakRepeatMin == m,
                    onClick = { AppSettings.update { it.copy(speakRepeatMin = m) } },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = repeats.size),
                ) { Text(label) }
            }
        }

        FilledTonalButton(
            enabled = tts.value != null,
            onClick = {
                scope.launch {
                    val w = if (SpeechText.needsWeather(s.speakText)) Weather.refresh() else null
                    val text = SpeechText.build(AppSettings.state.value, null, w)
                    tts.value?.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), "preview")
                }
            },
        ) {
            Icon(Icons.Outlined.RecordVoiceOver, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Probe-Ansage")
        }
        if (tts.value == null) {
            Text(
                "Keine Sprachausgabe gefunden. Bitte in den Android-Einstellungen unter „Sprachausgabe“ eine Stimme einrichten.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    if (showPlaceSearch) PlaceSearchDialog(onDismiss = { showPlaceSearch = false })
}

@Composable
private fun PlaceSearchDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Weather.Place>?>(null) }
    var loading by remember { mutableStateOf(false) }

    val search: () -> Unit = {
        if (query.isNotBlank()) {
            loading = true
            scope.launch {
                results = withContext(Dispatchers.IO) { runCatching { Weather.search(query) }.getOrNull() }
                loading = false
                if (results == null) Toast.makeText(context, "Suche fehlgeschlagen – Internet?", Toast.LENGTH_SHORT).show()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.LocationOn, contentDescription = null) },
        title = { Text("Ort für das Wetter") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Stadt oder Ort") },
                    singleLine = true,
                    trailingIcon = {
                        if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else IconButton(onClick = search) { Icon(Icons.Outlined.Search, contentDescription = "Suchen") }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                results?.let { list ->
                    if (list.isEmpty()) Text("Nichts gefunden.")
                    list.forEach { p ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    AppSettings.update { it.copy(weatherName = p.name, weatherLat = p.lat, weatherLon = p.lon) }
                                    scope.launch { Weather.refresh(maxAgeMs = 0L) }
                                    onDismiss()
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            Text(p.name, style = MaterialTheme.typography.bodyLarge)
                            if (p.region.isNotBlank()) {
                                Text(p.region, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
