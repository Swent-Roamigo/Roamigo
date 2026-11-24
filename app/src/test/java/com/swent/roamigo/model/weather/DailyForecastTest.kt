// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.weather

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class DailyForecastTest {
  private fun day(condition: WeatherCondition, rain: Int?) =
      DailyForecast(LocalDate.of(2026, 11, 28), condition, 10, 5, rain)

  @Test
  fun aDayIsWetWhenRainOrSnowIsForecast() {
    assertEquals(true, day(WeatherCondition.RAIN, 0).isWet)
    assertEquals(true, day(WeatherCondition.SNOW, null).isWet)
  }

  @Test
  fun aDayIsWetFromHalfAChanceOfRain() {
    assertEquals(false, day(WeatherCondition.CLOUDY, 49).isWet)
    assertEquals(true, day(WeatherCondition.CLOUDY, 50).isWet)
  }

  @Test
  fun aDryDayWithoutARainChanceIsNotWet() {
    assertEquals(false, day(WeatherCondition.SUNNY, null).isWet)
  }

  @Test
  fun rainIsLikelyFromHalfAChanceButSnowIsNotRain() {
    assertEquals(true, day(WeatherCondition.RAIN, null).isRainLikely)
    assertEquals(true, day(WeatherCondition.CLOUDY, 50).isRainLikely)
    assertEquals(false, day(WeatherCondition.CLOUDY, 49).isRainLikely)
    assertEquals(false, day(WeatherCondition.SNOW, 90).isRainLikely)
  }
}
