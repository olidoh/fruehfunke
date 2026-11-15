package de.fruehfunke.app

import android.app.Application
import android.net.Uri
import de.fruehfunke.app.util.Backup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import de.fruehfunke.app.alarm.AlarmScheduler
import de.fruehfunke.app.alarm.Notifications
import de.fruehfunke.app.data.AlarmRepository
import de.fruehfunke.app.data.AppSettings
import de.fruehfunke.app.data.StationRepository

class FruehfunkeApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        AppSettings.init(this)
        AlarmRepository.init(this)
        StationRepository.init(this)
        de.fruehfunke.app.util.Weather.init(this)
        Notifications.createChannels(this)
        AlarmScheduler.rescheduleAll(this)
        startAutoSync()
        startWidgetUpdates()
    }

    /** Widget neu zeichnen, sobald sich Wecker, Wetter oder Ort ändern. */
    @OptIn(FlowPreview::class)
    private fun startWidgetUpdates() {
        appScope.launch {
            combine(AlarmRepository.alarms, de.fruehfunke.app.util.Weather.latest, AppSettings.state) { a, w, s ->
                Triple(a, w, s.weatherName)
            }
                .distinctUntilChanged()
                .debounce(500)
                .collect { runCatching { de.fruehfunke.app.widget.FruehfunkeWidget.refresh(this@FruehfunkeApp) } }
        }
    }

    /** Schreibt Änderungen an Weckern und Sendern automatisch in die gewählte Sicherungsdatei. */
    @OptIn(FlowPreview::class)
    private fun startAutoSync() {
        appScope.launch {
            combine(AlarmRepository.alarms, StationRepository.stations) { _, _ -> Backup.content().toString() }
                .distinctUntilChanged()
                .drop(1)
                .debounce(3_000)
                .collect {
                    val s = AppSettings.state.value
                    val uri = s.syncUri
                    if (s.autoSync && uri != null && Backup.write(this@FruehfunkeApp, Uri.parse(uri))) {
                        AppSettings.update { it.copy(lastSync = System.currentTimeMillis()) }
                    }
                }
        }
    }
}
