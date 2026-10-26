// AI assistance was used for implementation support.
package com.swent.roamigo.ui.map

import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.core.view.descendants
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Timestamp
import com.mapbox.maps.MapView
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.trip.FakeTripRepository
import com.swent.roamigo.model.trip.TravelMode
import com.swent.roamigo.model.trip.Trip
import com.swent.roamigo.model.trip.TripRepository
import com.swent.roamigo.model.trip.TripStatus
import com.swent.roamigo.ui.theme.RoamigoTheme
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class TripOverviewMapScreenTest {
  // Mapbox annotation cleanup must run immediately on the UI lifecycle thread.
  @get:Rule
  val composeRule =
      createAndroidComposeRule<ComponentActivity>(effectContext = UnconfinedTestDispatcher())

  @Test
  fun mapMarkersTriggerClickCallback() {
    val sampleTrip =
        Trip(
            uid = "trip_1",
            name = "Eurotrip",
            description = "Exploring Europe",
            destinations =
                listOf(
                    TripLocation(latitude = 48.8566, longitude = 2.3522, name = "Paris"),
                    TripLocation(latitude = 41.3851, longitude = 2.1734, name = "Barcelona"),
                ),
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
    val pendingTrips = CompletableDeferred<List<Trip>>()
    val repository =
        object : TripRepository by FakeTripRepository() {
          override suspend fun getTrips(): List<Trip> = pendingTrips.await()
        }
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

    val firstMarkerId =
        sampleTrip.destinations.first().let { "trip_1_${it.name}_${it.latitude}_${it.longitude}" }

    composeRule.waitUntil(timeoutMillis = MAP_TEST_TIMEOUT_MILLIS) {
      composeRule
          .onAllNodesWithTag(TripMarkerTestTags.markerFor(firstMarkerId))
          .fetchSemanticsNodes()
          .isNotEmpty()
    }

    composeRule.onNodeWithTag(TripMarkerTestTags.markerFor(firstMarkerId)).performClick()
    assertEquals("trip_1", clickedTripId)
  }
}

internal const val MAP_TEST_TIMEOUT_MILLIS = 15_000L

/**
 * Makes marker tests independent of remote styles and waits for native map dimensions to settle.
 */
internal fun AndroidComposeTestRule<*, ComponentActivity>.loadLocalMapStyle() {
  val styleLoaded = AtomicBoolean(false)
  val mapRendered = AtomicBoolean(false)
  val mapboxMap = runOnIdle {
    val contentView = activity.findViewById<ViewGroup>(android.R.id.content)
    contentView.descendants.filterIsInstance<MapView>().single().mapboxMap
  }
  val frameSubscription = runOnIdle {
    mapboxMap.subscribeRenderFrameFinished { if (styleLoaded.get()) mapRendered.set(true) }
  }
  try {
    runOnIdle {
      // View annotations need a rendered style, but these tests do not need basemap tiles or fonts.
      mapboxMap.loadStyle("""{"version":8,"sources":{},"layers":[]}""") { styleLoaded.set(true) }
    }
    waitUntil(timeoutMillis = MAP_TEST_TIMEOUT_MILLIS) { mapRendered.get() }
  } finally {
    runOnUiThread { frameSubscription.cancel() }
  }
}
