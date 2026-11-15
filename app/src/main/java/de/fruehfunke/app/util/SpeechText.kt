package de.fruehfunke.app.util

import de.fruehfunke.app.data.Alarm
import de.fruehfunke.app.data.SettingsState
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Baut den Ansagetext der sprechenden Uhr aus den Textbausteinen. */
object SpeechText {
    data class Token(val key: String, val label: String)

    val tokens = listOf(
        Token("%t", "Uhrzeit"),
        Token("%d", "Wochentag"),
        Token("%D", "Datum"),
        Token("%n", "Name des Weckers"),
        Token("%w", "Wetterbericht"),
        Token("%c", "Temperatur jetzt"),
        Token("%max", "Höchstwert"),
        Token("%min", "Tiefstwert"),
        Token("%z", "Wetterlage heute"),
    )
    private val weatherKeys = listOf("%w", "%c", "%max", "%min", "%z")

    const val DEFAULT_TEXT = "Guten Morgen! Es ist %t, %d, der %D. %w"
    const val DEFAULT_FALLBACK = "Guten Morgen! Es ist %t, %d, der %D."

    fun needsWeather(template: String) = weatherKeys.any { template.contains(it) }

    fun build(s: SettingsState, alarm: Alarm?, w: Weather.Report?, now: ZonedDateTime = ZonedDateTime.now()): String {
        val template = if (needsWeather(s.speakText) && w == null) s.speakFallback else s.speakText
        val locale = Locale.GERMANY
        val time = if (now.minute == 0) "${now.hour} Uhr" else "${now.hour} Uhr ${now.minute}"
        val text = template
            .replace("%max", w?.let { deg(it.max) } ?: "")
            .replace("%min", w?.let { deg(it.min) } ?: "")
            .replace("%w", w?.let { report(it) } ?: "")
            .replace("%c", w?.let { deg(it.tempNow) } ?: "")
            .replace("%z", w?.let { Weather.describe(it.codeDay) } ?: "")
            .replace("%t", time)
            .replace("%D", now.format(DateTimeFormatter.ofPattern("d. MMMM", locale)))
            .replace("%d", now.format(DateTimeFormatter.ofPattern("EEEE", locale)))
            .replace("%n", alarm?.label ?: "")
        return text.replace(Regex("\\s+"), " ").trim()
    }

    fun deg(v: Double): String {
        val i = v.roundToInt()
        return when {
            i < 0 -> "minus ${-i} Grad"
            i == 1 -> "1 Grad"
            else -> "$i Grad"
        }
    }

    private fun report(w: Weather.Report): String {
        val where = if (w.place.isNotBlank()) "In ${w.place} ist es" else "Draußen ist es"
        val nowPart = "$where zurzeit ${Weather.describe(w.codeNow)} bei ${deg(w.tempNow)}."
        val minI = w.min.roundToInt()
        val dayPart = "Heute ${Weather.describe(w.codeDay)}, ${if (minI < 0) "minus ${-minI}" else "$minI"} bis ${deg(w.max)}."
        val rain = w.rainChance?.takeIf { it >= 20 }?.let { " Regenwahrscheinlichkeit $it Prozent." } ?: ""
        return "$nowPart $dayPart$rain"
    }
}
