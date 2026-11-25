// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.weather

import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FakeWeatherRepositoryTest {
  private fun day(date: String) =
      DailyForecast(LocalDate.parse(date), WeatherCondition.SUNNY, 16, 10, 5)

  @Test
  fun answersWithTheDaysInsideTheRequestedRangeAndRemembersTheRequest() = runTest {
    val repository =
        FakeWeatherRepository(listOf(day("2026-11-27"), day("2026-11-28"), day("2026-11-29")))
    val from = LocalDate.parse("2026-11-28")

    val forecast = repository.forecast(1.0, 2.0, from, from)

    assertEquals(listOf(from), forecast.days.map { it.date })
    assertEquals(listOf(from to from), repository.requests)
  }

  @Test
  fun failsWhenAFailureIsSet() = runTest {
    val repository = FakeWeatherRepository(failure = WeatherUnavailableException("offline"))
    val day = LocalDate.parse("2026-11-28")

    expectFailure<IOException> { repository.forecast(1.0, 2.0, day, day) }
  }

  @Test
  fun leavesOutDaysBeforeTodayAndBeyondTheForecastWindowLikeTheRealOne() = runTest {
    val repository =
        FakeWeatherRepository(
            listOf(day("2026-11-22"), day("2026-11-23"), day("2026-12-07"), day("2026-12-08"))
        )

    val forecast =
        repository.forecast(1.0, 2.0, LocalDate.parse("2026-11-01"), LocalDate.parse("2026-12-31"))

    // Its "today" is the day it was fetched, 23 November, so the window ends on 7 December.
    assertEquals(
        listOf(LocalDate.parse("2026-11-23"), LocalDate.parse("2026-12-07")),
        forecast.days.map { it.date },
    )
    assertEquals(LocalDate.parse("2026-11-23"), forecast.destinationToday)
  }

  @Test
  fun refusesCoordinatesThatAreNotOnEarthAndDoesNotRecordThem() = runTest {
    val repository = FakeWeatherRepository(listOf(day("2026-11-28")))
    val day = LocalDate.parse("2026-11-28")

    listOf(95.0 to 0.0, -91.0 to 0.0, 0.0 to 181.0, 0.0 to -181.0, Double.NaN to 0.0).forEach {
        (latitude, longitude) ->
      expectFailure<IllegalArgumentException> { repository.forecast(latitude, longitude, day, day) }
    }
    assertEquals(emptyList<Pair<LocalDate, LocalDate>>(), repository.requests)
  }
}
