// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import com.google.firebase.Timestamp
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.trip.FakeTripRepository
import com.swent.roamigo.model.trip.TravelMode
import com.swent.roamigo.model.trip.Trip
import com.swent.roamigo.model.trip.TripRepository
import com.swent.roamigo.model.trip.TripStatus
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
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TripMapViewModelTest {
  private val dispatcher = StandardTestDispatcher()
  private val store = ViewModelStore()

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    store.clear()
    dispatcher.scheduler.runCurrent()
    Dispatchers.resetMain()
  }

  @Test
  fun initialLoad_goesFromLoadingToLoaded() = runTest {
    val trip = trip(TRIP_ID, listOf(PARIS))
    val pendingTrips = CompletableDeferred<List<Trip>>()
    val viewModel = createViewModel(repository { pendingTrips.await() })

    runCurrent()
    assertEquals(TripMapUiState.Loading, viewModel.uiState.value)

    pendingTrips.complete(listOf(trip(OTHER_TRIP_ID), trip))
    advanceUntilIdle()

    assertEquals(
        TripMapUiState.Loaded(trip, TripMapViewport.Centered(PARIS.latitude, PARIS.longitude)),
        viewModel.uiState.value,
    )
  }

  @Test
  fun failedLoad_showsError_andRetryLoadsTheTrip() = runTest {
    val trip = trip(TRIP_ID, listOf(PARIS))
    val retryResult = CompletableDeferred<List<Trip>>()
    var requests = 0
    val viewModel =
        createViewModel(
            repository {
              requests++
              if (requests == 1) throw IllegalStateException("Load failed")
              retryResult.await()
            }
        )
    advanceUntilIdle()
    assertEquals(TripMapUiState.Error("Load failed"), viewModel.uiState.value)

    viewModel.retry()
    runCurrent()
    assertEquals(TripMapUiState.Loading, viewModel.uiState.value)

    retryResult.complete(listOf(trip))
    advanceUntilIdle()

    assertEquals(2, requests)
    assertTrue(viewModel.uiState.value is TripMapUiState.Loaded)
    assertEquals(trip, (viewModel.uiState.value as TripMapUiState.Loaded).trip)
  }

  @Test
  fun failedLoadWithoutMessage_showsDefaultError() = runTest {
    val viewModel = createViewModel(FakeTripRepository(failure = IllegalStateException()))

    advanceUntilIdle()

    assertEquals(
        TripMapUiState.Error(TripMapViewModel.LOAD_FAILED_MESSAGE),
        viewModel.uiState.value,
    )
  }

  @Test
  fun missingTrip_showsNotFoundError() = runTest {
    val viewModel = createViewModel(FakeTripRepository(trips = listOf(trip(OTHER_TRIP_ID))))

    advanceUntilIdle()

    assertEquals(
        TripMapUiState.Error(TripMapViewModel.TRIP_NOT_FOUND_MESSAGE),
        viewModel.uiState.value,
    )
  }

  @Test
  fun tripWithOneDestination_isCenteredOnIt() = runTest {
    val viewModel =
        createViewModel(FakeTripRepository(trips = listOf(trip(TRIP_ID, listOf(PARIS)))))

    advanceUntilIdle()

    assertEquals(
        TripMapViewport.Centered(PARIS.latitude, PARIS.longitude),
        (viewModel.uiState.value as TripMapUiState.Loaded).viewport,
    )
  }

  @Test
  fun tripWithSeveralDestinations_fitsAllOfThem() = runTest {
    val viewModel =
        createViewModel(FakeTripRepository(trips = listOf(trip(TRIP_ID, listOf(PARIS, LONDON)))))

    advanceUntilIdle()

    assertEquals(
        TripMapViewport.FitAll(
            listOf(
                MapCoordinate(PARIS.latitude, PARIS.longitude),
                MapCoordinate(LONDON.latitude, LONDON.longitude),
            )
        ),
        (viewModel.uiState.value as TripMapUiState.Loaded).viewport,
    )
  }

  @Test
  fun tripWithoutDestination_hasNoViewport() = runTest {
    val viewModel = createViewModel(FakeTripRepository(trips = listOf(trip(TRIP_ID))))

    advanceUntilIdle()

    assertEquals(
        TripMapViewport.None,
        (viewModel.uiState.value as TripMapUiState.Loaded).viewport,
    )
  }

  @Test
  fun retry_cancelsTheRunningLoadAndIgnoresItsResult() = runTest {
    val firstResult = CompletableDeferred<List<Trip>>()
    val latestResult = CompletableDeferred<List<Trip>>()
    var firstRequestCancelled = false
    var requests = 0
    val viewModel =
        createViewModel(
            repository {
              requests++
              if (requests == 1) {
                try {
                  firstResult.await()
                } finally {
                  firstRequestCancelled = true
                }
              } else {
                latestResult.await()
              }
            }
        )
    runCurrent()

    viewModel.retry()
    runCurrent()

    assertTrue(firstRequestCancelled)
    firstResult.complete(listOf(trip(TRIP_ID, listOf(LONDON))))
    runCurrent()
    assertEquals(TripMapUiState.Loading, viewModel.uiState.value)

    val latest = trip(TRIP_ID, listOf(PARIS))
    latestResult.complete(listOf(latest))
    advanceUntilIdle()

    assertEquals(latest, (viewModel.uiState.value as TripMapUiState.Loaded).trip)
  }

  @Test
  fun factory_createsTripMapViewModel_andRejectsOtherClasses() {
    val factory = TripMapViewModel.factory(TRIP_ID, FakeTripRepository())

    assertTrue(factory.create(TripMapViewModel::class.java) is TripMapViewModel)
    assertThrows(IllegalArgumentException::class.java) {
      factory.create(OtherViewModel::class.java)
    }
  }

  private class OtherViewModel : ViewModel()

  private fun createViewModel(repository: TripRepository): TripMapViewModel =
      TripMapViewModel(TRIP_ID, repository).also { store.put("map", it) }

  private fun repository(load: suspend () -> List<Trip>): TripRepository =
      object : TripRepository by FakeTripRepository() {
        override suspend fun getTrips(): List<Trip> = load()
      }

  private fun trip(id: String, destinations: List<TripLocation> = emptyList()): Trip =
      Trip(
          uid = id,
          name = "$id trip",
          description = null,
          destinations = destinations,
          startDate = Timestamp(0, 0),
          endDate = Timestamp(0, 0),
          flexibleDates = false,
          travelMode = TravelMode.SOLO,
          travelerCount = 1,
          ownerId = "user",
          memberIds = listOf("user"),
          status = TripStatus.PLANNED,
          createdAt = Timestamp(0, 0),
      )

  private companion object {
    const val TRIP_ID = "trip-1"
    const val OTHER_TRIP_ID = "trip-2"
    val PARIS = TripLocation(latitude = 48.8566, longitude = 2.3522, name = "Paris")
    val LONDON = TripLocation(latitude = 51.5074, longitude = -0.1278, name = "London")
  }
}
