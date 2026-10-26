// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.swent.roamigo.model.location.DeviceLocation
import com.swent.roamigo.model.location.FakeLocationRepository
import com.swent.roamigo.model.location.LocationSharingException
import com.swent.roamigo.model.location.MemberLocation
import com.swent.roamigo.ui.map.LocationPermissionState.Denied
import com.swent.roamigo.ui.map.LocationPermissionState.Granted
import com.swent.roamigo.ui.map.LocationPermissionState.NotRequested
import com.swent.roamigo.ui.map.LocationPermissionState.Revoked
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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

  private val clock = Clock.fixed(Instant.ofEpochMilli(NOW_MILLIS), ZoneOffset.UTC)

  private val interval = LocationViewModel.MY_LOCATION_REFRESH_INTERVAL

  private lateinit var repository: FakeLocationRepository

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    repository = FakeLocationRepository()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun createViewModel(hasPermission: Boolean): LocationViewModel {
    repository.hasPermission = hasPermission
    return LocationViewModel(
        tripId = TRIP_ID,
        currentUserId = USER_ID,
        locationRepository = repository,
        clock = clock,
        cleanupScope = CoroutineScope(testDispatcher),
    )
  }

  /** Answers the permission requests in order and checks the state reached after each answer. */
  private fun LocationViewModel.assertTransitions(
      vararg steps: Pair<Boolean, LocationPermissionState>
  ) {
    steps.forEach { (granted, expected) ->
      onPermissionResult(granted)
      assertEquals(expected, uiState.value.permissionState)
    }
  }

  @Test
  fun initialState_withoutPermission_isNotRequestedAndNotSharing() = runTest {
    val viewModel = createViewModel(hasPermission = false)
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals(NotRequested, state.permissionState)
    assertFalse(state.isSharing)
    assertNull(state.myLocation)
    assertTrue(state.memberLocations.isEmpty())
    assertTrue(state.staleMemberIds.isEmpty())
    assertFalse(state.isOffline)
    assertNull(state.errorMessage)
  }

  @Test
  fun initialState_withPermission_isGrantedAndNotSharing() = runTest {
    val viewModel = createViewModel(hasPermission = true)
    advanceUntilIdle()

    assertEquals(Granted, viewModel.uiState.value.permissionState)
    assertFalse(viewModel.uiState.value.isSharing)
    assertTrue(repository.startSharingCalls.isEmpty())
  }

  @Test
  fun notRequested_whenGranted_becomesGranted() = runTest {
    createViewModel(hasPermission = false).assertTransitions(true to Granted)
  }

  @Test
  fun notRequested_whenDenied_becomesDenied() = runTest {
    createViewModel(hasPermission = false).assertTransitions(false to Denied)
  }

  @Test
  fun granted_whenGrantedAgain_staysGranted() = runTest {
    createViewModel(hasPermission = true).assertTransitions(true to Granted)
  }

  @Test
  fun granted_whenDenied_becomesRevoked() = runTest {
    createViewModel(hasPermission = true).assertTransitions(false to Revoked)
  }

  @Test
  fun granted_fromRequest_whenDenied_becomesRevoked() = runTest {
    createViewModel(hasPermission = false).assertTransitions(true to Granted, false to Revoked)
  }

  @Test
  fun denied_whenGranted_becomesGranted() = runTest {
    createViewModel(hasPermission = false).assertTransitions(false to Denied, true to Granted)
  }

  @Test
  fun denied_whenDeniedAgain_staysDenied() = runTest {
    createViewModel(hasPermission = false).assertTransitions(false to Denied, false to Denied)
  }

  @Test
  fun revoked_whenGranted_becomesGranted() = runTest {
    createViewModel(hasPermission = true).assertTransitions(false to Revoked, true to Granted)
  }

  @Test
  fun revoked_whenDeniedAgain_staysRevoked() = runTest {
    createViewModel(hasPermission = true).assertTransitions(false to Revoked, false to Revoked)
  }

  @Test
  fun grantingPermission_doesNotStartSharing() = runTest {
    val viewModel = createViewModel(hasPermission = false)

    repository.hasPermission = true
    viewModel.onPermissionResult(granted = true)
    advanceUntilIdle()

    assertEquals(Granted, viewModel.uiState.value.permissionState)
    assertFalse(viewModel.uiState.value.isSharing)
    assertTrue(repository.startSharingCalls.isEmpty())
  }

  @Test
  fun permissionChanges_keepTheRestOfTheState() = runTest {
    val viewModel = createViewModel(hasPermission = false)
    val initial = viewModel.uiState.value

    viewModel.onPermissionResult(granted = true)
    viewModel.onPermissionResult(granted = false)
    advanceUntilIdle()

    assertEquals(initial.copy(permissionState = Revoked), viewModel.uiState.value)
  }

  @Test
  fun factory_createsLocationViewModel() {
    val viewModel =
        LocationViewModel.factory(TRIP_ID, USER_ID, repository)
            .create(LocationViewModel::class.java)

    assertEquals(NotRequested, viewModel.uiState.value.permissionState)
  }

  @Test
  fun factory_rejectsOtherViewModelClasses() {
    val factory = LocationViewModel.factory(TRIP_ID, USER_ID, repository)

    assertThrows(IllegalArgumentException::class.java) {
      factory.create(OtherViewModel::class.java)
    }
  }

  @Test
  fun startSharing_withPermission_sharesAndShowsMyLocation() = runTest {
    repository.currentLocation = LAUSANNE
    val viewModel = createViewModel(hasPermission = true)

    viewModel.startSharing()
    runCurrent()

    val state = viewModel.uiState.value
    assertTrue(state.isSharing)
    assertNull(state.errorMessage)
    assertEquals(listOf(TRIP_ID), repository.startSharingCalls)
    assertEquals(MemberLocation(USER_ID, 46.52, 6.63, LAUSANNE_MILLIS), state.myLocation)

    viewModel.stopSharing()
    runCurrent()
  }

  @Test
  fun stopSharing_stopsSharingAndRefreshingMyLocation() = runTest {
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
  fun myLocation_whileSharing_refreshesAtTheInterval() = runTest {
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

    viewModel.stopSharing()
    runCurrent()
  }

  @Test
  fun myLocation_whenGpsReturnsNull_keepsLastKnownPosition() = runTest {
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

    viewModel.stopSharing()
    runCurrent()
  }

  @Test
  fun sharingOff_collectsNoLocation() = runTest {
    repository.currentLocation = LAUSANNE
    val viewModel = createViewModel(hasPermission = true)

    advanceTimeBy(interval * 5)
    runCurrent()

    assertEquals(0, repository.getCurrentLocationCalls)
    assertNull(viewModel.uiState.value.myLocation)
    assertFalse(viewModel.uiState.value.isSharing)
  }

  @Test
  fun startSharing_withoutGrantedPermission_setsErrorAndDoesNotShare() = runTest {
    val notRequested = createViewModel(hasPermission = false)
    val denied = createViewModel(hasPermission = false).apply { onPermissionResult(false) }
    val revoked = createViewModel(hasPermission = true).apply { onPermissionResult(false) }

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
  fun startSharing_whenRepositoryFails_showsItsMessage() = runTest {
    repository.startSharingError = LocationSharingException("Could not publish the location")
    val viewModel = createViewModel(hasPermission = true)

    viewModel.startSharing()
    advanceUntilIdle()

    assertEquals("Could not publish the location", viewModel.uiState.value.errorMessage)
    assertFalse(viewModel.uiState.value.isSharing)
    assertEquals(0, repository.getCurrentLocationCalls)
  }

  @Test
  fun startSharing_whenRepositoryLacksPermission_showsErrorWithoutThrowing() = runTest {
    val viewModel = createViewModel(hasPermission = false)
    // The UI saw a grant, but the system permission was withdrawn before sharing started.
    viewModel.onPermissionResult(granted = true)

    viewModel.startSharing()
    advanceUntilIdle()

    assertEquals("Location permission is missing.", viewModel.uiState.value.errorMessage)
    assertFalse(viewModel.uiState.value.isSharing)
  }

  @Test
  fun stopSharing_whenRepositoryFails_showsItsMessageAndStopsRefreshing() = runTest {
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
  fun successfulRequest_clearsPreviousError() = runTest {
    repository.currentLocation = LAUSANNE
    val viewModel = createViewModel(hasPermission = false)
    viewModel.startSharing()

    repository.hasPermission = true
    viewModel.onPermissionResult(granted = true)
    viewModel.startSharing()
    runCurrent()

    assertNull(viewModel.uiState.value.errorMessage)
    assertTrue(viewModel.uiState.value.isSharing)

    viewModel.stopSharing()
    runCurrent()
  }

  @Test
  fun isSharing_mirrorsSharingStartedOutsideTheViewModel() = runTest {
    repository.currentLocation = LAUSANNE
    val viewModel = createViewModel(hasPermission = true)
    runCurrent()

    // Like a sharing resumed by restoreSharing after the app process was killed.
    repository.startSharing(TRIP_ID)
    runCurrent()

    assertTrue(viewModel.uiState.value.isSharing)
    assertEquals(1, repository.getCurrentLocationCalls)

    viewModel.stopSharing()
    runCurrent()
  }

  @Test
  fun onLeaveTrip_stopsSharing() = runTest {
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
  fun onCleared_whileSharing_stopsSharingAndCollection() = runTest {
    repository.currentLocation = LAUSANNE
    val store = ViewModelStore()
    val viewModel = createViewModelIn(store)
    viewModel.startSharing()
    runCurrent()

    store.clear()
    val callsWhenCleared = repository.getCurrentLocationCalls
    advanceTimeBy(interval * 3)
    runCurrent()

    assertEquals(listOf(TRIP_ID), repository.stopSharingCalls)
    assertEquals(callsWhenCleared, repository.getCurrentLocationCalls)
  }

  @Test
  fun onCleared_whileNotSharing_doesNotCallTheRepository() = runTest {
    val store = ViewModelStore()
    createViewModelIn(store)
    runCurrent()

    store.clear()
    advanceUntilIdle()

    assertTrue(repository.stopSharingCalls.isEmpty())
  }

  private fun createViewModelIn(store: ViewModelStore): LocationViewModel {
    val factory =
        object : ViewModelProvider.Factory {
          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>): T =
              createViewModel(hasPermission = true) as T
        }
    return ViewModelProvider(store, factory)[LocationViewModel::class.java]
  }

  private class OtherViewModel : ViewModel()

  private companion object {
    const val TRIP_ID = "trip-1"
    const val USER_ID = "user-1"
    const val NOW_MILLIS = 1_700_000_000_000L
    const val LAUSANNE_MILLIS = 1_699_999_990_000L

    val LAUSANNE =
        DeviceLocation(latitude = 46.52, longitude = 6.63, timestampMillis = LAUSANNE_MILLIS)
    val GENEVA = DeviceLocation(latitude = 46.2, longitude = 6.14)
  }
}
