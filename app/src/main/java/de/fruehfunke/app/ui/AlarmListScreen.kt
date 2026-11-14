package de.fruehfunke.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AlarmOff
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.fruehfunke.app.alarm.AlarmScheduler
import de.fruehfunke.app.alarm.Notifications
import de.fruehfunke.app.data.Alarm
import de.fruehfunke.app.data.AlarmRepository
import de.fruehfunke.app.util.Perm
import de.fruehfunke.app.util.Permissions
import de.fruehfunke.app.util.TimeText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmListScreen(
    snackbar: SnackbarHostState,
    onEdit: (Int) -> Unit,
    onSettings: () -> Unit,
    onSaved: (Alarm) -> Unit,
    onDeleted: (Alarm) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val alarms by AlarmRepository.alarms.collectAsStateWithLifecycle()
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(15_000)
            value = System.currentTimeMillis()
        }
    }
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumeTick++ }
    val missing = remember(resumeTick) { Permissions.missingCritical(context) }
    var showCountdown by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Frühfunke") },
                actions = {
                    IconButton(onClick = { showCountdown = true }) {
                        Icon(Icons.Outlined.Timer, contentDescription = "Countdown-Wecker")
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Einstellungen")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onEdit(0) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Wecker") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 340.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 104.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { NextAlarmHero(alarms, now) }
            if (missing.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { PermissionCard(missing) }
            }
            if (alarms.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { EmptyState() }
            }
            items(alarms, key = { it.id }) { alarm ->
                AlarmCard(
                    alarm = alarm,
                    now = now,
                    onClick = { onEdit(alarm.id) },
                    onToggle = { on ->
                        val updated = AlarmRepository.update(alarm.id) {
                            it.copy(enabled = on, snoozeUntil = 0L, snoozeCount = 0, skipUntil = 0L)
                        }
                        AlarmScheduler.rescheduleAll(context)
                        Notifications.cancelSnoozed(context, alarm.id)
                        if (on && updated != null) onSaved(updated)
                    },
                    onSkip = {
                        val next = AlarmScheduler.regularTrigger(alarm)
                        if (next != null) {
                            val updated = AlarmRepository.update(alarm.id) { it.copy(skipUntil = next) }
                            AlarmScheduler.rescheduleAll(context)
                            val after = updated?.let { AlarmScheduler.nextTrigger(it) }
                            scope.launch {
                                snackbar.showSnackbar(
                                    "Übersprungen" + (after?.let { " · danach " + TimeText.dayAndTime(context, it) } ?: "")
                                )
                            }
                        }
                    },
                    onUnskip = {
                        AlarmRepository.update(alarm.id) { it.copy(skipUntil = 0L) }
                        AlarmScheduler.rescheduleAll(context)
                    },
                    onDuplicate = {
                        AlarmRepository.upsert(alarm.copy(id = 0, snoozeUntil = 0L, snoozeCount = 0, skipUntil = 0L))
                        AlarmScheduler.rescheduleAll(context)
                    },
                    onDelete = {
                        AlarmRepository.delete(alarm.id)
                        AlarmScheduler.cancel(context, alarm.id)
                        Notifications.cancelSnoozed(context, alarm.id)
                        onDeleted(alarm)
                    },
                )
            }
        }
    }

    if (showCountdown) {
        CountdownDialog(
            onDismiss = { showCountdown = false },
            onStart = { minutes ->
                showCountdown = false
                val at = System.currentTimeMillis() + minutes * 60_000L
                val z = AlarmScheduler.zoned(at)
                val saved = AlarmRepository.upsert(
                    Alarm(
                        hour = z.hour,
                        minute = z.minute,
                        exactAt = at,
                        label = "Countdown $minutes Min.",
                        rampSeconds = 10,
                    )
                )
                AlarmScheduler.rescheduleAll(context)
                onSaved(saved)
            },
        )
    }
}

