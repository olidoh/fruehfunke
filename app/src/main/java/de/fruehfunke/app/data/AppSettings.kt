package de.fruehfunke.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class GestureAction(val label: String) { NOTHING("Nichts"), SNOOZE("Schlummern"), STOP("Stopp") }

enum class VolumeKeyAction(val label: String) {
    ADJUST("Lautstärke"), BLOCK("Sperren"), SNOOZE("Schlummern"), STOP("Stopp")
}

/** Wie der Wecker auf dem Weckbildschirm bedient wird. */
enum class RingControl { SLIDE, BUTTONS }

data class SettingsState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    /** Datei (z. B. auf OneDrive), in die automatisch gesichert wird */
    val syncUri: String? = null,
    val syncName: String? = null,
    val autoSync: Boolean = false,
    val lastSync: Long = 0L,
    // Steuerung beim Klingeln
    val ringControl: RingControl = RingControl.SLIDE,
    val flipAction: GestureAction = GestureAction.SNOOZE,
    val shakeAction: GestureAction = GestureAction.NOTHING,
    val powerAction: GestureAction = GestureAction.NOTHING,
    val proximityAction: GestureAction = GestureAction.NOTHING,
    val volumeKeyAction: VolumeKeyAction = VolumeKeyAction.ADJUST,
    /** Mindest-Beschleunigung fürs Schütteln in m/s² */
    val shakeThreshold: Float = 14f,
    val gestureHaptics: Boolean = true,
)

object AppSettings {
    private lateinit var prefs: SharedPreferences
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private inline fun <reified T : Enum<T>> SharedPreferences.enumOf(key: String, default: T): T =
        runCatching { enumValueOf<T>(getString(key, default.name)!!) }.getOrDefault(default)

    fun init(context: Context) {
        prefs = context.createDeviceProtectedStorageContext()
            .getSharedPreferences("settings", Context.MODE_PRIVATE)
        val d = SettingsState()
        _state.value = SettingsState(
            themeMode = prefs.enumOf("theme", d.themeMode),
            dynamicColor = prefs.getBoolean("dynamic", d.dynamicColor),
            syncUri = prefs.getString("syncUri", null),
            syncName = prefs.getString("syncName", null),
            autoSync = prefs.getBoolean("autoSync", false),
            lastSync = prefs.getLong("lastSync", 0L),
            ringControl = prefs.enumOf("ringControl", d.ringControl),
            flipAction = prefs.enumOf("flipAction", d.flipAction),
            shakeAction = prefs.enumOf("shakeAction", d.shakeAction),
            powerAction = prefs.enumOf("powerAction", d.powerAction),
            proximityAction = prefs.enumOf("proximityAction", d.proximityAction),
            volumeKeyAction = prefs.enumOf("volumeKeyAction", d.volumeKeyAction),
            shakeThreshold = prefs.getFloat("shakeThreshold", d.shakeThreshold),
            gestureHaptics = prefs.getBoolean("gestureHaptics", d.gestureHaptics),
        )
    }

    fun update(transform: (SettingsState) -> SettingsState) {
        val n = transform(_state.value)
        _state.value = n
        prefs.edit()
            .putString("theme", n.themeMode.name)
            .putBoolean("dynamic", n.dynamicColor)
            .putString("syncUri", n.syncUri)
            .putString("syncName", n.syncName)
            .putBoolean("autoSync", n.autoSync)
            .putLong("lastSync", n.lastSync)
            .putString("ringControl", n.ringControl.name)
            .putString("flipAction", n.flipAction.name)
            .putString("shakeAction", n.shakeAction.name)
            .putString("powerAction", n.powerAction.name)
            .putString("proximityAction", n.proximityAction.name)
            .putString("volumeKeyAction", n.volumeKeyAction.name)
            .putFloat("shakeThreshold", n.shakeThreshold)
            .putBoolean("gestureHaptics", n.gestureHaptics)
            .apply()
    }
}
