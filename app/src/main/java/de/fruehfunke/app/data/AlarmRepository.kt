package de.fruehfunke.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import java.io.File

/**
 * Speichert die Wecker als JSON im geräteverschlüsselten Speicher, damit sie
 * auch direkt nach einem Neustart vor dem ersten Entsperren verfügbar sind.
 */
object AlarmRepository {
    private lateinit var file: File
    private val _alarms = MutableStateFlow<List<Alarm>>(emptyList())
    val alarms: StateFlow<List<Alarm>> = _alarms.asStateFlow()
    private var initialized = false

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        val storage = context.createDeviceProtectedStorageContext()
        file = File(storage.filesDir, "alarms.json")
        _alarms.value = load()
        initialized = true
    }

    fun get(id: Int): Alarm? = _alarms.value.firstOrNull { it.id == id }

    @Synchronized
    fun upsert(alarm: Alarm): Alarm {
        val list = _alarms.value.toMutableList()
        val saved = if (alarm.id == 0) alarm.copy(id = (list.maxOfOrNull { it.id } ?: 0) + 1) else alarm
        val idx = list.indexOfFirst { it.id == saved.id }
        if (idx >= 0) list[idx] = saved else list.add(saved)
        commit(list)
        return saved
    }

    @Synchronized
    fun update(id: Int, transform: (Alarm) -> Alarm): Alarm? {
        val current = get(id) ?: return null
        return upsert(transform(current))
    }

    @Synchronized
    fun delete(id: Int) {
        commit(_alarms.value.filterNot { it.id == id })
    }

    private fun commit(list: List<Alarm>) {
        val sorted = list.sortedWith(compareBy({ it.hour }, { it.minute }, { it.id }))
        _alarms.value = sorted
        save(sorted)
    }

    private fun load(): List<Alarm> = runCatching {
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText())
        (0 until arr.length()).map { Alarm.fromJson(arr.getJSONObject(it)) }
    }.getOrElse { emptyList() }

    private fun save(list: List<Alarm>) {
        val json = JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()
        val tmp = File(file.parentFile, "alarms.json.tmp")
        tmp.writeText(json)
        if (!tmp.renameTo(file)) {
            file.writeText(json)
            tmp.delete()
        }
    }
}
