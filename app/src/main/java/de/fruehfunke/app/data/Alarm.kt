package de.fruehfunke.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate

enum class WeekParity { ALL, EVEN, ODD }

data class Alarm(
    val id: Int = 0,
    val hour: Int = 7,
    val minute: Int = 0,
    val enabled: Boolean = true,
    val label: String = "",
    /** Leer = einmaliger Wecker */
    val days: Set<DayOfWeek> = emptySet(),
    val weekParity: WeekParity = WeekParity.ALL,
    /** Optionales festes Datum für einmalige Wecker */
    val onceDate: LocalDate? = null,
    /** > 0: Countdown-Wecker, klingelt exakt zu diesem Zeitpunkt (epoch ms) */
    val exactAt: Long = 0L,
    val ringtoneUri: String? = null,
    val ringtoneTitle: String? = null,
    /** Internetradio als Weckton; der Klingelton dient dann als Ersatz */
    val streamUrl: String? = null,
    val streamName: String? = null,
    /** Wecker-Lautstärke in Prozent */
    val volume: Int = 80,
    /** Ansteigende Lautstärke über so viele Sekunden, 0 = sofort voll */
    val rampSeconds: Int = 30,
    val vibrate: Boolean = true,
    val snoozeMinutes: Int = 9,
    /** 0 = unbegrenzt */
    val maxSnoozes: Int = 0,
    /** Maximale Klingeldauer in Minuten */
    val ringMinutes: Int = 10,
    /** Nach Ablauf der Klingeldauer schlummern statt stoppen */
    val snoozeAfterTimeout: Boolean = true,
    /** Weckzeiten bis einschließlich dieses Zeitpunkts werden übersprungen */
    val skipUntil: Long = 0L,
    val snoozeUntil: Long = 0L,
    val snoozeCount: Int = 0,
) {
    val isRepeating: Boolean get() = days.isNotEmpty()
    val usesRadio: Boolean get() = streamUrl != null
    val isCountdown: Boolean get() = exactAt > 0L
    val canSnoozeAgain: Boolean get() = maxSnoozes == 0 || snoozeCount < maxSnoozes

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("hour", hour)
        put("minute", minute)
        put("enabled", enabled)
        put("label", label)
        put("days", JSONArray(days.map { it.value }))
        put("weekParity", weekParity.name)
        onceDate?.let { put("onceDate", it.toString()) }
        put("exactAt", exactAt)
        ringtoneUri?.let { put("ringtoneUri", it) }
        ringtoneTitle?.let { put("ringtoneTitle", it) }
        streamUrl?.let { put("streamUrl", it) }
        streamName?.let { put("streamName", it) }
        put("volume", volume)
        put("rampSeconds", rampSeconds)
        put("vibrate", vibrate)
        put("snoozeMinutes", snoozeMinutes)
        put("maxSnoozes", maxSnoozes)
        put("ringMinutes", ringMinutes)
        put("snoozeAfterTimeout", snoozeAfterTimeout)
        put("skipUntil", skipUntil)
        put("snoozeUntil", snoozeUntil)
        put("snoozeCount", snoozeCount)
    }

    companion object {
        fun fromJson(o: JSONObject): Alarm {
            val d = Alarm()
            val daysArr = o.optJSONArray("days")
            return Alarm(
                id = o.optInt("id"),
                hour = o.optInt("hour", d.hour),
                minute = o.optInt("minute", d.minute),
                enabled = o.optBoolean("enabled", true),
                label = o.optString("label", ""),
                days = if (daysArr == null) emptySet()
                else (0 until daysArr.length()).map { DayOfWeek.of(daysArr.getInt(it)) }.toSet(),
                weekParity = runCatching { WeekParity.valueOf(o.optString("weekParity", "ALL")) }
                    .getOrDefault(WeekParity.ALL),
                onceDate = o.optString("onceDate", "").takeIf { it.isNotEmpty() }
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                exactAt = o.optLong("exactAt", 0L),
                ringtoneUri = o.optString("ringtoneUri", "").takeIf { it.isNotEmpty() },
                ringtoneTitle = o.optString("ringtoneTitle", "").takeIf { it.isNotEmpty() },
                streamUrl = o.optString("streamUrl", "").takeIf { it.isNotEmpty() },
                streamName = o.optString("streamName", "").takeIf { it.isNotEmpty() },
                volume = o.optInt("volume", d.volume),
                rampSeconds = o.optInt("rampSeconds", d.rampSeconds),
                vibrate = o.optBoolean("vibrate", d.vibrate),
                snoozeMinutes = o.optInt("snoozeMinutes", d.snoozeMinutes),
                maxSnoozes = o.optInt("maxSnoozes", d.maxSnoozes),
                ringMinutes = o.optInt("ringMinutes", d.ringMinutes),
                snoozeAfterTimeout = o.optBoolean("snoozeAfterTimeout", d.snoozeAfterTimeout),
                skipUntil = o.optLong("skipUntil", 0L),
                snoozeUntil = o.optLong("snoozeUntil", 0L),
                snoozeCount = o.optInt("snoozeCount", 0),
            )
        }
    }
}
