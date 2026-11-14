package de.fruehfunke.app.ui

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.fruehfunke.app.alarm.AlarmScheduler
import de.fruehfunke.app.alarm.Notifications
import de.fruehfunke.app.data.Alarm
import de.fruehfunke.app.data.AlarmRepository
import de.fruehfunke.app.data.StationRepository
import de.fruehfunke.app.data.WeekParity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.fruehfunke.app.util.Sounds
import de.fruehfunke.app.util.TimeText
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditAlarmScreen(
    alarmId: Int,
    onSaved: (Alarm) -> Unit,
    onDeleted: (Alarm) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val original = remember(alarmId) { if (alarmId == 0) null else AlarmRepository.get(alarmId) }
    var draft by remember(alarmId) { mutableStateOf(original ?: Alarm()) }
    val timeState = rememberTimePickerState(
        initialHour = draft.hour,
        initialMinute = draft.minute,
        is24Hour = TimeText.is24(context),
    )
    var showDatePicker by remember { mutableStateOf(false) }
    var useRadio by remember(alarmId) { mutableStateOf(draft.usesRadio) }
    var showStationDialog by remember { mutableStateOf(false) }
    val stations by StationRepository.stations.collectAsStateWithLifecycle()

    val ringtonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val uri = res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            draft = draft.copy(
                ringtoneUri = uri?.toString(),
                ringtoneTitle = uri?.let { Sounds.ringtoneTitle(context, it) },
            )
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            draft = draft.copy(ringtoneUri = uri.toString(), ringtoneTitle = Sounds.fileName(context, uri))
        }
    }

    val save: () -> Unit = {
        val countdownDate = if (draft.isCountdown) AlarmScheduler.zoned(draft.exactAt).toLocalDate() else null
        val sound = if (useRadio && draft.streamUrl != null) draft
        else draft.copy(streamUrl = null, streamName = null)
        val toSave = sound.copy(
            hour = timeState.hour,
            minute = timeState.minute,
            enabled = true,
            exactAt = 0L,
            onceDate = if (draft.isRepeating) null else (draft.onceDate ?: countdownDate),
            skipUntil = 0L,
            snoozeUntil = 0L,
            snoozeCount = 0,
        )
        val saved = AlarmRepository.upsert(toSave)
        AlarmScheduler.rescheduleAll(context)
        Notifications.cancelSnoozed(context, saved.id)
        onSaved(saved)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (original == null) "Neuer Wecker" else "Wecker bearbeiten") },
                navigationIcon = {
                    IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = "Abbrechen") }
                },
                actions = { TextButton(onClick = save) { Text("Speichern") } },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = timeState)
                }

                OutlinedTextField(
                    value = draft.label,
                    onValueChange = { draft = draft.copy(label = it.take(60)) },
                    label = { Text("Name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Section("Wiederholen") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        DayOfWeek.values().forEach { d ->
                            val on = d in draft.days
                            FilterChip(
                                selected = on,
                                onClick = { draft = draft.copy(days = if (on) draft.days - d else draft.days + d) },
                                label = { Text(TimeText.shortDay(d)) },
                            )
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = { draft = draft.copy(days = TimeText.workdays) }, label = { Text("Werktags") })
                        AssistChip(onClick = { draft = draft.copy(days = TimeText.weekend) }, label = { Text("Wochenende") })
                        AssistChip(onClick = { draft = draft.copy(days = DayOfWeek.values().toSet()) }, label = { Text("Täglich") })
                        AssistChip(onClick = { draft = draft.copy(days = emptySet()) }, label = { Text("Einmalig") })
                    }
                    if (draft.isRepeating) {
                        val options = listOf(
                            WeekParity.ALL to "Jede Woche",
                            WeekParity.EVEN to "Gerade KW",
                            WeekParity.ODD to "Ungerade KW",
                        )
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            options.forEachIndexed { index, (parity, text) ->
                                SegmentedButton(
                                    selected = draft.weekParity == parity,
                                    onClick = { draft = draft.copy(weekParity = parity) },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                                ) { Text(text) }
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                draft.onceDate?.let { "Einmalig am " + TimeText.shortDate(it) }
                                    ?: "Einmalig – zum nächsten passenden Zeitpunkt",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            if (draft.onceDate != null) {
                                TextButton(onClick = { draft = draft.copy(onceDate = null) }) { Text("Entfernen") }
                            }
                            IconButton(onClick = { showDatePicker = true }) {
                                Icon(Icons.Outlined.CalendarMonth, contentDescription = "Datum wählen")
                            }
                        }
                    }
                }

                Section("Weckton") {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = !useRadio,
                            onClick = { useRadio = false },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        ) { Text("Klingelton") }
                        SegmentedButton(
                            selected = useRadio,
                            onClick = {
                                useRadio = true
                                if (draft.streamUrl == null) {
                                    stations.firstOrNull()?.let { draft = draft.copy(streamUrl = it.url, streamName = it.name) }
                                        ?: run { showStationDialog = true }
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        ) { Text("Radio") }
                    }

                    if (useRadio) {
                        if (stations.isEmpty()) {
                            Text(
                                "Noch keine Sender gespeichert.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            stations.forEach { st ->
                                FilterChip(
                                    selected = draft.streamUrl == st.url,
                                    onClick = { draft = draft.copy(streamUrl = st.url, streamName = st.name) },
                                    label = { Text(st.name) },
                                )
                            }
                            AssistChip(
                                onClick = { showStationDialog = true },
                                label = { Text("Sender hinzufügen") },
                                leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                            )
                        }
                        draft.streamUrl?.let { url ->
                            Text(
                                url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            StreamPreviewButton(url = url)
                        }
                        Text(
                            "Startet das Radio nicht innerhalb von 25 Sekunden (z. B. ohne WLAN), klingelt der Ersatz-Weckton.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Text(
                        (if (useRadio) "Ersatz-Weckton: " else "") +
                            (draft.ringtoneTitle ?: if (draft.ringtoneUri == null) "Standard-Weckton" else "Eigener Ton"),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = {
                            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Weckton wählen")
                                .putExtra(
                                    RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                    draft.ringtoneUri?.let { Uri.parse(it) },
                                )
                            runCatching { ringtonePicker.launch(intent) }
                        }) {
                            Icon(Icons.Outlined.MusicNote, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Klingelton")
                        }
                        FilledTonalButton(onClick = { runCatching { filePicker.launch(arrayOf("audio/*")) } }) {
                            Icon(Icons.Outlined.AudioFile, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Eigene Audiodatei")
                        }
                    }
                    SliderRow(
                        title = "Lautstärke",
                        valueText = "${draft.volume} %",
                        value = draft.volume.toFloat(),
                        range = 10f..100f,
                        onChange = { draft = draft.copy(volume = (it / 5f).roundToInt() * 5) },
                    )
                    SliderRow(
                        title = "Ansteigende Lautstärke",
                        valueText = if (draft.rampSeconds == 0) "Aus" else "${draft.rampSeconds} Sek.",
                        value = draft.rampSeconds.toFloat(),
                        range = 0f..120f,
                        onChange = { draft = draft.copy(rampSeconds = (it / 5f).roundToInt() * 5) },
                    )
                    SwitchRow(
                        title = "Vibrieren",
                        checked = draft.vibrate,
                        onChange = { draft = draft.copy(vibrate = it) },
                    )
                }

                Section("Schlummern & Klingeldauer") {
                    SliderRow(
                        title = "Schlummerdauer",
                        valueText = "${draft.snoozeMinutes} Min.",
                        value = draft.snoozeMinutes.toFloat(),
                        range = 1f..30f,
                        onChange = { draft = draft.copy(snoozeMinutes = it.roundToInt()) },
                    )
                    SliderRow(
                        title = "Höchstens so oft schlummern",
                        valueText = if (draft.maxSnoozes == 0) "Unbegrenzt" else "${draft.maxSnoozes}×",
                        value = draft.maxSnoozes.toFloat(),
                        range = 0f..10f,
                        onChange = { draft = draft.copy(maxSnoozes = it.roundToInt()) },
                    )
                    SliderRow(
                        title = "Klingelt maximal",
                        valueText = "${draft.ringMinutes} Min.",
                        value = draft.ringMinutes.toFloat(),
                        range = 1f..30f,
                        onChange = { draft = draft.copy(ringMinutes = it.roundToInt()) },
                    )
                    SwitchRow(
                        title = "Danach weiter schlummern",
                        subtitle = "Sonst wird der Wecker nach Ablauf der Klingeldauer beendet.",
                        checked = draft.snoozeAfterTimeout,
                        onChange = { draft = draft.copy(snoozeAfterTimeout = it) },
                    )
                }

                if (original != null) {
                    OutlinedButton(
                        onClick = {
                            AlarmRepository.delete(original.id)
                            AlarmScheduler.cancel(context, original.id)
                            Notifications.cancelSnoozed(context, original.id)
                            onDeleted(original)
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Wecker löschen")
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showStationDialog) {
        StationDialog(
            onDismiss = { showStationDialog = false },
            onSaved = { st ->
                showStationDialog = false
                useRadio = true
                draft = draft.copy(streamUrl = st.url, streamName = st.name)
            },
        )
    }

    if (showDatePicker) {
        val todayUtc = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = (draft.onceDate ?: LocalDate.now())
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= todayUtc
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { ms ->
                        draft = draft.copy(onceDate = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Abbrechen") } },
        ) {
            DatePicker(state = dateState)
        }
    }
}
