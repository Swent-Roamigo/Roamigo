// Implemented with assistance from OpenAI Codex.
package com.swent.roamigo.ui.map

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Timestamp
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraState
import com.mapbox.maps.MapView
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.gestures.gestures
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.authentication.AuthenticatedUser
import com.swent.roamigo.model.authentication.FakeAuthRepository
import com.swent.roamigo.model.connectivity.ConnectivityRepository
import com.swent.roamigo.model.location.DeviceLocation
import com.swent.roamigo.model.location.LocationRepository
import com.swent.roamigo.model.location.MemberLocation
import com.swent.roamigo.model.photo.FakePhotoRepository
import com.swent.roamigo.model.trip.FakeTripRepository
import com.swent.roamigo.model.trip.TravelMode
import com.swent.roamigo.model.trip.Trip
import com.swent.roamigo.model.trip.TripRepository
import com.swent.roamigo.model.trip.TripStatus
import com.swent.roamigo.model.trip.activity.FakeActivityRepository
import com.swent.roamigo.model.trip.voting.InMemoryVoteRepository
import com.swent.roamigo.model.users.FakeUserRepository
import com.swent.roamigo.model.users.User
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.RoamigoTheme
import com.swent.roamigo.ui.trip.TripRoute
import java.time.Instant
import kotlin.math.abs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.hamcrest.Matcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real camera with controlled UI state and the shared local map style harness. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class TripMapCameraTest {
  @get:Rule
  val composeRule =
      createAndroidComposeRule<ComponentActivity>(effectContext = UnconfinedTestDispatcher())

  private val now = Instant.now().epochSecond
  private val trip =
      Trip(
          uid = "camera-trip",
          name = "Lisbon",
          description = null,
          destinations = listOf(TripLocation(38.7223, -9.1393, "Lisbon")),
          startDate = Timestamp(now - 86_400, 0),
          endDate = Timestamp(now + 604_800, 0),
          flexibleDates = false,
          travelMode = TravelMode.GROUP,
          travelerCount = 1,
          ownerId = "member",
          memberIds = listOf("member"),
          status = TripStatus.ACTIVE,
          createdAt = Timestamp(0, 0),
      )
  private val loadedState =
      TripMapUiState.Loaded(
          trip = trip,
          viewport = TripMapViewport.Centered(38.7223, -9.1393),
          members = emptyMap(),
          activitiesLoadState = ActivitiesLoadState.LOADED,
      )
  private val distantMarkers =
      listOf(
          marker("london", 51.5074, -0.1278, TripMarkerKind.ACTIVITY),
          marker("rome", 41.9028, 12.4964, TripMarkerKind.SUGGESTION),
      )
  private val distantState =
      loadedState.copy(
          activityMarkers = distantMarkers,
          viewport =
              tripMapViewportOf(
                  trip.destinations.map { MapCoordinate(it.latitude, it.longitude) } +
                      distantMarkers.map { MapCoordinate(it.latitude, it.longitude) }
              ),
      )
  private val uiState = mutableStateOf<TripMapUiState>(TripMapUiState.Loading)

  @Test
  fun lateActivitiesExpandFramingBeforeUserInteraction() {
    setScreen()
    val before = camera()

    composeRule.runOnIdle { uiState.value = distantState }

    waitForAllLocations()
    assertTrue(camera().zoom < before.zoom)
    composeRule.onNodeWithTag(C.Tag.trip_map_show_all_locations).assertDoesNotExist()
  }

  @Test
  fun showAllLocationsFitsTheLatestBoundsAndCanBeUsedAgainAfterAnotherGesture() {
    setScreen()
    pan()
    val moved = camera()
    composeRule.onNodeWithTag(C.Tag.trip_map_show_all_locations).assertDoesNotExist()

    composeRule.runOnIdle {
      uiState.value =
          distantState.copy(
              activityMarkers = distantMarkers.take(1),
              viewport =
                  tripMapViewportOf(
                      listOf(MapCoordinate(38.7223, -9.1393), MapCoordinate(51.5074, -0.1278))
                  ),
          )
    }
    composeRule.onNodeWithTag(C.Tag.trip_map_show_all_locations).assertIsDisplayed()
    composeRule.runOnIdle { uiState.value = distantState }
    assertCameraEquals(moved)

    composeRule.onNodeWithTag(C.Tag.trip_map_show_all_locations).performClick()

    waitForAllLocations()
    composeRule.onNodeWithTag(C.Tag.trip_map_show_all_locations).assertDoesNotExist()
    pan()
    composeRule.runOnIdle { uiState.value = loadedState }
    composeRule.onNodeWithTag(C.Tag.trip_map_show_all_locations).performClick()
    composeRule.waitUntil(MAP_TEST_TIMEOUT_MILLIS) {
      val current = camera()
      abs(current.center.latitude() - 38.7223) < CAMERA_TOLERANCE &&
          abs(current.center.longitude() + 9.1393) < CAMERA_TOLERANCE &&
          abs(current.zoom - SINGLE_POINT_ZOOM) < CAMERA_TOLERANCE
    }
    composeRule.onNodeWithTag(C.Tag.trip_map_show_all_locations).assertDoesNotExist()
  }

  @Test
  fun tappingTheMapStillAllowsAutomaticFraming() {
    setScreen()
    composeRule.onNodeWithTag(C.Tag.trip_map_map).performTouchInput {
      click(Offset(width * 0.5f, height * 0.7f))
    }

    composeRule.runOnIdle { uiState.value = distantState }

    waitForAllLocations()
  }

  @Test
  fun panIsPreservedThroughActivityUpdatesFailuresAndRetries() {
    setScreen()

    assertGestureIsPreserved { moveCamera(Offset(0.25f, 0.7f) to Offset(0.65f, 0.7f)) }
  }

  @Test
  fun pinchZoomIsPreservedThroughActivityUpdates() {
    setScreen()
    val initialZoom = camera().zoom

    assertGestureIsPreserved {
      moveCamera(
          Offset(0.3f, 0.4f) to Offset(0.4f, 0.5f),
          Offset(0.7f, 0.8f) to Offset(0.6f, 0.7f),
      )
    }
    assertTrue(abs(camera().zoom - initialZoom) > 0.1)
  }

  @Test
  fun rotationIsPreservedThroughActivityUpdates() {
    setScreen()
    composeRule.runOnIdle {
      composeRule.findMapView().gestures.updateSettings {
        pinchToZoomEnabled = false
        scrollEnabled = false
        pitchEnabled = false
      }
    }

    assertGestureIsPreserved {
      moveCamera(
          Offset(0.25f, 0.6f) to Offset(0.5f, 0.4f),
          Offset(0.75f, 0.6f) to Offset(0.5f, 0.8f),
      )
    }
  }

  @Test
  fun doubleTapZoomIsPreservedThroughActivityUpdates() {
    setScreen()
    val before = camera().zoom
    composeRule.onNodeWithTag(C.Tag.trip_map_map).performTouchInput {
      doubleClick(Offset(width * 0.5f, height * 0.7f))
    }
    composeRule.waitUntil(MAP_TEST_TIMEOUT_MILLIS) { camera().zoom > before + 0.1 }
    stopCameraAnimations()
    val moved = camera()

    composeRule.runOnIdle { uiState.value = distantState }

    assertCameraEquals(moved)
  }

  @Test
  fun gestureBeforeTripCoordinatesArrivePreventsInitialReframing() {
    setScreen(initialState = TripMapUiState.Loading)
    pan()
    val moved = camera()

    composeRule.runOnIdle { uiState.value = loadedState }

    assertCameraEquals(moved)
  }

  @Test
  fun manualCameraIsPreservedAfterStateRestoration() {
    val restorationTester = StateRestorationTester(composeRule)
    setScreen(restorationTester = restorationTester)
    pan()
    val moved = camera()

    restorationTester.emulateSavedInstanceStateRestore()
    composeRule.loadLocalMapStyle()
    assertCameraEquals(moved)
    composeRule.runOnIdle { uiState.value = distantState }

    assertCameraEquals(moved)
  }

  @Test
  fun tripReadinessKeepsTheMapAndManualCameraWhenSharingControlsAppear() {
    val pendingTrip = CompletableDeferred<List<Trip>>()
    val trips =
        object : TripRepository by FakeTripRepository() {
          override suspend fun getTrips(): List<Trip> = pendingTrip.await()
        }
    composeRule.setContent {
      RoamigoTheme {
        TripRoute(
            tripId = trip.uid,
            fromHistory = false,
            openMap = true,
            authRepository =
                FakeAuthRepository(
                    AuthenticatedUser("member", "Member", null, null),
                    initiallySignedIn = true,
                ),
            tripRepository = trips,
            activityRepository = FakeActivityRepository(),
            userRepository = FakeUserRepository(User("member", "Member", null)),
            voteRepository = InMemoryVoteRepository(),
            photoRepository = FakePhotoRepository(composeRule.activity.cacheDir),
            connectivityRepository =
                object : ConnectivityRepository {
                  override val isOnline: Flow<Boolean> = flowOf(true)
                },
            locationRepository = EmptyLocationRepository,
            onBack = {},
            onAuthenticationRequired = {},
            onOpenVote = { _, _ -> },
            onCreateVote = {},
            onMembersClick = {},
            onTripsClick = {},
            onMapClick = {},
            onFriendsClick = {},
        )
      }
    }
    composeRule.loadLocalMapStyle()
    val initialMap = composeRule.runOnIdle { composeRule.findMapView() }
    pan()
    val moved = camera()

    composeRule.runOnIdle { pendingTrip.complete(listOf(trip)) }

    composeRule.waitUntil(MAP_TEST_TIMEOUT_MILLIS) {
      composeRule
          .onAllNodesWithTag(C.Tag.trip_map_sharing_banner)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
    composeRule.onNodeWithTag(C.Tag.trip_map_sharing_banner).assertIsDisplayed()
    composeRule.runOnIdle { assertSame(initialMap, composeRule.findMapView()) }
    assertCameraEquals(moved)
  }

  private fun assertGestureIsPreserved(gesture: () -> Unit) {
    val before = camera()
    gesture()
    waitForCameraMovement(before)
    stopCameraAnimations()
    val moved = camera()

    composeRule.runOnIdle { uiState.value = distantState }
    assertCameraEquals(moved)
    composeRule.runOnIdle {
      uiState.value = distantState.copy(activitiesLoadState = ActivitiesLoadState.ERROR)
    }
    composeRule.onNodeWithTag(C.Tag.trip_map_retry_activities).performClick()
    assertCameraEquals(moved)
    composeRule.runOnIdle { uiState.value = loadedState }
    assertCameraEquals(moved)
    composeRule.runOnIdle { uiState.value = TripMapUiState.Loading }
    composeRule.runOnIdle { uiState.value = loadedState }
    assertCameraEquals(moved)
  }

  private fun pan() {
    val before = camera()
    moveCamera(Offset(0.25f, 0.7f) to Offset(0.65f, 0.7f))
    waitForCameraMovement(before)
    stopCameraAnimations()
  }

  private fun waitForCameraMovement(before: CameraState) {
    composeRule.waitUntil(MAP_TEST_TIMEOUT_MILLIS) {
      val current = camera()
      current.center != before.center ||
          abs(current.zoom - before.zoom) > CAMERA_TOLERANCE ||
          abs(current.bearing - before.bearing) > CAMERA_TOLERANCE ||
          abs(current.pitch - before.pitch) > CAMERA_TOLERANCE
    }
  }

  // Mapbox needs real animation frames and a minimum movement speed to recognize a pinch.
  private fun moveCamera(vararg paths: Pair<Offset, Offset>) {
    onView(isAssignableFrom(MapView::class.java))
        .perform(
            object : ViewAction {
              override fun getDescription() = "Move the map with one or two fingers"

              override fun getConstraints(): Matcher<View> = isAssignableFrom(MapView::class.java)

              override fun perform(uiController: UiController, view: View) {
                val origin = IntArray(2).also(view::getLocationOnScreen)
                val downTime = SystemClock.uptimeMillis()
                val properties =
                    Array(paths.size) { index ->
                      MotionEvent.PointerProperties().apply {
                        id = index
                        toolType = MotionEvent.TOOL_TYPE_FINGER
                      }
                    }

                fun inject(action: Int, fraction: Float, count: Int) {
                  val coordinates =
                      Array(count) { index ->
                        val (start, end) = paths[index]
                        MotionEvent.PointerCoords().apply {
                          x = origin[0] + (start.x + (end.x - start.x) * fraction) * view.width
                          y = origin[1] + (start.y + (end.y - start.y) * fraction) * view.height
                          pressure = 1f
                          size = 1f
                        }
                      }
                  val event =
                      MotionEvent.obtain(
                          downTime,
                          SystemClock.uptimeMillis(),
                          action,
                          count,
                          properties,
                          coordinates,
                          0,
                          0,
                          1f,
                          1f,
                          0,
                          0,
                          InputDevice.SOURCE_TOUCHSCREEN,
                          0,
                      )
                  try {
                    check(uiController.injectMotionEvent(event))
                  } finally {
                    event.recycle()
                  }
                }

                inject(MotionEvent.ACTION_DOWN, 0f, 1)
                if (paths.size == 2) {
                  inject(MotionEvent.ACTION_POINTER_DOWN or (1 shl 8), 0f, 2)
                }
                repeat(6) { step ->
                  uiController.loopMainThreadForAtLeast(16)
                  inject(MotionEvent.ACTION_MOVE, (step + 1) / 6f, paths.size)
                }
                if (paths.size == 2) {
                  inject(MotionEvent.ACTION_POINTER_UP or (1 shl 8), 1f, 2)
                }
                inject(MotionEvent.ACTION_UP, 1f, 1)
                uiController.loopMainThreadUntilIdle()
              }
            }
        )
  }

  private fun stopCameraAnimations() {
    composeRule.runOnIdle { composeRule.findMapView().camera.cancelAllAnimators() }
    composeRule.waitForIdle()
  }

  private fun setScreen(
      initialState: TripMapUiState = loadedState,
      restorationTester: StateRestorationTester? = null,
  ) {
    if (restorationTester == null) composeRule.setContent { Screen() }
    else restorationTester.setContent { Screen() }
    composeRule.loadLocalMapStyle()
    composeRule.runOnIdle { uiState.value = initialState }
    if (initialState is TripMapUiState.Loaded) {
      composeRule.waitUntil(MAP_TEST_TIMEOUT_MILLIS) {
        abs(camera().zoom - SINGLE_POINT_ZOOM) < CAMERA_TOLERANCE
      }
    }
  }

  @Composable
  private fun Screen() {
    RoamigoTheme {
      TripMapScreen(
          uiState = uiState.value,
          isSharing = false,
          readOnly = true,
          onRetry = {},
          onRetryActivities = {
            val state = uiState.value as TripMapUiState.Loaded
            uiState.value = state.copy(activitiesLoadState = ActivitiesLoadState.LOADING)
          },
          onTurnOnSharing = {},
          onPauseSharing = {},
      )
    }
  }

  private fun camera(): CameraState = composeRule.runOnIdle {
    composeRule.findMapView().mapboxMap.cameraState
  }

  private fun assertCameraEquals(expected: CameraState) {
    val actual = camera()
    assertEquals(expected.center.latitude(), actual.center.latitude(), CAMERA_TOLERANCE)
    assertEquals(expected.center.longitude(), actual.center.longitude(), CAMERA_TOLERANCE)
    assertEquals(expected.zoom, actual.zoom, CAMERA_TOLERANCE)
    assertEquals(expected.bearing, actual.bearing, CAMERA_TOLERANCE)
    assertEquals(expected.pitch, actual.pitch, CAMERA_TOLERANCE)
  }

  private fun waitForAllLocations() {
    composeRule.waitUntil(MAP_TEST_TIMEOUT_MILLIS) {
      composeRule.runOnIdle {
        val mapView = composeRule.findMapView()
        val coordinates =
            trip.destinations.map { Point.fromLngLat(it.longitude, it.latitude) } +
                distantMarkers.map { Point.fromLngLat(it.longitude, it.latitude) }
        coordinates.all {
          val pixel = mapView.mapboxMap.pixelForCoordinate(it)
          pixel.x in 0.0..mapView.width.toDouble() && pixel.y in 0.0..mapView.height.toDouble()
        }
      }
    }
  }

  private fun marker(id: String, latitude: Double, longitude: Double, kind: TripMarkerKind) =
      TripMarkerUiState(
          id = id,
          tripId = trip.uid,
          title = id,
          locationName = id,
          latitude = latitude,
          longitude = longitude,
          kind = kind,
      )

  private object EmptyLocationRepository : LocationRepository {
    override fun hasLocationPermission(): Boolean = false

    override suspend fun getCurrentLocation(): DeviceLocation? = null

    override suspend fun startSharing(tripId: String) = Unit

    override suspend fun stopSharing(tripId: String) = Unit

    override suspend fun stopAllSharing() = Unit

    override suspend fun restoreSharing() = Unit

    override fun observeMemberLocations(tripId: String): Flow<List<MemberLocation>> =
        flowOf(emptyList())

    override fun isSharing(tripId: String): Flow<Boolean> = flowOf(false)
  }

  private companion object {
    const val CAMERA_TOLERANCE = 0.00001
  }
}
