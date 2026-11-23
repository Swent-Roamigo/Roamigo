// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.weather

import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FakeWeatherRepositoryTest {
  private fun day(date: String) =
      DailyForecast(LocalDate.parse(date), WeatherCondition.SUNNY, 16, 10, 5)

  @Test
  fun answersWithTheDaysInsideTheRequestedRangeAndRemembersTheRequest() = runTest {
    val repository = FakeWeatherRepository(listOf(day("2026-11-27"), day("2026-11-28")))
    val from = LocalDate.parse("2026-11-28")

    val forecast = repository.forecast(1.0, 2.0, from, from)

    assertEquals(listOf(from), forecast.days.map { it.date })
    assertEquals(listOf(from to from), repository.requests)
  }

  @Test
  fun failsWhenAFailureIsSet() = runTest {
    val repository = FakeWeatherRepository(failure = WeatherUnavailableException("offline"))
    val day = LocalDate.parse("2026-11-28")

    assertThrows(IOException::class.java) {
      kotlinx.coroutines.runBlocking { repository.forecast(1.0, 2.0, day, day) }
    }
  }
}
