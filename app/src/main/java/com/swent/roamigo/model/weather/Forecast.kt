// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.weather

import java.time.Instant
import java.time.LocalDate

/** What the sky does on a day, in the few words a traveller needs to pack and plan. */
enum class WeatherCondition {
  SUNNY,
  PARTLY_CLOUDY,
  CLOUDY,
  RAIN,
  SNOW,
}

/**
 * The forecast of one day at the destination.
 *
 * @param date the calendar day at the destination, not at the device.
 * @param highCelsius the highest temperature, in whole degrees.
 * @param lowCelsius the lowest temperature, in whole degrees.
 * @param rainChancePercent the chance of rain from 0 to 100, or null if the provider gave none.
 */
data class DailyForecast(
    val date: LocalDate,
    val condition: WeatherCondition,
    val highCelsius: Int,
    val lowCelsius: Int,
    val rainChancePercent: Int?,
)

/**
 * The forecast for a trip.
 *
 * @param days the days the provider could forecast, oldest first. Empty when none of the requested
 *   days is inside the forecast window.
 * @param fetchedAt when the app asked the provider, so the screen can say how fresh it is.
 * @param fromCache whether it was saved earlier and could not be refreshed now.
 * @param destinationToday the calendar day it was at the destination when the app asked, or null
 *   when that is not known (a saved forecast), in which case the device's date is the best guess.
 */
data class Forecast(
    val days: List<DailyForecast>,
    val fetchedAt: Instant,
    val fromCache: Boolean = false,
    val destinationToday: LocalDate? = null,
)
