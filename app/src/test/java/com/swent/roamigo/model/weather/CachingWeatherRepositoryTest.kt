// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.weather

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CachingWeatherRepositoryTest {
  private val saturday = LocalDate.of(2026, 11, 28)
  private val sunday = LocalDate.of(2026, 11, 29)
  private val fresh = Instant.parse("2026-11-23T10:00:00Z")
  private val saved = Instant.parse("2026-11-22T08:00:00Z")
  private val clock = Clock.fixed(fresh, ZoneOffset.UTC)

  // Runs a write at once, so a test can look at the cache right after the forecast.
  private val writes = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

  private fun day(date: LocalDate) = DailyForecast(date, WeatherCondition.SUNNY, 16, 10, 5)

  private class MemoryCache : WeatherCache {
    val entries = mutableMapOf<Pair<Double, Double>, Forecast>()
    var getFailure: Exception? = null
    var putFailure: Exception? = null

    override suspend fun get(
        latitude: Double,
        longitude: Double,
        from: LocalDate,
        to: LocalDate,
    ): Forecast? {
      getFailure?.let { throw it }
      val saved = entries[latitude to longitude] ?: return null
      return saved.copy(days = saved.days.filter { it.date in from..to }).takeIf {
        it.days.isNotEmpty()
      }
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
    val repository =
        CachingWeatherRepository(online(day(saturday), day(sunday)), cache, clock, writes)

    val forecast = repository.trip()

    assertEquals(listOf(saturday, sunday), forecast.days.map { it.date })
    assertFalse(forecast.fromCache)
    assertSame(forecast, cache.entries[38.7 to -9.1])
  }

  @Test
  fun doesNotSaveAnEmptyForecast() = runTest {
    val cache = MemoryCache()

    CachingWeatherRepository(online(), cache, clock, writes).trip()

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
    val repository =
        CachingWeatherRepository(online(failure = offline), MemoryCache(), clock, writes)

    val error = expectFailure<WeatherUnavailableException> { repository.trip() }

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

    expectFailure<WeatherUnavailableException> { repository.trip() }
  }

  @Test
  fun aBrokenCacheNeitherHidesAFreshForecastNorTheRealError() = runTest {
    val cache = MemoryCache().apply { putFailure = IllegalStateException("disk full") }
    assertEquals(
        2,
        CachingWeatherRepository(online(day(saturday), day(sunday)), cache, clock, writes)
            .trip()
            .days
            .size,
    )

    val offline = WeatherUnavailableException("offline")
    val unreadable = MemoryCache().apply { getFailure = IllegalStateException("corrupt") }
    val error =
        expectFailure<WeatherUnavailableException> {
          CachingWeatherRepository(online(failure = offline), unreadable, clock, writes).trip()
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

    expectFailure<IllegalArgumentException> { repository.trip() }
  }

  @Test
  fun neverSwallowsACancellationFromTheCache() = runTest {
    val offline = online(failure = WeatherUnavailableException("offline"))
    val readCancelled = MemoryCache().apply { getFailure = CancellationException("stopped") }
    expectFailure<CancellationException> {
      CachingWeatherRepository(offline, readCancelled, clock, writes).trip()
    }
  }

  @Test
  fun aWriteThatIsCancelledOrFailsNeverHidesTheFreshForecast() = runTest {
    listOf<Exception>(CancellationException("stopped"), IllegalStateException("disk full"))
        .forEach { failure ->
          val cache = MemoryCache().apply { putFailure = failure }

          val forecast =
              CachingWeatherRepository(online(day(saturday)), cache, clock, writes).trip()

          assertEquals(listOf(saturday), forecast.days.map { it.date })
        }
  }

  @Test
  fun fallsBackToTheSavedForecastWhateverIOErrorTheServiceReports() = runTest {
    val cache = MemoryCache()
    cache.entries[38.7 to -9.1] = Forecast(listOf(day(saturday)), saved)
    val repository =
        CachingWeatherRepository(
            online(failure = java.net.SocketTimeoutException("slow")),
            cache,
            clock,
            writes,
        )

    assertTrue(repository.trip().fromCache)
  }

  @Test
  fun anIOErrorWithNothingSavedBecomesAnUnavailableForecast() = runTest {
    val repository =
        CachingWeatherRepository(
            online(failure = java.net.SocketTimeoutException("slow")),
            MemoryCache(),
            clock,
            writes,
        )

    val error = expectFailure<WeatherUnavailableException> { repository.trip() }

    assertTrue(error.cause is java.net.SocketTimeoutException)
  }

  private fun offline(cache: WeatherCache) =
      CachingWeatherRepository(
          online(failure = WeatherUnavailableException("offline")),
          cache,
          clock,
          writes,
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

  @Test
  fun doesNotAskTheCacheForDaysOutsideTheForecastWindow() = runTest {
    val cache = MemoryCache()
    var asked = false
    val spy =
        object : WeatherCache by cache {
          override suspend fun get(
              latitude: Double,
              longitude: Double,
              from: LocalDate,
              to: LocalDate,
          ): Forecast? {
            asked = true
            return cache.get(latitude, longitude, from, to)
          }
        }
    val farAway = LocalDate.of(2027, 3, 1)

    expectFailure<WeatherUnavailableException> {
      offline(spy).forecast(38.7, -9.1, farAway, farAway.plusDays(2))
    }

    assertEquals(false, asked)
  }

  @Test
  fun treatsACacheThatAnswersWithNoDaysAsHavingNothing() = runTest {
    val empty =
        object : WeatherCache {
          override suspend fun get(
              latitude: Double,
              longitude: Double,
              from: LocalDate,
              to: LocalDate,
          ) = Forecast(emptyList(), saved)

          override suspend fun put(latitude: Double, longitude: Double, forecast: Forecast) = Unit
        }

    expectFailure<WeatherUnavailableException> { offline(empty).trip() }
  }

  @Test
  fun canBeCreatedWithItsDefaultClockAndWriteScope() {
    CachingWeatherRepository(online(), MemoryCache())
  }
}
