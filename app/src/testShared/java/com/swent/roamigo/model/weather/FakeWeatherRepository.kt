// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.weather

import com.swent.roamigo.model.MAX_LATITUDE
import com.swent.roamigo.model.MAX_LONGITUDE
import com.swent.roamigo.model.MIN_LATITUDE
import com.swent.roamigo.model.MIN_LONGITUDE
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * In-memory [WeatherRepository] for previews and tests. It follows the contract of the real one: it
 * refuses coordinates that are not on Earth, and it leaves out the days before "today" (the day it
 * was fetched, in UTC) and the days beyond [ForecastWindow], so a test cannot pass on a forecast that
 * production would never return. It fails with [failure] when that is set.
 */
class FakeWeatherRepository(
    var days: List<DailyForecast> = emptyList(),
    var failure: Exception? = null,
    private val fetchedAt: Instant = Instant.parse("2026-11-23T10:00:00Z"),
) : WeatherRepository {
  /** The date ranges that were asked for, oldest request first. */
  val requests = mutableListOf<Pair<LocalDate, LocalDate>>()

  override suspend fun forecast(
      latitude: Double,
      longitude: Double,
      from: LocalDate,
      to: LocalDate,
  ): Forecast {
    require(latitude in MIN_LATITUDE..MAX_LATITUDE && longitude in MIN_LONGITUDE..MAX_LONGITUDE) {
      "Invalid coordinates: $latitude, $longitude"
    }
    requests += from to to
    failure?.let { throw it }
    val today = fetchedAt.atZone(ZoneOffset.UTC).toLocalDate()
    val window = maxOf(from, today)..minOf(to, ForecastWindow.lastDay(today))
    return Forecast(days.filter { it.date in window }, fetchedAt, destinationToday = today)
  }
}
