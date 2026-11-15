package de.fruehfunke.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.style.TextOverflow
import de.fruehfunke.app.data.Station
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SaveAlt
import de.fruehfunke.app.util.Backup
import de.fruehfunke.app.util.Sounds
import de.fruehfunke.app.util.TimeText
import de.fruehfunke.app.data.StationRepository
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.fruehfunke.app.BuildConfig
import de.fruehfunke.app.data.AppSettings
import de.fruehfunke.app.data.ThemeMode
import de.fruehfunke.app.util.Perm
import de.fruehfunke.app.util.Permissions

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings by AppSettings.state.collectAsStateWithLifecycle()
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumeTick++ }
    val stations by StationRepository.stations.collectAsStateWithLifecycle()
    var editStation by remember { mutableStateOf<Station?>(null) }
    var showStationDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            val ok = Backup.write(context, uri)
            if (ok) {
                AppSettings.update {
                    it.copy(
                        syncUri = uri.toString(),
                        syncName = Sounds.fileName(context, uri) ?: "Sicherungsdatei",
                        lastSync = System.currentTimeMillis(),
                    )
                }
            }
            Toast.makeText(context, if (ok) "Wecker exportiert" else "Export fehlgeschlagen", Toast.LENGTH_SHORT).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { Backup.read(context, uri) }
                .onSuccess { Backup.pendingImport.value = it }
                .onFailure {
                    Toast.makeText(context, "Datei ist keine gültige Frühfunke-Sicherung", Toast.LENGTH_LONG).show()
                }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier.widthIn(max = 680.dp).fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Section("Erscheinungsbild") {
                    val modes = listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Hell", ThemeMode.DARK to "Dunkel")
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        modes.forEachIndexed { index, (mode, text) ->
                            SegmentedButton(
                                selected = settings.themeMode == mode,
                                onClick = { AppSettings.update { it.copy(themeMode = mode) } },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                            ) { Text(text) }
                        }
                    }
                    SwitchRow(
                        title = "Dynamische Farben",
                        subtitle = "Farben aus deinem Hintergrundbild übernehmen (Material You)",
                        checked = settings.dynamicColor,
                        onChange = { v -> AppSettings.update { it.copy(dynamicColor = v) } },
                    )
                }

                ControlsSection()

                SpeechSection()

                Section("Radiosender") {
                    if (stations.isEmpty()) {
                        Text(
                            "Lege Sender mit ihrer Stream-URL an und wähle sie dann im Wecker unter „Weckton → Radio“.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    stations.forEach { st ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(st.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    st.url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(onClick = { editStation = st; showStationDialog = true }) {
                                Icon(Icons.Outlined.Edit, contentDescription = "Bearbeiten")
                            }
                            IconButton(onClick = { StationRepository.delete(st.id) }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Löschen")
                            }
                        }
                    }
                    FilledTonalButton(onClick = { editStation = null; showStationDialog = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Sender hinzufügen")
                    }
                }

                Section("Sichern & Übertragen") {
                    Text(
                        "Teile alle Wecker und Radiosender als Datei – z. B. an OneDrive, per Mail oder Messenger. " +
                            "Auf dem anderen Gerät die Datei antippen und mit Frühfunke öffnen, dann wird sie importiert.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        FilledTonalButton(onClick = {
                            runCatching { Backup.share(context) }.onFailure {
                                Toast.makeText(context, "Teilen nicht möglich", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Outlined.Share, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Teilen")
                        }
                        FilledTonalButton(onClick = {
                            runCatching { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }
                        }) {
                            Icon(Icons.Outlined.CloudDownload, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Importieren")
                        }
                        OutlinedButton(onClick = { runCatching { exportLauncher.launch("fruehfunke-wecker.json") } }) {
                            Icon(Icons.Outlined.SaveAlt, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Als Datei speichern")
                        }
                    }
                    Text(
                        "„Als Datei speichern“ ist nötig für „Automatisch sichern“: Die App hält die gespeicherte Datei dann bei jeder Änderung aktuell.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (settings.syncUri != null) {
                        SwitchRow(
                            title = "Automatisch sichern",
                            subtitle = "Jede Änderung wird in „${settings.syncName ?: "Sicherungsdatei"}“ gespeichert" +
                                (if (settings.lastSync > 0L) " · zuletzt " + TimeText.dayAndTime(context, settings.lastSync) else ""),
                            checked = settings.autoSync,
                            onChange = { v ->
                                AppSettings.update { it.copy(autoSync = v) }
                                if (v) {
                                    val ok = Backup.write(context, Uri.parse(settings.syncUri))
                                    if (ok) AppSettings.update { it.copy(lastSync = System.currentTimeMillis()) }
                                    else Toast.makeText(context, "Datei nicht erreichbar – bitte erneut exportieren", Toast.LENGTH_LONG).show()
                                }
                            },
                        )
                    }
                }

                Section("Berechtigungen") {
                    Perm.values().forEach { p ->
                        val ok = remember(resumeTick) { Permissions.granted(context, p) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                                contentDescription = if (ok) "Erteilt" else "Fehlt",
                                tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.title, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    p.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (!ok) {
                                TextButton(onClick = {
                                    runCatching { context.startActivity(Permissions.settingsIntent(context, p)) }
                                }) { Text("Erlauben") }
                            }
                        }
                    }
                }

                Section("Über Frühfunke") {
                    Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Eigener Nachbau von AlarmDroid für aktuelle Android-Versionen – ohne Werbung, ohne Tracking, ohne Internetzwang.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showStationDialog) {
        StationDialog(
            initial = editStation,
            onDismiss = { showStationDialog = false },
            onSaved = { showStationDialog = false },
        )
    }

}
