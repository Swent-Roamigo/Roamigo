// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.weather

/** Keeps the last forecast of a place, so that a trip still shows one without a connection. */
interface WeatherCache {
  /** The forecast saved for the place around the point, or null if there is none. */
  suspend fun get(latitude: Double, longitude: Double): Forecast?

  /**
   * Saves [forecast] for the place around the point. Its days replace the saved days of the same
   * date and the other saved days are kept, so forecasts of different trips to one place add up
   * instead of evicting each other. The saved time becomes that of [forecast].
   */
  suspend fun put(latitude: Double, longitude: Double, forecast: Forecast)
}
