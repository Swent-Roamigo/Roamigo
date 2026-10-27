package com.swent.roamigo.ui.trip.history

import androidx.lifecycle.ViewModel
import com.google.firebase.Timestamp
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.trip.FakeTripRepository
import com.swent.roamigo.model.trip.TravelMode
import com.swent.roamigo.model.trip.Trip
import com.swent.roamigo.model.trip.TripRepository
import com.swent.roamigo.model.trip.TripStatus
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
import org.junit.Assert
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TripHistoryViewModelTest {

  private val dispatcher = StandardTestDispatcher()
  private val today = LocalDate.parse("2026-10-26")
  private val clock = Clock.fixed(Instant.parse("2026-10-26T12:00:00Z"), ZoneOffset.UTC)

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun selectsPastTripsRegardlessOfStoredStatus() {
    val planned = trip("planned", "2026-10-25T12:00:00Z")
    val active = trip("active", "2026-10-24T12:00:00Z", TripStatus.ACTIVE)
    val endingToday = trip("today", "2026-10-26T08:00:00Z")
    val future = trip("future", "2026-11-01T12:00:00Z", TripStatus.COMPLETED)

    val result =
        selectCompletedTrips(
            listOf(future, active, endingToday, planned),
            today,
            ZoneOffset.UTC,
        )

    Assert.assertEquals(listOf(planned, active), result)
  }

  @Test
  fun ordersTripsByMostRecentEndDate() {
    val oldest = trip("oldest", "2026-08-10T12:00:00Z")
    val newest = trip("newest", "2026-10-20T12:00:00Z")
    val middle = trip("middle", "2026-09-15T12:00:00Z")

    val result =
        selectCompletedTrips(
            listOf(oldest, newest, middle),
            today,
            ZoneOffset.UTC,
        )

    Assert.assertEquals(listOf(newest, middle, oldest), result)
  }

  @Test
  fun usesLocalDateRatherThanUtcDate() {
    val trip = trip("boundary", "2026-10-25T23:30:00Z")

    Assert.assertEquals(
        listOf(trip),
        selectCompletedTrips(listOf(trip), today, ZoneOffset.UTC),
    )
    Assert.assertTrue(
        selectCompletedTrips(
                listOf(trip),
                today,
                ZoneId.of("Asia/Tokyo"),
            )
            .isEmpty()
    )
  }

  @Test
  fun viewModelLoadsAndFormatsRepositoryTrips() = runTest {
    val completed = trip("Lisbon", "2026-10-25T12:00:00Z")
    val viewModel =
        TripHistoryViewModel(
            FakeTripRepository(trips = listOf(completed)),
            clock,
        )

    advanceUntilIdle()

    val state = viewModel.uiState.value
    Assert.assertEquals(false, state.isLoading)
    Assert.assertNull(state.errorMessage)
    Assert.assertEquals(1, state.trips.size)

    val item = state.trips.single()
    Assert.assertEquals("Lisbon", item.name)
    Assert.assertEquals(listOf("Lisbon"), item.destinations)
    Assert.assertEquals("24 Oct 2026 – 25 Oct 2026", item.dateRange)
    Assert.assertEquals(2, item.travelerCount)
  }

  @Test
  fun emptyRepositoryProducesEmptyState() = runTest {
    val viewModel = TripHistoryViewModel(FakeTripRepository(), clock)

    advanceUntilIdle()

    Assert.assertEquals(false, viewModel.uiState.value.isLoading)
    Assert.assertNull(viewModel.uiState.value.errorMessage)
    Assert.assertTrue(viewModel.uiState.value.trips.isEmpty())
  }

  @Test
  fun repositoryFailureProducesErrorState() = runTest {
    val repository = FakeTripRepository(failure = IllegalStateException("Test error"))
    val viewModel = TripHistoryViewModel(repository, clock)

    advanceUntilIdle()

    Assert.assertEquals(false, viewModel.uiState.value.isLoading)
    Assert.assertEquals(
        "Unable to load trip history.",
        viewModel.uiState.value.errorMessage,
    )
    Assert.assertTrue(viewModel.uiState.value.trips.isEmpty())
  }

  @Test
  fun newRefreshCancelsPreviousRequest() = runTest {
    val firstRequestGate = CompletableDeferred<Unit>()
    var requestCount = 0

    val repository =
        object : TripRepository by FakeTripRepository() {
          override suspend fun getTrips(): List<Trip> {
            requestCount++

            return if (requestCount == 1) {
              firstRequestGate.await()
              listOf(trip("old", "2026-10-20T12:00:00Z"))
            } else {
              listOf(trip("new", "2026-10-25T12:00:00Z"))
            }
          }
        }

    val viewModel = TripHistoryViewModel(repository, clock)

    // Start the first request, which remains suspended.
    runCurrent()
    Assert.assertEquals(1, requestCount)

    // A second refresh must cancel the previous one.
    viewModel.refresh()
    advanceUntilIdle()

    Assert.assertEquals(2, requestCount)
    Assert.assertEquals(
        listOf("new"),
        viewModel.uiState.value.trips.map { it.name },
    )
    Assert.assertEquals(false, viewModel.uiState.value.isLoading)
    Assert.assertNull(viewModel.uiState.value.errorMessage)
  }

  @Test
  fun ordersTripsWithSameEndSecondByNanoseconds() {
    val earlier =
        trip(
            "earlier",
            "2026-10-20T12:00:00.100Z",
        )
    val later =
        trip(
            "later",
            "2026-10-20T12:00:00.900Z",
        )

    val result =
        selectCompletedTrips(
            listOf(earlier, later),
            today,
            ZoneOffset.UTC,
        )

    Assert.assertEquals(listOf(later, earlier), result)
  }

  @Test
  fun factoryCreatesViewModelAndLoadsTrips() = runTest {
    val completedTrip =
        trip(
            "factory-trip",
            "2026-10-25T12:00:00Z",
        )

    val factory =
        TripHistoryViewModel.factory(
            FakeTripRepository(trips = listOf(completedTrip)),
            clock,
        )

    val viewModel = factory.create(TripHistoryViewModel::class.java)
    advanceUntilIdle()

    Assert.assertEquals(
        listOf("factory-trip"),
        viewModel.uiState.value.trips.map { it.name },
    )
  }

  @Test(expected = IllegalArgumentException::class)
  fun factoryRejectsUnexpectedViewModelClass() {
    class OtherViewModel : ViewModel()

    TripHistoryViewModel.factory(
            FakeTripRepository(),
            clock,
        )
        .create(OtherViewModel::class.java)
  }

  @Test
  fun retryAfterFailureLoadsTrips() = runTest {
    var attempts = 0

    val repository =
        object : TripRepository by FakeTripRepository() {
          override suspend fun getTrips(): List<Trip> {
            attempts++
            if (attempts == 1) {
              throw IllegalStateException("Temporary failure")
            }
            return listOf(trip("recovered", "2026-10-25T12:00:00Z"))
          }
        }

    val viewModel = TripHistoryViewModel(repository, clock)
    advanceUntilIdle()

    Assert.assertEquals(
        "Unable to load trip history.",
        viewModel.uiState.value.errorMessage,
    )

    viewModel.refresh()
    advanceUntilIdle()

    Assert.assertNull(viewModel.uiState.value.errorMessage)
    Assert.assertEquals(
        listOf("recovered"),
        viewModel.uiState.value.trips.map { it.name },
    )
  }

  private fun trip(
      name: String,
      end: String,
      status: TripStatus = TripStatus.PLANNED,
  ): Trip {
    val endInstant = Instant.parse(end)
    return Trip(
        uid = name,
        name = name,
        description = null,
        destinations = listOf(TripLocation(38.7, -9.1, "Lisbon")),
        startDate = timestamp(endInstant.minusSeconds(86_400)),
        endDate = timestamp(endInstant),
        flexibleDates = false,
        travelMode = TravelMode.GROUP,
        travelerCount = 2,
        ownerId = "alex",
        memberIds = listOf("alex", "sam"),
        status = status,
        createdAt = timestamp(endInstant.minusSeconds(172_800)),
    )
  }

  private fun timestamp(instant: Instant) = Timestamp(instant.epochSecond, instant.nano)
}
