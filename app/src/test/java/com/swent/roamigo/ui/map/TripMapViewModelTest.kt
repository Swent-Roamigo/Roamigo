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
import com.swent.roamigo.model.users.FakeUserRepository
import com.swent.roamigo.model.users.User
import com.swent.roamigo.model.users.UserRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    val factory = TripMapViewModel.factory(TRIP_ID, FakeTripRepository(), FakeUserRepository(ALICE))

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
        ),
        viewModel.uiState.value,
    )
  }

  @Test
  fun retry_duringUserLookup_cancelsItAndIgnoresItsResult() = runTest {
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

    viewModel.retry()
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
  ): TripMapViewModel =
      TripMapViewModel(TRIP_ID, repository, userRepository).also { store.put("map", it) }

  private fun repository(load: suspend () -> List<Trip>): TripRepository =
      object : TripRepository by FakeTripRepository() {
        override suspend fun getTrips(): List<Trip> = load()
      }

  private fun userRepository(load: suspend () -> List<User>): UserRepository =
      object : UserRepository by FakeUserRepository(ALICE) {
        override suspend fun getUsers(userIds: Set<String>): List<User> = load()
      }

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
