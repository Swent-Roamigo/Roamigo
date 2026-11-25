// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.weather

import java.time.LocalDate

/**
 * How far ahead a forecast can be trusted. Beyond about two weeks it is little better than the
 * climate, so the app shows nothing instead of a guess.
 */
object ForecastWindow {
  const val DAYS_AHEAD = 14L

  /** The last day that has a forecast when today is [today]. */
  fun lastDay(today: LocalDate): LocalDate = today.plusDays(DAYS_AHEAD)

  /** Whether a trip starting on [tripStart] already has a forecast. */
  fun isAvailable(tripStart: LocalDate, today: LocalDate): Boolean =
      !tripStart.isAfter(lastDay(today))

  /** The first day on which a trip starting on [tripStart] has a forecast. */
  fun availableFrom(tripStart: LocalDate): LocalDate = tripStart.minusDays(DAYS_AHEAD)
}
