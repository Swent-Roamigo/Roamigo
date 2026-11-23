// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.weather

import com.swent.roamigo.model.MAX_LATITUDE
import com.swent.roamigo.model.MAX_LONGITUDE
import com.swent.roamigo.model.MIN_LATITUDE
import com.swent.roamigo.model.MIN_LONGITUDE
import com.swent.roamigo.model.destination.DESTINATION_USER_AGENT
import com.swent.roamigo.model.place.await
import java.io.IOException
import java.time.Clock
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * [WeatherRepository] backed by Open-Meteo, which needs no key. Its data is free for non-commercial
 * use under CC BY 4.0, so the screen must credit "Open-Meteo".
 *
 * @param clock gives "today" for the forecast window, and the time the forecast was fetched.
 */
class OpenMeteoWeatherRepository(
    private val client: OkHttpClient = DEFAULT_CLIENT,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : WeatherRepository {

  override suspend fun forecast(
      latitude: Double,
      longitude: Double,
      from: LocalDate,
      to: LocalDate,
  ): Forecast {
    // A comparison with NaN is false, so NaN is refused here too.
    require(latitude in MIN_LATITUDE..MAX_LATITUDE && longitude in MIN_LONGITUDE..MAX_LONGITUDE) {
      "Invalid coordinates: $latitude, $longitude"
    }
    val today = LocalDate.now(clock)
    val first = maxOf(from, today)
    val last = minOf(to, ForecastWindow.lastDay(today))
    val fetchedAt = clock.instant()
    if (first.isAfter(last)) return Forecast(emptyList(), fetchedAt)

    val url =
        BASE_URL.toHttpUrl()
            .newBuilder()
            .addQueryParameter("latitude", latitude.toString())
            .addQueryParameter("longitude", longitude.toString())
            .addQueryParameter("daily", DAILY_FIELDS)
            .addQueryParameter("timezone", "auto")
            .addQueryParameter("start_date", first.toString())
            .addQueryParameter("end_date", last.toString())
            .build()
    val body = read(Request.Builder().url(url).header("User-Agent", DESTINATION_USER_AGENT).build())
    return Forecast(parseDays(body), fetchedAt)
  }

  private suspend fun read(request: Request): String =
      try {
        withContext(ioDispatcher) {
          client.newCall(request).await().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
              throw WeatherUnavailableException(
                  "Open-Meteo answered ${response.code}: ${reasonOf(text)}"
              )
            }
            text
          }
        }
      } catch (error: IOException) {
        if (error is WeatherUnavailableException) throw error
        throw WeatherUnavailableException("Open-Meteo could not be reached", error)
      }

  private fun reasonOf(text: String): String =
      try {
        JSONObject(text).optString("reason").ifBlank { "no reason given" }
      } catch (_: JSONException) {
        "no reason given"
      }

  private fun parseDays(body: String): List<DailyForecast> {
    val daily =
        try {
          JSONObject(body).optJSONObject("daily")
        } catch (_: JSONException) {
          null
        } ?: throw WeatherUnavailableException("Open-Meteo sent an answer the app cannot read")
    val times = daily.optJSONArray("time")
    val codes = daily.optJSONArray("weather_code")
    val highs = daily.optJSONArray("temperature_2m_max")
    val lows = daily.optJSONArray("temperature_2m_min")
    if (times == null || codes == null || highs == null || lows == null) {
      throw WeatherUnavailableException("Open-Meteo sent an answer the app cannot read")
    }
    val rain = daily.optJSONArray("precipitation_probability_max")
    return (0 until times.length()).mapNotNull { index ->
      dayAt(index, times, codes, highs, lows, rain)
    }
  }

  /**
   * The day at [index], or null if the provider left a value out or sent one that makes no sense.
   */
  private fun dayAt(
      index: Int,
      times: JSONArray,
      codes: JSONArray,
      highs: JSONArray,
      lows: JSONArray,
      rain: JSONArray?,
  ): DailyForecast? {
    val date =
        try {
          LocalDate.parse(times.optString(index))
        } catch (_: java.time.format.DateTimeParseException) {
          return null
        }
    if (codes.isNull(index)) return null
    val condition = conditionOf(codes.optInt(index, -1)) ?: return null
    val high = highs.optDouble(index)
    val low = lows.optDouble(index)
    if (!high.isFinite() || !low.isFinite()) return null
    val chance =
        if (rain == null || rain.isNull(index)) null else rain.optInt(index).coerceIn(0, 100)
    return DailyForecast(date, condition, high.roundToInt(), low.roundToInt(), chance)
  }

  /** WMO weather interpretation codes, as Open-Meteo documents them. */
  private fun conditionOf(code: Int): WeatherCondition? =
      when (code) {
        0,
        1 -> WeatherCondition.SUNNY
        2 -> WeatherCondition.PARTLY_CLOUDY
        3,
        45,
        48 -> WeatherCondition.CLOUDY
        in 51..67,
        in 80..82,
        in 95..99 -> WeatherCondition.RAIN
        in 71..77,
        85,
        86 -> WeatherCondition.SNOW
        else -> null
      }

  private companion object {
    const val BASE_URL = "https://api.open-meteo.com/v1/forecast"
    const val DAILY_FIELDS =
        "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max"
    const val TIMEOUT_SECONDS = 12L
    val DEFAULT_CLIENT: OkHttpClient =
        OkHttpClient.Builder().callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).build()
  }
}
