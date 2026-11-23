// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.weather

import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CachingWeatherRepositoryTest {
  private val saturday = LocalDate.of(2026, 11, 28)
  private val sunday = LocalDate.of(2026, 11, 29)
  private val fresh = Instant.parse("2026-11-23T10:00:00Z")
  private val saved = Instant.parse("2026-11-22T08:00:00Z")

  private fun day(date: LocalDate) = DailyForecast(date, WeatherCondition.SUNNY, 16, 10, 5)

  private class MemoryCache : WeatherCache {
    val entries = mutableMapOf<Pair<Double, Double>, Forecast>()
    var getFailure: Exception? = null
    var putFailure: Exception? = null

    override suspend fun get(latitude: Double, longitude: Double): Forecast? {
      getFailure?.let { throw it }
      return entries[latitude to longitude]
    }

    override suspend fun put(latitude: Double, longitude: Double, forecast: Forecast) {
      putFailure?.let { throw it }
      entries[latitude to longitude] = forecast
    }
  }

  private fun online(vararg days: DailyForecast, failure: Exception? = null) =
      FakeWeatherRepository(days.toList(), failure, fresh)

  private suspend fun WeatherRepository.trip() = forecast(38.7, -9.1, saturday, sunday)

  @Test
  fun returnsTheFreshForecastAndSavesIt() = runTest {
    val cache = MemoryCache()
    val repository = CachingWeatherRepository(online(day(saturday), day(sunday)), cache)

    val forecast = repository.trip()

    assertEquals(listOf(saturday, sunday), forecast.days.map { it.date })
    assertFalse(forecast.fromCache)
    assertSame(forecast, cache.entries[38.7 to -9.1])
  }

  @Test
  fun doesNotSaveAnEmptyForecast() = runTest {
    val cache = MemoryCache()

    CachingWeatherRepository(online(), cache).trip()

    assertTrue(cache.entries.isEmpty())
  }

  @Test
  fun givesTheSavedForecastWhenTheServiceIsUnavailable() = runTest {
    val cache = MemoryCache()
    cache.entries[38.7 to -9.1] =
        Forecast(listOf(day(saturday), day(sunday), day(sunday.plusDays(1))), saved)
    val repository =
        CachingWeatherRepository(online(failure = WeatherUnavailableException("offline")), cache)

    val forecast = repository.trip()

    assertEquals(listOf(saturday, sunday), forecast.days.map { it.date })
    assertEquals(saved, forecast.fetchedAt)
    assertTrue(forecast.fromCache)
  }

  @Test
  fun failsWhenNothingIsSaved() = runTest {
    val offline = WeatherUnavailableException("offline")
    val repository = CachingWeatherRepository(online(failure = offline), MemoryCache())

    val error =
        assertThrows(WeatherUnavailableException::class.java) {
          kotlinx.coroutines.runBlocking { repository.trip() }
        }

    assertSame(offline, error)
  }

  @Test
  fun failsWhenTheSavedForecastCoversNoneOfTheDays() = runTest {
    val cache = MemoryCache()
    cache.entries[38.7 to -9.1] = Forecast(listOf(day(saturday.minusDays(3))), saved)
    val repository =
        CachingWeatherRepository(online(failure = WeatherUnavailableException("offline")), cache)

    assertThrows(WeatherUnavailableException::class.java) {
      kotlinx.coroutines.runBlocking { repository.trip() }
    }
  }

  @Test
  fun aBrokenCacheNeitherHidesAFreshForecastNorTheRealError() = runTest {
    val cache = MemoryCache().apply { putFailure = IllegalStateException("disk full") }
    assertEquals(
        2,
        CachingWeatherRepository(online(day(saturday), day(sunday)), cache).trip().days.size,
    )

    val offline = WeatherUnavailableException("offline")
    val unreadable = MemoryCache().apply { getFailure = IllegalStateException("corrupt") }
    val error =
        assertThrows(WeatherUnavailableException::class.java) {
          kotlinx.coroutines.runBlocking {
            CachingWeatherRepository(online(failure = offline), unreadable).trip()
          }
        }
    assertSame(offline, error)
  }

  @Test
  fun passesOnAMistakeInTheArguments() = runTest {
    val repository =
        CachingWeatherRepository(online(failure = IllegalArgumentException("bad")), MemoryCache())

    assertThrows(IllegalArgumentException::class.java) {
      kotlinx.coroutines.runBlocking { repository.trip() }
    }
  }
}
