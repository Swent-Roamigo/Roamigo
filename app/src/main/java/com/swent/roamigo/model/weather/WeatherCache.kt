// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.weather

/** Keeps the last forecast of a place, so that a trip still shows one without a connection. */
interface WeatherCache {
  /** The forecast saved for the place around the point, or null if there is none. */
  suspend fun get(latitude: Double, longitude: Double): Forecast?

  /** Saves [forecast] for the place around the point, replacing the one saved before. */
  suspend fun put(latitude: Double, longitude: Double, forecast: Forecast)
}
