package de.fruehfunke.app.util

import android.content.Context
import android.text.format.DateFormat
import de.fruehfunke.app.data.Alarm
import de.fruehfunke.app.data.WeekParity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimeText {
    private val locale: Locale get() = Locale.getDefault()
    private val WORKDAYS = setOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
    )
    private val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

    fun is24(context: Context): Boolean = DateFormat.is24HourFormat(context)

    private fun pattern(context: Context) =
        DateTimeFormatter.ofPattern(if (is24(context)) "HH:mm" else "h:mm a", locale)

    private fun zdt(ms: Long): ZonedDateTime = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())

    fun time(context: Context, hour: Int, minute: Int): String =
        LocalTime.of(hour, minute).format(pattern(context))

    fun time(context: Context, epochMs: Long): String = zdt(epochMs).format(pattern(context))

    fun dayAndTime(context: Context, epochMs: Long): String {
        val z = zdt(epochMs)
        val today = LocalDate.now()
        val day = when (z.toLocalDate()) {
            today -> "Heute"
            today.plusDays(1) -> "Morgen"
            else -> z.format(DateTimeFormatter.ofPattern("EEE, d. MMM", locale))
        }
        return "$day, ${time(context, epochMs)}"
    }

    fun longDate(epochMs: Long): String =
        zdt(epochMs).format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", locale))

    fun shortDate(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("EEE, d. MMM yyyy", locale))

    fun remaining(ms: Long): String {
        val totalMin = ((ms + 59_999L) / 60_000L).coerceAtLeast(0L)
        if (totalMin < 1L) return "in weniger als einer Minute"
        val d = totalMin / 1440L
        val h = (totalMin % 1440L) / 60L
        val m = totalMin % 60L
        val parts = buildList {
            if (d > 0L) add(if (d == 1L) "1 Tag" else "$d Tagen")
            if (h > 0L) add("$h Std.")
            if (m > 0L && d == 0L) add("$m Min.")
        }
        return "in " + parts.joinToString(" ")
    }

    fun shortDay(d: DayOfWeek): String = when (d) {
        DayOfWeek.MONDAY -> "Mo"
        DayOfWeek.TUESDAY -> "Di"
        DayOfWeek.WEDNESDAY -> "Mi"
        DayOfWeek.THURSDAY -> "Do"
        DayOfWeek.FRIDAY -> "Fr"
        DayOfWeek.SATURDAY -> "Sa"
        DayOfWeek.SUNDAY -> "So"
    }

    fun letter(d: DayOfWeek): String = shortDay(d).take(1)

    fun repeatSummary(a: Alarm): String {
        val base = when {
            a.isCountdown -> "Countdown"
            a.days.isEmpty() -> a.onceDate?.let { "Einmalig am " + it.format(DateTimeFormatter.ofPattern("EEE, d. MMM", locale)) }
                ?: "Einmalig"
            a.days.size == 7 -> "Täglich"
            a.days == WORKDAYS -> "Werktags"
            a.days == WEEKEND -> "Am Wochenende"
            else -> DayOfWeek.values().filter { it in a.days }.joinToString(", ") { shortDay(it) }
        }
        if (!a.isRepeating) return base
        return base + when (a.weekParity) {
            WeekParity.ALL -> ""
            WeekParity.EVEN -> " · gerade Wochen"
            WeekParity.ODD -> " · ungerade Wochen"
        }
    }

    val workdays: Set<DayOfWeek> get() = WORKDAYS
    val weekend: Set<DayOfWeek> get() = WEEKEND
}
