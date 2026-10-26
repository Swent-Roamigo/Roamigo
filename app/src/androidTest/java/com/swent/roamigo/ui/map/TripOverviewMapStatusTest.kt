// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.map

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.Timestamp
import com.swent.roamigo.R
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.trip.FakeTripRepository
import com.swent.roamigo.model.trip.TravelMode
import com.swent.roamigo.model.trip.Trip
import com.swent.roamigo.model.trip.TripRepository
import com.swent.roamigo.model.trip.TripStatus
import com.swent.roamigo.ui.theme.RoamigoTheme
import kotlinx.coroutines.CompletableDeferred
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
  private val retryLabel = targetContext.getString(R.string.trip_map_retry)

  private val sampleTrip =
      Trip(
          uid = "trip_1",
          name = "Paris trip",
          description = null,
          destinations =
              listOf(TripLocation(latitude = 48.8566, longitude = 2.3522, name = "Paris")),
          startDate = Timestamp(0, 0),
          endDate = Timestamp(0, 0),
          flexibleDates = false,
          travelMode = TravelMode.SOLO,
          travelerCount = 1,
          ownerId = "user_1",
          memberIds = listOf("user_1"),
          status = TripStatus.PLANNED,
          createdAt = Timestamp(0, 0),
      )

  private val markerTag = TripMarkerTestTags.markerFor("trip_1_Paris_48.8566_2.3522")

  @Test
  fun loadingShowsProgressUntilDestinationsAreLoaded() {
    val pendingTrips = CompletableDeferred<List<Trip>>()
    val repository =
        object : TripRepository by FakeTripRepository() {
          override suspend fun getTrips(): List<Trip> = pendingTrips.await()
        }

    setMapContent(repository)

    waitForText(loadingMessage)
    composeRule.onNodeWithText(emptyMessage).assertDoesNotExist()
    composeRule.onNodeWithText(retryLabel).assertDoesNotExist()

    composeRule.runOnIdle { pendingTrips.complete(listOf(sampleTrip)) }

    waitForMarker()
    composeRule.onNodeWithText(loadingMessage).assertDoesNotExist()
    composeRule.onNodeWithText(emptyMessage).assertDoesNotExist()
  }

  @Test
  fun successfulEmptyResultShowsEmptyMessage() {
    setMapContent(FakeTripRepository())

    waitForText(emptyMessage)
    composeRule.onNodeWithText(loadingMessage).assertDoesNotExist()
    composeRule.onNodeWithText(retryLabel).assertDoesNotExist()
  }

  @Test
  fun failedLoadShowsErrorAndRetryLoadsDestinations() {
    val errorMessage = "Unable to load destinations"
    val pendingRetry = CompletableDeferred<List<Trip>>()
    var requests = 0
    val repository =
        object : TripRepository by FakeTripRepository() {
          override suspend fun getTrips(): List<Trip> {
            requests++
            if (requests == 1) throw IllegalStateException(errorMessage)
            return pendingRetry.await()
          }
        }

    setMapContent(repository)

    waitForText(errorMessage)
    composeRule.onNodeWithText(retryLabel).assertIsDisplayed()
    composeRule.onNodeWithText(loadingMessage).assertDoesNotExist()
    composeRule.onNodeWithText(emptyMessage).assertDoesNotExist()

    composeRule.onNodeWithText(retryLabel).performClick()

    waitForText(loadingMessage)
    composeRule.onNodeWithText(errorMessage).assertDoesNotExist()
    composeRule.onNodeWithText(retryLabel).assertDoesNotExist()
    composeRule.onNodeWithText(emptyMessage).assertDoesNotExist()

    composeRule.runOnIdle {
      assertEquals(2, requests)
      pendingRetry.complete(listOf(sampleTrip))
    }

    waitForMarker()
    composeRule.onNodeWithText(errorMessage).assertDoesNotExist()
    composeRule.onNodeWithText(loadingMessage).assertDoesNotExist()
    composeRule.onNodeWithText(emptyMessage).assertDoesNotExist()
    composeRule.onNodeWithText(retryLabel).assertDoesNotExist()
  }

  private fun setMapContent(repository: TripRepository) {
    composeRule.setContent {
      RoamigoTheme {
        TripOverviewMapScreen(viewModel = viewModel { TripOverviewMapViewModel(repository) })
      }
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
