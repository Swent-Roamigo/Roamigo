// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.weather

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * [WeatherCache] in a DataStore. A place is its coordinates to two decimals, about a kilometre, and
 * only the [MAX_PLACES] most recently saved places are kept, so the file stays small. A place keeps
 * at most [MAX_DAYS] days, the latest ones.
 *
 * Entries are read and written on [ioDispatcher], so decoding never runs on the caller's thread.
 * The order of saving is a counter rather than a time, which a wrong device clock cannot upset.
 */
class DataStoreWeatherCache(
    private val store: DataStore<Preferences>,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : WeatherCache {

  constructor(context: Context) : this(context.applicationContext.weatherDataStore)

  override suspend fun get(latitude: Double, longitude: Double): Forecast? =
      withContext(ioDispatcher) { store.data.first()[key(latitude, longitude)]?.let(::decode) }

  override suspend fun put(latitude: Double, longitude: Double, forecast: Forecast) {
    withContext(ioDispatcher) {
      store.edit { preferences ->
        val key = key(latitude, longitude)
        val earlier = preferences[key]?.let(::decode)
        val merged =
            ((earlier?.days.orEmpty().filter { old ->
                  forecast.days.none { it.date == old.date }
                }) + forecast.days)
                .sortedBy { it.date }
                .takeLast(MAX_DAYS)
        val saved = preferences.asMap().keys.filter { it.name.startsWith(PREFIX) }
        val newest = saved.maxOfOrNull { order(preferences[stringPreferencesKey(it.name)]) } ?: 0L
        preferences[key] = encode(forecast.copy(days = merged), order = newest + 1)
        val all = preferences.asMap().keys.filter { it.name.startsWith(PREFIX) }
        val oldestFirst = all.sortedBy { order(preferences[stringPreferencesKey(it.name)]) }
        oldestFirst.take((all.size - MAX_PLACES).coerceAtLeast(0)).forEach {
          preferences.remove(it)
        }
      }
    }
  }

  // BigDecimal gives plain digits, and no "-0.00" for a point just west of the meridian.
  private fun key(latitude: Double, longitude: Double) =
      stringPreferencesKey("$PREFIX${coarse(latitude)}:${coarse(longitude)}")

  private fun coarse(degrees: Double): String =
      BigDecimal(degrees).setScale(2, RoundingMode.HALF_UP).toPlainString()

  private fun encode(forecast: Forecast, order: Long): String =
      JSONObject()
          .put("t", forecast.fetchedAt.toEpochMilli())
          .put("s", order)
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
                (0 until days.length())
                    .map { index ->
                      val day = days.getJSONObject(index)
                      DailyForecast(
                          date = LocalDate.parse(day.getString("d")),
                          condition = WeatherCondition.valueOf(day.getString("c")),
                          highCelsius = day.getInt("h"),
                          lowCelsius = day.getInt("l"),
                          rainChancePercent =
                              if (day.has("r")) day.getInt("r").coerceIn(0, 100) else null,
                      )
                    }
                    .distinctBy { it.date }
                    .sortedBy { it.date },
            fetchedAt = Instant.ofEpochMilli(root.getLong("t")),
        )
      } catch (_: JSONException) {
        null
      } catch (_: RuntimeException) {
        // A wrong type or an unknown name: whatever it is, the entry is of no use.
        null
      }

  /** When the entry was saved, or 0 for one that cannot be read, so it is dropped first. */
  private fun order(text: String?): Long =
      try {
        JSONObject(text.orEmpty()).optLong("s", 0L)
      } catch (_: JSONException) {
        0L
      }

  companion object {
    /** Enough for the trips a person has on the go, and a bounded file. */
    const val MAX_PLACES = 10

    /** More than a trip to one place can ask for, and a bounded entry. */
    const val MAX_DAYS = 30
    private const val PREFIX = "forecast:"
  }
}

private val Context.weatherDataStore by preferencesDataStore(name = "weather_forecasts")
