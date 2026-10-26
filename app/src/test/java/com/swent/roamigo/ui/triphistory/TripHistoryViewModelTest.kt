package com.swent.roamigo.ui.triphistory

import com.google.firebase.Timestamp
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.trip.FakeTripRepository
import com.swent.roamigo.model.trip.TravelMode
import com.swent.roamigo.model.trip.Trip
import com.swent.roamigo.model.trip.TripStatus
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    assertEquals(listOf(planned, active), result)
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

    assertEquals(listOf(newest, middle, oldest), result)
  }

  @Test
  fun usesLocalDateRatherThanUtcDate() {
    val trip = trip("boundary", "2026-10-25T23:30:00Z")

    assertEquals(
        listOf(trip),
        selectCompletedTrips(listOf(trip), today, ZoneOffset.UTC),
    )
    assertTrue(
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
    assertEquals(false, state.isLoading)
    assertNull(state.errorMessage)
    assertEquals(1, state.trips.size)

    val item = state.trips.single()
    assertEquals("Lisbon", item.name)
    assertEquals(listOf("Lisbon"), item.destinations)
    assertEquals("24 Oct 2026 – 25 Oct 2026", item.dateRange)
    assertEquals(2, item.travelerCount)
  }

  @Test
  fun emptyRepositoryProducesEmptyState() = runTest {
    val viewModel = TripHistoryViewModel(FakeTripRepository(), clock)

    advanceUntilIdle()

    assertEquals(false, viewModel.uiState.value.isLoading)
    assertNull(viewModel.uiState.value.errorMessage)
    assertTrue(viewModel.uiState.value.trips.isEmpty())
  }

  @Test
  fun repositoryFailureProducesErrorState() = runTest {
    val repository = FakeTripRepository(failure = IllegalStateException("Test error"))
    val viewModel = TripHistoryViewModel(repository, clock)

    advanceUntilIdle()

    assertEquals(false, viewModel.uiState.value.isLoading)
    assertEquals(
        "Unable to load trip history.",
        viewModel.uiState.value.errorMessage,
    )
    assertTrue(viewModel.uiState.value.trips.isEmpty())
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
