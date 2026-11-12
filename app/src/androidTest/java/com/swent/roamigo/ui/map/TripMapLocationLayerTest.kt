// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.map

import androidx.activity.ComponentActivity
import androidx.compose.runtime.State
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
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
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

  private val locationState = mutableStateOf(LocationUiState())
  private val tripUiState = mutableStateOf<TripMapUiState>(lisbonTripMapState)

  @Test
  fun memberMarkersFollowTheSharingMembers() {
    setContent(LocationUiState())
    composeRule.onNodeWithTag(C.Tag.trip_map_map).assertIsDisplayed()
    composeRule
        .onAllNodes(hasTagStartingWith(C.Tag.trip_map_member_marker_prefix))
        .assertCountEquals(0)

    val ids = listOf("alex", "sam", "kim")
    showLocations(
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

    showLocations(LocationUiState(memberLocations = listOf(memberLocation("sam", offset = 1))))
    composeRule.waitUntil(timeoutMillis = MAP_TEST_TIMEOUT_MILLIS) {
      composeRule
          .onAllNodes(hasTagStartingWith(C.Tag.trip_map_member_marker_prefix))
          .fetchSemanticsNodes()
          .size == 1
    }
    composeRule.onNodeWithTag(memberMarkerTag("sam")).assertIsDisplayed()
  }

  @Test
  fun memberMarkersShowNameFreshnessAndStaleness() {
    setContent(
        LocationUiState(
            memberLocations =
                listOf(
                    memberLocation("alex"),
                    memberLocation("sam", offset = 1, updatedAt = NOW_MILLIS - 120_000),
                ),
            staleMemberIds = setOf("sam"),
        )
    )

    waitForMemberMarker("alex")
    waitForMemberMarker("sam")
    composeRule.onNodeWithTag(memberMarkerTag("alex")).assertIsDisplayed().assert(isNotStale)
    assertMemberLabel("alex", "Alex", live)
    composeRule
        .onNodeWithTag(C.Tag.trip_map_member_initial_prefix + "alex", useUnmergedTree = true)
        .assertExists()
    composeRule
        .onNodeWithTag(memberMarkerTag("sam"))
        .assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, staleDescription)
        )
    val twoMinutesAgo =
        targetContext.resources.getQuantityString(R.plurals.trip_map_freshness_minutes_ago, 2, 2)
    assertMemberLabel("sam", "Sam", twoMinutesAgo)
  }

  @Test
  fun membersWithoutProfileAreShownWithTheFallbackName() {
    setContent(
        LocationUiState(
            memberLocations = listOf(memberLocation("alex"), memberLocation("zoe", offset = 1))
        )
    )
    waitForMemberLabel("zoe", "Z")
    waitForMemberLabel("alex", "Alex")

    // No profile is known while the trip reloads or fails. The camera keeps its position, so the
    // marker stays on screen.
    composeRule.runOnIdle { tripUiState.value = TripMapUiState.Loading }
    waitForMemberLabel("alex", "A")
    composeRule.onNodeWithTag(memberMarkerTag("alex")).assertIsDisplayed()

    composeRule.runOnIdle { tripUiState.value = TripMapUiState.Error("failure") }
    composeRule.onNodeWithTag(C.Tag.trip_map_error).assertIsDisplayed()
    waitForMemberLabel("alex", "A")
    composeRule.onNodeWithTag(memberMarkerTag("alex")).assertIsDisplayed()
  }

  @Test
  fun ownPositionIsShownOnlyWhileSharingWithThePermission() {
    val sharingState =
        LocationUiState(
            permissionState = LocationPermissionState.Granted,
            isSharing = true,
            myLocation = memberLocation(USER_ID),
            memberLocations = listOf(memberLocation("alex", offset = 1)),
        )
    setContent(sharingState)
    waitForMyLocation(shown = true)
    composeRule
        .onNodeWithTag(C.Tag.trip_map_my_location)
        .assertIsDisplayed()
        .assertContentDescriptionEquals(targetContext.getString(R.string.trip_map_my_location))

    // The ViewModel keeps the last position after sharing stops. Member markers stay visible.
    showLocations(sharingState.copy(isSharing = false))
    waitForMyLocation(shown = false)
    composeRule.onNodeWithTag(memberMarkerTag("alex")).assertIsDisplayed()

    showLocations(sharingState)
    waitForMyLocation(shown = true)
    showLocations(sharingState.copy(permissionState = LocationPermissionState.Denied))
    waitForMyLocation(shown = false)
    composeRule.onNodeWithTag(memberMarkerTag("alex")).assertIsDisplayed()

    showLocations(sharingState.copy(permissionState = LocationPermissionState.Revoked))
    composeRule.onNodeWithTag(memberMarkerTag("alex")).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trip_map_my_location).assertDoesNotExist()
  }

  @Test
  fun locationErrorsAreShownInASnackbarWithoutHidingTheMap() {
    val members = LocationUiState(memberLocations = listOf(memberLocation("alex")))
    setContent(members)
    waitForMemberMarker("alex")
    errorMessages.forEach { message ->
      composeRule.onAllNodesWithText(targetContext.getString(message)).assertCountEquals(0)
    }

    showLocations(
        members.copy(
            sharingError = LocationSharingError.RequestFailed,
            // Also shows that a sharing error comes before an observation error.
            observationErrorMessage = "Backend detail that must not be shown",
        )
    )
    waitForText(R.string.trip_map_error_sharing_failed)
    composeRule.onNodeWithTag(C.Tag.trip_map_location_error).assertIsDisplayed()
    composeRule.onAllNodesWithText("Backend detail that must not be shown").assertCountEquals(0)
    composeRule.onNodeWithTag(C.Tag.trip_map_map).assertIsDisplayed()
    composeRule.onNodeWithTag(memberMarkerTag("alex")).assertIsDisplayed()

    showLocations(members.copy(sharingError = LocationSharingError.PermissionRequired))
    waitForText(R.string.trip_map_error_permission_required)
    showLocations(members.copy(observationErrorMessage = "read failed"))
    waitForText(R.string.trip_map_error_locations_unavailable)

    showLocations(members)
    composeRule.waitUntil(timeoutMillis = MAP_TEST_TIMEOUT_MILLIS) {
      errorMessages.all { message ->
        composeRule
            .onAllNodesWithText(targetContext.getString(message))
            .fetchSemanticsNodes()
            .isEmpty()
      }
    }
  }

  @Test
  fun offlineBannerIsShownOnlyWhileOffline() {
    setContent(LocationUiState(isOffline = false))
    composeRule.onNodeWithTag(C.Tag.trip_map_offline).assertDoesNotExist()

    showLocations(LocationUiState(isOffline = true))
    composeRule
        .onNodeWithTag(C.Tag.trip_map_offline)
        .assertIsDisplayed()
        .assert(hasAnyDescendant(hasText(targetContext.getString(R.string.trip_map_offline))))
    composeRule.onNodeWithTag(C.Tag.trip_map_map).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trip_map_sharing_button).assertIsDisplayed()

    showLocations(LocationUiState(isOffline = false))
    composeRule.onNodeWithTag(C.Tag.trip_map_offline).assertDoesNotExist()
  }

  // The state is applied once the local style is loaded, like in TripMapScreenTest: annotations
  // added before loadLocalMapStyle replaces the style are not displayed. Later changes go through
  // locationState, the way the Route's state flows do.
  private fun setContent(initialState: LocationUiState) {
    composeRule.setTripMapContent(tripUiState, locationState)
    composeRule.loadLocalMapStyle()
    showLocations(initialState)
  }

  private fun showLocations(state: LocationUiState) {
    composeRule.runOnIdle { locationState.value = state }
  }

  private fun waitForMemberMarker(userId: String) {
    composeRule.waitUntil(timeoutMillis = MAP_TEST_TIMEOUT_MILLIS) {
      composeRule.onAllNodesWithTag(memberMarkerTag(userId)).fetchSemanticsNodes().isNotEmpty()
    }
  }

  private fun waitForMyLocation(shown: Boolean) {
    composeRule.waitUntil(timeoutMillis = MAP_TEST_TIMEOUT_MILLIS) {
      composeRule
          .onAllNodesWithTag(C.Tag.trip_map_my_location)
          .fetchSemanticsNodes()
          .isNotEmpty() == shown
    }
  }

  private fun memberLabel(name: String, freshness: String): String =
      targetContext.getString(R.string.trip_map_member_marker_label, name, freshness)

  private fun assertMemberLabel(userId: String, name: String, freshness: String) {
    composeRule
        .onNodeWithTag(C.Tag.trip_map_member_label_prefix + userId, useUnmergedTree = true)
        .assertTextEquals(memberLabel(name, freshness))
  }

  private fun waitForMemberLabel(userId: String, name: String) {
    val label = memberLabel(name, live)
    composeRule.waitUntil(timeoutMillis = MAP_TEST_TIMEOUT_MILLIS) {
      composeRule
          .onAllNodesWithTag(C.Tag.trip_map_member_label_prefix + userId, useUnmergedTree = true)
          .fetchSemanticsNodes()
          .any { node -> node.config.getOrNull(SemanticsProperties.Text)?.joinToString() == label }
    }
  }

  private fun waitForText(message: Int) {
    val text = targetContext.getString(message)
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
          latitude = LISBON_LATITUDE + offset * MEMBER_SPACING_DEGREES,
          longitude = LISBON_LONGITUDE + offset * MEMBER_SPACING_DEGREES,
          updatedAt = updatedAt,
      )

  private fun memberMarkerTag(userId: String): String = C.Tag.trip_map_member_marker_prefix + userId

  private val isNotStale = SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription)

  private fun hasTagStartingWith(prefix: String): SemanticsMatcher =
      SemanticsMatcher("has a test tag starting with $prefix") { node ->
        node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
      }

  private companion object {
    const val USER_ID = "user_1"
    const val NOW_MILLIS = LOCATION_TEST_NOW_MILLIS
    const val MEMBER_SPACING_DEGREES = 0.000005

    val errorMessages =
        listOf(
            R.string.trip_map_error_permission_required,
            R.string.trip_map_error_sharing_failed,
            R.string.trip_map_error_locations_unavailable,
        )
  }
}

