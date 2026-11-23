// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.weather

import java.time.Instant
import java.time.LocalDate

/**
 * In-memory [WeatherRepository] for previews and tests. It answers with the days it was given that
 * fall inside the requested range, or fails with [failure] when that is set.
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
    requests += from to to
    failure?.let { throw it }
    return Forecast(days.filter { it.date in from..to }, fetchedAt)
  }
}
