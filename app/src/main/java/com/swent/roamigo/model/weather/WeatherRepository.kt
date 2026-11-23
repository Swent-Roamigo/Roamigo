// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.weather

import java.io.IOException
import java.time.LocalDate

/** Source of the daily weather forecast of a place. */
interface WeatherRepository {
  /**
   * The forecast for the days from [from] to [to], both included, at the given point. Days outside
   * [ForecastWindow] are left out, so the result can be empty.
   *
   * @throws IllegalArgumentException if the coordinates are not on Earth.
   * @throws WeatherUnavailableException if the forecast cannot be loaded, e.g. offline.
   */
  suspend fun forecast(
      latitude: Double,
      longitude: Double,
      from: LocalDate,
      to: LocalDate,
  ): Forecast
}

/** The forecast could not be loaded. [cause] tells why when the network failed. */
class WeatherUnavailableException(message: String, cause: Throwable? = null) :
    IOException(message, cause)
