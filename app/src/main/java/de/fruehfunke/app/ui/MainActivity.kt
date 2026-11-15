package de.fruehfunke.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.fruehfunke.app.util.Backup
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import de.fruehfunke.app.alarm.AlarmScheduler
import de.fruehfunke.app.data.Alarm
import de.fruehfunke.app.data.AlarmRepository
import de.fruehfunke.app.ui.theme.FruehfunkeTheme
import de.fruehfunke.app.util.Perm
import de.fruehfunke.app.util.Permissions
import de.fruehfunke.app.util.TimeText
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { FruehfunkeTheme { AppRoot() } }
        handleImportIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleImportIntent(intent)
    }

    /** Sicherungsdatei, die aus einer anderen App (z. B. OneDrive) geöffnet oder geteilt wurde. */
    private fun handleImportIntent(intent: Intent?) {
        val uri: Uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        } ?: return
        runCatching { Backup.read(this, uri) }
            .onSuccess { Backup.pendingImport.value = it }
            .onFailure { Toast.makeText(this, "Datei ist keine gültige Frühfunke-Sicherung", Toast.LENGTH_LONG).show() }
        setIntent(Intent(this, MainActivity::class.java))
    }
}

@Composable
private fun ImportDialog() {
    val context = LocalContext.current
    val preview by Backup.pendingImport.collectAsStateWithLifecycle()
    val p = preview ?: return
    val finish: (Boolean) -> Unit = { replace ->
        val n = Backup.apply(context, p, replace)
        Backup.pendingImport.value = null
        Toast.makeText(context, if (replace) "$n Wecker importiert" else "$n Wecker ergänzt", Toast.LENGTH_SHORT).show()
    }
    AlertDialog(
        onDismissRequest = { Backup.pendingImport.value = null },
        icon = { Icon(Icons.Outlined.CloudDownload, contentDescription = null) },
        title = { Text("Sicherung importieren") },
        text = {
            Text(
                "Gefunden: ${p.alarms.size} Wecker und ${p.stations.size} Radiosender.\n\n" +
                    "„Ersetzen“ löscht die aktuellen Wecker auf diesem Gerät. „Ergänzen“ fügt nur fehlende hinzu. " +
                    "Radiosender werden immer ergänzt.",
            )
        },
        confirmButton = { TextButton(onClick = { finish(true) }) { Text("Ersetzen") } },
        dismissButton = { TextButton(onClick = { finish(false) }) { Text("Ergänzen") } },
    )
}

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    // "list", "settings" oder "edit:<id>" (0 = neuer Wecker)
    var route by rememberSaveable { mutableStateOf("list") }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (!Permissions.granted(context, Perm.NOTIFICATIONS)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Wetter beim Öffnen der App auffrischen, damit es beim Wecken schon vorliegt
    LaunchedEffect(Unit) { de.fruehfunke.app.util.Weather.refresh() }

    BackHandler(enabled = route != "list") { route = "list" }

    val showSaved: (Alarm) -> Unit = { alarm ->
        val t = AlarmScheduler.nextTrigger(alarm)
        scope.launch {
            snackbar.showSnackbar(
                if (t != null) "Wecker klingelt " + TimeText.remaining(t - System.currentTimeMillis())
                else "Wecker gespeichert"
            )
        }
    }
    val showDeleted: (Alarm) -> Unit = { alarm ->
        scope.launch {
            val result = snackbar.showSnackbar(
                "Wecker gelöscht", actionLabel = "Rückgängig", duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                AlarmRepository.upsert(alarm)
                AlarmScheduler.rescheduleAll(context)
            }
        }
    }

    ImportDialog()

    AnimatedContent(
        targetState = route,
        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
        label = "navigation",
    ) { r ->
        when {
            r == "settings" -> SettingsScreen(onBack = { route = "list" })
            r.startsWith("edit:") -> EditAlarmScreen(
                alarmId = r.removePrefix("edit:").toIntOrNull() ?: 0,
                onSaved = { route = "list"; showSaved(it) },
                onDeleted = { route = "list"; showDeleted(it) },
                onCancel = { route = "list" },
            )
            else -> AlarmListScreen(
                snackbar = snackbar,
                onEdit = { id -> route = "edit:$id" },
                onSettings = { route = "settings" },
                onSaved = showSaved,
                onDeleted = showDeleted,
            )
        }
    }
}
