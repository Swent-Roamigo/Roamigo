// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.map

import androidx.lifecycle.ViewModelStore
import com.google.firebase.Timestamp
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.photo.FakePhotoRepository
import com.swent.roamigo.model.photo.PendingPhoto
import com.swent.roamigo.model.photo.PhotoRepository
import com.swent.roamigo.model.photo.TripPhoto
import com.swent.roamigo.model.photo.tripPhoto
import com.swent.roamigo.model.trip.Activity
import com.swent.roamigo.model.trip.ActivityType
import com.swent.roamigo.model.trip.FakeTripRepository
import com.swent.roamigo.model.trip.TravelMode
import com.swent.roamigo.model.trip.Trip
import com.swent.roamigo.model.trip.TripRepository
import com.swent.roamigo.model.trip.TripStatus
import com.swent.roamigo.model.trip.activity.ActivityRepository
import com.swent.roamigo.model.trip.activity.FakeActivityRepository
import com.swent.roamigo.model.users.FakeUserRepository
import com.swent.roamigo.model.users.User
import com.swent.roamigo.model.users.UserRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TripMapPhotoViewModelTest {
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
  fun waitsForTheTripAndDistinguishesPhotoLoadingFromAnEmptyGallery() = runTest {
    val tripResult = CompletableDeferred<List<Trip>>()
    val updates = MutableSharedFlow<List<TripPhoto>>(extraBufferCapacity = 1)
    val photos = ControlledPhotos { updates }
    val viewModel = createViewModel(photos, tripRepository { tripResult.await() })

    runCurrent()
    assertEquals(TripMapUiState.Loading, viewModel.uiState.value)
    viewModel.retryPhotos()
    runCurrent()
    assertTrue(photos.requestedTrips.isEmpty())

    tripResult.complete(listOf(trip()))
    runCurrent()
    val loading = viewModel.loaded()
    assertEquals(PhotosLoadState.LOADING, loading.photosLoadState)
    assertTrue(loading.photoMarkers.isEmpty())
    assertEquals(ActivitiesLoadState.LOADED, loading.activitiesLoadState)
    assertEquals(MemberDisplayInfo("Alice", "A", null), loading.members[USER.uid])
    assertEquals(listOf(TRIP_ID), photos.requestedTrips)
    assertEquals(1, updates.subscriptionCount.value)

    assertTrue(updates.tryEmit(emptyList()))
    runCurrent()
    assertEquals(PhotosLoadState.LOADED, viewModel.loaded().photosLoadState)
    assertTrue(viewModel.loaded().photoMarkers.isEmpty())
  }

  @Test
  fun failedTripDoesNotStartPhotoObservation() = runTest {
    val photos = ControlledPhotos { flowOf(emptyList()) }
    val trips = FakeTripRepository(failure = IllegalStateException("Trip unavailable"))
    val viewModel = createViewModel(photos, trips)

    runCurrent()
    assertEquals(
        TripMapUiState.Error(TripMapViewModel.LOAD_FAILED_MESSAGE),
        viewModel.uiState.value,
    )
    viewModel.retryPhotos()
    runCurrent()
    assertTrue(photos.requestedTrips.isEmpty())
  }

  @Test
  fun initialPhotoFailureLeavesTheTripAndActivitiesUsable() = runTest {
    val photos = ControlledPhotos { flow { error("Gallery unavailable") } }
    val viewModel = createViewModel(photos)

    runCurrent()

    val state = viewModel.loaded()
    assertEquals(PhotosLoadState.ERROR, state.photosLoadState)
    assertTrue(state.photoMarkers.isEmpty())
    assertEquals(ActivitiesLoadState.LOADED, state.activitiesLoadState)
    assertEquals(TRIP_ID, state.trip.uid)
    assertEquals(MemberDisplayInfo("Alice", "A", null), state.members[USER.uid])
  }

  @Test
  fun galleryUpdatesReplaceMarkersFilterInvalidPhotosAndKeepViewport() = runTest {
    val updates = MutableSharedFlow<List<TripPhoto>>(extraBufferCapacity = 1)
    val photos = ControlledPhotos { updates }
    val viewModel = createViewModel(photos)
    runCurrent()
    val viewport = viewModel.loaded().viewport
    val older = tripPhoto("older", takenAtMillis = 1, latitude = 10.0, longitude = 20.0)
    val localWrite = tripPhoto("local", takenAtMillis = 2, latitude = 10.0, longitude = 20.0)
    val elsewhere = tripPhoto("elsewhere", latitude = -30.0, longitude = 40.0)
    val invalid =
        listOf(
            tripPhoto("missing", latitude = null, longitude = 20.0),
            tripPhoto("nan", latitude = Double.NaN, longitude = 20.0),
            tripPhoto("out-of-range", latitude = 91.0, longitude = 20.0),
        )

    assertTrue(updates.tryEmit(listOf(localWrite, elsewhere, older) + invalid))
    runCurrent()
    val loaded = viewModel.loaded()
    val markers = loaded.photoMarkers.associateBy { it.coordinate }
    assertEquals(PhotosLoadState.LOADED, loaded.photosLoadState)
    assertEquals(setOf(MapCoordinate(10.0, 20.0), MapCoordinate(-30.0, 40.0)), markers.keys)
    val group = markers.getValue(MapCoordinate(10.0, 20.0))
    assertEquals(2, group.photoCount)
    assertEquals(listOf("local", "older"), group.photos.map { it.id })
    assertSame(localWrite, group.photos.first())
    assertEquals(viewport, loaded.viewport)

    assertTrue(updates.tryEmit(listOf(elsewhere)))
    runCurrent()
    assertEquals(listOf("elsewhere"), viewModel.loaded().photoMarkers.single().photos.map { it.id })
    assertEquals(viewport, viewModel.loaded().viewport)

    assertTrue(updates.tryEmit(emptyList()))
    runCurrent()
    assertEquals(PhotosLoadState.LOADED, viewModel.loaded().photosLoadState)
    assertTrue(viewModel.loaded().photoMarkers.isEmpty())
  }

  @Test
  fun failureKeepsTheMapAndRetryRecoversWithoutReloadingTheTripOrActivities() = runTest {
    val first = tripPhoto("first", latitude = 10.0, longitude = 20.0)
    val recovered = tripPhoto("recovered", latitude = 30.0, longitude = 40.0)
    val failure = CompletableDeferred<Unit>()
    val retryResult = CompletableDeferred<List<TripPhoto>>()
    val laterUpdates = MutableSharedFlow<List<TripPhoto>>(extraBufferCapacity = 1)
    var attempts = 0
    val photos = ControlledPhotos {
      if (++attempts == 1) {
        flow {
          emit(listOf(first))
          failure.await()
          error("Gallery unavailable")
        }
      } else {
        flow {
          emit(retryResult.await())
          emitAll(laterUpdates)
        }
      }
    }
    var tripRequests = 0
    var activityRequests = 0
    val activityDelegate = FakeActivityRepository(listOf(activity()))
    val activityRepository =
        object : ActivityRepository by activityDelegate {
          override fun observeActivities(tripId: String): Flow<List<Activity>> {
            activityRequests++
            return activityDelegate.observeActivities(tripId)
          }
        }
    val viewModel =
        createViewModel(
            photos,
            tripRepository {
              tripRequests++
              listOf(trip())
            },
            activityRepository,
        )
    runCurrent()
    val before = viewModel.loaded()
    assertEquals(PhotosLoadState.LOADED, before.photosLoadState)
    assertEquals(ActivitiesLoadState.LOADED, before.activitiesLoadState)
    assertEquals(1, before.activityMarkers.size)

    failure.complete(Unit)
    runCurrent()
    assertEquals(before.copy(photosLoadState = PhotosLoadState.ERROR), viewModel.loaded())

    viewModel.retryPhotos()
    runCurrent()
    assertEquals(before.copy(photosLoadState = PhotosLoadState.LOADING), viewModel.loaded())
    assertEquals(1, tripRequests)
    assertEquals(1, activityRequests)
    assertEquals(listOf(TRIP_ID, TRIP_ID), photos.requestedTrips)

    retryResult.complete(listOf(recovered))
    runCurrent()
    val after = viewModel.loaded()
    assertEquals(PhotosLoadState.LOADED, after.photosLoadState)
    assertEquals(listOf("recovered"), after.photoMarkers.single().photos.map { it.id })
    assertEquals(before.activityMarkers, after.activityMarkers)
    assertEquals(before.members, after.members)
    assertEquals(before.viewport, after.viewport)

    assertTrue(laterUpdates.tryEmit(emptyList()))
    runCurrent()
    assertTrue(viewModel.loaded().photoMarkers.isEmpty())
    assertEquals(PhotosLoadState.LOADED, viewModel.loaded().photosLoadState)
  }

  @Test
  fun repeatedRetriesAndTripReloadCancelOldObserversAndClearingStopsTheLastOne() = runTest {
    val updates = MutableSharedFlow<List<TripPhoto>>(extraBufferCapacity = 1)
    var subscriptions = 0
    var cancellations = 0
    val photos = ControlledPhotos {
      flow {
        subscriptions++
        try {
          emitAll(updates)
        } finally {
          cancellations++
        }
      }
    }
    val viewModel = createViewModel(photos)
    runCurrent()
    assertEquals(1, updates.subscriptionCount.value)
    assertTrue(updates.tryEmit(listOf(tripPhoto("first", latitude = 1.0, longitude = 2.0))))
    runCurrent()

    viewModel.retryPhotos()
    runCurrent()
    assertEquals(PhotosLoadState.LOADING, viewModel.loaded().photosLoadState)
    assertEquals("first", viewModel.loaded().photoMarkers.single().photos.single().id)
    viewModel.retryPhotos()
    viewModel.retryPhotos()
    runCurrent()
    assertEquals(3, subscriptions)
    assertEquals(2, cancellations)
    assertEquals(1, updates.subscriptionCount.value)

    assertTrue(updates.tryEmit(listOf(tripPhoto("next", latitude = 3.0, longitude = 4.0))))
    runCurrent()
    assertEquals("next", viewModel.loaded().photoMarkers.single().photos.single().id)

    viewModel.retryTrip()
    runCurrent()
    assertEquals(4, subscriptions)
    assertEquals(3, cancellations)
    assertEquals(PhotosLoadState.LOADING, viewModel.loaded().photosLoadState)
    assertTrue(viewModel.loaded().photoMarkers.isEmpty())
    assertEquals(1, updates.subscriptionCount.value)

    store.clear()
    runCurrent()
    assertEquals(4, cancellations)
    assertEquals(0, updates.subscriptionCount.value)
  }

  @Test
  fun photoRetryDuringTripReloadDoesNotObserveUntilTheTripLoadsAgain() = runTest {
    val tripResult = CompletableDeferred<List<Trip>>()
    var tripRequests = 0
    val updates = MutableSharedFlow<List<TripPhoto>>(extraBufferCapacity = 1)
    val photos = ControlledPhotos { updates }
    val viewModel =
        createViewModel(
            photos,
            tripRepository { if (++tripRequests == 1) listOf(trip()) else tripResult.await() },
        )
    runCurrent()
    assertEquals(listOf(TRIP_ID), photos.requestedTrips)

    viewModel.retryTrip()
    viewModel.retryPhotos()
    runCurrent()

    assertEquals(TripMapUiState.Loading, viewModel.uiState.value)
    assertEquals(listOf(TRIP_ID), photos.requestedTrips)
    assertEquals(0, updates.subscriptionCount.value)

    tripResult.complete(listOf(trip()))
    runCurrent()
    assertEquals(listOf(TRIP_ID, TRIP_ID), photos.requestedTrips)
    assertEquals(1, updates.subscriptionCount.value)
    assertEquals(PhotosLoadState.LOADING, viewModel.loaded().photosLoadState)
  }

  @Test
  fun aCancelledObserverCannotOverwriteTheResultOfARetry() = runTest {
    val cleanup = CompletableDeferred<Unit>()
    val updates = MutableSharedFlow<List<TripPhoto>>(extraBufferCapacity = 1)
    var attempts = 0
    val photos = ControlledPhotos {
      if (++attempts == 1) {
        flow {
          try {
            emit(listOf(tripPhoto("old", latitude = 1.0, longitude = 2.0)))
            awaitCancellation()
          } finally {
            withContext(NonCancellable) { cleanup.await() }
            error("Late failure from the cancelled observer")
          }
        }
      } else updates
    }
    val viewModel = createViewModel(photos)
    try {
      runCurrent()
      viewModel.retryPhotos()
      runCurrent()
      assertTrue(updates.tryEmit(listOf(tripPhoto("new", latitude = 3.0, longitude = 4.0))))
      runCurrent()
      val recovered = viewModel.loaded()
      assertEquals(PhotosLoadState.LOADED, recovered.photosLoadState)
      assertEquals("new", recovered.photoMarkers.single().photos.single().id)

      cleanup.complete(Unit)
      runCurrent()
      assertEquals(recovered, viewModel.loaded())
    } finally {
      cleanup.complete(Unit)
      runCurrent()
    }
  }

  @Test
  fun photoActivityAndMemberLoadingAndFailuresRemainIndependent() = runTest {
    val activityResult = CompletableDeferred<List<Activity>>()
    val userResult = CompletableDeferred<List<User>>()
    val photoResult = CompletableDeferred<List<TripPhoto>>()
    val photoFailure = CompletableDeferred<Unit>()
    var photoAttempts = 0
    val photos = ControlledPhotos {
      if (++photoAttempts == 1) {
        flow {
          emit(photoResult.await())
          photoFailure.await()
          error("Photo failure")
        }
      } else flowOf(emptyList())
    }
    var activityAttempts = 0
    val activities =
        object : ActivityRepository by FakeActivityRepository() {
          override fun observeActivities(tripId: String): Flow<List<Activity>> = flow {
            if (++activityAttempts == 1) emit(activityResult.await()) else emit(listOf(activity()))
          }
        }
    val users =
        object : UserRepository by FakeUserRepository(USER) {
          override suspend fun getUsers(userIds: Set<String>): List<User> = userResult.await()
        }
    val viewModel = createViewModel(photos, activityRepository = activities, userRepository = users)
    runCurrent()
    assertEquals(PhotosLoadState.LOADING, viewModel.loaded().photosLoadState)
    assertEquals(ActivitiesLoadState.LOADING, viewModel.loaded().activitiesLoadState)
    assertEquals(MemberDisplayInfo("A", "A", null), viewModel.loaded().members[USER.uid])

    photoResult.complete(listOf(tripPhoto("one", latitude = 1.0, longitude = 2.0)))
    runCurrent()
    assertEquals(PhotosLoadState.LOADED, viewModel.loaded().photosLoadState)
    assertEquals(ActivitiesLoadState.LOADING, viewModel.loaded().activitiesLoadState)

    activityResult.completeExceptionally(IllegalStateException("Activity failure"))
    userResult.complete(listOf(USER))
    runCurrent()
    assertEquals(ActivitiesLoadState.ERROR, viewModel.loaded().activitiesLoadState)
    assertEquals(PhotosLoadState.LOADED, viewModel.loaded().photosLoadState)
    assertEquals(MemberDisplayInfo("Alice", "A", null), viewModel.loaded().members[USER.uid])
    assertEquals("one", viewModel.loaded().photoMarkers.single().photos.single().id)

    photoFailure.complete(Unit)
    runCurrent()
    assertEquals(PhotosLoadState.ERROR, viewModel.loaded().photosLoadState)
    assertEquals(ActivitiesLoadState.ERROR, viewModel.loaded().activitiesLoadState)
    assertEquals("one", viewModel.loaded().photoMarkers.single().photos.single().id)

    viewModel.retryActivities()
    runCurrent()
    assertEquals(ActivitiesLoadState.LOADED, viewModel.loaded().activitiesLoadState)
    assertEquals(PhotosLoadState.ERROR, viewModel.loaded().photosLoadState)
    viewModel.retryPhotos()
    runCurrent()
    assertEquals(PhotosLoadState.LOADED, viewModel.loaded().photosLoadState)
    assertTrue(viewModel.loaded().photoMarkers.isEmpty())
    assertEquals(ActivitiesLoadState.LOADED, viewModel.loaded().activitiesLoadState)
  }

  @Test
  fun selectionFollowsTheGalleryAndClosesWhenItsGroupIsGone() = runTest {
    val updates = MutableSharedFlow<List<TripPhoto>>(extraBufferCapacity = 1)
    val viewModel = createViewModel(ControlledPhotos { updates })
    runCurrent()
    val here = MapCoordinate(10.0, 20.0)
    val elsewhere = MapCoordinate(-30.0, 40.0)

    // Nothing to open before the gallery has a group there.
    viewModel.selectPhotoGroup(here)
    assertEquals(null, viewModel.loaded().selectedPhotoCoordinate)

    val older = tripPhoto("older", takenAtMillis = 1, latitude = 10.0, longitude = 20.0)
    val other = tripPhoto("other", latitude = -30.0, longitude = 40.0)
    assertTrue(updates.tryEmit(listOf(older, other)))
    runCurrent()
    viewModel.selectPhotoGroup(here)
    assertEquals(listOf("older"), viewModel.loaded().selectedPhotoGroup?.photos?.map { it.id })

    val newer = tripPhoto("newer", takenAtMillis = 2, latitude = 10.0, longitude = 20.0)
    assertTrue(updates.tryEmit(listOf(newer, older, other)))
    runCurrent()
    assertEquals(here, viewModel.loaded().selectedPhotoCoordinate)
    assertEquals(
        listOf("newer", "older"),
        viewModel.loaded().selectedPhotoGroup?.photos?.map { it.id },
    )

    assertTrue(updates.tryEmit(listOf(other)))
    runCurrent()
    assertEquals(null, viewModel.loaded().selectedPhotoCoordinate)

    viewModel.selectPhotoGroup(elsewhere)
    viewModel.closePhotoViewer()
    assertEquals(null, viewModel.loaded().selectedPhotoGroup)
  }

  @Test
  fun selectionSurvivesPhotoFailureAndRetryButNotATripReload() = runTest {
    val photo = tripPhoto("one", latitude = 10.0, longitude = 20.0)
    val failure = CompletableDeferred<Unit>()
    var attempts = 0
    val photos = ControlledPhotos {
      if (++attempts == 1) {
        flow {
          emit(listOf(photo))
          failure.await()
          error("Gallery unavailable")
        }
      } else flow { emit(listOf(photo)) }
    }
    val viewModel = createViewModel(photos)
    runCurrent()
    val here = MapCoordinate(10.0, 20.0)
    viewModel.selectPhotoGroup(here)

    failure.complete(Unit)
    runCurrent()
    assertEquals(PhotosLoadState.ERROR, viewModel.loaded().photosLoadState)
    assertEquals(listOf("one"), viewModel.loaded().selectedPhotoGroup?.photos?.map { it.id })
    viewModel.retryPhotos()
    assertEquals(here, viewModel.loaded().selectedPhotoCoordinate)
    runCurrent()
    assertEquals(here, viewModel.loaded().selectedPhotoCoordinate)

    viewModel.retryTrip()
    runCurrent()
    assertEquals(null, viewModel.loaded().selectedPhotoCoordinate)
  }

  @Test
  fun photographerNamesHoldOnlyRealProfileNames() = runTest {
    val trips =
        FakeTripRepository(
            trips = listOf(trip().copy(memberIds = listOf("alice", "blank", "gone")))
        )
    val users =
        object : UserRepository by FakeUserRepository(USER) {
          override suspend fun getUsers(userIds: Set<String>): List<User> =
              listOf(USER.copy(displayName = " Alice "), User("blank", "  ", null))
        }
    val viewModel =
        createViewModel(ControlledPhotos { flowOf(emptyList()) }, trips, userRepository = users)

    runCurrent()

    val state = viewModel.loaded()
    assertEquals(mapOf("alice" to "Alice"), state.photographerNames)
    // Members keep their fallback for the map, which the viewer must not use as a name.
    assertEquals("G", state.members.getValue("gone").displayName)
  }

  private fun TripMapViewModel.loaded() = uiState.value as TripMapUiState.Loaded

  private fun createViewModel(
      photos: PhotoRepository,
      tripRepository: TripRepository = FakeTripRepository(trips = listOf(trip())),
      activityRepository: ActivityRepository = FakeActivityRepository(),
      userRepository: UserRepository = FakeUserRepository(USER),
  ): TripMapViewModel =
      TripMapViewModel(TRIP_ID, tripRepository, activityRepository, userRepository, photos).also {
        store.put("map", it)
      }

  private fun tripRepository(load: suspend () -> List<Trip>): TripRepository =
      object : TripRepository by FakeTripRepository() {
        override suspend fun getTrips(): List<Trip> = load()
      }

  private class ControlledPhotos(private val source: (String) -> Flow<List<TripPhoto>>) :
      PhotoRepository by FakePhotoRepository() {
    val requestedTrips = mutableListOf<String>()

    override fun tripPhotos(tripId: String): Flow<List<TripPhoto>> {
      requestedTrips += tripId
      return source(tripId)
    }

    override fun pendingPhotos(tripId: String): Flow<List<PendingPhoto>> =
        error("The map must not observe the pending-photo queue")
  }

  private fun trip() =
      Trip(
          uid = TRIP_ID,
          name = "Trip",
          description = null,
          destinations = listOf(DESTINATION),
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

  private fun activity() =
      Activity(
          uid = "activity",
          tripId = TRIP_ID,
          activityType = ActivityType.ACCEPTED,
          name = "Activity",
          description = null,
          location = DESTINATION,
          startTime = Timestamp(0, 0),
          endTime = null,
          createdByUserId = USER.uid,
          creationTime = Timestamp(0, 0),
      )

  private companion object {
    const val TRIP_ID = "trip-1"
    val DESTINATION = TripLocation(46.5, 6.6, "Lausanne")
    val USER = User(uid = "alice", displayName = "Alice", profilePictureUrl = null)
  }
}
