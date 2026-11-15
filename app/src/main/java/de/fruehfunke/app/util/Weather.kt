package de.fruehfunke.app.util

import android.content.Context
import de.fruehfunke.app.data.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Wetter über Open-Meteo (kostenlos, ohne API-Schlüssel). */
object Weather {
    data class Place(val name: String, val region: String, val lat: Double, val lon: Double)

    data class Report(
        val place: String,
        val tempNow: Double,
        val codeNow: Int,
        val max: Double,
        val min: Double,
        val codeDay: Int,
        val rainChance: Int?,
        val fetchedAt: Long,
    )

    val latest = MutableStateFlow<Report?>(null)
    private var cacheFile: File? = null
    private const val MAX_CACHE_AGE = 3 * 60 * 60_000L

    fun init(context: Context) {
        cacheFile = File(context.createDeviceProtectedStorageContext().filesDir, "weather.json")
        latest.value = runCatching { cacheFile?.takeIf { it.exists() }?.readText()?.let { fromJson(JSONObject(it)) } }.getOrNull()
    }

    /** Ortssuche; im Hintergrund-Thread aufrufen. */
    fun search(name: String): List<Place> {
        val q = URLEncoder.encode(name.trim(), "UTF-8")
        val o = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=8&language=de&format=json"))
        val arr = o.optJSONArray("results") ?: return emptyList()
        return (0 until arr.length()).map {
            val r = arr.getJSONObject(it)
            Place(
                name = r.getString("name"),
                region = listOfNotNull(r.optString("admin1").takeIf { s -> s.isNotBlank() }, r.optString("country").takeIf { s -> s.isNotBlank() })
                    .joinToString(", "),
                lat = r.getDouble("latitude"),
                lon = r.getDouble("longitude"),
            )
        }
    }

    /**
     * Aktuelles Wetter für den eingestellten Ort. Holt neue Daten, wenn der Cache älter als [maxAgeMs] ist;
     * klappt das nicht, wird ein bis zu 3 Stunden alter Cache benutzt.
     */
    suspend fun refresh(maxAgeMs: Long = 30 * 60_000L): Report? {
        val s = AppSettings.state.value
        val lat = s.weatherLat ?: return null
        val lon = s.weatherLon ?: return null
        val place = s.weatherName ?: ""
        val now = System.currentTimeMillis()
        val cached = latest.value?.takeIf { it.place == place }
        if (cached != null && now - cached.fetchedAt < maxAgeMs) return cached
        val fresh = withContext(Dispatchers.IO) { runCatching { fetch(lat, lon, place) }.getOrNull() }
        if (fresh != null) {
            latest.value = fresh
            runCatching { cacheFile?.writeText(toJson(fresh).toString()) }
            return fresh
        }
        return cached?.takeIf { now - it.fetchedAt < MAX_CACHE_AGE }
    }

    private fun fetch(lat: Double, lon: Double, place: String): Report {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,weather_code" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
            "&forecast_days=1&timezone=auto"
        val o = JSONObject(get(url))
        val cur = o.getJSONObject("current")
        val daily = o.getJSONObject("daily")
        val rain = daily.optJSONArray("precipitation_probability_max")
        return Report(
            place = place,
            tempNow = cur.getDouble("temperature_2m"),
            codeNow = cur.getInt("weather_code"),
            max = daily.getJSONArray("temperature_2m_max").getDouble(0),
            min = daily.getJSONArray("temperature_2m_min").getDouble(0),
            codeDay = daily.getJSONArray("weather_code").getInt(0),
            rainChance = if (rain == null || rain.isNull(0)) null else rain.getInt(0),
            fetchedAt = System.currentTimeMillis(),
        )
    }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 8_000
        return try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun toJson(r: Report) = JSONObject()
        .put("place", r.place).put("tempNow", r.tempNow).put("codeNow", r.codeNow)
        .put("max", r.max).put("min", r.min).put("codeDay", r.codeDay)
        .put("rainChance", r.rainChance ?: -1).put("fetchedAt", r.fetchedAt)

    private fun fromJson(o: JSONObject) = Report(
        place = o.getString("place"),
        tempNow = o.getDouble("tempNow"),
        codeNow = o.getInt("codeNow"),
        max = o.getDouble("max"),
        min = o.getDouble("min"),
        codeDay = o.getInt("codeDay"),
        rainChance = o.optInt("rainChance", -1).takeIf { it >= 0 },
        fetchedAt = o.getLong("fetchedAt"),
    )

    /** WMO-Wettercode als deutscher Text. */
    fun describe(code: Int): String = when (code) {
        0 -> "sonnig und klar"
        1 -> "überwiegend klar"
        2 -> "teilweise bewölkt"
        3 -> "bedeckt"
        45, 48 -> "neblig"
        51, 53, 55 -> "Nieselregen"
        56, 57 -> "gefrierender Nieselregen"
        61 -> "leichter Regen"
        63 -> "Regen"
        65 -> "starker Regen"
        66, 67 -> "gefrierender Regen"
        71 -> "leichter Schneefall"
        73 -> "Schneefall"
        75 -> "starker Schneefall"
        77 -> "Schneegriesel"
        80 -> "leichte Regenschauer"
        81 -> "Regenschauer"
        82 -> "heftige Regenschauer"
        85, 86 -> "Schneeschauer"
        95 -> "Gewitter"
        96, 99 -> "Gewitter mit Hagel"
        else -> "wechselhaft"
    }
}
