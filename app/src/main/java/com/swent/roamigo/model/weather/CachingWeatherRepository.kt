// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.weather

import java.io.IOException
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Saves every forecast of [delegate] in [cache] and gives the saved one back when the service
 * cannot be reached, whichever way [delegate] reports it with an [IOException]. The saved forecast
 * is marked [Forecast.fromCache], so the screen can say how old it is.
 *
 * The saved forecast may cover only some of the requested days, as the service would. Days already
 * past, or beyond [ForecastWindow], are left out, so offline shows what online would. The
 * destination's own date is unknown here, so the window is one day wider on each side.
 *
 * A cache that fails is ignored: it must never hide a forecast that was just loaded, nor the reason
 * a forecast could not be loaded.
 *
 * The forecast is saved in the background, so a slow disk never delays the screen.
 *
 * @param clock gives "today", to leave out the days that are over.
 * @param writeScope runs the saving; it outlives any one screen, so a leaving screen does not lose
 *   the write.
 */
class CachingWeatherRepository(
    private val delegate: WeatherRepository,
    private val cache: WeatherCache,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val writeScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : WeatherRepository {

  override suspend fun forecast(
      latitude: Double,
      longitude: Double,
      from: LocalDate,
      to: LocalDate,
  ): Forecast =
      try {
        delegate.forecast(latitude, longitude, from, to).also { fresh ->
          if (fresh.days.isNotEmpty()) {
            writeScope.launch { quietly { cache.put(latitude, longitude, fresh) } }
          }
        }
      } catch (failure: IOException) {
        saved(latitude, longitude, from, to)
            ?: throw (failure as? WeatherUnavailableException
                ?: WeatherUnavailableException("The forecast could not be loaded", failure, true))
      }

  private suspend fun saved(
      latitude: Double,
      longitude: Double,
      from: LocalDate,
      to: LocalDate,
  ): Forecast? {
    val today = LocalDate.now(clock)
    val first = maxOf(from, today.minusDays(1))
    val last = minOf(to, ForecastWindow.lastDay(today).plusDays(1))
    if (first.isAfter(last)) return null
    val forecast = quietly { cache.get(latitude, longitude, first, last) } ?: return null
    return if (forecast.days.isEmpty()) null else forecast.copy(fromCache = true)
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
