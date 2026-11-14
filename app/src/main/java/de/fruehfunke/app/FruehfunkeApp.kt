package de.fruehfunke.app

import android.app.Application
import de.fruehfunke.app.alarm.AlarmScheduler
import de.fruehfunke.app.alarm.Notifications
import de.fruehfunke.app.data.AlarmRepository
import de.fruehfunke.app.data.AppSettings
import de.fruehfunke.app.data.StationRepository

class FruehfunkeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppSettings.init(this)
        AlarmRepository.init(this)
        StationRepository.init(this)
        Notifications.createChannels(this)
        AlarmScheduler.rescheduleAll(this)
    }
}
