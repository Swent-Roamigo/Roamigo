// AI assistance was used for implementation support.
package com.swent.roamigo.ui.map

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Timestamp
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.trip.FakeTripRepository
import com.swent.roamigo.model.trip.TravelMode
import com.swent.roamigo.model.trip.Trip
import com.swent.roamigo.model.trip.TripRepository
import com.swent.roamigo.model.trip.TripStatus
import com.swent.roamigo.ui.components.NotImplemented
import com.swent.roamigo.ui.theme.RoamigoTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private val isNotImplemented = SemanticsMatcher.expectValue(NotImplemented, true)

/** Tests that unconnected marker actions in TripOverviewMapScreen show unsupported feedback. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class TripOverviewMapUnsupportedActionsTest {
  // Mapbox annotation cleanup must run immediately on the UI lifecycle thread.
  @get:Rule
  val composeRule =
      createAndroidComposeRule<ComponentActivity>(effectContext = UnconfinedTestDispatcher())

  private val pendingTrips = CompletableDeferred<List<Trip>>()
  private val repository =
      object : TripRepository by FakeTripRepository() {
        override suspend fun getTrips(): List<Trip> = pendingTrips.await()
      }

  private val sampleTrip =
      Trip(
          uid = "trip_1",
          name = "Eurotrip",
          description = "Exploring Europe",
          destinations =
              listOf(TripLocation(latitude = 48.8566, longitude = 2.3522, name = "Paris")),
          startDate = Timestamp.now(),
          endDate = Timestamp.now(),
          flexibleDates = false,
          travelMode = TravelMode.GROUP,
          travelerCount = 3,
          ownerId = "user_1",
          memberIds = listOf("user_1"),
          status = TripStatus.PLANNED,
          createdAt = Timestamp.now(),
      )

  @Test
  fun mapMarkerUnconnectedActionShowsFeedback() {
    composeRule.setContent {
      RoamigoTheme {
        TripOverviewMapScreen(
            viewModel = viewModel { TripOverviewMapViewModel(repository) },
        )
      }
    }

    composeRule.loadLocalMapStyle()
    composeRule.runOnIdle { pendingTrips.complete(listOf(sampleTrip)) }

    val firstLocation = sampleTrip.destinations.first()
    val firstMarkerId =
        "trip_1_${firstLocation.name}_${firstLocation.latitude}_${firstLocation.longitude}"
    val markerTag = TripMarkerTestTags.markerFor(firstMarkerId)

    composeRule.waitUntil(timeoutMillis = MAP_TEST_TIMEOUT_MILLIS) {
      composeRule.onAllNodesWithTag(markerTag).fetchSemanticsNodes().isNotEmpty()
    }

    composeRule.onNodeWithTag(markerTag).assert(isNotImplemented).performClick()
  }

  @Test
  fun connectedMarkerActionInvokesCallbackWithoutNotImplemented() {
    var clickedTripId: String? = null
    composeRule.setContent {
      RoamigoTheme {
        TripOverviewMapScreen(
            viewModel = viewModel { TripOverviewMapViewModel(repository) },
            onMarkerClick = { tripId -> clickedTripId = tripId },
        )
      }
    }

    composeRule.loadLocalMapStyle()
    composeRule.runOnIdle { pendingTrips.complete(listOf(sampleTrip)) }

    val firstLocation = sampleTrip.destinations.first()
    val firstMarkerId =
        "trip_1_${firstLocation.name}_${firstLocation.latitude}_${firstLocation.longitude}"
    val markerTag = TripMarkerTestTags.markerFor(firstMarkerId)

    composeRule.waitUntil(timeoutMillis = MAP_TEST_TIMEOUT_MILLIS) {
      composeRule.onAllNodesWithTag(markerTag).fetchSemanticsNodes().isNotEmpty()
    }

    composeRule.onNodeWithTag(markerTag).assert(isNotImplemented.not()).performClick()

    assertEquals("trip_1", clickedTripId)
  }
}
