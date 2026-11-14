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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings by AppSettings.state.collectAsStateWithLifecycle()
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumeTick++ }

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
}
