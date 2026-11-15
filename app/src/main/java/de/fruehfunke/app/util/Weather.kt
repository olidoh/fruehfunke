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
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * Wetter vom Deutschen Wetterdienst (offene DWD-Daten über Bright Sky, ohne API-Schlüssel).
 * Die Ortssuche nutzt den Geocoding-Dienst von Open-Meteo.
 */
object Weather {
    data class Place(val name: String, val region: String, val lat: Double, val lon: Double)

    data class Report(
        val place: String,
        /** Name der DWD-Station, deren Messwerte benutzt wurden */
        val station: String,
        val tempNow: Double,
        /** Aktuelle Wetterlage als deutscher Text, z. B. „bedeckt“ */
        val condNow: String,
        val max: Double,
        val min: Double,
        /** Wetterlage für den Tag, z. B. „zeitweise Regen“ */
        val condDay: String,
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

    /**
     * Daten des Deutschen Wetterdienstes über Bright Sky (api.brightsky.dev):
     * aktuelle Messwerte der nächstgelegenen DWD-Station und die stündliche DWD-Vorhersage (MOSMIX).
     */
    private fun fetch(lat: Double, lon: Double, place: String): Report {
        val tz = "Europe%2FBerlin"
        val current = JSONObject(get("https://api.brightsky.dev/current_weather?lat=$lat&lon=$lon&tz=$tz"))
        val cw = current.getJSONObject("weather")
        val station = current.optJSONArray("sources")?.optJSONObject(0)?.optString("station_name").orEmpty()

        val today = LocalDate.now(ZoneId.of("Europe/Berlin"))
        val day = JSONObject(
            get("https://api.brightsky.dev/weather?lat=$lat&lon=$lon&date=$today&last_date=${today.plusDays(1)}&tz=$tz")
        ).getJSONArray("weather")

        val temps = mutableListOf<Double>()
        val dayIcons = mutableListOf<String>()
        val dayConditions = mutableListOf<String>()
        var rainChance: Int? = null
        val nowHour = LocalTime.now(ZoneId.of("Europe/Berlin")).hour
        for (i in 0 until day.length()) {
            val r = day.getJSONObject(i)
            val hour = runCatching { OffsetDateTime.parse(r.getString("timestamp")).hour }.getOrDefault(12)
            if (!r.isNull("temperature")) temps += r.getDouble("temperature")
            if (hour in 7..20) {
                r.optString("icon").takeIf { it.isNotBlank() && it != "null" }?.let { dayIcons += it }
                r.optString("condition").takeIf { it.isNotBlank() && it != "null" }?.let { dayConditions += it }
            }
            if (hour >= nowHour && !r.isNull("precipitation_probability")) {
                val p = r.getInt("precipitation_probability")
                rainChance = maxOf(rainChance ?: 0, p)
            }
        }
        val tempNow = if (cw.isNull("temperature")) temps.firstOrNull() ?: 0.0 else cw.getDouble("temperature")
        return Report(
            place = place,
            station = station,
            tempNow = tempNow,
            condNow = describeNow(cw.optString("condition"), cw.optString("icon")),
            max = temps.maxOrNull() ?: tempNow,
            min = temps.minOrNull() ?: tempNow,
            condDay = describeDay(dayConditions, dayIcons),
            rainChance = rainChance,
            fetchedAt = System.currentTimeMillis(),
        )
    }

    private fun describeNow(condition: String, icon: String): String = when (condition) {
        "thunderstorm" -> "Gewitter"
        "hail" -> "Hagel"
        "snow" -> "Schneefall"
        "sleet" -> "Schneeregen"
        "rain" -> "Regen"
        "fog" -> "neblig"
        else -> describeIcon(icon)
    }

    private fun describeIcon(icon: String): String = when (icon) {
        "clear-day" -> "sonnig"
        "clear-night" -> "klar"
        "partly-cloudy-day", "partly-cloudy-night" -> "teilweise bewölkt"
        "cloudy" -> "bedeckt"
        "fog" -> "neblig"
        "wind" -> "windig"
        "rain" -> "Regen"
        "sleet" -> "Schneeregen"
        "snow" -> "Schneefall"
        "hail" -> "Hagel"
        "thunderstorm" -> "Gewitter"
        else -> "wechselhaft"
    }

    /** Fasst die Stunden von 7 bis 20 Uhr zu einer Tageswetterlage zusammen. */
    private fun describeDay(conditions: List<String>, icons: List<String>): String {
        val wet = conditions.count { it == "rain" || it == "sleet" }
        return when {
            "thunderstorm" in conditions -> "Gewitter möglich"
            "hail" in conditions -> "Hagel möglich"
            conditions.count { it == "snow" } >= 3 -> "Schneefall"
            "snow" in conditions -> "zeitweise Schnee"
            wet >= 5 -> "regnerisch"
            wet >= 1 -> "zeitweise Regen"
            icons.isEmpty() -> "wechselhaft"
            else -> {
                val clear = icons.count { it.startsWith("clear") }
                val cloudy = icons.count { it == "cloudy" }
                val fog = icons.count { it == "fog" }
                when {
                    fog * 2 >= icons.size -> "neblig"
                    clear * 2 >= icons.size -> "sonnig"
                    cloudy * 2 >= icons.size -> "bedeckt"
                    else -> "wechselnd bewölkt"
                }
            }
        }
    }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 8_000
        conn.setRequestProperty("User-Agent", "Fruehfunke-Wecker/1.0 (Android)")
        return try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun toJson(r: Report) = JSONObject()
        .put("place", r.place).put("station", r.station).put("tempNow", r.tempNow).put("condNow", r.condNow)
        .put("max", r.max).put("min", r.min).put("condDay", r.condDay)
        .put("rainChance", r.rainChance ?: -1).put("fetchedAt", r.fetchedAt)

    private fun fromJson(o: JSONObject) = Report(
        place = o.getString("place"),
        station = o.optString("station"),
        tempNow = o.getDouble("tempNow"),
        condNow = o.getString("condNow"),
        max = o.getDouble("max"),
        min = o.getDouble("min"),
        condDay = o.getString("condDay"),
        rainChance = o.optInt("rainChance", -1).takeIf { it >= 0 },
        fetchedAt = o.getLong("fetchedAt"),
    )
}
