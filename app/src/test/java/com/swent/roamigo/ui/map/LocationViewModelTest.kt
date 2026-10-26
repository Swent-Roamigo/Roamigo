// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.map

import androidx.lifecycle.ViewModel
import com.swent.roamigo.model.location.FakeLocationRepository
import com.swent.roamigo.ui.map.LocationPermissionState.Denied
import com.swent.roamigo.ui.map.LocationPermissionState.Granted
import com.swent.roamigo.ui.map.LocationPermissionState.NotRequested
import com.swent.roamigo.ui.map.LocationPermissionState.Revoked
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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
    return LocationViewModel(tripId = TRIP_ID, locationRepository = repository)
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
        LocationViewModel.factory(TRIP_ID, repository).create(LocationViewModel::class.java)

    assertEquals(NotRequested, viewModel.uiState.value.permissionState)
  }

  @Test
  fun factory_rejectsOtherViewModelClasses() {
    val factory = LocationViewModel.factory(TRIP_ID, repository)

    assertThrows(IllegalArgumentException::class.java) {
      factory.create(OtherViewModel::class.java)
    }
  }

  private class OtherViewModel : ViewModel()

  private companion object {
    const val TRIP_ID = "trip-1"
  }
}