internal const val LISBON_LATITUDE = 38.7223
internal const val LISBON_LONGITUDE = -9.1393

/** Fixed time given to the trip map in the location tests, in milliseconds since the epoch. */
internal const val LOCATION_TEST_NOW_MILLIS = 1_700_000_000_000L

/** A loaded trip to Lisbon, centered on its destination, with the profiles of alex, sam and kim. */
internal val lisbonTripMapState =
    TripMapUiState.Loaded(
        trip =
            Trip(
                uid = "trip_lisbon",
                name = "Lisbon",
                description = null,
                destinations =
                    listOf(
                        TripLocation(
                            latitude = LISBON_LATITUDE,
                            longitude = LISBON_LONGITUDE,
                            name = "Lisbon",
                        )
                    ),
                startDate = Timestamp(0, 0),
                endDate = Timestamp(0, 0),
                flexibleDates = false,
                travelMode = TravelMode.GROUP,
                travelerCount = 4,
                ownerId = "user_1",
                memberIds = listOf("user_1", "alex", "sam", "kim"),
                status = TripStatus.PLANNED,
                createdAt = Timestamp(0, 0),
            ),
        // Built like TripMapViewModel builds it from the trip's single destination.
        viewport = TripMapViewport.Centered(LISBON_LATITUDE, LISBON_LONGITUDE),
        members =
            mapOf(
                "alex" to MemberDisplayInfo("Alex", "A", null),
                "sam" to MemberDisplayInfo("Sam", "S", null),
                "kim" to MemberDisplayInfo("Kim", "K", null),
            ),
    )

/**
 * Shows [TripMapScreen] with the current [tripState] and [locationState], passed the way
 * [TripMapRoute] passes them, at the fixed time [LOCATION_TEST_NOW_MILLIS].
 */
internal fun AndroidComposeTestRule<*, ComponentActivity>.setTripMapContent(
    tripState: State<TripMapUiState>,
    locationState: State<LocationUiState>,
) {
  setContent {
    val state = locationState.value
    RoamigoTheme {
      TripMapScreen(
          uiState = tripState.value,
          isSharing = state.isSharing,
          memberLocations = state.memberLocations,
          staleMemberIds = state.staleMemberIds,
          myLocation = state.shownMyLocation,
          locationErrorMessage = state.errorMessageRes,
          isOffline = state.isOffline,
          nowMillis = LOCATION_TEST_NOW_MILLIS,
          onRetry = {},
          onRetryActivities = {},
          onTurnOnSharing = {},
          onPauseSharing = {},
      )
    }
  }
}
