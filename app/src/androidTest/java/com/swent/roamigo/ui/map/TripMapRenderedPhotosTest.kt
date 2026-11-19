// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.map

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.CameraState
import com.swent.roamigo.R
import com.swent.roamigo.model.location.MemberLocation
import com.swent.roamigo.model.photo.TripPhoto
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.RoamigoTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks the photo markers on the rendered Mapbox map: they coexist with the other layers, open the
 * viewer for their own group, follow gallery updates and can be told apart once zoomed in.
 *
 * The screen receives fake states and a local empty style, so no tiles or other network resources
 * are loaded.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class TripMapRenderedPhotosTest {
  // Mapbox annotation cleanup must run immediately on the UI lifecycle thread.
  @get:Rule
  val composeRule =
      createAndroidComposeRule<ComponentActivity>(effectContext = UnconfinedTestDispatcher())

  private val tripState = mutableStateOf<TripMapUiState>(TripMapUiState.Loading)
  private val memberLocations = mutableStateOf<List<MemberLocation>>(emptyList())
  private val selected: MapCoordinate?
    get() = (tripState.value as? TripMapUiState.Loaded)?.selectedPhotoCoordinate

  @Test
  fun photoMarkersCoexistWithActivityAndMemberMarkers() {
    showTrip(listOf(group(HERE, photo("a", 2), photo("b", 1))))
    // Beside the activity rather than on it: member markers already hide colliding activity pins.
    memberLocations.value =
        listOf(
            MemberLocation(
                "alex",
                HERE.latitude + MEMBER_OFFSET_DEGREES,
                HERE.longitude,
                LOCATION_TEST_NOW_MILLIS,
            )
        )
    composeRule.runOnIdle {
      composeRule.findMapView().mapboxMap.setCamera(CameraOptions.Builder().zoom(19.0).build())
    }

    waitForTag(photoMarkerTag(HERE))
    waitForTag(TripMarkerTestTags.markerFor(ACTIVITY_ID))
    waitForTag(C.Tag.trip_map_member_marker_prefix + "alex")
    // The badge is hidden from accessibility services; the marker's label carries the count.
    composeRule
        .onNodeWithTag(C.Tag.trip_map_photo_marker_count, useUnmergedTree = true)
        .assertExists()
    composeRule
        .onNodeWithContentDescription(
            composeRule.activity.resources.getQuantityString(
                R.plurals.trip_map_photo_marker_description,
                2,
                2,
            )
        )
        .assertExists()
  }

  @Test
  fun tappingAMarkerBrowsesItsGroupAndClosingKeepsTheCamera() {
    showTrip(listOf(group(HERE, photo("newest", 2), photo("older", 1))))
    waitForTag(photoMarkerTag(HERE))
    val camera = cameraState()

    composeRule.onNodeWithTag(photoMarkerTag(HERE)).performClick()
    assertPosition(1, 2)
    composeRule.onNodeWithTag(C.Tag.photo_viewer_next).performClick()
    assertPosition(2, 2)
    composeRule.onNodeWithTag(C.Tag.photo_viewer_close).performClick()

    composeRule.onNodeWithTag(C.Tag.photo_viewer).assertDoesNotExist()
    composeRule.runOnIdle { assertEquals(null, selected) }
    assertSameCamera(camera, cameraState())

    waitForTag(photoMarkerTag(HERE))
    composeRule.onNodeWithTag(photoMarkerTag(HERE)).performClick()
    composeRule.onNodeWithTag(C.Tag.photo_viewer).assertExists()
    composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
    composeRule.onNodeWithTag(C.Tag.photo_viewer).assertDoesNotExist()
    assertSameCamera(camera, cameraState())
  }

  @Test
  fun nearbyGroupsCanBeTappedIndividuallyAfterZooming() {
    showTrip(listOf(group(HERE, photo("here", 1)), group(NEARBY, photo("nearby", 1))))
    // At the trip's zoom the groups collide and Mapbox may hide one of them.
    composeRule.waitUntil(timeoutMillis = MAP_TEST_TIMEOUT_MILLIS) {
      exists(photoMarkerTag(HERE)) || exists(photoMarkerTag(NEARBY))
    }

    composeRule.runOnIdle {
      composeRule.findMapView().mapboxMap.setCamera(CameraOptions.Builder().zoom(20.0).build())
    }
    waitForTag(photoMarkerTag(NEARBY))
    composeRule.waitUntil(timeoutMillis = MAP_TEST_TIMEOUT_MILLIS) {
      exists(photoMarkerTag(HERE)) &&
          !bounds(photoMarkerTag(HERE)).overlaps(bounds(photoMarkerTag(NEARBY)))
    }
    assertFalse(bounds(photoMarkerTag(HERE)).overlaps(bounds(photoMarkerTag(NEARBY))))

    composeRule.onNodeWithTag(photoMarkerTag(NEARBY)).performClick()
    composeRule.runOnIdle { assertEquals(NEARBY, selected) }
    composeRule.onNodeWithTag(C.Tag.photo_viewer_close).performClick()

    composeRule.onNodeWithTag(photoMarkerTag(HERE)).performClick()
    composeRule.runOnIdle { assertEquals(HERE, selected) }
  }

  @Test
  fun markersFollowGalleryUpdatesAndAnEmptyGallery() {
    showTrip(listOf(group(HERE, photo("one", 1))))
    waitForTag(photoMarkerTag(HERE))

    composeRule.runOnIdle { tripState.value = loaded(listOf(group(NEARBY, photo("two", 2)))) }
    waitForTag(photoMarkerTag(NEARBY))
    composeRule.waitUntil(timeoutMillis = MAP_TEST_TIMEOUT_MILLIS) { !exists(photoMarkerTag(HERE)) }

    composeRule.runOnIdle { tripState.value = loaded(emptyList()) }
    composeRule.waitUntil(timeoutMillis = MAP_TEST_TIMEOUT_MILLIS) {
      !exists(photoMarkerTag(NEARBY))
    }
    composeRule.onNodeWithTag(C.Tag.trip_map_photos_empty).assertExists()
  }

  /** Shows the screen, then the trip once the local style is loaded. */
  private fun showTrip(markers: List<PhotoMarkerUiState>) {
    composeRule.setContent {
      RoamigoTheme {
        TripMapScreen(
            uiState = tripState.value,
            isSharing = false,
            memberLocations = memberLocations.value,
            nowMillis = LOCATION_TEST_NOW_MILLIS,
            onRetry = {},
            onRetryActivities = {},
            onTurnOnSharing = {},
            onPauseSharing = {},
            // Selection is updated as TripMapViewModel updates it.
            onPhotoMarkerClick = { select(it) },
            onPhotoViewerClose = { select(null) },
        )
      }
    }
    composeRule.loadLocalMapStyle()
    composeRule.runOnIdle { tripState.value = loaded(markers) }
  }

  private fun select(coordinate: MapCoordinate?) {
    tripState.value =
        (tripState.value as TripMapUiState.Loaded).copy(selectedPhotoCoordinate = coordinate)
  }

  private fun loaded(markers: List<PhotoMarkerUiState>) =
      lisbonTripMapState.copy(
          activityMarkers =
              listOf(
                  TripMarkerUiState(
                      id = ACTIVITY_ID,
                      tripId = lisbonTripMapState.trip.uid,
                      title = "Museum",
                      locationName = "Museum",
                      latitude = HERE.latitude,
                      longitude = HERE.longitude,
                      kind = TripMarkerKind.ACTIVITY,
                  )
              ),
          activitiesLoadState = ActivitiesLoadState.LOADED,
          photoMarkers = markers,
          photosLoadState = PhotosLoadState.LOADED,
      )

  private fun waitForTag(tag: String) {
    composeRule.waitUntil(timeoutMillis = MAP_TEST_TIMEOUT_MILLIS) { exists(tag) }
  }

  private fun exists(tag: String) =
      composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

  private fun bounds(tag: String): Rect =
      composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInWindow

  private fun cameraState(): CameraState = composeRule.runOnIdle {
    composeRule.findMapView().mapboxMap.cameraState
  }

  private fun assertSameCamera(expected: CameraState, actual: CameraState) {
    assertEquals(expected.center.latitude(), actual.center.latitude(), 1e-9)
    assertEquals(expected.center.longitude(), actual.center.longitude(), 1e-9)
    assertEquals(expected.zoom, actual.zoom, 1e-9)
  }

  private fun assertPosition(index: Int, size: Int) {
    composeRule
        .onNodeWithTag(C.Tag.photo_viewer_position)
        .assertTextEquals(
            composeRule.activity.getString(R.string.photo_viewer_position, index, size)
        )
  }

  private fun group(coordinate: MapCoordinate, vararg photos: TripPhoto) =
      PhotoMarkerUiState(coordinate, photos.toList())

  private fun photo(id: String, takenAtMillis: Long) =
      TripPhoto(id, "alex", takenAtMillis, LISBON_LATITUDE, LISBON_LONGITUDE, byteArrayOf(1, 2, 3))

  private companion object {
    const val ACTIVITY_ID = "activity_museum"
    // About 22 m north: above the pin and its label at zoom 19, and still on screen.
    const val MEMBER_OFFSET_DEGREES = 0.0002
    val HERE = MapCoordinate(LISBON_LATITUDE, LISBON_LONGITUDE)
    // About 20 m north: one spot at the trip's zoom, but separate markers once zoomed in.
    val NEARBY = MapCoordinate(LISBON_LATITUDE + 0.0002, LISBON_LONGITUDE)
  }
}
