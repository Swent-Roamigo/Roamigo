// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.map

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Timestamp
import com.mapbox.geojson.Point
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.location.MemberLocation
import com.swent.roamigo.model.trip.TravelMode
import com.swent.roamigo.model.trip.Trip
import com.swent.roamigo.model.trip.TripStatus
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.RoamigoTheme
import kotlin.math.abs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks the trip map's location layer on the rendered Mapbox map: the camera frames the trip, and
 * the member markers and the user's own dot are drawn at their coordinates.
 *
 * The screen receives fake states and a local empty style, so no tiles or other network resources
 * are loaded.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class TripMapRenderedLocationsTest {
  // Mapbox annotation cleanup must run immediately on the UI lifecycle thread.
  @get:Rule
  val composeRule =
      createAndroidComposeRule<ComponentActivity>(effectContext = UnconfinedTestDispatcher())

  private val destination =
      TripLocation(latitude = LATITUDE, longitude = LONGITUDE, name = "Lisbon")

  private val trip =
      Trip(
          uid = "trip_lisbon",
          name = "Lisbon",
          description = null,
          destinations = listOf(destination),
          startDate = Timestamp(0, 0),
          endDate = Timestamp(0, 0),
          flexibleDates = false,
          travelMode = TravelMode.GROUP,
          travelerCount = 3,
          ownerId = USER_ID,
          memberIds = listOf(USER_ID, "alex", "sam"),
          status = TripStatus.PLANNED,
          createdAt = Timestamp(0, 0),
      )

  // Members a few metres from the destination, so they stay on screen at the zoom fitting it.
  private val memberLocations =
      listOf(
          MemberLocation("alex", LATITUDE + 0.000005, LONGITUDE - 0.000005, NOW_MILLIS),
          MemberLocation("sam", LATITUDE - 0.000005, LONGITUDE + 0.000005, NOW_MILLIS),
      )
  private val myLocation = MemberLocation(USER_ID, LATITUDE, LONGITUDE + 0.00001, NOW_MILLIS)

  private val tripState = mutableStateOf<TripMapUiState>(TripMapUiState.Loading)
  private val locationState = mutableStateOf(LocationUiState())

  @Test
  fun cameraIsCenteredOnTheTrip() {
    showLoadedTrip(LocationUiState(memberLocations = memberLocations))

    composeRule.waitUntil(timeoutMillis = MAP_TEST_TIMEOUT_MILLIS) {
      val center = composeRule.runOnIdle { composeRule.findMapView().mapboxMap.cameraState.center }
      abs(center.latitude() - LATITUDE) < CENTER_TOLERANCE_DEGREES &&
          abs(center.longitude() - LONGITUDE) < CENTER_TOLERANCE_DEGREES
    }
  }

  @Test
  fun memberMarkersAndOwnDotAreDrawnAtTheirCoordinates() {
    showLoadedTrip(
        LocationUiState(
            permissionState = LocationPermissionState.Granted,
            isSharing = true,
            myLocation = myLocation,
            memberLocations = memberLocations,
        )
    )
    waitForCameraToSettle()

    // Member markers hang above their position, while the own dot is centered on it.
    memberLocations.forEach { location ->
      assertDrawnAt(C.Tag.trip_map_member_marker_prefix + location.userId, location) { bounds ->
        Offset(bounds.center.x, bounds.bottom)
      }
    }
    assertDrawnAt(C.Tag.trip_map_my_location, myLocation) { bounds -> bounds.center }
  }

  @Test
  fun ownDotIsRemovedFromTheMapWhenSharingStops() {
    val sharingState =
        LocationUiState(
            permissionState = LocationPermissionState.Granted,
            isSharing = true,
            myLocation = myLocation,
            memberLocations = memberLocations,
        )
    showLoadedTrip(sharingState)
    waitForTag(C.Tag.trip_map_my_location)

    composeRule.runOnIdle { locationState.value = sharingState.copy(isSharing = false) }

    composeRule.waitUntil(timeoutMillis = MAP_TEST_TIMEOUT_MILLIS) {
      composeRule.onAllNodesWithTag(C.Tag.trip_map_my_location).fetchSemanticsNodes().isEmpty()
    }
    memberLocations.forEach { location ->
      composeRule
          .onNodeWithTag(C.Tag.trip_map_member_marker_prefix + location.userId)
          .assertExists()
    }
  }

  /**
   * Shows the screen, then the trip and [locations] once the local style is loaded: annotations
   * added before the style is replaced are not displayed.
   */
  private fun showLoadedTrip(locations: LocationUiState) {
    composeRule.setContent {
      val state = locationState.value
      RoamigoTheme {
        // Passes the location state the way TripMapRoute does.
        TripMapScreen(
            uiState = tripState.value,
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
    composeRule.runOnIdle {
      tripState.value =
          TripMapUiState.Loaded(
              trip = trip,
              // Built like TripMapViewModel builds it from the trip's destinations.
              viewport =
                  tripMapViewportOf(
                      listOf(MapCoordinate(destination.latitude, destination.longitude))
                  ),
              members =
                  mapOf(
                      "alex" to MemberDisplayInfo("Alex", "A", null),
                      "sam" to MemberDisplayInfo("Sam", "S", null),
                  ),
          )
      locationState.value = locations
    }
  }

  // The annotations follow the camera, so their positions are only final once it stops moving.
  private fun waitForCameraToSettle() {
    var previous: Point? = null
    composeRule.waitUntil(timeoutMillis = MAP_TEST_TIMEOUT_MILLIS) {
      val center = composeRule.runOnIdle { composeRule.findMapView().mapboxMap.cameraState.center }
      val settled =
          abs(center.latitude() - LATITUDE) < CENTER_TOLERANCE_DEGREES &&
              center.latitude() == previous?.latitude() &&
              center.longitude() == previous?.longitude()
      previous = center
      settled
    }
  }

  private fun waitForTag(tag: String) {
    composeRule.waitUntil(timeoutMillis = MAP_TEST_TIMEOUT_MILLIS) {
      composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
  }

  /**
   * Asserts that the node tagged [tag] is drawn where the map projects [location], comparing the
   * map's projection with the point of the node's window bounds picked by [anchorOf].
   */
  private fun assertDrawnAt(
      tag: String,
      location: MemberLocation,
      anchorOf: (androidx.compose.ui.geometry.Rect) -> Offset,
  ) {
    waitForTag(tag)
    composeRule.waitUntil(timeoutMillis = MAP_TEST_TIMEOUT_MILLIS) {
      val expected = expectedWindowPosition(location)
      val actual = anchorOf(composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInWindow)
      abs(actual.x - expected.x) < POSITION_TOLERANCE_PX &&
          abs(actual.y - expected.y) < POSITION_TOLERANCE_PX
    }
    val expected = expectedWindowPosition(location)
    val actual = anchorOf(composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInWindow)
    assertEquals(expected.x, actual.x, POSITION_TOLERANCE_PX)
    assertEquals(expected.y, actual.y, POSITION_TOLERANCE_PX)
  }

  private fun expectedWindowPosition(location: MemberLocation): Offset = composeRule.runOnIdle {
    val mapView = composeRule.findMapView()
    val pixel =
        mapView.mapboxMap.pixelForCoordinate(
            Point.fromLngLat(location.longitude, location.latitude)
        )
    val mapOrigin = IntArray(2).also(mapView::getLocationInWindow)
    Offset((mapOrigin[0] + pixel.x).toFloat(), (mapOrigin[1] + pixel.y).toFloat())
  }

  private companion object {
    const val USER_ID = "user_1"
    const val LATITUDE = 38.7223
    const val LONGITUDE = -9.1393
    const val NOW_MILLIS = 1_700_000_000_000L
    const val CENTER_TOLERANCE_DEGREES = 1e-6
    const val POSITION_TOLERANCE_PX = 4f
  }
}
