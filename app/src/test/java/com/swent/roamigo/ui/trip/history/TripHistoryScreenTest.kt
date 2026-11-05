// AI assistance was used for implementation support and code review.
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
import java.time.LocalDate
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
          startDate = LocalDate.of(2016, 6, 5),
          endDate = LocalDate.of(2016, 6, 12),
          travelerCount = 3,
      )

  private fun show(
      state: TripHistoryUiState,
      onOpenTrip: (String) -> Unit = {},
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
            onOpenTrip = onOpenTrip,
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

    show(TripHistoryUiState.Success(listOf(rome, paris)))

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
    show(TripHistoryUiState.Success(emptyList()))

    composeRule.onNodeWithText("0 completed trips").assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trip_history_empty).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trip_history_world_map).assertDoesNotExist()
  }

  @Test
  fun loadingDoesNotDisplayEmptyState() {
    show(TripHistoryUiState.Loading)

    composeRule.onNodeWithTag(C.Tag.trip_history_loading).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trip_history_empty).assertDoesNotExist()
    composeRule.onNodeWithTag(C.Tag.trip_history_world_map).assertDoesNotExist()
  }

  @Test
  fun errorDoesNotDisplayEmptyState() {
    show(TripHistoryUiState.Error("Unable to load trip history."))

    composeRule.onNodeWithTag(C.Tag.trip_history_error).assertIsDisplayed()
    composeRule.onNodeWithText("Unable to load trip history.").assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trip_history_empty).assertDoesNotExist()
    composeRule.onNodeWithTag(C.Tag.trip_history_world_map).assertDoesNotExist()
  }

  @Test
  fun forwardsNavigationActionsToCaller() {
    var backClicks = 0
    var worldMapClicks = 0
    var tripsClicks = 0
    var mapClicks = 0
    var friendsClicks = 0

    show(
        state = TripHistoryUiState.Success(listOf(rome)),
        onBackClick = { backClicks++ },
        onOpenWorldMapClick = { worldMapClicks++ },
        onTripsClick = { tripsClicks++ },
        onMapClick = { mapClicks++ },
        onFriendsClick = { friendsClicks++ },
    )

    composeRule.onNodeWithTag(C.Tag.trip_history_back).performClick()
    composeRule.onNodeWithTag(C.Tag.trip_history_world_map).performClick()
    composeRule.onNodeWithTag(C.Tag.trips_navigation_trips).performClick()
    composeRule.onNodeWithTag(C.Tag.trips_navigation_map).performClick()
    composeRule.onNodeWithTag(C.Tag.trips_navigation_photos).assertDoesNotExist()
    composeRule.onNodeWithTag(C.Tag.trips_navigation_friends).performClick()

    Assert.assertEquals(1, backClicks)
    Assert.assertEquals(1, worldMapClicks)
    Assert.assertEquals(1, tripsClicks)
    Assert.assertEquals(1, mapClicks)
    Assert.assertEquals(1, friendsClicks)
  }

  @Test
  fun clickingCompletedTripPassesCorrectId() {
    val openedTripIds = mutableListOf<String>()
    val paris = rome.copy(id = "paris", name = "Paris Trip")

    show(
        state = TripHistoryUiState.Success(listOf(rome, paris)),
        onOpenTrip = { tripId -> openedTripIds.add(tripId) },
    )

    composeRule.onNodeWithTag(C.Tag.trip_history_card_prefix + "paris").performClick()

    Assert.assertEquals(listOf("paris"), openedTripIds)

    composeRule.onNodeWithTag(C.Tag.trip_history_card_prefix + "rome").performClick()

    Assert.assertEquals(listOf("paris", "rome"), openedTripIds)
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

    show(TripHistoryUiState.Success(manyTrips))

    composeRule.onNodeWithTag(C.Tag.trip_history_world_map).assertIsDisplayed()
  }

  @Test
  fun displaysFallbackWhenDestinationIsMissing() {
    show(TripHistoryUiState.Success(listOf(rome.copy(destinations = emptyList()))))

    composeRule.onNodeWithText("Destination unavailable").assertIsDisplayed()
  }

  @Test
  fun worldMapPlaceholderShowsNotImplementedFeedback() {
    composeRule.setContent {
      RoamigoTheme {
        TripHistoryScreen(
            uiState = TripHistoryUiState.Success(listOf(rome)),
            onOpenTrip = {},
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
    show(TripHistoryUiState.Error("   "))

    composeRule.onNodeWithTag(C.Tag.trip_history_error).assertIsDisplayed()
    composeRule.onNodeWithText("Unable to load trip history.").assertIsDisplayed()
  }

  @Test
  fun retryButtonInvokesCallback() {
    var retryClicks = 0

    show(
        state = TripHistoryUiState.Error("Unable to load trip history."),
        onRetryClick = { retryClicks++ },
    )

    composeRule.onNodeWithTag(C.Tag.trip_history_retry).assertIsDisplayed().performClick()

    Assert.assertEquals(1, retryClicks)
  }
}
