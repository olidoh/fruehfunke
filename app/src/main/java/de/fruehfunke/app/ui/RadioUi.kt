package de.fruehfunke.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import de.fruehfunke.app.data.Station
import de.fruehfunke.app.data.StationRepository
import de.fruehfunke.app.util.StreamResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Dialog zum Anlegen oder Bearbeiten eines Radiosenders. */
@Composable
fun StationDialog(initial: Station? = null, onDismiss: () -> Unit, onSaved: (Station) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var url by remember { mutableStateOf(initial?.url ?: "") }
    val valid = StationRepository.isValidUrl(url)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Radio, contentDescription = null) },
        title = { Text(if (initial == null) "Radiosender hinzufügen" else "Radiosender bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("Name") },
                    placeholder = { Text("z. B. WDR 2") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it.trim() },
                    label = { Text("Stream-URL") },
                    placeholder = { Text("https://…/stream.mp3") },
                    singleLine = true,
                    isError = url.isNotEmpty() && !valid,
                    supportingText = {
                        Text("Direkte Stream-Adresse, .m3u-, .pls- oder .m3u8-Link")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                StreamPreviewButton(url = url.takeIf { valid })
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onSaved(StationRepository.save(initial?.id ?: 0, name, url)) },
            ) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

private enum class PreviewState { IDLE, LOADING, PLAYING, ERROR }

/** Spielt einen Stream zum Ausprobieren ab, bis gestoppt oder der Bildschirm verlassen wird. */
@Composable
fun StreamPreviewButton(url: String?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(PreviewState.IDLE) }
    val player = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(s: Int) {
                if (s == Player.STATE_READY) state = PreviewState.PLAYING
                if (s == Player.STATE_ENDED) state = PreviewState.IDLE
            }

            override fun onPlayerError(error: PlaybackException) {
                state = PreviewState.ERROR
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    // URL geändert → laufende Vorschau beenden
    DisposableEffect(url) {
        onDispose {
            player.stop()
            state = PreviewState.IDLE
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FilledTonalButton(
            enabled = url != null,
            onClick = {
                if (state == PreviewState.PLAYING || state == PreviewState.LOADING) {
                    player.stop()
                    state = PreviewState.IDLE
                } else if (url != null) {
                    state = PreviewState.LOADING
                    scope.launch {
                        val resolved = withContext(Dispatchers.IO) { StreamResolver.resolve(url) }
                        player.setMediaItem(MediaItem.fromUri(resolved))
                        player.prepare()
                        player.play()
                    }
                }
            },
        ) {
            when (state) {
                PreviewState.LOADING -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                PreviewState.PLAYING -> Icon(Icons.Outlined.Stop, contentDescription = null)
                else -> Icon(Icons.Outlined.PlayArrow, contentDescription = null)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                when (state) {
                    PreviewState.LOADING -> "Verbinde …"
                    PreviewState.PLAYING -> "Anhören beenden"
                    else -> "Probehören"
                }
            )
        }
        if (state == PreviewState.ERROR) {
            Text(
                "Dieser Stream lässt sich nicht abspielen. Bitte URL prüfen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
