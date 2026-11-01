package com.swent.roamigo.ui.trip.history.components

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
class TripHistoryHeaderTest {
  @get:Rule val composeRule = createComposeRule()

  @Test
  fun headerShowsCountAndForwardsBackClick() {
    var backClicks = 0

    composeRule.setContent {
      RoamigoTheme {
        TripHistoryHeader(
            onBackClick = { backClicks++ },
            completedTripCount = 2,
        )
      }
    }

    composeRule.onNodeWithText("2 completed trips").assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trip_history_back).performClick()

    assertEquals(1, backClicks)
  }

  @Test
  fun headerHidesCountWhileLoadingOrOnError() {
    composeRule.setContent {
      RoamigoTheme {
        TripHistoryHeader(
            onBackClick = {},
            completedTripCount = null,
        )
      }
    }

    composeRule.onNodeWithText("Trip history").assertIsDisplayed()
    composeRule.onNodeWithText("0 completed trips").assertDoesNotExist()
    composeRule.onNodeWithTag(C.Tag.trip_history_back).assertIsDisplayed()
  }

  @Test
  fun headerDisplaysZeroForEmptyHistory() {
    composeRule.setContent {
      RoamigoTheme {
        TripHistoryHeader(
            onBackClick = {},
            completedTripCount = 0,
        )
      }
    }

    composeRule.onNodeWithText("0 completed trips").assertIsDisplayed()
  }
}
