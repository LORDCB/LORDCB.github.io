package com.lordcb.homelauncher.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate

enum class WeatherKind { Clear, PartlyCloudy, Cloudy, Fog, Drizzle, Rain, Snow, Thunder }

data class DailyForecast(
    val date: LocalDate,
    val code: Int,
    val max: Double,
    val min: Double,
    val precipChance: Int?,
)

data class Weather(
    val locationName: String,
    val temperature: Double,
    val feelsLike: Double,
    val code: Int,
    val isDay: Boolean,
    val humidity: Int?,
    val daily: List<DailyForecast>,
)

data class GeoResult(val name: String, val detail: String, val latitude: Double, val longitude: Double)

data class WeatherKey(val latitude: Double, val longitude: Double, val name: String, val fahrenheit: Boolean)

fun weatherKind(code: Int): WeatherKind = when (code) {
    0 -> WeatherKind.Clear
    1, 2 -> WeatherKind.PartlyCloudy
    3 -> WeatherKind.Cloudy
    45, 48 -> WeatherKind.Fog
    in 51..57 -> WeatherKind.Drizzle
    in 61..67, in 80..82 -> WeatherKind.Rain
    in 71..77, 85, 86 -> WeatherKind.Snow
    in 95..99 -> WeatherKind.Thunder
    else -> WeatherKind.Cloudy
}

fun weatherLabel(code: Int): String = when (code) {
    0 -> "Clear"
    1 -> "Mostly clear"
    2 -> "Partly cloudy"
    3 -> "Overcast"
    45, 48 -> "Fog"
    51, 53, 55 -> "Drizzle"
    56, 57 -> "Freezing drizzle"
    61 -> "Light rain"
    63 -> "Rain"
    65 -> "Heavy rain"
    66, 67 -> "Freezing rain"
    71 -> "Light snow"
    73 -> "Snow"
    75 -> "Heavy snow"
    77 -> "Snow grains"
    80, 81 -> "Showers"
    82 -> "Heavy showers"
    85, 86 -> "Snow showers"
    95 -> "Thunderstorm"
    96, 99 -> "Thunder & hail"
    else -> "Cloudy"
}

/** Weather from Open-Meteo (free, no API key needed). */
class WeatherRepository(private val http: OkHttpClient) {

    suspend fun forecast(key: WeatherKey): Weather = withContext(Dispatchers.IO) {
        val url = "https://api.open-meteo.com/v1/forecast".toHttpUrl().newBuilder()
            .addQueryParameter("latitude", key.latitude.toString())
            .addQueryParameter("longitude", key.longitude.toString())
            .addQueryParameter(
                "current",
                "temperature_2m,apparent_temperature,weather_code,is_day,relative_humidity_2m"
            )
            .addQueryParameter(
                "daily",
                "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max"
            )
            .addQueryParameter("timezone", "auto")
            .addQueryParameter("forecast_days", "6")
            .addQueryParameter("temperature_unit", if (key.fahrenheit) "fahrenheit" else "celsius")
            .build()
        val json = JSONObject(get(url.toString()))
        val current = json.getJSONObject("current")
        val daily = json.getJSONObject("daily")
        val times = daily.getJSONArray("time")
        val codes = daily.getJSONArray("weather_code")
        val maxes = daily.getJSONArray("temperature_2m_max")
        val mins = daily.getJSONArray("temperature_2m_min")
        val rain = daily.optJSONArray("precipitation_probability_max")
        val days = (0 until times.length()).map { i ->
            DailyForecast(
                date = LocalDate.parse(times.getString(i)),
                code = codes.optInt(i),
                max = maxes.optDouble(i),
                min = mins.optDouble(i),
                precipChance = if (rain == null || rain.isNull(i)) null else rain.optInt(i),
            )
        }
        Weather(
            locationName = key.name,
            temperature = current.optDouble("temperature_2m"),
            feelsLike = current.optDouble("apparent_temperature"),
            code = current.optInt("weather_code"),
            isDay = current.optInt("is_day", 1) == 1,
            humidity = if (current.isNull("relative_humidity_2m")) null else current.optInt("relative_humidity_2m"),
            daily = days,
        )
    }

    suspend fun searchCity(query: String): List<GeoResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val url = "https://geocoding-api.open-meteo.com/v1/search".toHttpUrl().newBuilder()
            .addQueryParameter("name", query.trim())
            .addQueryParameter("count", "8")
            .addQueryParameter("language", "en")
            .build()
        val results = JSONObject(get(url.toString())).optJSONArray("results")
            ?: return@withContext emptyList()
        (0 until results.length()).mapNotNull { i ->
            val r = results.optJSONObject(i) ?: return@mapNotNull null
            GeoResult(
                name = r.optString("name"),
                detail = listOfNotNull(r.str("admin1"), r.str("country")).joinToString(", "),
                latitude = r.optDouble("latitude"),
                longitude = r.optDouble("longitude"),
            )
        }
    }

    private fun get(url: String): String =
        http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Weather service returned ${resp.code}")
            resp.body?.string() ?: throw IOException("Empty response")
        }
}
