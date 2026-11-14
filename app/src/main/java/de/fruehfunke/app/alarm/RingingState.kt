package de.fruehfunke.app.alarm

import kotlinx.coroutines.flow.MutableStateFlow

data class RingInfo(
    val alarmId: Int,
    val label: String,
    val canSnooze: Boolean,
    val snoozeMinutes: Int,
    val snoozeCount: Int,
    /** z. B. "Radio: WDR 2" oder Hinweis auf den Ersatzton */
    val source: String = "",
)

/** Welcher Wecker gerade klingelt – geteilt zwischen Dienst und Weckbildschirm. */
object RingingState {
    val current = MutableStateFlow<RingInfo?>(null)
}
