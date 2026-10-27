package com.swent.roamigo.ui.trip.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.ui.theme.RoamigoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TripTabsTest {
  @get:Rule val composeRule = createComposeRule()

  @Test
  fun navigationCallbacksAndSelectionFollowTheCaller() {
    var selected by mutableStateOf(TripTab.OVERVIEW)
    val visited = mutableListOf<TripTab>()
    composeRule.setContent {
      RoamigoTheme {
        TripTabs(
            selectedTab = selected,
            onOverviewClick = {
              visited.add(TripTab.OVERVIEW)
              selected = TripTab.OVERVIEW
            },
            onItineraryClick = {
              visited.add(TripTab.ITINERARY)
              selected = TripTab.ITINERARY
            },
            onVotesClick = {
              visited.add(TripTab.VOTES)
              selected = TripTab.VOTES
            },
        )
      }
    }
    composeRule.onNodeWithText("Overview").assertIsSelected()
    composeRule.onNodeWithText("Itinerary").performClick().assertIsSelected()
    composeRule.onNodeWithText("Votes").performClick().assertIsSelected()
    composeRule.onNodeWithText("Overview").performClick().assertIsSelected()
    composeRule.runOnIdle {
      assertEquals(listOf(TripTab.ITINERARY, TripTab.VOTES, TripTab.OVERVIEW), visited)
    }
  }

  @Test
  fun unavailableDestinationsAreDisabledAndCurrentTabRemainsSelected() {
    composeRule.setContent { RoamigoTheme { TripTabs(selectedTab = TripTab.VOTES) } }
    composeRule.onNodeWithText("Overview").assertIsNotEnabled()
    composeRule.onNodeWithText("Itinerary").assertIsNotEnabled()
    composeRule.onNodeWithText("Votes").performClick().assertIsSelected()
  }
}
