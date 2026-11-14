package de.fruehfunke.app.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.outlined.AlarmOff
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.fruehfunke.app.alarm.AlarmActions
import de.fruehfunke.app.alarm.RingInfo
import de.fruehfunke.app.alarm.RingingState
import de.fruehfunke.app.ui.theme.FruehfunkeTheme
import de.fruehfunke.app.util.TimeText
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class RingingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        // Zurück-Taste soll den Wecker nicht versehentlich wegwischen.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {}
        })
        val activity = this
        setContent {
            FruehfunkeTheme {
                val info by RingingState.current.collectAsStateWithLifecycle()
                LaunchedEffect(info) { if (info == null) activity.finish() }
                info?.let { i ->
                    RingingScreen(
                        info = i,
                        onSnooze = { AlarmActions.snooze(activity, i.alarmId) },
                        onStop = { AlarmActions.stop(activity, i.alarmId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RingingScreen(info: RingInfo, onSnooze: () -> Unit, onStop: () -> Unit) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale",
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(cs.primaryContainer, cs.surface, cs.surface)))
    ) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(240.dp)
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .clip(CircleShape)
                        .background(cs.primary.copy(alpha = 0.12f))
                )
                Box(Modifier.size(180.dp).clip(CircleShape).background(cs.primary.copy(alpha = 0.18f)))
                Icon(Icons.Filled.Alarm, contentDescription = null, modifier = Modifier.size(72.dp), tint = cs.primary)
            }
            Spacer(Modifier.height(32.dp))
            Text(
                TimeText.time(context, now),
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 96.sp, fontWeight = FontWeight.Light),
                color = cs.onSurface,
            )
            Text(TimeText.longDate(now), style = MaterialTheme.typography.titleLarge, color = cs.onSurfaceVariant)
            if (info.label.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(info.label, style = MaterialTheme.typography.headlineSmall, color = cs.onSurface, textAlign = TextAlign.Center)
            }
            if (info.snoozeCount > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Schon ${info.snoozeCount}× geschlummert",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            SlideToAct(info.canSnooze, info.snoozeMinutes, onSnooze, onStop)
            Spacer(Modifier.height(12.dp))
            Text(
                if (info.canSnooze) "Nach links: schlummern · nach rechts: stoppen" else "Nach rechts ziehen zum Stoppen",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SlideToAct(canSnooze: Boolean, snoozeMinutes: Int, onSnooze: () -> Unit, onStop: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val density = LocalDensity.current
    var dragX by remember { mutableFloatStateOf(0f) }
    var done by remember { mutableStateOf(false) }

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp)
            .height(96.dp)
            .clip(CircleShape)
            .background(cs.surfaceContainerHighest)
    ) {
        val knob = 80.dp
        val limit = with(density) { ((maxWidth - knob) / 2 - 8.dp).toPx() }
        val progress = if (limit > 0f) (dragX / limit).coerceIn(-1f, 1f) else 0f

        if (canSnooze) {
            Row(
                Modifier.align(Alignment.CenterStart).padding(start = 24.dp).alpha(1f - max(0f, progress)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Snooze, contentDescription = null, tint = cs.secondary)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Schlummern", style = MaterialTheme.typography.labelLarge, color = cs.secondary)
                    Text("$snoozeMinutes Min.", style = MaterialTheme.typography.labelSmall, color = cs.secondary)
                }
            }
        }
        Row(
            Modifier.align(Alignment.CenterEnd).padding(end = 24.dp).alpha(1f + min(0f, progress)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Stopp", style = MaterialTheme.typography.labelLarge, color = cs.error)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Outlined.AlarmOff, contentDescription = null, tint = cs.error)
        }
        Box(
            Modifier
                .align(Alignment.Center)
                .offset { IntOffset(dragX.roundToInt(), 0) }
                .size(knob)
                .clip(CircleShape)
                .background(
                    when {
                        progress > 0.5f -> cs.error
                        progress < -0.5f -> cs.secondary
                        else -> cs.primary
                    }
                )
                .draggable(
                    orientation = Orientation.Horizontal,
                    enabled = !done,
                    state = rememberDraggableState { delta ->
                        dragX = (dragX + delta).coerceIn(if (canSnooze) -limit else 0f, limit)
                    },
                    onDragStopped = {
                        when {
                            dragX >= limit * 0.85f -> { done = true; onStop() }
                            canSnooze && dragX <= -limit * 0.85f -> { done = true; onSnooze() }
                            else -> animate(dragX, 0f) { value, _ -> dragX = value }
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Alarm, contentDescription = "Wecker", tint = cs.onPrimary, modifier = Modifier.size(36.dp))
        }
    }
}
