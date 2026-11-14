package de.fruehfunke.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Station(val id: Int, val name: String, val url: String)

/** Gespeicherte Radiosender, die in jedem Wecker ausgewählt werden können. */
object StationRepository {
    private lateinit var file: File
    private val _stations = MutableStateFlow<List<Station>>(emptyList())
    val stations: StateFlow<List<Station>> = _stations.asStateFlow()

    fun init(context: Context) {
        file = File(context.createDeviceProtectedStorageContext().filesDir, "stations.json")
        _stations.value = runCatching {
            if (!file.exists()) emptyList()
            else JSONArray(file.readText()).let { a ->
                (0 until a.length()).map {
                    val o = a.getJSONObject(it)
                    Station(o.getInt("id"), o.getString("name"), o.getString("url"))
                }
            }
        }.getOrElse { emptyList() }
    }

    @Synchronized
    fun save(id: Int, name: String, url: String): Station {
        val list = _stations.value.toMutableList()
        val station = Station(
            id = if (id == 0) (list.maxOfOrNull { it.id } ?: 0) + 1 else id,
            name = name.trim().ifBlank { url.trim() },
            url = url.trim(),
        )
        val idx = list.indexOfFirst { it.id == station.id }
        if (idx >= 0) list[idx] = station else list.add(station)
        commit(list)
        return station
    }

    @Synchronized
    fun delete(id: Int) = commit(_stations.value.filterNot { it.id == id })

    private fun commit(list: List<Station>) {
        val sorted = list.sortedBy { it.name.lowercase() }
        _stations.value = sorted
        val json = JSONArray().apply {
            sorted.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("url", it.url)) }
        }.toString()
        val tmp = File(file.parentFile, "stations.json.tmp")
        tmp.writeText(json)
        if (!tmp.renameTo(file)) {
            file.writeText(json)
            tmp.delete()
        }
    }

    fun isValidUrl(url: String): Boolean {
        val u = url.trim().lowercase()
        return (u.startsWith("http://") || u.startsWith("https://")) && u.length > 10
    }
}
