package de.fruehfunke.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import de.fruehfunke.app.R
import de.fruehfunke.app.alarm.AlarmScheduler
import de.fruehfunke.app.data.Alarm
import de.fruehfunke.app.data.AlarmRepository
import de.fruehfunke.app.data.AppSettings
import de.fruehfunke.app.ui.MainActivity
import de.fruehfunke.app.util.TimeText
import de.fruehfunke.app.util.Weather
import kotlin.math.roundToInt

/** Startbildschirm-Widget: nächster Wecker, Wetter und die folgenden Weckzeiten. */
class FruehfunkeWidget : GlanceAppWidget() {

    companion object {
        private val SMALL = DpSize(110.dp, 48.dp)
        private val MEDIUM = DpSize(180.dp, 110.dp)
        private val LARGE = DpSize(250.dp, 200.dp)

        /** Alle Widgets neu zeichnen – nach jeder Änderung an Weckern oder Wetter. */
        suspend fun refresh(context: Context) {
            if (GlanceAppWidgetManager(context).getGlanceIds(FruehfunkeWidget::class.java).isNotEmpty()) {
                FruehfunkeWidget().updateAll(context)
            }
        }
    }

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                val alarms by AlarmRepository.alarms.collectAsState()
                val weather by Weather.latest.collectAsState()
                val settings by AppSettings.state.collectAsState()
                WidgetContent(
                    alarms = alarms,
                    weather = weather?.takeIf {
                        it.place == settings.weatherName && System.currentTimeMillis() - it.fetchedAt < 3 * 60 * 60_000L
                    },
                )
            }
        }
    }
}

class FruehfunkeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FruehfunkeWidget()
}

private fun openApp(context: Context, editId: Int? = null) = actionStartActivity(
    Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        .apply { if (editId != null) putExtra(MainActivity.EXTRA_EDIT_ALARM, editId) }
        // eindeutige Daten, damit jede Zeile ihren eigenen Klick bekommt
        .setData(android.net.Uri.parse("fruehfunke://widget/" + (editId ?: 0)))
)

@Composable
private fun WidgetContent(alarms: List<Alarm>, weather: Weather.Report?) {
    val context = LocalContext.current
    val size = LocalSize.current
    val now = System.currentTimeMillis()
    val zNow = AlarmScheduler.zoned(now)
    val upcoming = alarms
        .mapNotNull { a -> AlarmScheduler.nextTrigger(a, zNow)?.let { a to it } }
        .sortedBy { it.second }
    val next = upcoming.firstOrNull()
    val c = GlanceTheme.colors
    val compact = size.height < 100.dp

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(c.widgetBackground)
            .cornerRadius(24.dp)
            .padding(horizontal = 16.dp, vertical = if (compact) 8.dp else 14.dp)
            .clickable(openApp(context)),
        verticalAlignment = if (compact) Alignment.CenterVertically else Alignment.Top,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
            Image(
                provider = ImageProvider(R.drawable.ic_notification),
                contentDescription = null,
                colorFilter = ColorFilter.tint(c.primary),
                modifier = GlanceModifier.size(if (compact) 22.dp else 18.dp),
            )
            Spacer(GlanceModifier.width(8.dp))
            if (compact) {
                // 2×1: alles in einer Zeile
                if (next != null) {
                    Text(
                        TimeText.time(context, next.second),
                        style = TextStyle(color = c.primary, fontSize = 24.sp, fontWeight = FontWeight.Bold),
                    )
                    Spacer(GlanceModifier.width(8.dp))
                    Text(
                        dayLabel(context, next.first, next.second, now),
                        style = TextStyle(color = c.onSurfaceVariant, fontSize = 13.sp),
                        maxLines = 1,
                    )
                } else {
                    Text("Kein Wecker aktiv", style = TextStyle(color = c.onSurfaceVariant, fontSize = 14.sp))
                }
            } else {
                Text(
                    if (next != null) "Nächster Wecker" else "Frühfunke",
                    style = TextStyle(color = c.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                )
            }
        }
        if (compact) return@Column

        Spacer(GlanceModifier.height(4.dp))
        if (next != null) {
            Text(
                TimeText.time(context, next.second),
                style = TextStyle(color = c.primary, fontSize = 40.sp, fontWeight = FontWeight.Bold),
            )
            Text(
                dayLabel(context, next.first, next.second, now),
                style = TextStyle(color = c.onSurface, fontSize = 14.sp),
                maxLines = 1,
            )
        } else {
            Text("Kein Wecker aktiv", style = TextStyle(color = c.onSurface, fontSize = 18.sp))
            Text("Tippen, um einen anzulegen", style = TextStyle(color = c.onSurfaceVariant, fontSize = 12.sp))
        }

        if (weather != null) {
            Spacer(GlanceModifier.height(6.dp))
            Text(
                "${weather.place} · ${weather.condNow} · ${weather.tempNow.roundToInt()}° " +
                    "(${weather.min.roundToInt()}–${weather.max.roundToInt()}°)",
                style = TextStyle(color = c.onSurfaceVariant, fontSize = 12.sp),
                maxLines = 1,
            )
        }

        if (size.height >= 180.dp && upcoming.size > 1) {
            Spacer(GlanceModifier.height(10.dp))
            Text("Danach", style = TextStyle(color = c.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium))
            val rows = ((size.height.value - 150f) / 34f).toInt().coerceIn(1, 5)
            upcoming.drop(1).take(rows).forEach { (alarm, t) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable(openApp(context, alarm.id)),
                ) {
                    Text(
                        TimeText.time(context, t),
                        style = TextStyle(color = c.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                    )
                    Spacer(GlanceModifier.width(10.dp))
                    Text(
                        dayLabel(context, alarm, t, now),
                        style = TextStyle(color = c.onSurfaceVariant, fontSize = 13.sp),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** „Morgen · Arbeit“, „Schlummert bis 07:09“ oder „Sa., 15. Nov.“ */
private fun dayLabel(context: Context, alarm: Alarm, trigger: Long, now: Long): String {
    if (alarm.snoozeUntil > now) return "Schlummert · ${alarm.label.ifBlank { "Wecker" }}"
    val day = TimeText.dayAndTime(context, trigger).substringBeforeLast(",")
    return listOfNotNull(day, alarm.label.takeIf { it.isNotBlank() }, alarm.streamName?.let { "Radio" })
        .joinToString(" · ")
}
