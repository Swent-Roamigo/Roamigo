package com.swent.roamigo.ui.trip.history

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.R
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.RoamigoTheme
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w800dp-h1600dp")
class TripHistoryScreenTest {

  @get:Rule val composeRule = createComposeRule()

  private val rome =
      TripHistoryItemUiModel(
          id = "rome",
          name = "Summer in Italy",
          destinations = listOf("Rome", "Florence"),
          dateRange = "5 Jun 2016 – 12 Jun 2016",
          travelerCount = 3,
      )

  private fun show(
      state: TripHistoryUiState,
      onBackClick: () -> Unit = {},
      onOpenWorldMapClick: () -> Unit = {},
      onTripsClick: () -> Unit = {},
      onMapClick: () -> Unit = {},
      onPhotosClick: () -> Unit = {},
      onFriendsClick: () -> Unit = {},
      onRetryClick: () -> Unit = {},
  ) {
    composeRule.setContent {
      RoamigoTheme {
        TripHistoryScreen(
            uiState = state,
            onBackClick = onBackClick,
            onOpenWorldMapClick = onOpenWorldMapClick,
            onTripsClick = onTripsClick,
            onMapClick = onMapClick,
            onPhotosClick = onPhotosClick,
            onFriendsClick = onFriendsClick,
            onRetryClick = onRetryClick,
        )
      }
    }
  }

  @Test
  fun displaysCompletedTripsAndTheirDetails() {
    val paris =
        rome.copy(
            id = "paris",
            name = "Paris 2010",
            destinations = listOf("Paris"),
            travelerCount = 2,
        )
    show(
        TripHistoryUiState(
            isLoading = false,
            trips = listOf(rome, paris),
        )
    )

    composeRule.onNodeWithText("2 completed trips").assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trip_history_card_prefix + "rome").assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trip_history_card_prefix + "paris").assertIsDisplayed()
    composeRule.onNodeWithText("Summer in Italy").assertIsDisplayed()
    composeRule.onNodeWithText("Rome · Florence").assertIsDisplayed()
    composeRule.onNodeWithText("5 Jun 2016 – 12 Jun 2016 · 3 travelers").assertIsDisplayed()
    composeRule.onAllNodesWithText("Completed").assertCountEquals(2)
  }

  @Test
  fun displaysEmptyState() {
    show(TripHistoryUiState(isLoading = false))

    composeRule.onNodeWithText("0 completed trips").assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trip_history_empty).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trip_history_world_map).assertDoesNotExist()
  }

  @Test
  fun loadingDoesNotDisplayEmptyState() {
    show(TripHistoryUiState())

    composeRule.onNodeWithTag(C.Tag.trip_history_loading).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trip_history_empty).assertDoesNotExist()
  }

  @Test
  fun errorDoesNotDisplayEmptyState() {
    show(
        TripHistoryUiState(
            isLoading = false,
            errorMessage = "Unable to load trip history.",
        )
    )

    composeRule.onNodeWithTag(C.Tag.trip_history_error).assertIsDisplayed()
    composeRule.onNodeWithText("Unable to load trip history.").assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trip_history_empty).assertDoesNotExist()
  }

  @Test
  fun forwardsNavigationActionsToCaller() {
    var backClicks = 0
    var worldMapClicks = 0
    var tripsClicks = 0
    var mapClicks = 0
    var photosClicks = 0
    var friendsClicks = 0

    show(
        state = TripHistoryUiState(isLoading = false, trips = listOf(rome)),
        onBackClick = { backClicks++ },
        onOpenWorldMapClick = { worldMapClicks++ },
        onTripsClick = { tripsClicks++ },
        onMapClick = { mapClicks++ },
        onPhotosClick = { photosClicks++ },
        onFriendsClick = { friendsClicks++ },
    )

    composeRule.onNodeWithTag(C.Tag.trip_history_back).performClick()
    composeRule.onNodeWithTag(C.Tag.trip_history_world_map).performClick()
    composeRule.onNodeWithTag(C.Tag.trips_navigation_trips).performClick()
    composeRule.onNodeWithTag(C.Tag.trips_navigation_map).performClick()
    composeRule.onNodeWithTag(C.Tag.trips_navigation_photos).performClick()
    composeRule.onNodeWithTag(C.Tag.trips_navigation_friends).performClick()

    Assert.assertEquals(1, backClicks)
    Assert.assertEquals(1, worldMapClicks)
    Assert.assertEquals(1, tripsClicks)
    Assert.assertEquals(1, mapClicks)
    Assert.assertEquals(1, photosClicks)
    Assert.assertEquals(1, friendsClicks)
  }

  @Test
  fun worldMapButtonRemainsVisibleWithLongHistory() {
    val manyTrips =
        (1..30).map { index ->
          rome.copy(
              id = "trip-$index",
              name = "Trip $index",
          )
        }

    show(
        TripHistoryUiState(
            isLoading = false,
            trips = manyTrips,
        )
    )

    composeRule.onNodeWithTag(C.Tag.trip_history_world_map).assertIsDisplayed()
  }

  @Test
  fun displaysFallbackWhenDestinationIsMissing() {
    show(
        TripHistoryUiState(
            isLoading = false,
            trips = listOf(rome.copy(destinations = emptyList())),
        )
    )

    composeRule.onNodeWithText("Destination unavailable").assertIsDisplayed()
  }

  @Test
  fun worldMapPlaceholderShowsNotImplementedFeedback() {
    composeRule.setContent {
      RoamigoTheme {
        TripHistoryScreen(
            uiState =
                TripHistoryUiState(
                    isLoading = false,
                    trips = listOf(rome),
                )
        )
      }
    }

    ShadowToast.reset()

    composeRule.onNodeWithTag(C.Tag.trip_history_world_map).assertIsDisplayed().performClick()

    val expectedMessage =
        RuntimeEnvironment.getApplication().getString(R.string.feature_not_implemented)

    composeRule.runOnIdle {
      Assert.assertEquals(
          expectedMessage,
          ShadowToast.getTextOfLatestToast(),
      )
    }
  }

  @Test
  fun blankErrorDisplaysFallbackMessage() {
    show(
        TripHistoryUiState(
            isLoading = false,
            errorMessage = "   ",
        )
    )

    composeRule.onNodeWithTag(C.Tag.trip_history_error).assertIsDisplayed()

    composeRule.onNodeWithText("Unable to load trip history.").assertIsDisplayed()
  }

  @Test
  fun retryButtonInvokesCallback() {
    var retryClicks = 0

    show(
        state =
            TripHistoryUiState(
                isLoading = false,
                errorMessage = "Unable to load trip history.",
            ),
        onRetryClick = { retryClicks++ },
    )

    composeRule.onNodeWithTag(C.Tag.trip_history_retry).assertIsDisplayed().performClick()

    Assert.assertEquals(1, retryClicks)
  }
}
