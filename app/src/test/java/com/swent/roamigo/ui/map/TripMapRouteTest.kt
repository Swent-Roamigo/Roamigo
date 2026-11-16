// Implemented with assistance from OpenAI Codex.
package com.swent.roamigo.ui.map

import android.Manifest
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import com.google.firebase.Timestamp
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.connectivity.FakeConnectivityRepository
import com.swent.roamigo.model.location.FakeLocationRepository
import com.swent.roamigo.model.trip.FakeTripRepository
import com.swent.roamigo.model.trip.TravelMode
import com.swent.roamigo.model.trip.Trip
import com.swent.roamigo.model.trip.TripStatus
import com.swent.roamigo.model.trip.activity.FakeActivityRepository
import com.swent.roamigo.model.users.FakeUserRepository
import com.swent.roamigo.model.users.User
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.RoamigoTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercises route permission transitions with a preview map and controlled system responses. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TripMapRouteTest {
  @get:Rule val composeRule = createComposeRule()

  private val readOnly = mutableStateOf(false)
  private val locations = FakeLocationRepository()
  private val permissionChecker = TestPermissionChecker()
  private val registry = RecordingActivityResultRegistry()
  private val registryOwner =
      object : ActivityResultRegistryOwner {
        override val activityResultRegistry: ActivityResultRegistry = registry
      }
  private val trip =
      Trip(
          uid = "trip",
          name = "Lisbon",
          description = null,
          destinations = listOf(TripLocation(38.7223, -9.1393, "Lisbon")),
          startDate = Timestamp(0, 0),
          endDate = Timestamp(0, 0),
          flexibleDates = false,
          travelMode = TravelMode.GROUP,
          travelerCount = 1,
          ownerId = "member",
          memberIds = listOf("member"),
          status = TripStatus.PLANNED,
          createdAt = Timestamp(0, 0),
      )

  @Test
  fun becomingReadOnlyClearsTheRationaleWithoutRemovingTheMap() {
    setScreen()
    composeRule.onNodeWithTag(C.Tag.trip_map_sharing_button).performClick()
    composeRule.onNodeWithTag(C.Tag.trip_map_share_location_sheet).assertIsDisplayed()

    assertPermissionFlowClearedAcrossReadOnlyTransition()

    assertTrue(registry.launchedInputs.isEmpty())
    composeRule.onNodeWithTag(C.Tag.trip_map_sharing_button).performClick()
    composeRule.onNodeWithTag(C.Tag.trip_map_share_location_sheet).assertIsDisplayed()
  }

  @Test
  fun becomingReadOnlyClearsDeniedAndBlockedSheets() {
    setScreen()
    listOf(true, false).forEach { shouldShowRationale ->
      requestPermissionThroughRationale()
      answerPermissionRequest(granted = false, shouldShowRationale = shouldShowRationale)
      val sheetTag =
          if (shouldShowRationale) C.Tag.trip_map_location_denied_sheet
          else C.Tag.trip_map_location_blocked_sheet
      composeRule.onNodeWithTag(sheetTag).assertIsDisplayed()

      assertPermissionFlowClearedAcrossReadOnlyTransition()
    }
  }

  @Test
  fun permissionGrantedAfterBecomingReadOnlyDoesNotStartSharing() {
    setScreen()
    requestPermissionThroughRationale()
    composeRule.runOnIdle { readOnly.value = true }

    answerPermissionRequest(granted = true)

    assertNoPermissionSheet()
    composeRule.runOnIdle { readOnly.value = false }
    assertNoPermissionSheet()
    composeRule.runOnIdle { assertTrue(locations.startSharingCalls.isEmpty()) }
  }

  private fun assertPermissionFlowClearedAcrossReadOnlyTransition() {
    composeRule.runOnIdle { readOnly.value = true }
    composeRule.onNodeWithTag(C.Tag.trip_map_map).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trip_map_sharing_button).assertDoesNotExist()
    assertNoPermissionSheet()

    composeRule.runOnIdle { readOnly.value = false }
    composeRule.onNodeWithTag(C.Tag.trip_map_map).assertIsDisplayed()
    assertNoPermissionSheet()
    composeRule.runOnIdle { assertTrue(locations.startSharingCalls.isEmpty()) }
  }

  private fun requestPermissionThroughRationale() {
    composeRule.onNodeWithTag(C.Tag.trip_map_sharing_button).performClick()
    composeRule.onNodeWithTag(C.Tag.trip_map_share_location_continue).performClick()
  }

  private fun answerPermissionRequest(granted: Boolean, shouldShowRationale: Boolean = true) {
    composeRule.runOnIdle {
      permissionChecker.granted = granted
      permissionChecker.rationale = shouldShowRationale
      locations.hasPermission = granted
      registry.answerLastLaunch(
          mapOf(
              Manifest.permission.ACCESS_FINE_LOCATION to granted,
              Manifest.permission.ACCESS_COARSE_LOCATION to granted,
          )
      )
    }
  }

  private fun assertNoPermissionSheet() {
    composeRule.onNodeWithTag(C.Tag.trip_map_share_location_sheet).assertDoesNotExist()
    composeRule.onNodeWithTag(C.Tag.trip_map_location_denied_sheet).assertDoesNotExist()
    composeRule.onNodeWithTag(C.Tag.trip_map_location_blocked_sheet).assertDoesNotExist()
  }

  private fun setScreen() {
    val tripMapFactory =
        TripMapViewModel.factory(
            tripId = trip.uid,
            tripRepository = FakeTripRepository(trips = listOf(trip)),
            activityRepository = FakeActivityRepository(),
            userRepository = FakeUserRepository(User("member", "Member", null)),
        )
    val locationFactory =
        LocationViewModel.factory(
            tripId = trip.uid,
            currentUserId = "member",
            locationRepository = locations,
            connectivityRepository = FakeConnectivityRepository(),
        )
    composeRule.setContent {
      CompositionLocalProvider(
          LocalInspectionMode provides true,
          LocalActivityResultRegistryOwner provides registryOwner,
      ) {
        RoamigoTheme {
          TripMapRoute(
              tripId = trip.uid,
              tripMapViewModelFactory = tripMapFactory,
              locationViewModelFactory = locationFactory,
              permissionChecker = permissionChecker,
              startActivity = { error("Permission settings must not be opened") },
              readOnly = readOnly.value,
          )
        }
      }
    }
  }

  private class TestPermissionChecker : LocationPermissionChecker {
    var granted = false
    var rationale = true

    override fun isGranted(): Boolean = granted

    override fun shouldShowRationale(): Boolean = rationale
  }

  private class RecordingActivityResultRegistry : ActivityResultRegistry() {
    val launchedInputs = mutableListOf<Any?>()
    private var lastRequestCode: Int? = null

    override fun <I, O> onLaunch(
        requestCode: Int,
        contract: ActivityResultContract<I, O>,
        input: I,
        options: ActivityOptionsCompat?,
    ) {
      launchedInputs += input
      lastRequestCode = requestCode
    }

    fun answerLastLaunch(result: Map<String, Boolean>) {
      check(dispatchResult(checkNotNull(lastRequestCode), result)) { "No callback received it" }
    }
  }
}
