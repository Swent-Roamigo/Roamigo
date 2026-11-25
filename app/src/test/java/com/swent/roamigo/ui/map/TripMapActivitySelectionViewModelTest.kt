// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.map

import androidx.lifecycle.ViewModelStore
import com.google.firebase.Timestamp
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.photo.FakePhotoRepository
import com.swent.roamigo.model.trip.Activity
import com.swent.roamigo.model.trip.ActivityType
import com.swent.roamigo.model.trip.FakeTripRepository
import com.swent.roamigo.model.trip.TravelMode
import com.swent.roamigo.model.trip.Trip
import com.swent.roamigo.model.trip.TripStatus
import com.swent.roamigo.model.trip.activity.ActivityRepository
import com.swent.roamigo.model.trip.activity.FakeActivityRepository
import com.swent.roamigo.model.users.FakeUserRepository
import com.swent.roamigo.model.users.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TripMapActivitySelectionViewModelTest {
  private val dispatcher = StandardTestDispatcher()
  private val store = ViewModelStore()
  private val activities = MutableStateFlow(listOf(activity("museum"), activity("cafe")))

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
  fun nothingIsSelectedOnceTheMapLoads() = runTest {
    val viewModel = createViewModel()
    advanceUntilIdle()

    assertNull(viewModel.loaded().selectedActivityId)
    assertNull(viewModel.loaded().selectedActivity)
  }

  @Test
  fun selectingAMarkerOpensItsActivityAndAnotherMarkerReplacesIt() = runTest {
    val viewModel = createViewModel()
    advanceUntilIdle()

    viewModel.selectActivity(MUSEUM_MARKER)
    assertEquals("museum activity", viewModel.loaded().selectedActivity?.title)

    viewModel.selectActivity(CAFE_MARKER)
    assertEquals(CAFE_MARKER, viewModel.loaded().selectedActivityId)
    assertEquals("cafe activity", viewModel.loaded().selectedActivity?.title)
  }

  @Test
  fun clearingTheSelectionClosesThePopup() = runTest {
    val viewModel = createViewModel()
    advanceUntilIdle()
    viewModel.selectActivity(MUSEUM_MARKER)

    viewModel.clearActivitySelection()

    assertNull(viewModel.loaded().selectedActivityId)
  }

  @Test
  fun unknownMarkersAreIgnoredAndKeepTheCurrentSelection() = runTest {
    val viewModel = createViewModel()
    advanceUntilIdle()

    viewModel.selectActivity("missing")
    assertNull(viewModel.loaded().selectedActivityId)

    viewModel.selectActivity(MUSEUM_MARKER)
    viewModel.selectActivity("missing")
    assertEquals(MUSEUM_MARKER, viewModel.loaded().selectedActivityId)
  }

  @Test
  fun selectionFollowsActivityUpdatesAndClosesWhenItsActivityIsRemoved() = runTest {
    val viewModel = createViewModel()
    advanceUntilIdle()
    viewModel.selectActivity(MUSEUM_MARKER)

    val renamed = activity("museum").copy(name = "Louvre")
    activities.value = listOf(renamed, activity("cafe"))
    advanceUntilIdle()
    assertEquals(MUSEUM_MARKER, viewModel.loaded().selectedActivityId)
    assertEquals("Louvre", viewModel.loaded().selectedActivity?.title)

    activities.value = listOf(activity("cafe"))
    advanceUntilIdle()
    assertNull(viewModel.loaded().selectedActivityId)
    assertNull(viewModel.loaded().selectedActivity)
  }

  @Test
  fun selectionIsIndependentOfThePhotoViewerAndClearedByATripReload() = runTest {
    val viewModel = createViewModel()
    advanceUntilIdle()
    viewModel.selectActivity(MUSEUM_MARKER)

    viewModel.closePhotoViewer()
    assertEquals(MUSEUM_MARKER, viewModel.loaded().selectedActivityId)

    viewModel.retryTrip()
    advanceUntilIdle()
    assertNull(viewModel.loaded().selectedActivityId)
  }

  @Test
  fun selectionBeforeTheTripLoadsHasNoEffect() = runTest {
    val viewModel = createViewModel()

    viewModel.selectActivity(MUSEUM_MARKER)
    viewModel.clearActivitySelection()
    advanceUntilIdle()

    assertNull(viewModel.loaded().selectedActivityId)
  }

  private fun TripMapViewModel.loaded() = uiState.value as TripMapUiState.Loaded

  private fun createViewModel(): TripMapViewModel =
      TripMapViewModel(
              tripId = TRIP_ID,
              tripRepository = FakeTripRepository(trips = listOf(trip())),
              activityRepository =
                  object : ActivityRepository by FakeActivityRepository() {
                    override fun observeActivities(tripId: String): Flow<List<Activity>> =
                        activities
                  },
              userRepository = FakeUserRepository(USER),
              photoRepository = FakePhotoRepository(),
          )
          .also { store.put("map", it) }

  private fun activity(id: String): Activity =
      Activity(
          uid = id,
          tripId = TRIP_ID,
          activityType = ActivityType.ACCEPTED,
          name = "$id activity",
          description = null,
          location = PARIS,
          startTime = Timestamp(0, 0),
          endTime = null,
          createdByUserId = USER.uid,
          creationTime = Timestamp(0, 0),
      )

  private fun trip(): Trip =
      Trip(
          uid = TRIP_ID,
          name = "Paris trip",
          description = null,
          destinations = listOf(PARIS),
          startDate = Timestamp(0, 0),
          endDate = Timestamp(0, 0),
          flexibleDates = false,
          travelMode = TravelMode.SOLO,
          travelerCount = 1,
          ownerId = USER.uid,
          memberIds = listOf(USER.uid),
          status = TripStatus.PLANNED,
          createdAt = Timestamp(0, 0),
      )

  private companion object {
    const val TRIP_ID = "trip-1"
    const val MUSEUM_MARKER = "${TRIP_ID}_activity_museum"
    const val CAFE_MARKER = "${TRIP_ID}_activity_cafe"
    val PARIS = TripLocation(latitude = 48.8566, longitude = 2.3522, name = "Paris")
    val USER = User(uid = "alice-id", displayName = "Alice", profilePictureUrl = null)
  }
}
