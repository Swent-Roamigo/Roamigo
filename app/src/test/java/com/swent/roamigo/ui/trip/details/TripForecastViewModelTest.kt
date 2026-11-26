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

  /** A clock the test moves forward by hand. */
  private class MovableClock(var now: Instant) : Clock() {
    override fun getZone(): ZoneOffset = ZoneOffset.UTC

    override fun withZone(zone: java.time.ZoneId): Clock = this

    override fun instant(): Instant = now
  }

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

  @Test
  fun aTripStartingExactlyAtTheEndOfTheWindowHasAForecastAndOneDayLaterNone() = runTest {
    val today = LocalDate.of(2026, 11, 23)
    val inside = FakeWeatherRepository(listOf(day(today.plusDays(14))))
    val insideModel = viewModel(inside, today.plusDays(14), today.plusDays(16))
    val beyondModel = viewModel(FakeWeatherRepository(), today.plusDays(15), today.plusDays(17))

    advanceUntilIdle()

    assertTrue(insideModel.state.value is ForecastUiState.Ready)
    assertEquals(ForecastUiState.TooFar(today.plusDays(1)), beyondModel.state.value)
  }

  @Test
  fun aTripUnderwayAsksForItsWholeRangeAndKeepsTheDaysThatAreLeft() = runTest {
    val repository = FakeWeatherRepository(listOf(day(sunday)))

    val model = viewModel(repository, LocalDate.of(2026, 11, 21), sunday)
    advanceUntilIdle()

    assertEquals(listOf(LocalDate.of(2026, 11, 21) to sunday), repository.requests)
    assertEquals(listOf(sunday), (model.state.value as ForecastUiState.Ready).days.map { it.date })
  }

  @Test
  fun retryDoesNothingWhileTheForecastIsLoading() = runTest {
    val repository = FakeWeatherRepository(listOf(day(saturday)))
    val model = viewModel(repository)

    model.retry()
    advanceUntilIdle()

    assertEquals(1, repository.requests.size)
  }

  @Test
  fun refreshingTheNextDayLoadsAgainAndKeepsTheForecastShownMeanwhile() = runTest {
    val movable = MovableClock(Instant.parse("2026-11-23T10:00:00Z"))
    val repository = FakeWeatherRepository(listOf(day(saturday), day(sunday)))
    val model = TripForecastViewModel(lisbon, saturday, sunday, repository, movable)
    advanceUntilIdle()
    val before = model.state.value

    model.refresh()
    advanceUntilIdle()
    assertEquals(1, repository.requests.size)

    movable.now = Instant.parse("2026-11-24T08:00:00Z")
    model.refresh()
    assertEquals(before, model.state.value)
    advanceUntilIdle()

    assertEquals(2, repository.requests.size)
  }

  @Test
  fun refreshingAfterALongWhileLoadsAgainEvenOnTheSameDay() = runTest {
    val movable = MovableClock(Instant.parse("2026-11-23T08:00:00Z"))
    val repository = FakeWeatherRepository(listOf(day(saturday)))
    val model = TripForecastViewModel(lisbon, saturday, sunday, repository, movable)
    advanceUntilIdle()

    movable.now = Instant.parse("2026-11-23T09:30:00Z")
    model.refresh()
    advanceUntilIdle()

    assertEquals(2, repository.requests.size)
  }

  @Test
  fun aFailedRefreshKeepsTheForecastThatWasShown() = runTest {
    val movable = MovableClock(Instant.parse("2026-11-23T08:00:00Z"))
    val repository = FakeWeatherRepository(listOf(day(saturday)))
    val model = TripForecastViewModel(lisbon, saturday, sunday, repository, movable)
    advanceUntilIdle()
    val before = model.state.value

    repository.failure = WeatherUnavailableException("offline")
    movable.now = Instant.parse("2026-11-24T08:00:00Z")
    model.refresh()
    advanceUntilIdle()

    assertEquals(before, model.state.value)
  }

  @Test
  fun refreshingAfterAFailureTriesAgain() = runTest {
    val repository =
        FakeWeatherRepository(listOf(day(saturday)), WeatherUnavailableException("offline"))
    val model = viewModel(repository)
    advanceUntilIdle()

    repository.failure = null
    model.refresh()
    advanceUntilIdle()

    assertTrue(model.state.value is ForecastUiState.Ready)
  }

  @Test
  fun aServiceThatIsBusyIsNotAPlainFailureAndCanBeRetried() = runTest {
    val repository =
        FakeWeatherRepository(
            listOf(day(saturday)),
            WeatherUnavailableException("slow down", rateLimited = true),
        )
    val model = viewModel(repository)
    advanceUntilIdle()
    assertEquals(ForecastUiState.Busy, model.state.value)

    repository.failure = null
    model.retry()
    advanceUntilIdle()

    assertTrue(model.state.value is ForecastUiState.Ready)
  }

  /**
   * Answers from the cache while [offline] is set, as the real repository does without a network.
   */
  private class FlakyNetwork(var offline: Boolean, private val days: List<DailyForecast>) :
      com.swent.roamigo.model.weather.WeatherRepository {
    var requests = 0

    override suspend fun forecast(
        latitude: Double,
        longitude: Double,
        from: LocalDate,
        to: LocalDate,
    ): com.swent.roamigo.model.weather.Forecast {
      requests++
      return com.swent.roamigo.model.weather.Forecast(
          days,
          Instant.parse("2026-11-22T08:00:00Z"),
          fromCache = offline,
      )
    }
  }

  @Test
  fun aSavedForecastIsRefreshedAsSoonAsTheScreenComesBackOnline() = runTest {
    val network = FlakyNetwork(offline = true, days = listOf(day(saturday)))
    val model = TripForecastViewModel(lisbon, saturday, sunday, network, clock)
    advanceUntilIdle()
    assertEquals(true, (model.state.value as ForecastUiState.Ready).fromCache)

    // Back online a few minutes later: no need to wait for the hour to pass.
    network.offline = false
    model.refresh()
    advanceUntilIdle()

    assertEquals(false, (model.state.value as ForecastUiState.Ready).fromCache)
    assertEquals(2, network.requests)
  }

  @Test
  fun aFailedRefreshDoesNotPushTheNextTryAnotherHourAway() = runTest {
    val movable = MovableClock(Instant.parse("2026-11-23T08:00:00Z"))
    val repository = FakeWeatherRepository(listOf(day(saturday)))
    val model = TripForecastViewModel(lisbon, saturday, sunday, repository, movable)
    advanceUntilIdle()

    repository.failure = WeatherUnavailableException("offline")
    movable.now = Instant.parse("2026-11-23T09:30:00Z")
    model.refresh()
    advanceUntilIdle()
    repository.failure = null
    // Ten minutes after the failed try, but over an hour after the last forecast.
    movable.now = Instant.parse("2026-11-23T09:40:00Z")
    model.refresh()
    advanceUntilIdle()

    assertEquals(3, repository.requests.size)
  }

  @Test
  fun aFailedRefreshDoesNotBringBackATooFarMessageThatIsOutOfDate() = runTest {
    val movable = MovableClock(Instant.parse("2026-11-23T10:00:00Z"))
    val repository = FakeWeatherRepository(listOf(day(LocalDate.of(2026, 12, 8))))
    val model =
        TripForecastViewModel(
            lisbon,
            LocalDate.of(2026, 12, 8),
            LocalDate.of(2026, 12, 9),
            repository,
            movable,
        )
    advanceUntilIdle()
    assertEquals(ForecastUiState.TooFar(LocalDate.of(2026, 11, 24)), model.state.value)

    // On the 24th the forecast is due, but this time there is no connection and nothing saved.
    repository.failure = WeatherUnavailableException("offline")
    movable.now = Instant.parse("2026-11-24T10:00:00Z")
    model.refresh()
    advanceUntilIdle()

    assertEquals(ForecastUiState.Failed, model.state.value)
  }

  @Test
  fun comingBackSeveralTimesWhileARefreshIsRunningAsksOnlyOnce() = runTest {
    val network = FlakyNetwork(offline = true, days = listOf(day(saturday)))
    val model = TripForecastViewModel(lisbon, saturday, sunday, network, clock)
    advanceUntilIdle()

    model.refresh()
    model.refresh()
    advanceUntilIdle()

    assertEquals(2, network.requests)
  }

  @Test
  fun coordinatesThatAreNotOnEarthShowNoCardInsteadOfAnEndlessRetry() = runTest {
    val repository =
        FakeWeatherRepository(
            listOf(day(saturday)),
            IllegalArgumentException("Invalid coordinates"),
        )
    val model = viewModel(repository)

    advanceUntilIdle()

    assertEquals(ForecastUiState.Hidden, model.state.value)
  }

  @Test
  fun comingBackWhileTheFirstLoadIsStillRunningAsksNothingMore() = runTest {
    val repository = FakeWeatherRepository(listOf(day(saturday)))
    val model = viewModel(repository)

    model.refresh()
    advanceUntilIdle()

    assertEquals(1, repository.requests.size)
    assertTrue(model.state.value is ForecastUiState.Ready)
  }

  @Test
  fun aLoadThatIsCancelledIsNotShownAsAFailure() = runTest {
    val repository =
        object : com.swent.roamigo.model.weather.WeatherRepository {
          override suspend fun forecast(
              latitude: Double,
              longitude: Double,
              from: LocalDate,
              to: LocalDate,
          ): com.swent.roamigo.model.weather.Forecast =
              throw kotlinx.coroutines.CancellationException("The screen went away")
        }
    val model = TripForecastViewModel(lisbon, saturday, sunday, repository, clock)

    advanceUntilIdle()

    assertEquals(ForecastUiState.Loading, model.state.value)
  }
}
