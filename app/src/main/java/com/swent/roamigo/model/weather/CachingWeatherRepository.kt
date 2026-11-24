// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.weather

import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.CancellationException

/**
 * Saves every forecast of [delegate] in [cache] and gives the saved one back when the service
 * cannot be reached. The saved forecast is marked [Forecast.fromCache], so the screen can say how
 * old it is.
 *
 * The saved forecast may cover only some of the requested days, as the service would. Days already
 * past, or beyond [ForecastWindow], are left out, so offline shows what online would. The
 * destination's own date is unknown here, so the window is one day wider on each side.
 *
 * A cache that fails is ignored: it must never hide a forecast that was just loaded, nor the reason
 * a forecast could not be loaded.
 *
 * @param clock gives "today", to leave out the days that are over.
 */
class CachingWeatherRepository(
    private val delegate: WeatherRepository,
    private val cache: WeatherCache,
    private val clock: Clock = Clock.systemDefaultZone(),
) : WeatherRepository {

  override suspend fun forecast(
      latitude: Double,
      longitude: Double,
      from: LocalDate,
      to: LocalDate,
  ): Forecast =
      try {
        delegate.forecast(latitude, longitude, from, to).also { fresh ->
          if (fresh.days.isNotEmpty()) quietly { cache.put(latitude, longitude, fresh) }
        }
      } catch (unavailable: WeatherUnavailableException) {
        saved(latitude, longitude, from, to) ?: throw unavailable
      }

  private suspend fun saved(
      latitude: Double,
      longitude: Double,
      from: LocalDate,
      to: LocalDate,
  ): Forecast? {
    val forecast = quietly { cache.get(latitude, longitude) } ?: return null
    val today = LocalDate.now(clock)
    val window = today.minusDays(1)..ForecastWindow.lastDay(today).plusDays(1)
    val days = forecast.days.filter { it.date in from..to && it.date in window }
    return if (days.isEmpty()) null else Forecast(days, forecast.fetchedAt, fromCache = true)
  }

  private suspend fun <T> quietly(block: suspend () -> T): T? =
      try {
        block()
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: Exception) {
        null
      }
}
