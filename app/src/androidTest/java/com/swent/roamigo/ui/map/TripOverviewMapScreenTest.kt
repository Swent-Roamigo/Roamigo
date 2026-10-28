// AI assistance was used for implementation support.
package com.swent.roamigo.ui.map

import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.core.view.descendants
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mapbox.maps.MapView
import com.swent.roamigo.ui.theme.RoamigoTheme
import java.util.concurrent.atomic.AtomicBoolean
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
    val markers =
        listOf(
            TripMarkerUiState(
                id = "paris_marker",
                tripId = "trip_1",
                tripTitle = "Eurotrip",
                locationName = "Paris",
                latitude = 48.8566,
                longitude = 2.3522,
            ),
            TripMarkerUiState(
                id = "barcelona_marker",
                tripId = "trip_1",
                tripTitle = "Eurotrip",
                locationName = "Barcelona",
                latitude = 41.3851,
                longitude = 2.1734,
            ),
        )
    val uiState = mutableStateOf(TripOverviewMapUiState(isLoading = true))
    var clickedTripId: String? = null
    composeRule.setContent {
      RoamigoTheme {
        TripOverviewMapScreen(
            uiState = uiState.value,
            onMarkerClick = { tripId -> clickedTripId = tripId },
            onRetry = {},
        )
      }
    }

    composeRule.loadLocalMapStyle()
    composeRule.runOnIdle { uiState.value = TripOverviewMapUiState(markers = markers) }

    val firstMarkerId = markers.first().id

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
