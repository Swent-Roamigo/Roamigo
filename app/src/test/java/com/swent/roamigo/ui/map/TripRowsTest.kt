// AI assistance was used for implementation support.
package com.swent.roamigo.ui.map

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.RoamigoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TripRowsTest {
  @get:Rule val composeRule = createComposeRule()

  private fun marker(id: String, tripId: String, title: String, place: String) =
      TripMarkerUiState(
          id = id,
          tripId = tripId,
          title = title,
          locationName = place,
          latitude = 0.0,
          longitude = 0.0,
      )

  @Test
  fun eachTripGetsOneRowListingItsDestinationsInOrder() {
    composeRule.setContent {
      RoamigoTheme {
        TripRows(
            markers =
                listOf(
                    marker("a1", "trip-a", "Portugal", "Lisbon"),
                    marker("a2", "trip-a", "Portugal", "Porto"),
                    marker("b1", "trip-b", "Japan", "Kyoto"),
                ),
            onTripClick = {},
        )
      }
    }

    composeRule.onNodeWithTag(C.Tag.trip_overview_row_prefix + "trip-a").assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trip_overview_row_prefix + "trip-b").assertIsDisplayed()
    composeRule.onNodeWithText("Lisbon → Porto").assertIsDisplayed()
    composeRule.onNodeWithText("Kyoto").assertIsDisplayed()
  }

  @Test
  fun clickingARowOpensThatTrip() {
    val opened = mutableListOf<String>()
    composeRule.setContent {
      RoamigoTheme {
        TripRows(
            markers =
                listOf(
                    marker("a1", "trip-a", "Portugal", "Lisbon"),
                    marker("b1", "trip-b", "Japan", "Kyoto"),
                ),
            onTripClick = { opened += it },
        )
      }
    }

    composeRule.onNodeWithTag(C.Tag.trip_overview_row_prefix + "trip-b").performClick()

    assertEquals(listOf("trip-b"), opened)
  }

  @Test
  fun withoutTripsNoRowIsShown() {
    composeRule.setContent { RoamigoTheme { TripRows(markers = emptyList(), onTripClick = {}) } }

    composeRule.onNodeWithTag(C.Tag.trip_overview_row_prefix + "trip-a").assertDoesNotExist()
  }
}
