// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.weather

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
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
  private val clock = Clock.fixed(fresh, ZoneOffset.UTC)

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
    val repository = CachingWeatherRepository(online(day(saturday), day(sunday)), cache, clock)

    val forecast = repository.trip()

    assertEquals(listOf(saturday, sunday), forecast.days.map { it.date })
    assertFalse(forecast.fromCache)
    assertSame(forecast, cache.entries[38.7 to -9.1])
  }

  @Test
  fun doesNotSaveAnEmptyForecast() = runTest {
    val cache = MemoryCache()

    CachingWeatherRepository(online(), cache, clock).trip()

    assertTrue(cache.entries.isEmpty())
  }

  @Test
  fun givesTheSavedForecastWhenTheServiceIsUnavailable() = runTest {
    val cache = MemoryCache()
    cache.entries[38.7 to -9.1] =
        Forecast(listOf(day(saturday), day(sunday), day(sunday.plusDays(1))), saved)
    val repository =
        CachingWeatherRepository(
            online(failure = WeatherUnavailableException("offline")),
            cache,
            clock,
        )

    val forecast = repository.trip()

    assertEquals(listOf(saturday, sunday), forecast.days.map { it.date })
    assertEquals(saved, forecast.fetchedAt)
    assertTrue(forecast.fromCache)
  }

  @Test
  fun failsWhenNothingIsSaved() = runTest {
    val offline = WeatherUnavailableException("offline")
    val repository = CachingWeatherRepository(online(failure = offline), MemoryCache(), clock)

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
        CachingWeatherRepository(
            online(failure = WeatherUnavailableException("offline")),
            cache,
            clock,
        )

    assertThrows(WeatherUnavailableException::class.java) {
      kotlinx.coroutines.runBlocking { repository.trip() }
    }
  }

  @Test
  fun aBrokenCacheNeitherHidesAFreshForecastNorTheRealError() = runTest {
    val cache = MemoryCache().apply { putFailure = IllegalStateException("disk full") }
    assertEquals(
        2,
        CachingWeatherRepository(online(day(saturday), day(sunday)), cache, clock).trip().days.size,
    )

    val offline = WeatherUnavailableException("offline")
    val unreadable = MemoryCache().apply { getFailure = IllegalStateException("corrupt") }
    val error =
        assertThrows(WeatherUnavailableException::class.java) {
          kotlinx.coroutines.runBlocking {
            CachingWeatherRepository(online(failure = offline), unreadable, clock).trip()
          }
        }
    assertSame(offline, error)
  }

  @Test
  fun passesOnAMistakeInTheArguments() = runTest {
    val repository =
        CachingWeatherRepository(
            online(failure = IllegalArgumentException("bad")),
            MemoryCache(),
            clock,
        )

    assertThrows(IllegalArgumentException::class.java) {
      kotlinx.coroutines.runBlocking { repository.trip() }
    }
  }

  @Test
  fun neverSwallowsACancellationFromTheCache() = runTest {
    val offline = online(failure = WeatherUnavailableException("offline"))
    val readCancelled = MemoryCache().apply { getFailure = CancellationException("stopped") }
    assertThrows(CancellationException::class.java) {
      kotlinx.coroutines.runBlocking {
        CachingWeatherRepository(offline, readCancelled, clock).trip()
      }
    }

    val writeCancelled = MemoryCache().apply { putFailure = CancellationException("stopped") }
    assertThrows(CancellationException::class.java) {
      kotlinx.coroutines.runBlocking {
        CachingWeatherRepository(online(day(saturday)), writeCancelled, clock).trip()
      }
    }
  }

  private fun offline(cache: MemoryCache) =
      CachingWeatherRepository(
          online(failure = WeatherUnavailableException("offline")),
          cache,
          clock,
      )

  @Test
  fun leavesOutTheDaysThatArePastOrBeyondTheForecastWindowWhenOffline() = runTest {
    val cache = MemoryCache()
    val today = LocalDate.of(2026, 11, 23)
    val stale = listOf(-3L, -1L, 0L, 15L, 16L).map { day(today.plusDays(it)) }
    cache.entries[38.7 to -9.1] = Forecast(stale, saved)

    val forecast = offline(cache).forecast(38.7, -9.1, today.minusDays(5), today.plusDays(30))

    // One day of margin on each side, as the destination's date is not known here.
    assertEquals(
        listOf(-1L, 0L, 15L).map { today.plusDays(it) },
        forecast.days.map { it.date },
    )
  }

  @Test
  fun givesOnlyTheSavedPartOfTheRequestedDays() = runTest {
    val cache = MemoryCache()
    cache.entries[38.7 to -9.1] = Forecast(listOf(day(saturday), day(sunday)), saved)

    val forecast = offline(cache).forecast(38.7, -9.1, saturday, sunday.plusDays(3))

    assertEquals(listOf(saturday, sunday), forecast.days.map { it.date })
    assertTrue(forecast.fromCache)
  }
}
