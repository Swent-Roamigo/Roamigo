// Implemented with assistance from Claude (Anthropic) and OpenAI Codex.
package com.swent.roamigo.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import com.google.firebase.Timestamp
import com.swent.roamigo.model.TripLocation
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
        TripMapUiState.Loaded(
            trip = trip,
            viewport = TripMapViewport.Centered(PARIS.latitude, PARIS.longitude),
            members = mapOf(ALICE.uid to MemberDisplayInfo("Alice", "A", ALICE_AVATAR)),
            activitiesLoadState = ActivitiesLoadState.LOADED,
        ),
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
    assertEquals(
        TripMapUiState.Error(TripMapViewModel.LOAD_FAILED_MESSAGE),
        viewModel.uiState.value,
    )

    viewModel.retryTrip()
    runCurrent()
    assertEquals(TripMapUiState.Loading, viewModel.uiState.value)

    retryResult.complete(listOf(trip))
    advanceUntilIdle()

    assertEquals(2, requests)
    assertTrue(viewModel.uiState.value is TripMapUiState.Loaded)
    assertEquals(trip, (viewModel.uiState.value as TripMapUiState.Loaded).trip)
  }

  @Test
  fun failedLoad_showsFixedMessage_insteadOfTheTechnicalOne() = runTest {
    val viewModel =
        createViewModel(
            FakeTripRepository(
                failure = IllegalStateException("FirebaseFirestoreException: PERMISSION_DENIED")
            )
        )

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
  fun tripWithoutLocations_hasNoViewport() = runTest {
    val viewModel = createViewModel(FakeTripRepository(trips = listOf(trip(TRIP_ID))))

    advanceUntilIdle()

    assertEquals(
        TripMapViewport.None,
        (viewModel.uiState.value as TripMapUiState.Loaded).viewport,
    )
  }

  @Test
  fun viewportIncludesDestinationsAcceptedActivitiesAndSuggestions() = runTest {
    val tokyo = TripLocation(latitude = 35.6762, longitude = 139.6503, name = "Tokyo")
    val accepted = activity("accepted").copy(location = LONDON)
    val suggestion = activity("suggestion", ActivityType.SUGGESTION).copy(location = tokyo)
    val viewModel =
        createViewModel(
            FakeTripRepository(trips = listOf(trip(TRIP_ID, listOf(PARIS)))),
            activityRepository = FakeActivityRepository(listOf(accepted, suggestion)),
        )

    advanceUntilIdle()

    assertEquals(
        TripMapViewport.FitAll(
            listOf(
                MapCoordinate(PARIS.latitude, PARIS.longitude),
                MapCoordinate(LONDON.latitude, LONDON.longitude),
                MapCoordinate(tokyo.latitude, tokyo.longitude),
            )
        ),
        (viewModel.uiState.value as TripMapUiState.Loaded).viewport,
    )
  }

  @Test
  fun tripWithoutDestinations_centersOnItsSuggestion() = runTest {
    val suggestion = activity("suggestion", ActivityType.SUGGESTION).copy(location = LONDON)
    val viewModel =
        createViewModel(
            FakeTripRepository(trips = listOf(trip(TRIP_ID))),
            activityRepository = FakeActivityRepository(listOf(suggestion)),
        )

    advanceUntilIdle()

    assertEquals(
        TripMapViewport.Centered(LONDON.latitude, LONDON.longitude),
        (viewModel.uiState.value as TripMapUiState.Loaded).viewport,
    )
  }

  @Test
  fun colocatedDestinationsActivitiesAndSuggestions_useOneViewportCoordinate() = runTest {
    val viewModel =
        createViewModel(
            FakeTripRepository(trips = listOf(trip(TRIP_ID, listOf(PARIS)))),
            activityRepository =
                FakeActivityRepository(
                    listOf(
                        activity("accepted"),
                        activity("suggestion", ActivityType.SUGGESTION),
                    )
                ),
        )

    advanceUntilIdle()

    assertEquals(
        TripMapViewport.Centered(PARIS.latitude, PARIS.longitude),
        (viewModel.uiState.value as TripMapUiState.Loaded).viewport,
    )
  }

  @Test
  fun retryTrip_cancelsTheRunningLoadAndIgnoresItsResult() = runTest {
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

    viewModel.retryTrip()
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
  fun initialLoad_populatesActivitiesAndSuggestionsForTheSelectedTrip() = runTest {
    val accepted = activity("accepted")
    val suggestion = activity("suggestion", ActivityType.SUGGESTION)
    val otherTripActivity = activity("other", tripId = OTHER_TRIP_ID)
    val viewModel =
        createViewModel(
            FakeTripRepository(trips = listOf(trip(TRIP_ID), trip(OTHER_TRIP_ID))),
            activityRepository =
                FakeActivityRepository(listOf(suggestion, accepted, otherTripActivity)),
        )

    advanceUntilIdle()

    val state = viewModel.uiState.value as TripMapUiState.Loaded
    assertEquals(
        listOf(suggestion.toTripMarkerUiState(), accepted.toTripMarkerUiState()),
        state.activityMarkers,
    )
    assertEquals(MemberDisplayInfo("Alice", "A", ALICE_AVATAR), state.members[ALICE.uid])
  }

  @Test
  fun slowActivityLoad_showsTheTripAndProfilesBeforeMarkersAreAvailable() = runTest {
    val pendingActivities = CompletableDeferred<List<Activity>>()
    val requestedTripIds = mutableListOf<String>()
    val viewModel =
        createViewModel(
            repository { listOf(trip(TRIP_ID)) },
            activityRepository =
                activityRepository { tripId ->
                  requestedTripIds += tripId
                  pendingActivities.await()
                },
        )

    runCurrent()

    assertEquals(listOf(TRIP_ID), requestedTripIds)
    val before = viewModel.uiState.value as TripMapUiState.Loaded
    assertEquals(TRIP_ID, before.trip.uid)
    assertEquals(ActivitiesLoadState.LOADING, before.activitiesLoadState)
    assertTrue(before.activityMarkers.isEmpty())
    assertEquals(MemberDisplayInfo("Alice", "A", ALICE_AVATAR), before.members[ALICE.uid])

    val suggestion = activity("suggestion", ActivityType.SUGGESTION)
    pendingActivities.complete(listOf(suggestion))
    advanceUntilIdle()

    val state = viewModel.uiState.value as TripMapUiState.Loaded
    assertEquals(listOf(suggestion.toTripMarkerUiState()), state.activityMarkers)
  }

  @Test
  fun failedActivityLoad_keepsTripAndMembersAndActivityRetryLoadsMarkers() = runTest {
    val trip = trip(TRIP_ID, listOf(PARIS))
    val accepted = activity("accepted").copy(location = LONDON)
    var tripRequests = 0
    var activityRequests = 0
    val viewModel =
        createViewModel(
            repository {
              tripRequests++
              listOf(trip)
            },
            activityRepository =
                activityRepository {
                  activityRequests++
                  if (activityRequests == 1) throw IllegalStateException("Activities unavailable")
                  listOf(accepted)
                },
        )

    advanceUntilIdle()

    val failedState = viewModel.uiState.value as TripMapUiState.Loaded
    assertEquals(trip, failedState.trip)
    assertEquals(ActivitiesLoadState.ERROR, failedState.activitiesLoadState)
    assertEquals(emptyList<TripMarkerUiState>(), failedState.activityMarkers)
    assertEquals(
        TripMapViewport.Centered(PARIS.latitude, PARIS.longitude),
        failedState.viewport,
    )
    assertEquals(MemberDisplayInfo("Alice", "A", ALICE_AVATAR), failedState.members[ALICE.uid])

    viewModel.retryActivities()
    advanceUntilIdle()

    val state = viewModel.uiState.value as TripMapUiState.Loaded
    assertEquals(1, tripRequests)
    assertEquals(2, activityRequests)
    assertEquals(ActivitiesLoadState.LOADED, state.activitiesLoadState)
    assertEquals(listOf(accepted.toTripMarkerUiState()), state.activityMarkers)
    assertEquals(
        TripMapViewport.FitAll(
            listOf(
                MapCoordinate(PARIS.latitude, PARIS.longitude),
                MapCoordinate(LONDON.latitude, LONDON.longitude),
            )
        ),
        state.viewport,
    )
  }

  @Test
  fun failedActivityLoad_withoutDestinationsStillLoadsTheTrip() = runTest {
    val viewModel =
        createViewModel(
            repository { listOf(trip(TRIP_ID)) },
            activityRepository =
                activityRepository { throw IllegalStateException("Activities unavailable") },
        )

    advanceUntilIdle()

    val state = viewModel.uiState.value as TripMapUiState.Loaded
    assertEquals(TRIP_ID, state.trip.uid)
    assertEquals(TripMapViewport.None, state.viewport)
    assertEquals(ActivitiesLoadState.ERROR, state.activitiesLoadState)
    assertTrue(state.activityMarkers.isEmpty())
  }

  @Test
  fun retryTrip_replacesOldMarkersAndUpdatesAnAcceptedSuggestion() = runTest {
    val accepted = activity("accepted")
    val suggestion = activity("suggestion", ActivityType.SUGGESTION)
    var activities = listOf(accepted, suggestion)
    val viewModel =
        createViewModel(
            repository { listOf(trip(TRIP_ID)) },
            activityRepository = activityRepository { activities },
        )
    advanceUntilIdle()
    assertEquals(
        listOf(accepted.toTripMarkerUiState(), suggestion.toTripMarkerUiState()),
        (viewModel.uiState.value as TripMapUiState.Loaded).activityMarkers,
    )

    val newlyAccepted = suggestion.copy(activityType = ActivityType.ACCEPTED)
    activities = listOf(newlyAccepted)
    viewModel.retryTrip()
    advanceUntilIdle()

    val state = viewModel.uiState.value as TripMapUiState.Loaded
    assertEquals(listOf(newlyAccepted.toTripMarkerUiState()), state.activityMarkers)
  }

  @Test
  fun retryTrip_duringActivityLoad_cancelsItAndIgnoresItsResult() = runTest {
    val firstActivities = CompletableDeferred<List<Activity>>()
    val latestActivities = CompletableDeferred<List<Activity>>()
    var firstLoadCancelled = false
    var requests = 0
    val viewModel =
        createViewModel(
            repository { listOf(trip(TRIP_ID)) },
            activityRepository =
                activityRepository {
                  requests++
                  if (requests == 1) {
                    try {
                      firstActivities.await()
                    } finally {
                      firstLoadCancelled = true
                    }
                  } else {
                    latestActivities.await()
                  }
                },
        )
    runCurrent()

    viewModel.retryTrip()
    runCurrent()

    assertTrue(firstLoadCancelled)
    firstActivities.complete(listOf(activity("outdated", ActivityType.SUGGESTION)))
    runCurrent()
    val pending = viewModel.uiState.value as TripMapUiState.Loaded
    assertEquals(ActivitiesLoadState.LOADING, pending.activitiesLoadState)
    assertTrue(pending.activityMarkers.isEmpty())

    val latest = activity("latest")
    latestActivities.complete(listOf(latest))
    advanceUntilIdle()

    val state = viewModel.uiState.value as TripMapUiState.Loaded
    assertEquals(listOf(latest.toTripMarkerUiState()), state.activityMarkers)
  }

  @Test
  fun retryTrip_cancelsAnActivityRefreshBeforeLoadingNewMarkers() = runTest {
    val pendingActivities = CompletableDeferred<List<Activity>>()
    val latest = activity("latest", ActivityType.SUGGESTION)
    var activityRequests = 0
    var refreshCancelled = false
    val viewModel =
        createViewModel(
            repository { listOf(trip(TRIP_ID)) },
            activityRepository =
                activityRepository {
                  when (++activityRequests) {
                    1 -> emptyList()
                    2 ->
                        try {
                          pendingActivities.await()
                        } finally {
                          refreshCancelled = true
                        }
                    else -> listOf(latest)
                  }
                },
        )
    advanceUntilIdle()

    viewModel.retryActivities()
    runCurrent()
    viewModel.retryTrip()
    advanceUntilIdle()

    assertTrue(refreshCancelled)
    pendingActivities.complete(listOf(activity("outdated")))
    runCurrent()

    val state = viewModel.uiState.value as TripMapUiState.Loaded
    assertEquals(listOf(latest.toTripMarkerUiState()), state.activityMarkers)
    assertEquals(ActivitiesLoadState.LOADED, state.activitiesLoadState)
  }

  @Test
  fun retryActivities_preservesTheTripWhileLoadingAndUpdatesItsViewport() = runTest {
    val pendingActivities = CompletableDeferred<List<Activity>>()
    val original = activity("original")
    var tripRequests = 0
    var activityRequests = 0
    val viewModel =
        createViewModel(
            repository {
              tripRequests++
              listOf(trip(TRIP_ID, listOf(PARIS)))
            },
            activityRepository =
                activityRepository {
                  activityRequests++
                  if (activityRequests == 1) listOf(original) else pendingActivities.await()
                },
        )
    advanceUntilIdle()
    val before = viewModel.uiState.value as TripMapUiState.Loaded

    viewModel.retryActivities()
    runCurrent()

    assertEquals(
        before.copy(activitiesLoadState = ActivitiesLoadState.LOADING),
        viewModel.uiState.value,
    )

    val suggestion = activity("suggestion", ActivityType.SUGGESTION).copy(location = LONDON)
    pendingActivities.complete(listOf(suggestion))
    advanceUntilIdle()

    assertEquals(1, tripRequests)
    assertEquals(
        before.copy(
            activityMarkers = listOf(suggestion.toTripMarkerUiState()),
            viewport =
                TripMapViewport.FitAll(
                    listOf(
                        MapCoordinate(PARIS.latitude, PARIS.longitude),
                        MapCoordinate(LONDON.latitude, LONDON.longitude),
                    )
                ),
            activitiesLoadState = ActivitiesLoadState.LOADED,
        ),
        viewModel.uiState.value,
    )
  }

  @Test
  fun retryActivities_failureKeepsExistingDataAndAnotherRetryCanLoadAnEmptyList() = runTest {
    var failActivities = false
    var activities = listOf(activity("original"))
    val viewModel =
        createViewModel(
            repository { listOf(trip(TRIP_ID, listOf(PARIS))) },
            activityRepository =
                activityRepository {
                  if (failActivities) throw IllegalStateException("Activities unavailable")
                  activities
                },
        )
    advanceUntilIdle()
    val before = viewModel.uiState.value as TripMapUiState.Loaded

    failActivities = true
    viewModel.retryActivities()
    advanceUntilIdle()

    assertEquals(
        before.copy(activitiesLoadState = ActivitiesLoadState.ERROR),
        viewModel.uiState.value,
    )

    failActivities = false
    activities = emptyList()
    viewModel.retryActivities()
    advanceUntilIdle()

    assertEquals(
        before.copy(
            activityMarkers = emptyList(),
            activitiesLoadState = ActivitiesLoadState.LOADED,
        ),
        viewModel.uiState.value,
    )
  }

  @Test
  fun retryActivities_cancelsThePreviousActivityRequest() = runTest {
    val firstActivities = CompletableDeferred<List<Activity>>()
    val latestActivities = CompletableDeferred<List<Activity>>()
    var requests = 0
    var firstLoadCancelled = false
    val viewModel =
        createViewModel(
            repository { listOf(trip(TRIP_ID)) },
            activityRepository =
                activityRepository {
                  when (++requests) {
                    1 -> emptyList()
                    2 ->
                        try {
                          firstActivities.await()
                        } finally {
                          firstLoadCancelled = true
                        }
                    else -> latestActivities.await()
                  }
                },
        )
    advanceUntilIdle()

    viewModel.retryActivities()
    runCurrent()
    viewModel.retryActivities()
    runCurrent()

    assertTrue(firstLoadCancelled)
    firstActivities.complete(listOf(activity("outdated")))
    val latest = activity("latest", ActivityType.SUGGESTION)
    latestActivities.complete(listOf(latest))
    advanceUntilIdle()

    val state = viewModel.uiState.value as TripMapUiState.Loaded
    assertEquals(listOf(latest.toTripMarkerUiState()), state.activityMarkers)
    assertEquals(ActivitiesLoadState.LOADED, state.activitiesLoadState)
  }

  @Test
  fun liveActivities_addUpdateAndRemoveMarkersWithoutReloadingTheTrip() = runTest {
    val original = activity("original")
    val suggestion = activity("suggestion", ActivityType.SUGGESTION)
    val updates = MutableStateFlow(listOf(original, suggestion))
    var tripRequests = 0
    val observedTripIds = mutableListOf<String>()
    val trips =
        object : TripRepository by FakeTripRepository() {
          override suspend fun getTrips(): List<Trip> {
            tripRequests++
            return listOf(trip(TRIP_ID, listOf(PARIS)))
          }

          override suspend fun getActivitiesForTrip(tripId: String): List<Activity> =
              error("The map must observe activities instead of reading them once")
        }
    val activities =
        object : ActivityRepository by FakeActivityRepository() {
          override fun observeActivities(tripId: String): Flow<List<Activity>> {
            observedTripIds += tripId
            return updates
          }
        }
    val viewModel = createViewModel(trips, activityRepository = activities)
    advanceUntilIdle()
    val before = viewModel.uiState.value as TripMapUiState.Loaded
    assertEquals(listOf(original, suggestion).toTripMarkerUiStates(), before.activityMarkers)

    val accepted = suggestion.copy(activityType = ActivityType.ACCEPTED, location = LONDON)
    val added = activity("added")
    updates.value = listOf(accepted, added)
    advanceUntilIdle()

    assertEquals(
        before.copy(
            activityMarkers = listOf(accepted, added).toTripMarkerUiStates(),
            viewport =
                TripMapViewport.FitAll(
                    listOf(
                        MapCoordinate(PARIS.latitude, PARIS.longitude),
                        MapCoordinate(LONDON.latitude, LONDON.longitude),
                    )
                ),
        ),
        viewModel.uiState.value,
    )

    updates.value = emptyList()
    advanceUntilIdle()

    assertEquals(before.copy(activityMarkers = emptyList()), viewModel.uiState.value)
    assertEquals(1, tripRequests)
    assertEquals(listOf(TRIP_ID), observedTripIds)
  }

  @Test
  fun liveActivityFailure_preservesMarkersAndRetryResumesObservation() = runTest {
    val original = activity("original")
    val recovered = activity("recovered", ActivityType.SUGGESTION)
    val updates = MutableStateFlow(listOf(recovered))
    val failure = CompletableDeferred<Unit>()
    var subscriptions = 0
    val activities =
        object : ActivityRepository by FakeActivityRepository() {
          override fun observeActivities(tripId: String): Flow<List<Activity>> = flow {
            if (++subscriptions == 1) {
              emit(listOf(original))
              failure.await()
              error("Activities unavailable")
            }
            emitAll(updates)
          }
        }
    val viewModel =
        createViewModel(
            FakeTripRepository(trips = listOf(trip(TRIP_ID))),
            activityRepository = activities,
        )
    advanceUntilIdle()
    val before = viewModel.uiState.value as TripMapUiState.Loaded

    failure.complete(Unit)
    advanceUntilIdle()

    assertEquals(
        before.copy(activitiesLoadState = ActivitiesLoadState.ERROR),
        viewModel.uiState.value,
    )

    viewModel.retryActivities()
    advanceUntilIdle()
    assertEquals(
        before.copy(activityMarkers = listOf(recovered.toTripMarkerUiState())),
        viewModel.uiState.value,
    )

    updates.value = emptyList()
    advanceUntilIdle()

    assertEquals(
        before.copy(activityMarkers = emptyList(), viewport = TripMapViewport.None),
        viewModel.uiState.value,
    )
    assertEquals(2, subscriptions)
  }

  @Test
  fun activityObserver_isReplacedOnRetriesAndCancelledWhenViewModelIsCleared() = runTest {
    val updates = MutableStateFlow(emptyList<Activity>())
    var cancellations = 0
    val activities =
        object : ActivityRepository by FakeActivityRepository() {
          override fun observeActivities(tripId: String): Flow<List<Activity>> = flow {
            try {
              emitAll(updates)
            } finally {
              cancellations++
            }
          }
        }
    val viewModel =
        createViewModel(
            FakeTripRepository(trips = listOf(trip(TRIP_ID))),
            activityRepository = activities,
        )
    advanceUntilIdle()
    assertEquals(1, updates.subscriptionCount.value)

    viewModel.retryActivities()
    advanceUntilIdle()
    assertEquals(1, updates.subscriptionCount.value)
    assertEquals(1, cancellations)

    viewModel.retryTrip()
    advanceUntilIdle()
    assertEquals(1, updates.subscriptionCount.value)
    assertEquals(2, cancellations)

    store.clear()
    runCurrent()
    assertEquals(0, updates.subscriptionCount.value)
    assertEquals(3, cancellations)
  }

  @Test
  fun factory_createsTripMapViewModel_andRejectsOtherClasses() {
    val factory =
        TripMapViewModel.factory(
            TRIP_ID,
            FakeTripRepository(),
            FakeActivityRepository(),
            FakeUserRepository(ALICE),
        )

    assertTrue(factory.create(TripMapViewModel::class.java) is TripMapViewModel)
    assertThrows(IllegalArgumentException::class.java) {
      factory.create(OtherViewModel::class.java)
    }
  }

  @Test
  fun memberWithFullProfile_showsTheirNameAndAvatar() = runTest {
    assertEquals(
        MemberDisplayInfo(displayName = "Alice", initial = "A", avatarUrl = ALICE_AVATAR),
        loadMembers(ALICE.uid)[ALICE.uid],
    )
  }

  @Test
  fun memberWithBlankName_fallsBackToTheirUserIdInitial() = runTest {
    val nameless = User(uid = "bob-id", displayName = "  ", profilePictureUrl = BOB_AVATAR)

    assertEquals(
        MemberDisplayInfo(displayName = "B", initial = "B", avatarUrl = BOB_AVATAR),
        loadMembers(nameless.uid, users = listOf(nameless))[nameless.uid],
    )
  }

  @Test
  fun memberWithoutAvatar_hasNoAvatarUrl() = runTest {
    val noAvatar = User(uid = "bob-id", displayName = "Bob", profilePictureUrl = null)
    val blankAvatar = User(uid = "carol-id", displayName = "Carol", profilePictureUrl = " ")

    val members = loadMembers(noAvatar.uid, blankAvatar.uid, users = listOf(noAvatar, blankAvatar))

    assertEquals(MemberDisplayInfo("Bob", "B", null), members[noAvatar.uid])
    assertEquals(MemberDisplayInfo("Carol", "C", null), members[blankAvatar.uid])
  }

  @Test
  fun memberWithoutProfile_fallsBackToTheirUserIdInitial() = runTest {
    val members = loadMembers(ALICE.uid, "zoe-id")

    assertEquals(setOf(ALICE.uid, "zoe-id"), members.keys)
    assertEquals(MemberDisplayInfo("Z", "Z", null), members["zoe-id"])
  }

  @Test
  fun failedUserLookup_stillLoadsTheMap_withFallbackForEveryMember() = runTest {
    val viewModel =
        createViewModel(
            FakeTripRepository(
                trips = listOf(trip(TRIP_ID, memberIds = listOf(ALICE.uid, "bob-id")))
            ),
            FakeUserRepository(ALICE, failure = IllegalStateException("Users unavailable")),
        )

    advanceUntilIdle()

    assertEquals(
        mapOf(
            ALICE.uid to MemberDisplayInfo("A", "A", null),
            "bob-id" to MemberDisplayInfo("B", "B", null),
        ),
        (viewModel.uiState.value as TripMapUiState.Loaded).members,
    )
  }

  @Test
  fun slowUserLookup_showsTheMapWithFallbackMembers_thenTheirProfiles() = runTest {
    val trip = trip(TRIP_ID, listOf(PARIS), memberIds = listOf(ALICE.uid, "bob-id"))
    val pendingUsers = CompletableDeferred<List<User>>()
    val viewModel =
        createViewModel(
            FakeTripRepository(trips = listOf(trip)),
            userRepository { pendingUsers.await() },
        )
    runCurrent()

    val viewport = TripMapViewport.Centered(PARIS.latitude, PARIS.longitude)
    assertEquals(
        TripMapUiState.Loaded(
            trip = trip,
            viewport = viewport,
            members =
                mapOf(
                    ALICE.uid to MemberDisplayInfo("A", "A", null),
                    "bob-id" to MemberDisplayInfo("B", "B", null),
                ),
            activitiesLoadState = ActivitiesLoadState.LOADED,
        ),
        viewModel.uiState.value,
    )

    pendingUsers.complete(listOf(ALICE, BOB))
    advanceUntilIdle()

    assertEquals(
        TripMapUiState.Loaded(
            trip = trip,
            viewport = viewport,
            members =
                mapOf(
                    ALICE.uid to MemberDisplayInfo("Alice", "A", ALICE_AVATAR),
                    "bob-id" to MemberDisplayInfo("Bob", "B", BOB_AVATAR),
                ),
            activitiesLoadState = ActivitiesLoadState.LOADED,
        ),
        viewModel.uiState.value,
    )
  }

  @Test
  fun retryTrip_duringUserLookup_cancelsItAndIgnoresItsResult() = runTest {
    val firstUsers = CompletableDeferred<List<User>>()
    val latestUsers = CompletableDeferred<List<User>>()
    var firstLookupCancelled = false
    var lookups = 0
    val viewModel =
        createViewModel(
            FakeTripRepository(trips = listOf(trip(TRIP_ID))),
            userRepository {
              lookups++
              if (lookups == 1) {
                try {
                  firstUsers.await()
                } finally {
                  firstLookupCancelled = true
                }
              } else {
                latestUsers.await()
              }
            },
        )
    runCurrent()

    viewModel.retryTrip()
    runCurrent()

    assertTrue(firstLookupCancelled)
    firstUsers.complete(listOf(ALICE.copy(displayName = "Outdated")))
    runCurrent()
    val fallback = mapOf(ALICE.uid to MemberDisplayInfo("A", "A", null))
    assertEquals(fallback, (viewModel.uiState.value as TripMapUiState.Loaded).members)

    latestUsers.complete(listOf(ALICE))
    advanceUntilIdle()

    assertEquals(
        mapOf(ALICE.uid to MemberDisplayInfo("Alice", "A", ALICE_AVATAR)),
        (viewModel.uiState.value as TripMapUiState.Loaded).members,
    )
  }

  @Test
  fun profilesFinishingBeforeActivityRetry_preserveBothResults() = runTest {
    assertConcurrentProfileAndActivityLoads(profilesFirst = true, activityFails = false)
  }

  @Test
  fun profilesFinishingAfterActivityRetry_preserveBothResults() = runTest {
    assertConcurrentProfileAndActivityLoads(profilesFirst = false, activityFails = false)
  }

  @Test
  fun profilesFinishingBeforeFailedActivityRetry_preserveProfilesAndActivityError() = runTest {
    assertConcurrentProfileAndActivityLoads(profilesFirst = true, activityFails = true)
  }

  @Test
  fun profilesFinishingAfterFailedActivityRetry_preserveProfilesAndActivityError() = runTest {
    assertConcurrentProfileAndActivityLoads(profilesFirst = false, activityFails = true)
  }

  private fun TestScope.assertConcurrentProfileAndActivityLoads(
      profilesFirst: Boolean,
      activityFails: Boolean,
  ) {
    val pendingUsers = CompletableDeferred<List<User>>()
    val pendingActivities = CompletableDeferred<List<Activity>>()
    val original = activity("original")
    val accepted = activity("accepted").copy(location = LONDON)
    val suggestion =
        activity("suggestion", ActivityType.SUGGESTION)
            .copy(location = TripLocation(latitude = 35.6762, longitude = 139.6503, name = "Tokyo"))
    var tripRequests = 0
    var activityRequests = 0
    val viewModel =
        createViewModel(
            repository {
              tripRequests++
              listOf(trip(TRIP_ID, listOf(PARIS)))
            },
            activityRepository =
                activityRepository {
                  activityRequests++
                  if (activityRequests == 1) listOf(original) else pendingActivities.await()
                },
            userRepository = userRepository { pendingUsers.await() },
        )
    runCurrent()
    val initial = viewModel.uiState.value as TripMapUiState.Loaded
    val profiles = mapOf(ALICE.uid to MemberDisplayInfo("Alice", "A", ALICE_AVATAR))

    viewModel.retryActivities()
    runCurrent()
    assertEquals(
        initial.copy(activitiesLoadState = ActivitiesLoadState.LOADING),
        viewModel.uiState.value,
    )

    if (profilesFirst) {
      pendingUsers.complete(listOf(ALICE))
      runCurrent()
      assertEquals(
          initial.copy(members = profiles, activitiesLoadState = ActivitiesLoadState.LOADING),
          viewModel.uiState.value,
      )
    }

    if (activityFails) {
      pendingActivities.completeExceptionally(IllegalStateException("Activities unavailable"))
    } else {
      pendingActivities.complete(listOf(accepted, suggestion))
    }
    runCurrent()
    val expected =
        if (activityFails) {
          initial.copy(activitiesLoadState = ActivitiesLoadState.ERROR)
        } else {
          initial.copy(
              activityMarkers = listOf(accepted, suggestion).toTripMarkerUiStates(),
              viewport =
                  TripMapViewport.FitAll(
                      listOf(PARIS, LONDON, suggestion.location).map {
                        MapCoordinate(it.latitude, it.longitude)
                      }
                  ),
              activitiesLoadState = ActivitiesLoadState.LOADED,
          )
        }
    assertEquals(
        expected.copy(members = if (profilesFirst) profiles else initial.members),
        viewModel.uiState.value,
    )

    if (!profilesFirst) {
      pendingUsers.complete(listOf(ALICE))
    }
    advanceUntilIdle()

    assertEquals(expected.copy(members = profiles), viewModel.uiState.value)
    assertEquals(1, tripRequests)
    assertEquals(2, activityRequests)
  }

  private class OtherViewModel : ViewModel()

  /** Loads a trip with the given members and returns their display information. */
  private fun TestScope.loadMembers(
      vararg memberIds: String,
      users: List<User> = emptyList(),
  ): Map<String, MemberDisplayInfo> {
    val viewModel =
        createViewModel(
            FakeTripRepository(trips = listOf(trip(TRIP_ID, memberIds = memberIds.toList()))),
            FakeUserRepository(ALICE, users),
        )
    advanceUntilIdle()
    return (viewModel.uiState.value as TripMapUiState.Loaded).members
  }

  private fun createViewModel(
      repository: TripRepository,
      userRepository: UserRepository = FakeUserRepository(ALICE),
      activityRepository: ActivityRepository = FakeActivityRepository(),
  ): TripMapViewModel =
      TripMapViewModel(TRIP_ID, repository, activityRepository, userRepository).also {
        store.put("map", it)
      }

  private fun repository(load: suspend () -> List<Trip>): TripRepository =
      object : TripRepository by FakeTripRepository() {
        override suspend fun getTrips(): List<Trip> = load()
      }

  private fun activityRepository(load: suspend (String) -> List<Activity>): ActivityRepository =
      object : ActivityRepository by FakeActivityRepository() {
        override fun observeActivities(tripId: String): Flow<List<Activity>> = flow {
          emit(load(tripId))
        }
      }

  private fun userRepository(load: suspend () -> List<User>): UserRepository =
      object : UserRepository by FakeUserRepository(ALICE) {
        override suspend fun getUsers(userIds: Set<String>): List<User> = load()
      }

  private fun activity(
      id: String,
      type: ActivityType = ActivityType.ACCEPTED,
      tripId: String = TRIP_ID,
  ): Activity =
      Activity(
          uid = id,
          tripId = tripId,
          activityType = type,
          name = "$id activity",
          description = null,
          location = PARIS,
          startTime = Timestamp(0, 0),
          endTime = null,
          createdByUserId = ALICE.uid,
          creationTime = Timestamp(0, 0),
      )

  private fun trip(
      id: String,
      destinations: List<TripLocation> = emptyList(),
      memberIds: List<String> = listOf(ALICE.uid),
  ): Trip =
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
          ownerId = memberIds.firstOrNull().orEmpty(),
          memberIds = memberIds,
          status = TripStatus.PLANNED,
          createdAt = Timestamp(0, 0),
      )

  private companion object {
    const val TRIP_ID = "trip-1"
    const val OTHER_TRIP_ID = "trip-2"
    val PARIS = TripLocation(latitude = 48.8566, longitude = 2.3522, name = "Paris")
    val LONDON = TripLocation(latitude = 51.5074, longitude = -0.1278, name = "London")
    const val ALICE_AVATAR = "https://example.com/alice.png"
    const val BOB_AVATAR = "https://example.com/bob.png"
    val ALICE = User(uid = "alice-id", displayName = "Alice", profilePictureUrl = ALICE_AVATAR)
    val BOB = User(uid = "bob-id", displayName = "Bob", profilePictureUrl = BOB_AVATAR)
  }
}
