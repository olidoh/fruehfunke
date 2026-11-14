package de.fruehfunke.app.ui

import android.Manifest
import android.os.Bundle
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
    }
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
