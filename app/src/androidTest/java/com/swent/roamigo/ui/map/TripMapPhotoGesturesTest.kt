// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.map

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mapbox.maps.CameraState
import com.swent.roamigo.R
import com.swent.roamigo.model.photo.FakePhotoRepository
import com.swent.roamigo.model.photo.TripPhoto
import com.swent.roamigo.model.trip.FakeTripRepository
import com.swent.roamigo.model.trip.activity.FakeActivityRepository
import com.swent.roamigo.model.users.FakeUserRepository
import com.swent.roamigo.model.users.User
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.RoamigoTheme
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks that gestures on a photo opened from the trip map stay on the photo instead of moving the
 * map drawn beneath it, that the viewer's controls still respond to touches, and that the map
 * responds to gestures again once the photo is closed.
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
    showTripWithPhotos()
    composeRule.waitUntil(timeoutMillis = MAP_TEST_TIMEOUT_MILLIS) {
      composeRule.onAllNodesWithTag(MARKER_TAG).fetchSemanticsNodes().isNotEmpty()
    }
    val camera = cameraState()

    composeRule.onNodeWithTag(MARKER_TAG).performTouchInput { click() }
    composeRule.onNodeWithTag(C.Tag.photo_viewer).performTouchInput {
      swipeLeft()
      swipeUp()
      pinch(
          start0 = center - Offset(10f, 0f),
          end0 = center - Offset(width / 3f, 0f),
          start1 = center + Offset(10f, 0f),
          end1 = center + Offset(width / 3f, 0f),
      )
      rotate(degrees = 90f, radius = width / 4f)
      doubleClick()
    }

    // The controls still receive real touches through the viewer's pointer handling.
    assertPosition(1)
    composeRule.onNodeWithTag(C.Tag.photo_viewer_next).performTouchInput { click() }
    assertPosition(2)
    composeRule.onNodeWithTag(C.Tag.photo_viewer_previous).performTouchInput { click() }
    assertPosition(1)
    composeRule.onNodeWithTag(C.Tag.photo_viewer_close).performTouchInput { click() }

    composeRule.onNodeWithTag(C.Tag.photo_viewer).assertDoesNotExist()
    val after = cameraState()
    assertEquals(camera.center.latitude(), after.center.latitude(), 1e-9)
    assertEquals(camera.center.longitude(), after.center.longitude(), 1e-9)
    assertEquals(camera.zoom, after.zoom, 1e-9)
    assertEquals(camera.bearing, after.bearing, 1e-9)
    assertEquals(camera.pitch, after.pitch, 1e-9)

    // Long, beside the marker in the middle: on CI's 320x640 screen, shorter swipes did not pan the
    // map.
    composeRule.onNodeWithTag(C.Tag.trip_map_map).performTouchInput {
      swipe(
          start = Offset(width * 0.25f, height * 0.9f),
          end = Offset(width * 0.25f, height * 0.1f),
      )
    }
    composeRule.waitUntil(timeoutMillis = MAP_TEST_TIMEOUT_MILLIS) {
      cameraState().center != camera.center
    }
  }

  /**
   * Shows the trip map route with a gallery holding two photos taken at the same spot in Lisbon.
   */
  private fun showTripWithPhotos() {
    val trip = lisbonTripMapState.trip
    val photoRepository =
        FakePhotoRepository().apply { setUploaded(listOf(photo("newest", 2), photo("older", 1))) }
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

  /** Turns two fingers around the center by [degrees], [radius] away from it on either side. */
  private fun TouchInjectionScope.rotate(degrees: Float, radius: Float, steps: Int = 10) {
    fun finger(angleDegrees: Float) =
        Math.toRadians(angleDegrees.toDouble()).let {
          center + Offset(radius * cos(it).toFloat(), radius * sin(it).toFloat())
        }
    down(0, finger(0f))
    down(1, finger(180f))
    for (step in 1..steps) {
      val angle = degrees * step / steps
      updatePointerTo(0, finger(angle))
      updatePointerTo(1, finger(180f + angle))
      move()
    }
    up(0)
    up(1)
  }

  private fun assertPosition(index: Int) {
    composeRule
        .onNodeWithTag(C.Tag.photo_viewer_position)
        .assertTextEquals(composeRule.activity.getString(R.string.photo_viewer_position, index, 2))
  }

  private fun cameraState(): CameraState = composeRule.runOnIdle {
    composeRule.findMapView().mapboxMap.cameraState
  }

  private fun photo(id: String, takenAtMillis: Long) =
      TripPhoto(id, "alex", takenAtMillis, LISBON_LATITUDE, LISBON_LONGITUDE, byteArrayOf(1, 2, 3))

  private companion object {
    val MARKER_TAG = photoMarkerTag(MapCoordinate(LISBON_LATITUDE, LISBON_LONGITUDE))
  }
}
