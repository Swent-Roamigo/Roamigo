// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.map

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.Timestamp
import com.swent.roamigo.R
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.location.MemberLocation
import com.swent.roamigo.model.trip.TravelMode
import com.swent.roamigo.model.trip.Trip
import com.swent.roamigo.model.trip.TripStatus
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.RoamigoTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for the location layer of [TripMapScreen]: member markers, the user's own position, the
 * location error snackbar and the offline banner.
 *
 * The screen receives a fake [LocationUiState], passed the way [TripMapRoute] passes it, and a
 * fixed time, so no repository, network or clock is involved.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class TripMapLocationLayerTest {
  // Mapbox annotation cleanup must run immediately on the UI lifecycle thread.
  @get:Rule
  val composeRule =
      createAndroidComposeRule<ComponentActivity>(effectContext = UnconfinedTestDispatcher())

  private val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
  private val staleDescription = targetContext.getString(R.string.trip_map_member_location_stale)
  private val live = targetContext.getString(R.string.trip_map_freshness_live)

  private val trip =
      Trip(
          uid = TRIP_ID,
          name = "Lisbon",
          description = null,
          destinations =
              listOf(TripLocation(latitude = LATITUDE, longitude = LONGITUDE, name = "Lisbon")),
          startDate = Timestamp.now(),
          endDate = Timestamp.now(),
          flexibleDates = false,
          travelMode = TravelMode.GROUP,
          travelerCount = 4,
          ownerId = USER_ID,
          memberIds = listOf(USER_ID, "alex", "sam", "kim"),
          status = TripStatus.PLANNED,
          createdAt = Timestamp.now(),
      )

  private val tripState =
      TripMapUiState.Loaded(
          trip = trip,
          viewport = TripMapViewport.Centered(LATITUDE, LONGITUDE),
          members =
              mapOf(
                  "alex" to MemberDisplayInfo("Alex", "A", null),
                  "sam" to MemberDisplayInfo("Sam", "S", null),
                  "kim" to MemberDisplayInfo("Kim", "K", null),
              ),
      )

  private val locationState = mutableStateOf(LocationUiState())

  @Test
  fun noSharingMemberShowsNoMemberMarker() {
    setContent(LocationUiState(memberLocations = emptyList()))

    composeRule.onNodeWithTag(C.Tag.trip_map_map).assertIsDisplayed()
    composeRule
        .onAllNodes(hasTagStartingWith(C.Tag.trip_map_member_marker_prefix))
        .assertCountEquals(0)
  }

  @Test
  fun oneSharingMemberShowsTheirNameAndFreshness() {
    setContent(LocationUiState(memberLocations = listOf(memberLocation("alex"))))

    waitForMemberMarker("alex")
    composeRule.onNodeWithTag(memberMarkerTag("alex")).assertIsDisplayed().assert(isNotStale)
    composeRule
        .onNodeWithTag(C.Tag.trip_map_member_label_prefix + "alex", useUnmergedTree = true)
        .assertTextEquals(
            targetContext.getString(R.string.trip_map_member_marker_label, "Alex", live)
        )
    composeRule
        .onNodeWithTag(C.Tag.trip_map_member_initial_prefix + "alex", useUnmergedTree = true)
        .assertExists()
  }

  @Test
  fun manySharingMembersShowOneMarkerEach() {
    val ids = listOf("alex", "sam", "kim")
    setContent(
        LocationUiState(
            memberLocations = ids.mapIndexed { index, id -> memberLocation(id, offset = index) }
        )
    )

    ids.forEach { id ->
      waitForMemberMarker(id)
      composeRule.onNodeWithTag(memberMarkerTag(id)).assertIsDisplayed()
    }
    composeRule
        .onAllNodes(hasTagStartingWith(C.Tag.trip_map_member_marker_prefix))
        .assertCountEquals(ids.size)
  }

  @Test
  fun memberMarkersFollowStateChanges() {
    setContent(LocationUiState(memberLocations = listOf(memberLocation("alex"))))
    waitForMemberMarker("alex")

    composeRule.runOnIdle {
      locationState.value = LocationUiState(memberLocations = listOf(memberLocation("sam")))
    }

    waitForMemberMarker("sam")
    composeRule.waitUntil(timeoutMillis = MAP_TEST_TIMEOUT_MILLIS) {
      composeRule.onAllNodesWithTag(memberMarkerTag("alex")).fetchSemanticsNodes().isEmpty()
    }
  }

  @Test
  fun memberWithoutProfileIsShownWithTheFallbackName() {
    setContent(LocationUiState(memberLocations = listOf(memberLocation("zoe"))))

    waitForMemberMarker("zoe")
    composeRule
        .onNodeWithTag(C.Tag.trip_map_member_label_prefix + "zoe", useUnmergedTree = true)
        .assertTextEquals(targetContext.getString(R.string.trip_map_member_marker_label, "Z", live))
  }

  @Test
  fun freshnessUsesTheGivenTime() {
    setContent(
        LocationUiState(
            memberLocations = listOf(memberLocation("alex", updatedAt = NOW_MILLIS - 120_000))
        )
    )

    waitForMemberMarker("alex")
    val twoMinutesAgo =
        targetContext.resources.getQuantityString(R.plurals.trip_map_freshness_minutes_ago, 2, 2)
    composeRule
        .onNodeWithTag(C.Tag.trip_map_member_label_prefix + "alex", useUnmergedTree = true)
        .assertTextEquals(
            targetContext.getString(R.string.trip_map_member_marker_label, "Alex", twoMinutesAgo)
        )
  }

  @Test
  fun staleMemberIsMarkedStaleAndOthersAreNot() {
    setContent(
        LocationUiState(
            memberLocations = listOf(memberLocation("alex"), memberLocation("sam", offset = 1)),
            staleMemberIds = setOf("sam"),
        )
    )

    waitForMemberMarker("alex")
    waitForMemberMarker("sam")
    composeRule
        .onNodeWithTag(memberMarkerTag("sam"))
        .assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, staleDescription)
        )
    composeRule.onNodeWithTag(memberMarkerTag("alex")).assert(isNotStale)
  }

  @Test
  fun ownPositionIsShownWhileSharingAndHiddenOnceSharingStops() {
    val sharingState =
        LocationUiState(
            permissionState = LocationPermissionState.Granted,
            isSharing = true,
            myLocation = memberLocation(USER_ID),
        )
    setContent(sharingState)

    composeRule.waitUntil(timeoutMillis = MAP_TEST_TIMEOUT_MILLIS) {
      composeRule.onAllNodesWithTag(C.Tag.trip_map_my_location).fetchSemanticsNodes().isNotEmpty()
    }
    composeRule
        .onNodeWithTag(C.Tag.trip_map_my_location)
        .assertIsDisplayed()
        .assertContentDescriptionEquals(targetContext.getString(R.string.trip_map_my_location))

    // The ViewModel keeps the last position after sharing stops.
    composeRule.runOnIdle { locationState.value = sharingState.copy(isSharing = false) }

    composeRule.waitUntil(timeoutMillis = MAP_TEST_TIMEOUT_MILLIS) {
      composeRule.onAllNodesWithTag(C.Tag.trip_map_my_location).fetchSemanticsNodes().isEmpty()
    }
  }

  @Test
  fun ownPositionIsHiddenWhenPermissionIsDenied() {
    setContent(
        LocationUiState(
            permissionState = LocationPermissionState.Denied,
            isSharing = true,
            myLocation = memberLocation(USER_ID),
            memberLocations = listOf(memberLocation("alex")),
        )
    )

    // Waiting for the member marker shows that the annotations were laid out.
    waitForMemberMarker("alex")
    composeRule.onNodeWithTag(C.Tag.trip_map_my_location).assertDoesNotExist()
  }

  @Test
  fun memberMarkersStayVisibleWhileSharingIsOff() {
    setContent(
        LocationUiState(
            permissionState = LocationPermissionState.Granted,
            isSharing = false,
            memberLocations = listOf(memberLocation("alex")),
        )
    )

    waitForMemberMarker("alex")
    composeRule.onNodeWithTag(memberMarkerTag("alex")).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trip_map_my_location).assertDoesNotExist()
  }

  @Test
  fun memberMarkersStayVisibleWhenPermissionIsDeniedOrRevoked() {
    val deniedState =
        LocationUiState(
            permissionState = LocationPermissionState.Denied,
            memberLocations = listOf(memberLocation("alex")),
        )
    setContent(deniedState)
    waitForMemberMarker("alex")
    composeRule.onNodeWithTag(memberMarkerTag("alex")).assertIsDisplayed()

    composeRule.runOnIdle {
      locationState.value = deniedState.copy(permissionState = LocationPermissionState.Revoked)
    }
    composeRule.onNodeWithTag(memberMarkerTag("alex")).assertIsDisplayed()
  }

  @Test
  fun locationErrorIsShownInASnackbarWithoutHidingTheMap() {
    setContent(
        LocationUiState(
            sharingErrorMessage = "Backend detail that must not be shown",
            memberLocations = listOf(memberLocation("alex")),
        )
    )

    val message = targetContext.getString(R.string.trip_map_error_sharing_failed)
    waitForText(message)
    composeRule.onNodeWithTag(C.Tag.trip_map_location_error).assertIsDisplayed()
    composeRule.onAllNodesWithText("Backend detail that must not be shown").assertCountEquals(0)
    composeRule.onNodeWithTag(C.Tag.trip_map_map).assertIsDisplayed()
    waitForMemberMarker("alex")
    composeRule.onNodeWithTag(memberMarkerTag("alex")).assertIsDisplayed()
  }

  @Test
  fun eachErrorSourceShowsItsOwnMessage() {
    setContent(LocationUiState(observationErrorMessage = "read failed"))
    waitForText(targetContext.getString(R.string.trip_map_error_locations_unavailable))

    composeRule.runOnIdle {
      locationState.value =
          LocationUiState(sharingErrorMessage = LocationViewModel.PERMISSION_REQUIRED_MESSAGE)
    }
    waitForText(targetContext.getString(R.string.trip_map_error_permission_required))
  }

  @Test
  fun snackbarIsDismissedOnceTheErrorIsCleared() {
    setContent(LocationUiState(observationErrorMessage = "read failed"))
    val message = targetContext.getString(R.string.trip_map_error_locations_unavailable)
    waitForText(message)

    composeRule.runOnIdle { locationState.value = LocationUiState() }

    composeRule.waitUntil(timeoutMillis = MAP_TEST_TIMEOUT_MILLIS) {
      composeRule.onAllNodesWithText(message).fetchSemanticsNodes().isEmpty()
    }
  }

  @Test
  fun noSnackbarWithoutError() {
    setContent(LocationUiState())

    listOf(
            R.string.trip_map_error_permission_required,
            R.string.trip_map_error_sharing_failed,
            R.string.trip_map_error_locations_unavailable,
        )
        .forEach { message ->
          composeRule.onAllNodesWithText(targetContext.getString(message)).assertCountEquals(0)
        }
  }

  @Test
  fun offlineBannerIsShownOnlyWhileOffline() {
    setContent(LocationUiState(isOffline = false))
    composeRule.onNodeWithTag(C.Tag.trip_map_offline).assertDoesNotExist()

    composeRule.runOnIdle { locationState.value = LocationUiState(isOffline = true) }
    composeRule
        .onNodeWithTag(C.Tag.trip_map_offline)
        .assertIsDisplayed()
        .assert(hasAnyDescendant(hasText(targetContext.getString(R.string.trip_map_offline))))
    composeRule.onNodeWithTag(C.Tag.trip_map_map).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trip_map_sharing_button).assertIsDisplayed()

    composeRule.runOnIdle { locationState.value = LocationUiState(isOffline = false) }
    composeRule.onNodeWithTag(C.Tag.trip_map_offline).assertDoesNotExist()
  }

  // The state is applied once the local style is loaded, like in TripMapScreenTest: annotations
  // added before loadLocalMapStyle replaces the style are not displayed. Later changes go through
  // locationState, the way the Route's state flows do.
  private fun setContent(initialState: LocationUiState) {
    locationState.value = LocationUiState()
    composeRule.setContent {
      val state = locationState.value
      RoamigoTheme {
        // Mirrors how TripMapRoute passes the location state to the screen.
        TripMapScreen(
            uiState = tripState,
            isSharing = state.isSharing,
            memberLocations = state.memberLocations,
            staleMemberIds = state.staleMemberIds,
            myLocation = state.shownMyLocation,
            locationErrorMessage = state.errorMessageRes,
            isOffline = state.isOffline,
            nowMillis = NOW_MILLIS,
            onRetry = {},
            onRetryActivities = {},
            onTurnOnSharing = {},
            onPauseSharing = {},
        )
      }
    }
    composeRule.loadLocalMapStyle()
    composeRule.runOnIdle { locationState.value = initialState }
  }

  private fun waitForMemberMarker(userId: String) {
    composeRule.waitUntil(timeoutMillis = MAP_TEST_TIMEOUT_MILLIS) {
      composeRule.onAllNodesWithTag(memberMarkerTag(userId)).fetchSemanticsNodes().isNotEmpty()
    }
  }

  private fun waitForText(text: String) {
    composeRule.waitUntil(timeoutMillis = MAP_TEST_TIMEOUT_MILLIS) {
      composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
  }

  private fun memberLocation(
      userId: String,
      offset: Int = 0,
      updatedAt: Long = NOW_MILLIS,
  ): MemberLocation =
      MemberLocation(
          userId = userId,
          // A few metres apart: the camera fits the single trip destination at a high zoom, so
          // farther members would be off screen. Their markers collide, like those of a group.
          latitude = LATITUDE + offset * MEMBER_SPACING_DEGREES,
          longitude = LONGITUDE + offset * MEMBER_SPACING_DEGREES,
          updatedAt = updatedAt,
      )

  private fun memberMarkerTag(userId: String): String = C.Tag.trip_map_member_marker_prefix + userId

  private val isNotStale = SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription)

  private fun hasTagStartingWith(prefix: String): SemanticsMatcher =
      SemanticsMatcher("has a test tag starting with $prefix") { node ->
        node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
      }

  private companion object {
    const val TRIP_ID = "trip_lisbon"
    const val USER_ID = "user_1"
    const val LATITUDE = 38.7223
    const val LONGITUDE = -9.1393
    const val NOW_MILLIS = 1_700_000_000_000L
    const val MEMBER_SPACING_DEGREES = 0.000005
  }
}