@Composable
private fun NextAlarmHero(alarms: List<Alarm>, now: Long) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val next = remember(alarms, now) { AlarmScheduler.nextAlarm(alarms, now) }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(Brush.linearGradient(listOf(cs.primaryContainer, cs.tertiaryContainer)))
            .padding(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (next != null) "Nächster Wecker" else "Kein Wecker aktiv",
                    style = MaterialTheme.typography.labelLarge,
                    color = cs.onPrimaryContainer.copy(alpha = 0.75f),
                )
                if (next != null) {
                    Text(
                        TimeText.dayAndTime(context, next.second),
                        style = MaterialTheme.typography.displaySmall,
                        color = cs.onPrimaryContainer,
                    )
                    Text(
                        TimeText.remaining(next.second - now) +
                            (next.first.label.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.titleMedium,
                        color = cs.onPrimaryContainer,
                    )
                } else {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Genieß die Ruhe – oder stell dir einen Wecker.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = cs.onPrimaryContainer,
                    )
                }
            }
            Icon(
                if (next != null) Icons.Filled.Alarm else Icons.Outlined.AlarmOff,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = cs.onPrimaryContainer.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun PermissionCard(missing: List<Perm>) {
    val context = LocalContext.current
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.width(12.dp))
                Text(
                    "Damit der Wecker sicher klingelt",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            missing.forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text(
                            p.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                        )
                    }
                    TextButton(onClick = { runCatching { context.startActivity(Permissions.settingsIntent(context, p)) } }) {
                        Text("Erlauben")
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.WbTwilight,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text("Noch keine Wecker", style = MaterialTheme.typography.titleLarge)
        Text(
            "Tippe auf „Wecker“, um deinen ersten anzulegen.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AlarmCard(
    alarm: Alarm,
    now: Long,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onSkip: () -> Unit,
    onUnskip: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val snoozing = alarm.snoozeUntil > now
    val active = alarm.enabled || snoozing
    val skipped = alarm.enabled && alarm.skipUntil > now
    val contentAlpha = if (active) 1f else 0.5f
    var menu by remember { mutableStateOf(false) }

    val status: String? = when {
        snoozing -> "Schlummert bis " + TimeText.time(context, alarm.snoozeUntil)
        !alarm.enabled -> null
        else -> AlarmScheduler.nextTrigger(alarm, AlarmScheduler.zoned(now))?.let { t ->
            if (skipped) "Einmal übersprungen · dann " + TimeText.dayAndTime(context, t)
            else TimeText.remaining(t - now).replaceFirstChar { it.uppercase() }
        }
    }

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (active) cs.surfaceContainerHigh else cs.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 14.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).alpha(contentAlpha)) {
                    Text(
                        TimeText.time(context, alarm.hour, alarm.minute),
                        style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Light),
                    )
                    Text(
                        listOfNotNull(
                            alarm.label.takeIf { it.isNotBlank() },
                            TimeText.repeatSummary(alarm),
                            alarm.streamName?.let { "Radio: $it" },
                        )
                            .joinToString(" · "),
                        style = MaterialTheme.typography.titleSmall,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Switch(checked = alarm.enabled, onCheckedChange = onToggle)
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Mehr")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (alarm.enabled && alarm.isRepeating) {
                            if (skipped) {
                                DropdownMenuItem(
                                    text = { Text("Überspringen aufheben") },
                                    leadingIcon = { Icon(Icons.Outlined.Undo, null) },
                                    onClick = { menu = false; onUnskip() },
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Nächsten überspringen") },
                                    leadingIcon = { Icon(Icons.Outlined.SkipNext, null) },
                                    onClick = { menu = false; onSkip() },
                                )
                            }
                        }
                        DropdownMenuItem(
                            text = { Text("Duplizieren") },
                            leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                            onClick = { menu = false; onDuplicate() },
                        )
                        DropdownMenuItem(
                            text = { Text("Löschen") },
                            leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                            onClick = { menu = false; onDelete() },
                        )
                    }
                }
            }
            if (alarm.isRepeating) {
                DayDots(alarm.days, Modifier.alpha(contentAlpha).padding(top = 10.dp))
            }
            if (status != null) {
                Row(
                    Modifier.padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (snoozing) Icons.Outlined.Snooze else Icons.Filled.Alarm,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = cs.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(status, style = MaterialTheme.typography.labelLarge, color = cs.primary)
                }
            }
        }
    }
}

@Composable
private fun DayDots(days: Set<DayOfWeek>, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DayOfWeek.values().forEach { d ->
            val on = d in days
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (on) cs.primary else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    TimeText.letter(d),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (on) cs.onPrimary else cs.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CountdownDialog(onDismiss: () -> Unit, onStart: (Int) -> Unit) {
    var selected by remember { mutableIntStateOf(15) }
    var custom by remember { mutableStateOf("") }
    val presets = listOf(5, 10, 15, 20, 30, 45, 60, 90)
    val minutes = custom.toIntOrNull()?.takeIf { it in 1..1440 } ?: selected
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Timer, contentDescription = null) },
        title = { Text("Countdown-Wecker") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Klingelt einmalig nach:")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    presets.forEach { m ->
                        FilterChip(
                            selected = custom.isEmpty() && selected == m,
                            onClick = { selected = m; custom = "" },
                            label = { Text(if (m < 60 || m % 60 != 0) "$m Min." else "${m / 60} Std.") },
                        )
                    }
                }
                OutlinedTextField(
                    value = custom,
                    onValueChange = { v -> custom = v.filter { it.isDigit() }.take(4) },
                    label = { Text("Eigene Minuten") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onStart(minutes) }) { Text("Starten") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
