// AI assistance was used for implementation support.
package com.swent.roamigo.ui.map

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.ui.components.NotImplemented
import com.swent.roamigo.ui.theme.RoamigoTheme
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

  private val marker =
      TripMarkerUiState(
          id = "paris_marker",
          tripId = "trip_1",
          tripTitle = "Eurotrip",
          locationName = "Paris",
          latitude = 48.8566,
          longitude = 2.3522,
      )
  private val uiState = mutableStateOf(TripOverviewMapUiState(isLoading = true))

  @Test
  fun connectedMarkerActionInvokesCallbackWithoutNotImplemented() {
    var clickedTripId: String? = null
    composeRule.setContent {
      RoamigoTheme {
        TripOverviewMapScreen(
            uiState = uiState.value,
            onMarkerClick = { tripId -> clickedTripId = tripId },
        )
      }
    }

    composeRule.loadLocalMapStyle()
    composeRule.runOnIdle { uiState.value = TripOverviewMapUiState(markers = listOf(marker)) }

    val markerTag = TripMarkerTestTags.markerFor(marker.id)

    composeRule.waitUntil(timeoutMillis = MAP_TEST_TIMEOUT_MILLIS) {
      composeRule.onAllNodesWithTag(markerTag).fetchSemanticsNodes().isNotEmpty()
    }

    composeRule.onNodeWithTag(markerTag).assert(isNotImplemented.not()).performClick()

    assertEquals("trip_1", clickedTripId)
  }
}
