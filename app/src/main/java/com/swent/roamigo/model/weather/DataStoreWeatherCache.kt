// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.weather

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * [WeatherCache] in a DataStore. A place is its coordinates to two decimals, about a kilometre, and
 * only the [MAX_PLACES] most recently saved places are kept, so the file stays small.
 */
class DataStoreWeatherCache(private val store: DataStore<Preferences>) : WeatherCache {

  constructor(context: Context) : this(context.applicationContext.weatherDataStore)

  override suspend fun get(latitude: Double, longitude: Double): Forecast? =
      store.data.first()[key(latitude, longitude)]?.let(::decode)

  override suspend fun put(latitude: Double, longitude: Double, forecast: Forecast) {
    store.edit { preferences ->
      preferences[key(latitude, longitude)] = encode(forecast)
      val saved = preferences.asMap().keys.filter { it.name.startsWith(PREFIX) }
      val oldestFirst = saved.sortedBy { savedAt(preferences[stringPreferencesKey(it.name)]) }
      oldestFirst.take((saved.size - MAX_PLACES).coerceAtLeast(0)).forEach {
        preferences.remove(it)
      }
    }
  }

  private fun key(latitude: Double, longitude: Double) =
      stringPreferencesKey(
          "$PREFIX${"%.2f".format(Locale.ROOT, latitude)}:${"%.2f".format(Locale.ROOT, longitude)}"
      )

  private fun encode(forecast: Forecast): String =
      JSONObject()
          .put("t", forecast.fetchedAt.toEpochMilli())
          .put(
              "d",
              JSONArray(
                  forecast.days.map { day ->
                    JSONObject()
                        .put("d", day.date.toString())
                        .put("c", day.condition.name)
                        .put("h", day.highCelsius)
                        .put("l", day.lowCelsius)
                        .apply { day.rainChancePercent?.let { put("r", it) } }
                  }
              ),
          )
          .toString()

  /** The forecast in [text], or null if the entry cannot be read. */
  private fun decode(text: String): Forecast? =
      try {
        val root = JSONObject(text)
        val days = root.getJSONArray("d")
        Forecast(
            days =
                (0 until days.length()).map { index ->
                  val day = days.getJSONObject(index)
                  DailyForecast(
                      date = LocalDate.parse(day.getString("d")),
                      condition = WeatherCondition.valueOf(day.getString("c")),
                      highCelsius = day.getInt("h"),
                      lowCelsius = day.getInt("l"),
                      rainChancePercent = if (day.has("r")) day.getInt("r") else null,
                  )
                },
            fetchedAt = Instant.ofEpochMilli(root.getLong("t")),
        )
      } catch (_: JSONException) {
        null
      } catch (_: IllegalArgumentException) {
        null
      } catch (_: java.time.format.DateTimeParseException) {
        null
      }

  /** When the entry was saved, or 0 for one that cannot be read, so it is dropped first. */
  private fun savedAt(text: String?): Long =
      try {
        JSONObject(text.orEmpty()).getLong("t")
      } catch (_: JSONException) {
        0L
      }

  companion object {
    /** Enough for the trips a person has on the go, and a bounded file. */
    const val MAX_PLACES = 10
    private const val PREFIX = "forecast:"
  }
}

private val Context.weatherDataStore by preferencesDataStore(name = "weather_forecasts")
