package de.fruehfunke.app.util

import android.content.Context
import android.net.Uri
import de.fruehfunke.app.alarm.AlarmScheduler
import de.fruehfunke.app.alarm.Notifications
import de.fruehfunke.app.data.Alarm
import de.fruehfunke.app.data.AlarmRepository
import de.fruehfunke.app.data.Station
import de.fruehfunke.app.data.StationRepository
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZonedDateTime

/** Export und Import aller Wecker und Radiosender als JSON-Datei (z. B. auf OneDrive). */
object Backup {
    private const val FORMAT = "fruehfunke-backup"

    data class Preview(val alarms: List<Alarm>, val stations: List<Station>)

    /** Inhalt ohne Zeitstempel – damit lässt sich erkennen, ob sich etwas geändert hat. */
    fun content(): JSONObject = JSONObject().apply {
        put("format", FORMAT)
        put("version", 1)
        put("alarms", JSONArray().apply {
            AlarmRepository.alarms.value
                .filterNot { it.isCountdown }
                .forEach { put(clean(it).toJson().apply { remove("id") }) }
        })
        put("stations", JSONArray().apply {
            StationRepository.stations.value.forEach { put(JSONObject().put("name", it.name).put("url", it.url)) }
        })
    }

    fun exportJson(): String = content().put("exportedAt", ZonedDateTime.now().toString()).toString(2)

    fun write(context: Context, uri: Uri): Boolean = runCatching {
        context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(exportJson().toByteArray()) }
        true
    }.getOrDefault(false)

    fun read(context: Context, uri: Uri): Preview {
        val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
        val o = JSONObject(text)
        require(o.optString("format") == FORMAT) { "Keine Frühfunke-Sicherung" }
        val alarms = o.optJSONArray("alarms")?.let { a ->
            (0 until a.length()).map { clean(Alarm.fromJson(a.getJSONObject(it)).copy(id = 0)) }
        } ?: emptyList()
        val stations = o.optJSONArray("stations")?.let { a ->
            (0 until a.length()).mapNotNull {
                val s = a.getJSONObject(it)
                val url = s.optString("url")
                if (StationRepository.isValidUrl(url)) Station(0, s.optString("name", url), url) else null
            }
        } ?: emptyList()
        return Preview(alarms, stations)
    }

    /** @param replace true = vorhandene Wecker werden ersetzt, false = nur Neues ergänzen */
    fun apply(context: Context, preview: Preview, replace: Boolean): Int {
        if (replace) {
            AlarmRepository.alarms.value.forEach {
                AlarmScheduler.cancel(context, it.id)
                Notifications.cancelSnoozed(context, it.id)
                AlarmRepository.delete(it.id)
            }
        }
        val existing = AlarmRepository.alarms.value.map { key(it) }.toSet()
        var added = 0
        preview.alarms.forEach { a ->
            if (key(a) !in existing) {
                AlarmRepository.upsert(a)
                added++
            }
        }
        val knownUrls = StationRepository.stations.value.map { it.url }.toSet()
        preview.stations.filter { it.url !in knownUrls }.forEach { StationRepository.save(0, it.name, it.url) }
        AlarmScheduler.rescheduleAll(context)
        return added
    }

    /** Laufzeitzustand (Schlummern, Überspringen) gehört nicht in die Sicherung. */
    private fun clean(a: Alarm) = a.copy(snoozeUntil = 0L, snoozeCount = 0, skipUntil = 0L)

    private fun key(a: Alarm) = listOf(
        a.hour, a.minute, a.label, a.days.sorted(), a.weekParity, a.onceDate, a.streamUrl,
    ).joinToString("|")
}
