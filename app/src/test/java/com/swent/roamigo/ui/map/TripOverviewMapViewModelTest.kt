// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.map

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
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TripOverviewMapViewModelTest {
  private val dispatcher = StandardTestDispatcher()
  private val store = ViewModelStore()
  private val paris = TripLocation(latitude = 48.8566, longitude = 2.3522, name = "Paris")

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
  fun initialLoadShowsLoadingUntilRepositoryReturns() = runTest {
    val pendingTrips = CompletableDeferred<List<Trip>>()
    var requests = 0
    val viewModel =
        createViewModel(
            repository {
              requests++
              pendingTrips.await()
            }
        )

    assertEquals(TripOverviewMapUiState(isLoading = true), viewModel.uiState.value)
    runCurrent()
    assertEquals(1, requests)
    assertEquals(TripOverviewMapUiState(isLoading = true), viewModel.uiState.value)

    pendingTrips.complete(emptyList())
    advanceUntilIdle()

    assertEquals(TripOverviewMapUiState(), viewModel.uiState.value)
  }

  @Test
  fun successfulLoadIncludesEveryDestinationFromEveryTrip() = runTest {
    val barcelona = TripLocation(latitude = 41.3851, longitude = 2.1734, name = "Barcelona")
    val london = TripLocation(latitude = 51.5074, longitude = -0.1278, name = "London")
    val viewModel =
        createViewModel(
            FakeTripRepository(
                trips =
                    listOf(
                        trip("first", listOf(paris, barcelona)),
                        trip("without-destinations", emptyList()),
                        trip("second", listOf(london)),
                    )
            )
        )

    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertFalse(state.isLoading)
    assertNull(state.errorMessage)
    assertEquals(listOf("Paris", "Barcelona", "London"), state.markers.map { it.locationName })
    assertEquals(listOf("first", "first", "second"), state.markers.map { it.tripId })
    assertEquals(
        listOf("first trip", "first trip", "second trip"),
        state.markers.map { it.tripTitle },
    )
    assertEquals("first_Paris_48.8566_2.3522", state.markers.first().id)
    assertEquals(paris.latitude, state.markers.first().latitude, 0.0)
    assertEquals(paris.longitude, state.markers.first().longitude, 0.0)
  }

  @Test
  fun failedInitialLoadStopsLoadingAndExposesTheError() = runTest {
    val viewModel =
        createViewModel(FakeTripRepository(failure = IllegalStateException("Load failed")))

    advanceUntilIdle()

    assertEquals(TripOverviewMapUiState(errorMessage = "Load failed"), viewModel.uiState.value)
  }

  @Test
  fun retryClearsTheErrorAndLoadsFreshMarkers() = runTest {
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
    assertEquals("Load failed", viewModel.uiState.value.errorMessage)

    viewModel.loadTrips()
    runCurrent()

    assertEquals(2, requests)
    assertEquals(TripOverviewMapUiState(isLoading = true), viewModel.uiState.value)

    retryResult.complete(listOf(trip("retry")))
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isLoading)
    assertNull(viewModel.uiState.value.errorMessage)
    assertEquals(listOf("retry"), viewModel.uiState.value.markers.map { it.tripId })
  }

  @Test
  fun refreshKeepsMarkersWhileLoadingAndReplacesThemOnSuccess() = runTest {
    val refreshedTrips = CompletableDeferred<List<Trip>>()
    var requests = 0
    val viewModel =
        createViewModel(
            repository {
              requests++
              if (requests == 1) listOf(trip("original")) else refreshedTrips.await()
            }
        )
    advanceUntilIdle()
    val originalState = viewModel.uiState.value

    viewModel.loadTrips()
    runCurrent()

    assertEquals(originalState.copy(isLoading = true), viewModel.uiState.value)

    refreshedTrips.complete(listOf(trip("replacement")))
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isLoading)
    assertNull(viewModel.uiState.value.errorMessage)
    assertEquals(listOf("replacement"), viewModel.uiState.value.markers.map { it.tripId })
  }

  @Test
  fun failedRefreshRemovesStaleMarkers() = runTest {
    var requests = 0
    val viewModel =
        createViewModel(
            repository {
              requests++
              if (requests == 1) listOf(trip("original"))
              else throw IllegalStateException("Refresh failed")
            }
        )
    advanceUntilIdle()
    assertTrue(viewModel.uiState.value.markers.isNotEmpty())

    viewModel.loadTrips()
    advanceUntilIdle()

    assertEquals(TripOverviewMapUiState(errorMessage = "Refresh failed"), viewModel.uiState.value)
  }

  @Test
  fun newLoadCancelsThePreviousRequestAndIgnoresItsResult() = runTest {
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

    viewModel.loadTrips()
    runCurrent()

    assertTrue(firstRequestCancelled)
    assertEquals(2, requests)
    firstResult.complete(listOf(trip("outdated")))
    runCurrent()
    assertEquals(TripOverviewMapUiState(isLoading = true), viewModel.uiState.value)

    latestResult.complete(listOf(trip("latest")))
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isLoading)
    assertNull(viewModel.uiState.value.errorMessage)
    assertEquals(listOf("latest"), viewModel.uiState.value.markers.map { it.tripId })
  }

  @Test
  fun clearingViewModelCancelsTheRequestWithoutShowingAnError() = runTest {
    val requestCancelled = CompletableDeferred<Unit>()
    val viewModel =
        createViewModel(
            repository {
              try {
                awaitCancellation()
              } finally {
                requestCancelled.complete(Unit)
              }
            }
        )
    runCurrent()

    store.clear()
    runCurrent()

    assertTrue(requestCancelled.isCompleted)
    assertEquals(TripOverviewMapUiState(isLoading = true), viewModel.uiState.value)
  }

  private fun createViewModel(repository: TripRepository): TripOverviewMapViewModel =
      TripOverviewMapViewModel(repository).also { store.put("map", it) }

  private fun repository(load: suspend () -> List<Trip>): TripRepository =
      object : TripRepository by FakeTripRepository() {
        override suspend fun getTrips(): List<Trip> = load()
      }

  private fun trip(id: String, destinations: List<TripLocation> = listOf(paris)): Trip =
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
}
