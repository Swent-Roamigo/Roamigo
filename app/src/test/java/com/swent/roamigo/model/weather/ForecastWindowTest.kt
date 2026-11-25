// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.weather

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastWindowTest {
  private val today = LocalDate.of(2026, 11, 23)

  @Test
  fun aTripStartingWithinFourteenDaysHasAForecast() {
    assertTrue(ForecastWindow.isAvailable(today, today))
    assertTrue(ForecastWindow.isAvailable(LocalDate.of(2026, 12, 7), today))
  }

  @Test
  fun aTripStartingLaterHasNoForecastYet() {
    assertFalse(ForecastWindow.isAvailable(LocalDate.of(2026, 12, 8), today))
  }

  @Test
  fun theForecastComesFourteenDaysBeforeTheTrip() {
    assertEquals(
        LocalDate.of(2026, 12, 6),
        ForecastWindow.availableFrom(LocalDate.of(2026, 12, 20)),
    )
  }

  @Test
  fun theLastForecastDayIsFourteenDaysAhead() {
    assertEquals(LocalDate.of(2026, 12, 7), ForecastWindow.lastDay(today))
  }
}
