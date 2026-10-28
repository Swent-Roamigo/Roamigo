// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.map

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swent.roamigo.R
import com.swent.roamigo.ui.theme.RoamigoTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class TripOverviewMapStatusTest {
  // Mapbox annotation cleanup must run immediately on the UI lifecycle thread.
  @get:Rule
  val composeRule =
      createAndroidComposeRule<ComponentActivity>(effectContext = UnconfinedTestDispatcher())

  private val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
  private val loadingMessage = targetContext.getString(R.string.trip_map_loading)
  private val emptyMessage = targetContext.getString(R.string.trip_map_empty)
  private val errorMessage = targetContext.getString(R.string.trip_map_load_error)
  private val retryLabel = targetContext.getString(R.string.trip_map_retry)

  private val marker =
      TripMarkerUiState(
          id = "paris_marker",
          tripId = "trip_1",
          tripTitle = "Paris trip",
          locationName = "Paris",
          latitude = 48.8566,
          longitude = 2.3522,
      )
  private val loadedState = TripOverviewMapUiState(markers = listOf(marker))
  private val uiState = mutableStateOf(TripOverviewMapUiState())
  private val markerTag = TripMarkerTestTags.markerFor(marker.id)

  @Test
  fun loadingStateShowsProgressUntilMarkersAreProvided() {
    setMapContent(TripOverviewMapUiState(isLoading = true))

    waitForText(loadingMessage)
    composeRule.onNodeWithText(emptyMessage).assertDoesNotExist()
    composeRule.onNodeWithText(retryLabel).assertDoesNotExist()

    composeRule.runOnIdle { uiState.value = loadedState }

    waitForMarker()
    composeRule.onNodeWithText(loadingMessage).assertDoesNotExist()
    composeRule.onNodeWithText(emptyMessage).assertDoesNotExist()
  }

  @Test
  fun emptyStateShowsEmptyMessage() {
    setMapContent(TripOverviewMapUiState())

    waitForText(emptyMessage)
    composeRule.onNodeWithText(loadingMessage).assertDoesNotExist()
    composeRule.onNodeWithText(retryLabel).assertDoesNotExist()
  }

  @Test
  fun errorStateShowsGenericMessageAndRetryInvokesCallback() {
    val repositoryErrorMessage = "Missing or invalid destinations"
    var retryRequests = 0
    setMapContent(
        initialState = TripOverviewMapUiState(errorMessage = repositoryErrorMessage),
        onRetry = {
          retryRequests++
          uiState.value = TripOverviewMapUiState(isLoading = true)
        },
    )

    waitForText(errorMessage)
    composeRule.onNodeWithText(repositoryErrorMessage).assertDoesNotExist()
    composeRule.onNodeWithText(retryLabel).assertIsDisplayed()
    composeRule.onNodeWithText(loadingMessage).assertDoesNotExist()
    composeRule.onNodeWithText(emptyMessage).assertDoesNotExist()

    composeRule.onNodeWithText(retryLabel).performClick()

    waitForText(loadingMessage)
    composeRule.onNodeWithText(errorMessage).assertDoesNotExist()
    composeRule.onNodeWithText(retryLabel).assertDoesNotExist()
    composeRule.onNodeWithText(emptyMessage).assertDoesNotExist()

    composeRule.runOnIdle {
      assertEquals(1, retryRequests)
      uiState.value = loadedState
    }

    waitForMarker()
    composeRule.onNodeWithText(errorMessage).assertDoesNotExist()
    composeRule.onNodeWithText(loadingMessage).assertDoesNotExist()
    composeRule.onNodeWithText(emptyMessage).assertDoesNotExist()
    composeRule.onNodeWithText(retryLabel).assertDoesNotExist()
  }

  private fun setMapContent(initialState: TripOverviewMapUiState, onRetry: () -> Unit = {}) {
    uiState.value = initialState
    composeRule.setContent {
      RoamigoTheme { TripOverviewMapScreen(uiState = uiState.value, onRetry = onRetry) }
    }
    composeRule.loadLocalMapStyle()
  }

  private fun waitForText(text: String) {
    composeRule.waitUntil(timeoutMillis = 5_000) {
      composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithText(text).assertIsDisplayed()
  }

  private fun waitForMarker() {
    composeRule.waitUntil(timeoutMillis = MAP_TEST_TIMEOUT_MILLIS) {
      composeRule.onAllNodesWithTag(markerTag).fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag(markerTag).assertExists()
  }
}
