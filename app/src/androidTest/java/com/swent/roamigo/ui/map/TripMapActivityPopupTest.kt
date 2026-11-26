// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.map

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Timestamp
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.photo.FakePhotoRepository
import com.swent.roamigo.model.trip.Activity
import com.swent.roamigo.model.trip.ActivityType
import com.swent.roamigo.model.trip.FakeTripRepository
import com.swent.roamigo.model.trip.TravelMode
import com.swent.roamigo.model.trip.Trip
import com.swent.roamigo.model.trip.TripStatus
import com.swent.roamigo.model.trip.activity.ActivityRepository
import com.swent.roamigo.model.trip.activity.FakeActivityRepository
import com.swent.roamigo.model.users.FakeUserRepository
import com.swent.roamigo.model.users.User
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.isNotImplemented
import com.swent.roamigo.ui.theme.RoamigoTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for the popup of a tapped activity marker, through [TripMapRoute] and its real
 * ViewModel, with a recording [UriNavigator] instead of other apps.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class TripMapActivityPopupTest {
  // Mapbox annotation cleanup must run immediately on the UI lifecycle thread.
  @get:Rule
  val composeRule =
      createAndroidComposeRule<ComponentActivity>(effectContext = UnconfinedTestDispatcher())

  private val activities = MutableStateFlow<List<Activity>>(emptyList())
  private val attemptedUris = mutableListOf<Uri>()
  private val installedApps = mutableStateOf<(Uri) -> Boolean>({ true })
  private val navigator = UriNavigator { uri ->
    attemptedUris += uri
    if (!installedApps.value(uri)) throw ActivityNotFoundException("No app for $uri")
  }

  @Test
  fun tappingMarkersOpensAndSwitchesThePopupAndTappingTheMapClosesIt() {
    setScreen(listOf(SOUTH, NORTH))

    tapMarker(SOUTH)
    waitForPopup(SOUTH.name)
    composeRule.onNodeWithTag(C.Tag.trip_map_activity_directions).assertIsDisplayed()
    assertPopupHangsBelowMarkerOf(SOUTH)
    composeRule.onNodeWithTag(C.Tag.trip_map_activity_details).assertIsDisplayed()

    // The north marker stands above the south one's popup, which hangs below its own marker.
    tapMarker(NORTH)
    waitForPopup(NORTH.name)
    composeRule.onAllNodesWithText(SOUTH.name).assertCountIs(0)
    composeRule.onAllNodesWithTag(C.Tag.trip_map_activity_popup).assertCountIs(1)

    tapEmptyMap()
    composeRule.waitUntil(timeoutMillis = MAP_TEST_TIMEOUT_MILLIS) {
      composeRule.onAllNodesWithTag(C.Tag.trip_map_activity_popup).fetchSemanticsNodes().isEmpty()
    }
  }

  @Test
  fun directionsOpenTheGeoUriOfTheSelectedActivityAndKeepThePopup() {
    setScreen(listOf(SOUTH))
    tapMarker(SOUTH)
    waitForPopup(SOUTH.name)

    composeRule.onNodeWithTag(C.Tag.trip_map_activity_directions).performClick()

    composeRule.runOnIdle {
      assertEquals(
          listOf("geo:0,0?q=38.7,-9.1393(Visit%20the%20south)"),
          attemptedUris.map { it.toString() },
      )
    }
    composeRule.onNodeWithTag(C.Tag.trip_map_activity_popup).assertIsDisplayed()
  }

  @Test
  fun directionsFallBackToTheBrowserAndSurviveWhenNoAppCanOpenThem() {
    installedApps.value = { false }
    setScreen(listOf(SOUTH))
    tapMarker(SOUTH)
    waitForPopup(SOUTH.name)

    composeRule.onNodeWithTag(C.Tag.trip_map_activity_directions).performClick()

    composeRule.runOnIdle {
      assertEquals(
          listOf(
              "geo:0,0?q=38.7,-9.1393(Visit%20the%20south)",
              "https://www.google.com/maps/dir/?api=1&destination=38.7,-9.1393",
          ),
          attemptedUris.map { it.toString() },
      )
    }
    // The map stays usable. The toast telling the user is checked by DirectionsTest.
    composeRule.onNodeWithTag(C.Tag.trip_map_activity_popup).assertIsDisplayed()
  }

  @Test
  fun detailsKeepsThePlaceholderFeedback() {
    setScreen(listOf(SOUTH))
    tapMarker(SOUTH)
    waitForPopup(SOUTH.name)

    composeRule
        .onNodeWithTag(C.Tag.trip_map_activity_details)
        .assert(isNotImplemented)
        .performClick()

    composeRule.runOnIdle { assertEquals(emptyList<Uri>(), attemptedUris) }
  }

  @Test
  fun popupClosesWhenItsActivityIsRemovedFromTheTrip() {
    setScreen(listOf(SOUTH, NORTH))
    tapMarker(SOUTH)
    waitForPopup(SOUTH.name)

    composeRule.runOnIdle { activities.value = listOf(NORTH.activity()) }

    composeRule.waitUntil(timeoutMillis = MAP_TEST_TIMEOUT_MILLIS) {
      composeRule.onAllNodesWithTag(C.Tag.trip_map_activity_popup).fetchSemanticsNodes().isEmpty()
    }
  }

  private fun setScreen(places: List<Place>) {
    val factory =
        TripMapViewModel.factory(
            tripId = TRIP.uid,
            tripRepository = FakeTripRepository(trips = listOf(TRIP)),
            activityRepository =
                object : ActivityRepository by FakeActivityRepository() {
                  override fun observeActivities(tripId: String): Flow<List<Activity>> = activities
                },
            userRepository = FakeUserRepository(User(USER_ID, "Alex", null)),
            photoRepository = FakePhotoRepository(),
        )
    composeRule.setContent {
      RoamigoTheme {
        TripMapRoute(
            tripId = TRIP.uid,
            tripMapViewModelFactory = factory,
            locationViewModelFactory = null,
            uriNavigator = navigator,
        )
      }
    }
    composeRule.loadLocalMapStyle()
    // Published once the local style is loaded, since loading a style resets the camera.
    composeRule.runOnIdle { activities.value = places.map { it.activity() } }
  }

  private fun tapMarker(place: Place) {
    val tag = markerTag(place)
    composeRule.waitUntil(timeoutMillis = MAP_TEST_TIMEOUT_MILLIS) {
      composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag(tag).performClick()
  }

  // The pin stands above its point, so the popup goes under it rather than covering it. Window
  // bounds are compared, since each map annotation is a separate composition.
  private fun assertPopupHangsBelowMarkerOf(place: Place) {
    val marker = composeRule.onNodeWithTag(markerTag(place)).fetchSemanticsNode().boundsInWindow
    val popup =
        composeRule.onNodeWithTag(C.Tag.trip_map_activity_popup).fetchSemanticsNode().boundsInWindow
    assertTrue(
        "Popup top ${popup.top} is above marker bottom ${marker.bottom}",
        popup.top >= marker.bottom,
    )
  }

  private fun markerTag(place: Place) =
      TripMarkerTestTags.markerFor("${TRIP.uid}_activity_${place.uid}")

  /** Taps the map near its left edge, away from the centered markers and their popups. */
  private fun tapEmptyMap() {
    composeRule.onNodeWithTag(C.Tag.trip_map_map).performTouchInput {
      click(Offset(width * 0.08f, height * 0.85f))
    }
  }

  private fun waitForPopup(name: String) {
    composeRule.waitUntil(timeoutMillis = MAP_TEST_TIMEOUT_MILLIS) {
      composeRule.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithText(name).assertIsDisplayed()
  }

  private fun SemanticsNodeInteractionCollection.assertCountIs(expected: Int) =
      assertEquals(expected, fetchSemanticsNodes().size)

  /** An activity of the trip, named after where it is. */
  private data class Place(val uid: String, val latitude: Double) {
    val name = "Visit the $uid"

    fun activity() =
        Activity(
            uid = uid,
            tripId = TRIP.uid,
            activityType = ActivityType.ACCEPTED,
            name = name,
            description = null,
            location = TripLocation(latitude = latitude, longitude = LONGITUDE, name = uid),
            startTime = Timestamp(0, 0),
            endTime = null,
            createdByUserId = USER_ID,
            creationTime = Timestamp(0, 0),
        )
  }

  private companion object {
    const val USER_ID = "user_1"
    const val LONGITUDE = -9.1393
    val SOUTH = Place("south", 38.7)
    val NORTH = Place("north", 38.75)
    val TRIP =
        Trip(
            uid = "trip_lisbon",
            name = "Lisbon",
            description = null,
            destinations = emptyList(),
            startDate = Timestamp(0, 0),
            endDate = Timestamp(0, 0),
            flexibleDates = false,
            travelMode = TravelMode.SOLO,
            travelerCount = 1,
            ownerId = USER_ID,
            memberIds = listOf(USER_ID),
            status = TripStatus.PLANNED,
            createdAt = Timestamp(0, 0),
        )
  }
}
