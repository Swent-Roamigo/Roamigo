// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.map

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mapbox.geojson.Point
import com.swent.roamigo.model.location.MemberLocation
import com.swent.roamigo.resources.C
import kotlin.math.abs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

  // Members a few metres from the destination, so they stay on screen at the zoom fitting it.
  private val memberLocations =
      listOf(
          MemberLocation("alex", LATITUDE + 0.000005, LONGITUDE - 0.000005, NOW_MILLIS),
          MemberLocation("sam", LATITUDE - 0.000005, LONGITUDE + 0.000005, NOW_MILLIS),
      )
  private val myLocation = MemberLocation("user_1", LATITUDE, LONGITUDE + 0.00001, NOW_MILLIS)

  private val tripState = mutableStateOf<TripMapUiState>(TripMapUiState.Loading)
  private val locationState = mutableStateOf(LocationUiState())

  @Test
  fun cameraIsCenteredOnTheTrip() {
    showLoadedTrip(LocationUiState(memberLocations = memberLocations))

    composeRule.waitUntil(timeoutMillis = MAP_TEST_TIMEOUT_MILLIS) { isOnTheTrip(cameraCenter()) }
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

  /**
   * Shows the screen, then the trip and [locations] once the local style is loaded: annotations
   * added before the style is replaced are not displayed.
   */
  private fun showLoadedTrip(locations: LocationUiState) {
    composeRule.setTripMapContent(tripState, locationState)
    composeRule.loadLocalMapStyle()
    composeRule.runOnIdle {
      tripState.value = lisbonTripMapState
      locationState.value = locations
    }
  }

  private fun cameraCenter(): Point = composeRule.runOnIdle {
    composeRule.findMapView().mapboxMap.cameraState.center
  }

  private fun isOnTheTrip(center: Point): Boolean =
      abs(center.latitude() - LATITUDE) < CENTER_TOLERANCE_DEGREES &&
          abs(center.longitude() - LONGITUDE) < CENTER_TOLERANCE_DEGREES

  // The annotations follow the camera, so their positions are only final once it stops moving.
  private fun waitForCameraToSettle() {
    var previous: Point? = null
    composeRule.waitUntil(timeoutMillis = MAP_TEST_TIMEOUT_MILLIS) {
      val center = cameraCenter()
      val settled =
          isOnTheTrip(center) &&
              center.latitude() == previous?.latitude() &&
              center.longitude() == previous?.longitude()
      previous = center
      settled
    }
  }

  /**
   * Waits until the node tagged [tag] is drawn where the map projects [location], comparing the
   * map's projection with the point of the node's window bounds picked by [anchorOf].
   */
  private fun assertDrawnAt(tag: String, location: MemberLocation, anchorOf: (Rect) -> Offset) {
    composeRule.waitUntil(timeoutMillis = MAP_TEST_TIMEOUT_MILLIS) {
      composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.waitUntil(timeoutMillis = MAP_TEST_TIMEOUT_MILLIS) {
      val expected = expectedWindowPosition(location)
      val actual = anchorOf(composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInWindow)
      abs(actual.x - expected.x) < POSITION_TOLERANCE_PX &&
          abs(actual.y - expected.y) < POSITION_TOLERANCE_PX
    }
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
    const val LATITUDE = LISBON_LATITUDE
    const val LONGITUDE = LISBON_LONGITUDE
    const val NOW_MILLIS = LOCATION_TEST_NOW_MILLIS
    const val CENTER_TOLERANCE_DEGREES = 1e-6
    const val POSITION_TOLERANCE_PX = 4f
  }
}
