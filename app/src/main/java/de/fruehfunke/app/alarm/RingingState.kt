package de.fruehfunke.app.alarm

import kotlinx.coroutines.flow.MutableStateFlow

data class RingInfo(
    val alarmId: Int,
    val label: String,
    val canSnooze: Boolean,
    val snoozeMinutes: Int,
    val snoozeCount: Int,
)

/** Welcher Wecker gerade klingelt – geteilt zwischen Dienst und Weckbildschirm. */
object RingingState {
    val current = MutableStateFlow<RingInfo?>(null)
}
