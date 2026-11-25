// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.map

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mapbox.maps.CameraState
import com.swent.roamigo.model.photo.FakePhotoRepository
import com.swent.roamigo.model.photo.TripPhoto
import com.swent.roamigo.model.trip.FakeTripRepository
import com.swent.roamigo.model.trip.activity.FakeActivityRepository
import com.swent.roamigo.model.users.FakeUserRepository
import com.swent.roamigo.model.users.User
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.RoamigoTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks that gestures on a photo opened from the trip map stay on the photo instead of moving the
 * map drawn beneath it.
 *
 * The route is given fake repositories and a local empty style, so no network resources are used.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class TripMapPhotoGesturesTest {
  // Mapbox annotation cleanup must run immediately on the UI lifecycle thread.
  @get:Rule
  val composeRule =
      createAndroidComposeRule<ComponentActivity>(effectContext = UnconfinedTestDispatcher())

  @Test
  fun gesturesOnAnOpenPhotoDoNotMoveTheMap() {
    showTripWithPhoto()
    composeRule.waitUntil(timeoutMillis = MAP_TEST_TIMEOUT_MILLIS) {
      composeRule.onAllNodesWithTag(MARKER_TAG).fetchSemanticsNodes().isNotEmpty()
    }
    val camera = cameraState()

    composeRule.onNodeWithTag(MARKER_TAG).performClick()
    composeRule.onNodeWithTag(C.Tag.photo_viewer).performTouchInput {
      swipeLeft()
      swipeUp()
      pinch(
          start0 = center - Offset(10f, 0f),
          end0 = center - Offset(width / 3f, 0f),
          start1 = center + Offset(10f, 0f),
          end1 = center + Offset(width / 3f, 0f),
      )
      doubleClick()
    }
    composeRule.onNodeWithTag(C.Tag.photo_viewer_close).performClick()

    composeRule.onNodeWithTag(C.Tag.photo_viewer).assertDoesNotExist()
    val after = cameraState()
    assertEquals(camera.center.latitude(), after.center.latitude(), 1e-9)
    assertEquals(camera.center.longitude(), after.center.longitude(), 1e-9)
    assertEquals(camera.zoom, after.zoom, 1e-9)
    assertEquals(camera.bearing, after.bearing, 1e-9)
  }

  /** Shows the trip map route with a gallery holding a single photo in Lisbon. */
  private fun showTripWithPhoto() {
    val trip = lisbonTripMapState.trip
    val photoRepository =
        FakePhotoRepository().apply {
          setUploaded(
              listOf(
                  TripPhoto(
                      "photo",
                      "alex",
                      1,
                      LISBON_LATITUDE,
                      LISBON_LONGITUDE,
                      byteArrayOf(1, 2, 3),
                  )
              )
          )
        }
    val factory =
        TripMapViewModel.factory(
            tripId = trip.uid,
            tripRepository = FakeTripRepository(trips = listOf(trip)),
            activityRepository = FakeActivityRepository(),
            userRepository = FakeUserRepository(User("alex", "Alex", null)),
            photoRepository = photoRepository,
        )
    composeRule.setContent {
      RoamigoTheme {
        TripMapRoute(
            tripId = trip.uid,
            tripMapViewModelFactory = factory,
            locationViewModelFactory = null,
        )
      }
    }
    composeRule.loadLocalMapStyle()
  }

  private fun cameraState(): CameraState = composeRule.runOnIdle {
    composeRule.findMapView().mapboxMap.cameraState
  }

  private companion object {
    val MARKER_TAG = photoMarkerTag(MapCoordinate(LISBON_LATITUDE, LISBON_LONGITUDE))
  }
}
