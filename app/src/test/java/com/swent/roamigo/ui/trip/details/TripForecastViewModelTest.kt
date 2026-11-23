// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.trip.details

import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.weather.DailyForecast
import com.swent.roamigo.model.weather.FakeWeatherRepository
import com.swent.roamigo.model.weather.WeatherCondition
import com.swent.roamigo.model.weather.WeatherUnavailableException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TripForecastViewModelTest {
  private val dispatcher = StandardTestDispatcher()
  private val clock = Clock.fixed(Instant.parse("2026-11-23T10:00:00Z"), ZoneOffset.UTC)
  private val lisbon = TripLocation(38.72, -9.14, "Lisbon", "PT")
  private val saturday = LocalDate.of(2026, 11, 28)
  private val sunday = LocalDate.of(2026, 11, 29)

  @Before fun setUp() = Dispatchers.setMain(dispatcher)

  @After fun tearDown() = Dispatchers.resetMain()

  private fun day(date: LocalDate) = DailyForecast(date, WeatherCondition.SUNNY, 16, 10, 5)

  private fun viewModel(
      repository: FakeWeatherRepository,
      start: LocalDate = saturday,
      end: LocalDate = sunday,
  ) = TripForecastViewModel(lisbon, start, end, repository, clock)

  @Test
  fun loadsTheForecastOfTheTripDays() = runTest {
    val repository = FakeWeatherRepository(listOf(day(saturday), day(sunday)))
    val model = viewModel(repository)

    assertEquals(ForecastUiState.Loading, model.state.value)
    advanceUntilIdle()

    val ready = model.state.value as ForecastUiState.Ready
    assertEquals(listOf(saturday, sunday), ready.days.map { it.date })
    assertEquals(false, ready.fromCache)
    assertEquals(listOf(saturday to sunday), repository.requests)
  }

  @Test
  fun saysWhenTheForecastWillComeForATripTooFarAhead() = runTest {
    val repository = FakeWeatherRepository(listOf(day(saturday)))
    val model = viewModel(repository, LocalDate.of(2026, 12, 20), LocalDate.of(2026, 12, 27))

    advanceUntilIdle()

    assertEquals(ForecastUiState.TooFar(LocalDate.of(2026, 12, 6)), model.state.value)
    assertTrue(repository.requests.isEmpty())
  }

  @Test
  fun showsNothingForATripThatIsOver() = runTest {
    val repository = FakeWeatherRepository(listOf(day(saturday)))
    val model = viewModel(repository, LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 20))

    advanceUntilIdle()

    assertEquals(ForecastUiState.Hidden, model.state.value)
    assertTrue(repository.requests.isEmpty())
  }

  @Test
  fun showsNothingWhenTheProviderHasNoDayForTheTrip() = runTest {
    val model = viewModel(FakeWeatherRepository(emptyList()))

    advanceUntilIdle()

    assertEquals(ForecastUiState.Hidden, model.state.value)
  }

  @Test
  fun failsThenRecoversOnRetry() = runTest {
    val repository =
        FakeWeatherRepository(listOf(day(saturday)), WeatherUnavailableException("offline"))
    val model = viewModel(repository)
    advanceUntilIdle()
    assertEquals(ForecastUiState.Failed, model.state.value)

    repository.failure = null
    model.retry()
    assertEquals(ForecastUiState.Loading, model.state.value)
    advanceUntilIdle()

    assertTrue(model.state.value is ForecastUiState.Ready)
  }

  @Test
  fun retryDoesNothingWhileTheForecastIsFine() = runTest {
    val repository = FakeWeatherRepository(listOf(day(saturday)))
    val model = viewModel(repository)
    advanceUntilIdle()

    model.retry()
    advanceUntilIdle()

    assertEquals(1, repository.requests.size)
  }

  @Test
  fun anUnexpectedFailureIsAFailureToo() = runTest {
    val model = viewModel(FakeWeatherRepository(failure = IllegalStateException("boom")))

    advanceUntilIdle()

    assertEquals(ForecastUiState.Failed, model.state.value)
  }

  @Test
  fun aForecastFromTheCacheSaysSo() = runTest {
    val gate = CompletableDeferred<Unit>()
    val repository =
        object : com.swent.roamigo.model.weather.WeatherRepository {
          override suspend fun forecast(
              latitude: Double,
              longitude: Double,
              from: LocalDate,
              to: LocalDate,
          ): com.swent.roamigo.model.weather.Forecast {
            gate.await()
            return com.swent.roamigo.model.weather.Forecast(
                listOf(day(saturday)),
                Instant.parse("2026-11-22T08:00:00Z"),
                fromCache = true,
            )
          }
        }
    val model = TripForecastViewModel(lisbon, saturday, sunday, repository, clock)
    runCurrent()

    gate.complete(Unit)
    advanceUntilIdle()

    val ready = model.state.value as ForecastUiState.Ready
    assertEquals(true, ready.fromCache)
    assertEquals(Instant.parse("2026-11-22T08:00:00Z"), ready.fetchedAt)
  }
}
