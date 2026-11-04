// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.trip.voting

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.RoamigoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VotesScreenPhotosTabTest {
  @get:Rule val composeRule = createComposeRule()

  @Test
  fun thePhotosTabOpensThePhotosOfTheTrip() {
    var photosClicks = 0
    composeRule.setContent {
      RoamigoTheme {
        VotesScreenContent(
            state = VotesUiState.Empty,
            tripName = "Lisbon",
            onOpenVote = {},
            onCreateVote = {},
            onRetry = {},
            onTripsClick = {},
            onPhotosClick = { photosClicks++ },
        )
      }
    }

    composeRule.onNodeWithTag(C.Tag.trips_navigation_photos).performClick()

    assertEquals(1, photosClicks)
  }

  @Test
  fun thePhotosTabStillWorksAsAPlaceholderUntilItIsConnected() {
    composeRule.setContent {
      RoamigoTheme {
        VotesScreenContent(
            state = VotesUiState.Empty,
            tripName = "Lisbon",
            onOpenVote = {},
            onCreateVote = {},
            onRetry = {},
            onTripsClick = {},
        )
      }
    }

    composeRule.onNodeWithTag(C.Tag.trips_navigation_photos).performClick()
  }
}
