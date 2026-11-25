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
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
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
 * at most [MAX_DAYS] days, the latest ones, and drops the days that are over.
 *
 * Every day is saved with the time it was fetched. A day is replaced only by a forecast fetched at
 * least as late, so a slow request that finishes after a newer one cannot bring back older data. A
 * saved time more than [MAX_RACE] ahead of the new one cannot be such a race, only a device clock
 * that was set back, so it does not hold the forecast back.
 *
 * Entries are read and written on [ioDispatcher], so decoding never runs on the caller's thread.
 * The order of saving places is a counter rather than a time, which a wrong device clock cannot
 * upset.
 */
class DataStoreWeatherCache(
    private val store: DataStore<Preferences>,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : WeatherCache {

  constructor(context: Context) : this(context.applicationContext.weatherDataStore)

  /** A day and the time it was fetched. */
  private class SavedDay(val day: DailyForecast, val fetchedAt: Instant)

  override suspend fun get(
      latitude: Double,
      longitude: Double,
      from: LocalDate,
      to: LocalDate,
  ): Forecast? =
      withContext(ioDispatcher) {
        val days =
            store.data.first()[key(latitude, longitude)]?.let(::decode).orEmpty().filter {
              it.day.date in from..to
            }
        if (days.isEmpty()) null
        else Forecast(days.map { it.day }, fetchedAt = days.minOf { it.fetchedAt })
      }

  override suspend fun put(latitude: Double, longitude: Double, forecast: Forecast) {
    withContext(ioDispatcher) {
      store.edit { preferences ->
        val key = key(latitude, longitude)
        val byDate =
            preferences[key]?.let(::decode).orEmpty().associateBy { it.day.date }.toMutableMap()
        for (day in forecast.days) {
          val saved = byDate[day.date]
          if (saved == null || !isNewerForecast(saved.fetchedAt, than = forecast.fetchedAt)) {
            byDate[day.date] = SavedDay(day, forecast.fetchedAt)
          }
        }
        // The destination can be a day behind UTC, so yesterday is still worth keeping.
        val firstKept = forecast.fetchedAt.atZone(ZoneOffset.UTC).toLocalDate().minusDays(1)
        val merged =
            byDate.values
                .filter { it.day.date >= firstKept }
                .sortedBy { it.day.date }
                .takeLast(MAX_DAYS)
        if (merged.isEmpty()) {
          preferences.remove(key)
          return@edit
        }
        val saved = preferences.asMap().keys.filter { it.name.startsWith(PREFIX) }
        val newest = saved.maxOfOrNull { order(preferences[stringPreferencesKey(it.name)]) } ?: 0L
        preferences[key] = encode(merged, order = newest + 1)
        val all = preferences.asMap().keys.filter { it.name.startsWith(PREFIX) }
        val oldestFirst = all.sortedBy { order(preferences[stringPreferencesKey(it.name)]) }
        oldestFirst.take((all.size - MAX_PLACES).coerceAtLeast(0)).forEach {
          preferences.remove(it)
        }
      }
    }
  }

  /** Whether [saved] is a forecast fetched shortly after [than], as a request finishing late is. */
  private fun isNewerForecast(saved: Instant, than: Instant): Boolean =
      saved.isAfter(than) && Duration.between(than, saved) <= MAX_RACE

  // BigDecimal gives plain digits, and no "-0.00" for a point just west of the meridian.
  private fun key(latitude: Double, longitude: Double) =
      stringPreferencesKey("$PREFIX${coarse(latitude)}:${coarse(longitude)}")

  private fun coarse(degrees: Double): String =
      BigDecimal(degrees).setScale(2, RoundingMode.HALF_UP).toPlainString()

  private fun encode(days: List<SavedDay>, order: Long): String =
      JSONObject()
          .put("s", order)
          .put(
              "d",
              JSONArray(
                  days.map { saved ->
                    JSONObject()
                        .put("d", saved.day.date.toString())
                        .put("c", saved.day.condition.name)
                        .put("h", saved.day.highCelsius)
                        .put("l", saved.day.lowCelsius)
                        .put("t", saved.fetchedAt.toEpochMilli())
                        .apply { saved.day.rainChancePercent?.let { put("r", it) } }
                  }
              ),
          )
          .toString()

  /** The days in [text], oldest first, or null if the entry cannot be read. */
  private fun decode(text: String): List<SavedDay>? =
      try {
        val root = JSONObject(text)
        val days = root.getJSONArray("d")
        // An entry saved before every day had its own time has one time for all of them.
        val entryTime = root.optLong("t", 0L)
        (0 until days.length())
            .map { index ->
              val day = days.getJSONObject(index)
              SavedDay(
                  DailyForecast(
                      date = LocalDate.parse(day.getString("d")),
                      condition = WeatherCondition.valueOf(day.getString("c")),
                      highCelsius = day.getInt("h"),
                      lowCelsius = day.getInt("l"),
                      rainChancePercent =
                          if (day.has("r")) day.getInt("r").coerceIn(0, 100) else null,
                  ),
                  Instant.ofEpochMilli(day.optLong("t", entryTime)),
              )
            }
            .distinctBy { it.day.date }
            .sortedBy { it.day.date }
      } catch (_: JSONException) {
        null
      } catch (_: RuntimeException) {
        // A wrong type or an unknown name: whatever it is, the entry is of no use.
        null
      }

  /**
   * Where the entry comes in the order of saving, or 0 for one whose days cannot be read, so it is
   * dropped first, whatever order it claims.
   */
  private fun order(text: String?): Long =
      try {
        if (text == null || decode(text) == null) 0L else JSONObject(text).optLong("s", 0L)
      } catch (_: JSONException) {
        0L
      }

  companion object {
    /** Enough for the trips a person has on the go, and a bounded file. */
    const val MAX_PLACES = 10

    /** More than a trip to one place can ask for, and a bounded entry. */
    const val MAX_DAYS = 30

    /**
     * Requests that run together finish within the time the network allows, far less than this. A
     * saved day fetched more than this after a new one is a clock that went back, not a late reply.
     */
    val MAX_RACE: Duration = Duration.ofDays(1)
    private const val PREFIX = "forecast:"
  }
}

private val Context.weatherDataStore by preferencesDataStore(name = "weather_forecasts")
