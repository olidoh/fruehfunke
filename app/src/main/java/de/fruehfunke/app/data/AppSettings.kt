package de.fruehfunke.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class SettingsState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
)

object AppSettings {
    private lateinit var prefs: SharedPreferences
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    fun init(context: Context) {
        prefs = context.createDeviceProtectedStorageContext()
            .getSharedPreferences("settings", Context.MODE_PRIVATE)
        _state.value = SettingsState(
            themeMode = runCatching { ThemeMode.valueOf(prefs.getString("theme", "SYSTEM")!!) }
                .getOrDefault(ThemeMode.SYSTEM),
            dynamicColor = prefs.getBoolean("dynamic", true),
        )
    }

    fun update(transform: (SettingsState) -> SettingsState) {
        val next = transform(_state.value)
        _state.value = next
        prefs.edit()
            .putString("theme", next.themeMode.name)
            .putBoolean("dynamic", next.dynamicColor)
            .apply()
    }
}
