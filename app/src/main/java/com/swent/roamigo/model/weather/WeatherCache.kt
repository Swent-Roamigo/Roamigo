// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.weather

import java.time.LocalDate

/** Keeps the last forecast of a place, so that a trip still shows one without a connection. */
interface WeatherCache {
  /**
   * The saved days of the place around the point that fall from [from] to [to], or null if there is
   * none. Each day was saved at its own time, so [Forecast.fetchedAt] is that of the oldest day
   * returned: it never makes the forecast look fresher than its stalest day.
   */
  suspend fun get(latitude: Double, longitude: Double, from: LocalDate, to: LocalDate): Forecast?

  /**
   * Saves [forecast] for the place around the point, each day with the time [forecast] was fetched.
   * Its days replace the saved days of the same date, and the other saved days are kept, so
   * forecasts of different trips to one place add up, and a short request never shrinks a long one.
   *
   * A saved day is not replaced by a forecast fetched shortly before it, which is what a slow
   * request finishing after a newer one looks like. Days that are over are dropped.
   */
  suspend fun put(latitude: Double, longitude: Double, forecast: Forecast)
}
