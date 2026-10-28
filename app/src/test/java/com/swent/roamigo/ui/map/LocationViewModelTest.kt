// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.swent.roamigo.model.connectivity.FakeConnectivityRepository
import com.swent.roamigo.model.location.DeviceLocation
import com.swent.roamigo.model.location.FakeLocationRepository
import com.swent.roamigo.model.location.LocationSharingException
import com.swent.roamigo.model.location.MemberLocation
import com.swent.roamigo.ui.map.LocationPermissionState.Denied
import com.swent.roamigo.ui.map.LocationPermissionState.Granted
import com.swent.roamigo.ui.map.LocationPermissionState.NotRequested
import com.swent.roamigo.ui.map.LocationPermissionState.Revoked
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocationViewModelTest {

  private val testDispatcher = StandardTestDispatcher()

  // Current time of the fake clock, in milliseconds since the epoch.
  private var now = NOW_MILLIS

  private val interval = LocationViewModel.MY_LOCATION_REFRESH_INTERVAL

  private val staleCheckInterval = LocationViewModel.STALENESS_CHECK_INTERVAL

  private lateinit var repository: FakeLocationRepository

  private lateinit var connectivity: FakeConnectivityRepository

  private val viewModelStore = ViewModelStore()

  private var createdViewModels = 0

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    repository = FakeLocationRepository()
    connectivity = FakeConnectivityRepository()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  /**
   * Runs a test, then clears its ViewModels. Their periodic refreshes never end on their own, and
   * runTest would wait for them until it times out.
   */
  private fun runLocationTest(testBody: suspend TestScope.() -> Unit): TestResult = runTest {
    try {
      testBody()
    } finally {
      viewModelStore.clear()
      runCurrent()
    }
  }

  /** Creates a ViewModel owned by [viewModelStore], so that clearing the store clears it. */
  private fun createViewModel(hasPermission: Boolean): LocationViewModel {
    repository.hasPermission = hasPermission
    val factory =
        object : ViewModelProvider.Factory {
          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>): T =
              LocationViewModel(
                  tripId = TRIP_ID,
                  currentUserId = USER_ID,
                  locationRepository = repository,
                  connectivityRepository = connectivity,
                  clock = { now },
                  cleanupScope = CoroutineScope(testDispatcher),
              )
                  as T
        }
    return ViewModelProvider(viewModelStore, factory)[
        "viewModel${createdViewModels++}", LocationViewModel::class.java]
  }

  /** A permission result: whether it was granted, and whether the rationale should be shown. */
  private data class PermissionResult(val granted: Boolean, val shouldShowRationale: Boolean)

  /** Answers the permission requests in order and checks the state reached after each answer. */
  private fun LocationViewModel.assertTransitions(
      vararg steps: Pair<PermissionResult, LocationPermissionState>
  ) {
    steps.forEach { (result, expected) ->
      onPermissionResult(result.granted, result.shouldShowRationale)
      assertEquals(expected, uiState.value.permissionState)
    }
  }

  @Test
  fun initialState_withoutPermission_isNotRequestedAndNotSharing() = runLocationTest {
    val viewModel = createViewModel(hasPermission = false)
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals(NotRequested, state.permissionState)
    assertFalse(state.isSharing)
    assertNull(state.myLocation)
    assertFalse(state.isMyLocationStale)
    assertTrue(state.memberLocations.isEmpty())
    assertTrue(state.staleMemberIds.isEmpty())
    assertFalse(state.isOffline)
    assertNull(state.errorMessage)
  }

  @Test
  fun initialState_withPermission_isGrantedAndNotSharing() = runLocationTest {
    val viewModel = createViewModel(hasPermission = true)
    advanceUntilIdle()

    assertEquals(Granted, viewModel.uiState.value.permissionState)
    assertFalse(viewModel.uiState.value.isSharing)
    assertTrue(repository.startSharingCalls.isEmpty())
  }

  @Test
  fun notRequested_whenGranted_becomesGranted() = runLocationTest {
    createViewModel(hasPermission = false).assertTransitions(GRANTED to Granted)
  }

  @Test
  fun notRequested_whenDeniedWithRationale_becomesDenied() = runLocationTest {
    createViewModel(hasPermission = false).assertTransitions(DENIED_WITH_RATIONALE to Denied)
  }

  @Test
  fun notRequested_whenDeniedWithoutRationale_becomesRevoked() = runLocationTest {
    createViewModel(hasPermission = false).assertTransitions(DENIED_WITHOUT_RATIONALE to Revoked)
  }

  @Test
  fun granted_whenGrantedAgain_staysGranted() = runLocationTest {
    createViewModel(hasPermission = true).assertTransitions(GRANTED to Granted)
  }

  @Test
  fun granted_whenDeniedWithRationale_becomesDenied() = runLocationTest {
    createViewModel(hasPermission = true).assertTransitions(DENIED_WITH_RATIONALE to Denied)
  }

  @Test
  fun granted_whenDeniedWithoutRationale_becomesRevoked() = runLocationTest {
    createViewModel(hasPermission = true).assertTransitions(DENIED_WITHOUT_RATIONALE to Revoked)
  }

  @Test
  fun denied_whenGranted_becomesGranted() = runLocationTest {
    createViewModel(hasPermission = false)
        .assertTransitions(DENIED_WITH_RATIONALE to Denied, GRANTED to Granted)
  }

  @Test
  fun denied_whenDeniedAgainWithoutRationale_becomesRevoked() = runLocationTest {
    createViewModel(hasPermission = false)
        .assertTransitions(DENIED_WITH_RATIONALE to Denied, DENIED_WITHOUT_RATIONALE to Revoked)
  }

  @Test
  fun revoked_whenGranted_becomesGranted() = runLocationTest {
    createViewModel(hasPermission = false)
        .assertTransitions(DENIED_WITHOUT_RATIONALE to Revoked, GRANTED to Granted)
  }

  @Test
  fun revoked_whenDeniedAgainWithoutRationale_staysRevoked() = runLocationTest {
    createViewModel(hasPermission = false)
        .assertTransitions(DENIED_WITHOUT_RATIONALE to Revoked, DENIED_WITHOUT_RATIONALE to Revoked)
  }

  @Test
  fun grantingPermission_doesNotStartSharing() = runLocationTest {
    val viewModel = createViewModel(hasPermission = false)

    repository.hasPermission = true
    viewModel.onPermissionResult(granted = true, shouldShowRationale = false)
    advanceUntilIdle()

    assertEquals(Granted, viewModel.uiState.value.permissionState)
    assertFalse(viewModel.uiState.value.isSharing)
    assertTrue(repository.startSharingCalls.isEmpty())
  }

  @Test
  fun permissionChanges_keepTheRestOfTheState() = runLocationTest {
    val viewModel = createViewModel(hasPermission = false)
    val initial = viewModel.uiState.value

    viewModel.onPermissionResult(granted = true, shouldShowRationale = false)
    viewModel.onPermissionResult(granted = false, shouldShowRationale = false)
    advanceUntilIdle()

    assertEquals(initial.copy(permissionState = Revoked), viewModel.uiState.value)
  }

  @Test
  fun factory_createsLocationViewModel() {
    val viewModel =
        LocationViewModel.factory(TRIP_ID, USER_ID, repository, connectivity)
            .create(LocationViewModel::class.java)

    assertEquals(NotRequested, viewModel.uiState.value.permissionState)
  }

  @Test
  fun factory_rejectsOtherViewModelClasses() {
    val factory = LocationViewModel.factory(TRIP_ID, USER_ID, repository, connectivity)

    assertThrows(IllegalArgumentException::class.java) {
      factory.create(OtherViewModel::class.java)
    }
  }

  @Test
  fun startSharing_withPermission_sharesAndShowsMyLocation() = runLocationTest {
    repository.currentLocation = LAUSANNE
    val viewModel = createViewModel(hasPermission = true)

    viewModel.startSharing()
    runCurrent()

    val state = viewModel.uiState.value
    assertTrue(state.isSharing)
    assertNull(state.errorMessage)
    assertEquals(listOf(TRIP_ID), repository.startSharingCalls)
    assertEquals(MemberLocation(USER_ID, 46.52, 6.63, LAUSANNE_MILLIS), state.myLocation)
  }

  @Test
  fun stopSharing_stopsSharingAndRefreshingMyLocation() = runLocationTest {
    repository.currentLocation = LAUSANNE
    val viewModel = createViewModel(hasPermission = true)
    viewModel.startSharing()
    runCurrent()

    viewModel.stopSharing()
    val callsWhenStopped = repository.getCurrentLocationCalls
    advanceTimeBy(interval * 3)
    runCurrent()

    assertFalse(viewModel.uiState.value.isSharing)
    assertEquals(listOf(TRIP_ID), repository.stopSharingCalls)
    assertEquals(callsWhenStopped, repository.getCurrentLocationCalls)
  }

  @Test
  fun stopSharing_beforeFirstPublish_cancelsPendingStartWithoutWaiting() = runLocationTest {
    repository.currentLocation = LAUSANNE
    // Never completed, like a start still waiting for its first GPS fix or database update.
    repository.startSharingGate = CompletableDeferred()
    val viewModel = createViewModel(hasPermission = true)
    viewModel.startSharing()
    runCurrent()
    assertEquals(listOf(TRIP_ID), repository.startSharingCalls)

    viewModel.stopSharing()
    runCurrent()

    assertEquals(listOf(TRIP_ID), repository.stopSharingCalls)
    assertEquals(1, repository.cancelledStartSharingCalls)
    assertTrue(repository.sharedTripIds.isEmpty())
    assertFalse(viewModel.uiState.value.isSharing)
    assertEquals(0, repository.getCurrentLocationCalls)
  }

  @Test
  fun myLocation_whileSharing_refreshesAtTheInterval() = runLocationTest {
    repository.currentLocation = LAUSANNE
    val viewModel = createViewModel(hasPermission = true)
    viewModel.startSharing()
    runCurrent()

    repository.currentLocation = GENEVA
    advanceTimeBy(interval)
    runCurrent()

    assertEquals(2, repository.getCurrentLocationCalls)
    assertEquals(
        MemberLocation(USER_ID, 46.2, 6.14, NOW_MILLIS),
        viewModel.uiState.value.myLocation,
    )
  }

  @Test
  fun myLocation_whenGpsReturnsNull_keepsLastKnownPosition() = runLocationTest {
    repository.currentLocation = LAUSANNE
    val viewModel = createViewModel(hasPermission = true)
    viewModel.startSharing()
    runCurrent()
    val lastKnown = viewModel.uiState.value.myLocation

    repository.currentLocation = null
    advanceTimeBy(interval * 2)
    runCurrent()

    assertEquals(3, repository.getCurrentLocationCalls)
    assertEquals(lastKnown, viewModel.uiState.value.myLocation)
    assertTrue(viewModel.uiState.value.isSharing)
  }

  @Test
  fun myLocation_withoutGpsLongerThanStaleAfter_isMarkedStaleThenRecovers() = runLocationTest {
    repository.currentLocation = GENEVA
    val viewModel = createViewModel(hasPermission = true)
    viewModel.startSharing()
    runCurrent()
    assertFalse(viewModel.uiState.value.isMyLocationStale)

    repository.currentLocation = null
    now += (LocationViewModel.STALE_AFTER + 1.milliseconds).inWholeMilliseconds
    advanceTimeBy(interval)
    runCurrent()

    assertEquals(
        MemberLocation(USER_ID, 46.2, 6.14, NOW_MILLIS),
        viewModel.uiState.value.myLocation,
    )
    assertTrue(viewModel.uiState.value.isMyLocationStale)

    repository.currentLocation = GENEVA
    advanceTimeBy(interval)
    runCurrent()

    assertEquals(now, viewModel.uiState.value.myLocation?.updatedAt)
    assertFalse(viewModel.uiState.value.isMyLocationStale)
  }

  @Test
  fun sharingOff_collectsNoLocation() = runLocationTest {
    repository.currentLocation = LAUSANNE
    val viewModel = createViewModel(hasPermission = true)

    advanceTimeBy(interval * 5)
    runCurrent()

    assertEquals(0, repository.getCurrentLocationCalls)
    assertNull(viewModel.uiState.value.myLocation)
    assertFalse(viewModel.uiState.value.isSharing)
  }

  @Test
  fun startSharing_withoutGrantedPermission_setsErrorAndDoesNotShare() = runLocationTest {
    val notRequested = createViewModel(hasPermission = false)
    val denied =
        createViewModel(hasPermission = false).apply {
          onPermissionResult(granted = false, shouldShowRationale = true)
        }
    val revoked =
        createViewModel(hasPermission = true).apply {
          onPermissionResult(granted = false, shouldShowRationale = false)
        }

    listOf(notRequested, denied, revoked).forEach { viewModel ->
      viewModel.startSharing()
      advanceUntilIdle()

      assertEquals(
          LocationViewModel.PERMISSION_REQUIRED_MESSAGE,
          viewModel.uiState.value.errorMessage,
      )
      assertFalse(viewModel.uiState.value.isSharing)
    }
    assertTrue(repository.startSharingCalls.isEmpty())
    assertEquals(0, repository.getCurrentLocationCalls)
  }

  @Test
  fun startSharing_whenRepositoryFails_showsItsMessage() = runLocationTest {
    repository.startSharingError = LocationSharingException("Could not publish the location")
    val viewModel = createViewModel(hasPermission = true)

    viewModel.startSharing()
    advanceUntilIdle()

    assertEquals("Could not publish the location", viewModel.uiState.value.errorMessage)
    assertFalse(viewModel.uiState.value.isSharing)
    assertEquals(0, repository.getCurrentLocationCalls)
  }

  @Test
  fun startSharing_whenRepositoryLacksPermission_showsErrorWithoutThrowing() = runLocationTest {
    val viewModel = createViewModel(hasPermission = false)
    // The UI saw a grant, but the system permission was withdrawn before sharing started.
    viewModel.onPermissionResult(granted = true, shouldShowRationale = false)

    viewModel.startSharing()
    advanceUntilIdle()

    assertEquals("Location permission is missing.", viewModel.uiState.value.errorMessage)
    assertFalse(viewModel.uiState.value.isSharing)
  }

  @Test
  fun stopSharing_whenRepositoryFails_showsItsMessageAndStopsRefreshing() = runLocationTest {
    repository.currentLocation = LAUSANNE
    repository.stopSharingError = LocationSharingException("Could not remove the location")
    val viewModel = createViewModel(hasPermission = true)
    viewModel.startSharing()
    runCurrent()

    viewModel.stopSharing()
    val callsWhenStopped = repository.getCurrentLocationCalls
    advanceTimeBy(interval * 3)
    runCurrent()

    assertEquals("Could not remove the location", viewModel.uiState.value.errorMessage)
    assertEquals(callsWhenStopped, repository.getCurrentLocationCalls)
  }

  @Test
  fun successfulRequest_clearsPreviousError() = runLocationTest {
    repository.currentLocation = LAUSANNE
    val viewModel = createViewModel(hasPermission = false)
    viewModel.startSharing()

    repository.hasPermission = true
    viewModel.onPermissionResult(granted = true, shouldShowRationale = false)
    viewModel.startSharing()
    runCurrent()

    assertNull(viewModel.uiState.value.errorMessage)
    assertTrue(viewModel.uiState.value.isSharing)
  }

  @Test
  fun isSharing_mirrorsSharingStartedOutsideTheViewModel() = runLocationTest {
    repository.currentLocation = LAUSANNE
    val viewModel = createViewModel(hasPermission = true)
    runCurrent()

    // Like a sharing resumed by restoreSharing after the app process was killed.
    repository.startSharing(TRIP_ID)
    runCurrent()

    assertTrue(viewModel.uiState.value.isSharing)
    assertEquals(1, repository.getCurrentLocationCalls)
  }

  @Test
  fun onLeaveTrip_stopsSharing() = runLocationTest {
    repository.currentLocation = LAUSANNE
    val viewModel = createViewModel(hasPermission = true)
    viewModel.startSharing()
    runCurrent()

    viewModel.onLeaveTrip()
    runCurrent()

    assertFalse(viewModel.uiState.value.isSharing)
    assertEquals(listOf(TRIP_ID), repository.stopSharingCalls)
  }

  @Test
  fun onLeaveTrip_clearsShownLocationsAtOnce() = runLocationTest {
    repository.currentLocation = LAUSANNE
    val staleAlice =
        ALICE.copy(
            updatedAt =
                NOW_MILLIS - (LocationViewModel.STALE_AFTER + 1.milliseconds).inWholeMilliseconds
        )
    repository.memberLocations.value = listOf(staleAlice, BOB)
    val viewModel = createViewModel(hasPermission = true)
    viewModel.startSharing()
    runCurrent()
    assertEquals(listOf(staleAlice, BOB), viewModel.uiState.value.memberLocations)
    assertEquals(setOf("alice"), viewModel.uiState.value.staleMemberIds)
    assertEquals(LAUSANNE_MILLIS, viewModel.uiState.value.myLocation?.updatedAt)

    viewModel.onLeaveTrip()

    val state = viewModel.uiState.value
    assertTrue(state.memberLocations.isEmpty())
    assertTrue(state.staleMemberIds.isEmpty())
    assertNull(state.myLocation)
  }

  @Test
  fun afterLeavingTrip_locationsAreNotShownAgain() = runLocationTest {
    repository.currentLocation = LAUSANNE
    repository.memberLocations.value = listOf(ALICE)
    val viewModel = createViewModel(hasPermission = true)
    viewModel.startSharing()
    runCurrent()
    viewModel.onLeaveTrip()
    runCurrent()
    val observationsWhenLeft = repository.observeMemberLocationsCalls

    repository.memberLocations.value = listOf(ALICE.copy(latitude = 46.53), BOB)
    runCurrent()
    connectivity.online.value = false
    runCurrent()
    connectivity.online.value = true
    viewModel.onPermissionResult(granted = true, shouldShowRationale = false)
    advanceTimeBy(interval * 3)
    runCurrent()

    val state = viewModel.uiState.value
    assertTrue(state.memberLocations.isEmpty())
    assertTrue(state.staleMemberIds.isEmpty())
    assertNull(state.myLocation)
    assertEquals(observationsWhenLeft, repository.observeMemberLocationsCalls)
  }

  @Test
  fun startSharing_afterLeavingTrip_doesNotCallTheRepository() = runLocationTest {
    repository.currentLocation = LAUSANNE
    val viewModel = createViewModel(hasPermission = true)
    viewModel.onLeaveTrip()
    runCurrent()

    viewModel.startSharing()
    runCurrent()

    assertTrue(repository.startSharingCalls.isEmpty())
    assertFalse(viewModel.uiState.value.isSharing)
    assertEquals(0, repository.getCurrentLocationCalls)
  }

  @Test
  fun onLeaveTrip_calledTwice_stopsSharingOnce() = runLocationTest {
    repository.currentLocation = LAUSANNE
    val viewModel = createViewModel(hasPermission = true)
    viewModel.startSharing()
    runCurrent()

    viewModel.onLeaveTrip()
    viewModel.onLeaveTrip()
    runCurrent()
    viewModelStore.clear()
    runCurrent()

    assertFalse(viewModel.uiState.value.isSharing)
    assertEquals(listOf(TRIP_ID), repository.stopSharingCalls)
  }

  @Test
  fun onCleared_whileStopAfterLeavingTripIsPending_stopsSharing() = runLocationTest {
    repository.currentLocation = LAUSANNE
    // Never completed, like a start still waiting for its first update when the user leaves.
    repository.startSharingGate = CompletableDeferred()
    val viewModel = createViewModel(hasPermission = true)
    viewModel.startSharing()
    runCurrent()

    viewModel.onLeaveTrip()
    viewModelStore.clear()
    runCurrent()

    assertEquals(TRIP_ID, repository.stopSharingCalls.lastOrNull())
    assertTrue(repository.sharedTripIds.isEmpty())
  }

  @Test
  fun onCleared_whileSharing_stopsSharingAndCollection() = runLocationTest {
    repository.currentLocation = LAUSANNE
    val viewModel = createViewModel(hasPermission = true)
    viewModel.startSharing()
    runCurrent()

    viewModelStore.clear()
    val callsWhenCleared = repository.getCurrentLocationCalls
    advanceTimeBy(interval * 3)
    runCurrent()

    assertEquals(listOf(TRIP_ID), repository.stopSharingCalls)
    assertEquals(callsWhenCleared, repository.getCurrentLocationCalls)
  }

  @Test
  fun onCleared_whileNotSharing_doesNotCallTheRepository() = runLocationTest {
    createViewModel(hasPermission = true)
    runCurrent()

    viewModelStore.clear()
    advanceUntilIdle()

    assertTrue(repository.stopSharingCalls.isEmpty())
  }

  @Test
  fun memberLocations_followTheRepositoryInRealTime() = runLocationTest {
    val viewModel = createViewModel(hasPermission = true)

    repository.memberLocations.value = listOf(ALICE)
    runCurrent()
    assertEquals(listOf(ALICE), viewModel.uiState.value.memberLocations)

    val movedAlice = ALICE.copy(latitude = 46.53)
    repository.memberLocations.value = listOf(movedAlice, BOB)
    runCurrent()
    assertEquals(listOf(movedAlice, BOB), viewModel.uiState.value.memberLocations)

    // Alice stopped sharing.
    repository.memberLocations.value = listOf(BOB)
    runCurrent()
    assertEquals(listOf(BOB), viewModel.uiState.value.memberLocations)
    assertTrue(viewModel.uiState.value.staleMemberIds.isEmpty())
  }

  @Test
  fun memberLocation_atStaleAfter_isNotStaleYet() = runLocationTest {
    val viewModel = createViewModel(hasPermission = true)
    repository.memberLocations.value = listOf(ALICE)
    runCurrent()

    now += LocationViewModel.STALE_AFTER.inWholeMilliseconds
    advanceTimeBy(staleCheckInterval)
    runCurrent()

    assertTrue(viewModel.uiState.value.staleMemberIds.isEmpty())
  }

  @Test
  fun memberLocation_olderThanStaleAfter_becomesStaleWithoutNewEmission() = runLocationTest {
    val viewModel = createViewModel(hasPermission = true)
    repository.memberLocations.value = listOf(ALICE, BOB.copy(updatedAt = NOW_MILLIS + 30_000))
    runCurrent()

    now += (LocationViewModel.STALE_AFTER + 1.milliseconds).inWholeMilliseconds
    advanceTimeBy(staleCheckInterval)
    runCurrent()

    val state = viewModel.uiState.value
    assertEquals(setOf("alice"), state.staleMemberIds)
    assertEquals(listOf("alice", "bob"), state.memberLocations.map { it.userId })
  }

  @Test
  fun staleMember_whoUpdates_isNoLongerStale() = runLocationTest {
    val viewModel = createViewModel(hasPermission = true)
    repository.memberLocations.value = listOf(ALICE)
    runCurrent()
    now += (LocationViewModel.STALE_AFTER + 1.milliseconds).inWholeMilliseconds
    advanceTimeBy(staleCheckInterval)
    runCurrent()

    repository.memberLocations.value = listOf(ALICE.copy(updatedAt = now))
    runCurrent()

    assertTrue(viewModel.uiState.value.staleMemberIds.isEmpty())
    assertEquals(1, viewModel.uiState.value.memberLocations.size)
  }

  @Test
  fun memberLocation_atHideAfter_isStillShown() = runLocationTest {
    val viewModel = createViewModel(hasPermission = true)
    repository.memberLocations.value = listOf(ALICE)
    runCurrent()

    now += LocationViewModel.HIDE_AFTER.inWholeMilliseconds
    advanceTimeBy(staleCheckInterval)
    runCurrent()

    assertEquals(listOf(ALICE), viewModel.uiState.value.memberLocations)
    assertEquals(setOf("alice"), viewModel.uiState.value.staleMemberIds)
  }

  @Test
  fun memberLocation_olderThanHideAfter_isHiddenWithoutNewEmission() = runLocationTest {
    val viewModel = createViewModel(hasPermission = true)
    repository.memberLocations.value = listOf(ALICE, BOB.copy(updatedAt = NOW_MILLIS + 30_000))
    runCurrent()

    now += (LocationViewModel.HIDE_AFTER + 1.milliseconds).inWholeMilliseconds
    advanceTimeBy(staleCheckInterval)
    runCurrent()

    val state = viewModel.uiState.value
    assertEquals(listOf("bob"), state.memberLocations.map { it.userId })
    assertEquals(setOf("bob"), state.staleMemberIds)
  }

  @Test
  fun memberLocation_alreadyTooOldWhenReceived_isNotShown() = runLocationTest {
    val viewModel = createViewModel(hasPermission = true)
    val expired = NOW_MILLIS - (LocationViewModel.HIDE_AFTER + 1.milliseconds).inWholeMilliseconds

    repository.memberLocations.value = listOf(ALICE.copy(updatedAt = expired), BOB)
    runCurrent()

    assertEquals(listOf(BOB), viewModel.uiState.value.memberLocations)
  }

  @Test
  fun memberLocations_areShownWhatever_thePermissionState() = runLocationTest {
    val notRequested = createViewModel(hasPermission = false)
    val denied =
        createViewModel(hasPermission = false).apply {
          onPermissionResult(granted = false, shouldShowRationale = true)
        }
    val revoked =
        createViewModel(hasPermission = true).apply {
          onPermissionResult(granted = false, shouldShowRationale = false)
        }

    repository.memberLocations.value = listOf(ALICE, BOB)
    runCurrent()

    listOf(notRequested, denied, revoked).forEach { viewModel ->
      assertEquals(listOf(ALICE, BOB), viewModel.uiState.value.memberLocations)
      assertNull(viewModel.uiState.value.errorMessage)
    }
    assertEquals(Denied, denied.uiState.value.permissionState)
    assertEquals(Revoked, revoked.uiState.value.permissionState)
  }

  @Test
  fun memberLocations_whenRepositoryFails_showsItsMessage() = runLocationTest {
    repository.observeMemberLocationsError =
        LocationSharingException("Could not read the member locations")

    val viewModel = createViewModel(hasPermission = true)
    advanceUntilIdle()

    assertEquals("Could not read the member locations", viewModel.uiState.value.errorMessage)
    assertTrue(viewModel.uiState.value.memberLocations.isEmpty())
  }

  @Test
  fun memberLocations_afterObservationFails_keepAgingUntilHidden() = runLocationTest {
    val viewModel = createViewModel(hasPermission = true)
    repository.memberLocations.value = listOf(ALICE)
    runCurrent()

    repository.failMemberLocations(LocationSharingException("Could not read the trip locations"))
    runCurrent()
    assertEquals("Could not read the trip locations", viewModel.uiState.value.errorMessage)
    assertEquals(listOf(ALICE), viewModel.uiState.value.memberLocations)
    assertTrue(viewModel.uiState.value.staleMemberIds.isEmpty())

    now += (LocationViewModel.STALE_AFTER + 1.milliseconds).inWholeMilliseconds
    advanceTimeBy(staleCheckInterval)
    runCurrent()
    assertEquals(listOf(ALICE), viewModel.uiState.value.memberLocations)
    assertEquals(setOf("alice"), viewModel.uiState.value.staleMemberIds)

    now = NOW_MILLIS + (LocationViewModel.HIDE_AFTER + 1.milliseconds).inWholeMilliseconds
    advanceTimeBy(staleCheckInterval)
    runCurrent()
    assertTrue(viewModel.uiState.value.memberLocations.isEmpty())
    assertTrue(viewModel.uiState.value.staleMemberIds.isEmpty())
  }

  @Test
  fun reconnecting_afterAFailedObservation_clearsTheObservationError() = runLocationTest {
    repository.observeMemberLocationsError = LocationSharingException("Network error")
    val viewModel = createViewModel(hasPermission = true)
    runCurrent()
    assertEquals("Network error", viewModel.uiState.value.observationErrorMessage)
    connectivity.online.value = false
    runCurrent()

    repository.observeMemberLocationsError = null
    repository.memberLocations.value = listOf(ALICE)
    connectivity.online.value = true
    runCurrent()

    assertEquals(listOf(ALICE), viewModel.uiState.value.memberLocations)
    assertNull(viewModel.uiState.value.observationErrorMessage)
    assertNull(viewModel.uiState.value.errorMessage)
  }

  @Test
  fun memberLocations_whenReadAfterASharingError_keepTheSharingError() = runLocationTest {
    repository.startSharingError = LocationSharingException("Could not publish the location")
    val viewModel = createViewModel(hasPermission = true)
    viewModel.startSharing()
    runCurrent()

    repository.memberLocations.value = listOf(ALICE)
    runCurrent()

    assertEquals(listOf(ALICE), viewModel.uiState.value.memberLocations)
    assertEquals("Could not publish the location", viewModel.uiState.value.sharingErrorMessage)
    assertEquals("Could not publish the location", viewModel.uiState.value.errorMessage)
  }

  @Test
  fun goingOffline_keepsMemberLocationsAndMarksThemStale() = runLocationTest {
    val viewModel = createViewModel(hasPermission = true)
    repository.memberLocations.value = listOf(ALICE, BOB)
    runCurrent()

    connectivity.online.value = false
    runCurrent()

    val state = viewModel.uiState.value
    assertTrue(state.isOffline)
    assertEquals(listOf(ALICE, BOB), state.memberLocations)
    assertEquals(setOf("alice", "bob"), state.staleMemberIds)
  }

  @Test
  fun whileOffline_memberLocationsAreNotHidden() = runLocationTest {
    val viewModel = createViewModel(hasPermission = true)
    repository.memberLocations.value = listOf(ALICE)
    runCurrent()
    connectivity.online.value = false
    runCurrent()

    now += (LocationViewModel.HIDE_AFTER + 1.milliseconds).inWholeMilliseconds
    advanceTimeBy(staleCheckInterval * 3)
    runCurrent()

    assertEquals(listOf(ALICE), viewModel.uiState.value.memberLocations)
    assertEquals(setOf("alice"), viewModel.uiState.value.staleMemberIds)
  }

  @Test
  fun reconnecting_resumesMemberLocationsWithoutRefresh() = runLocationTest {
    val viewModel = createViewModel(hasPermission = true)
    repository.memberLocations.value = listOf(ALICE, BOB)
    runCurrent()
    connectivity.online.value = false
    runCurrent()

    now += (LocationViewModel.HIDE_AFTER + 1.milliseconds).inWholeMilliseconds
    val movedAlice = ALICE.copy(latitude = 46.53, updatedAt = now)
    repository.memberLocations.value = listOf(movedAlice, BOB)
    connectivity.online.value = true
    runCurrent()

    val state = viewModel.uiState.value
    assertFalse(state.isOffline)
    // Bob's location expired while offline.
    assertEquals(listOf(movedAlice), state.memberLocations)
    assertTrue(state.staleMemberIds.isEmpty())
  }

  @Test
  fun reconnecting_afterAFailedObservation_resumesMemberLocations() = runLocationTest {
    repository.observeMemberLocationsError = LocationSharingException("Network error")
    val viewModel = createViewModel(hasPermission = true)
    runCurrent()
    connectivity.online.value = false
    runCurrent()

    repository.observeMemberLocationsError = null
    repository.memberLocations.value = listOf(ALICE)
    connectivity.online.value = true
    runCurrent()

    assertEquals(listOf(ALICE), viewModel.uiState.value.memberLocations)
  }

  @Test
  fun startingOffline_isOfflineWithoutMemberLocations() = runLocationTest {
    connectivity.online.value = false
    repository.memberLocations.value = listOf(ALICE)

    val viewModel = createViewModel(hasPermission = true)
    runCurrent()

    assertTrue(viewModel.uiState.value.isOffline)
    assertTrue(viewModel.uiState.value.memberLocations.isEmpty())
  }

  @Test
  fun sharing_continuesWhileOfflineAndAfterReconnecting() = runLocationTest {
    repository.currentLocation = LAUSANNE
    val viewModel = createViewModel(hasPermission = true)
    viewModel.startSharing()
    runCurrent()

    connectivity.online.value = false
    advanceTimeBy(interval)
    runCurrent()

    assertTrue(viewModel.uiState.value.isSharing)
    // The position still comes from GPS, which needs no network.
    assertEquals(2, repository.getCurrentLocationCalls)

    connectivity.online.value = true
    runCurrent()

    assertTrue(viewModel.uiState.value.isSharing)
    assertFalse(viewModel.uiState.value.isOffline)
    assertEquals(listOf(TRIP_ID), repository.startSharingCalls)
    assertTrue(repository.stopSharingCalls.isEmpty())
  }

  private class OtherViewModel : ViewModel()

  private companion object {
    const val TRIP_ID = "trip-1"
    const val USER_ID = "user-1"
    const val NOW_MILLIS = 1_700_000_000_000L
    const val LAUSANNE_MILLIS = 1_699_999_990_000L

    val LAUSANNE =
        DeviceLocation(latitude = 46.52, longitude = 6.63, timestampMillis = LAUSANNE_MILLIS)
    val ALICE = MemberLocation("alice", 46.52, 6.57, NOW_MILLIS)
    val BOB = MemberLocation("bob", 46.95, 7.44, NOW_MILLIS)
    val GENEVA = DeviceLocation(latitude = 46.2, longitude = 6.14)

    val GRANTED = PermissionResult(granted = true, shouldShowRationale = false)
    val DENIED_WITH_RATIONALE = PermissionResult(granted = false, shouldShowRationale = true)
    val DENIED_WITHOUT_RATIONALE = PermissionResult(granted = false, shouldShowRationale = false)
  }
}
