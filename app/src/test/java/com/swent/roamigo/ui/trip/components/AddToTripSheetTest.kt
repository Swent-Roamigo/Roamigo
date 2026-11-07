// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.components

import androidx.compose.ui.test.assertIsDisplayed
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
class AddToTripSheetTest {
  @get:Rule val composeRule = createComposeRule()
  private val events = mutableListOf<String>()

  private fun show() {
    composeRule.setContent {
      RoamigoTheme {
        AddToTripSheet(
            onDismiss = { events += "dismiss" },
            onActivity = { events += "activity" },
            onSuggestion = { events += "suggestion" },
            onVote = { events += "vote" },
            onPhoto = { events += "photo" },
        )
      }
    }
  }

  @Test
  fun offersFourActionsWithAHintEach() {
    show()

    listOf(
            "Add an activity" to "Put something in the plan for a day",
            "Suggest a place" to "The group can vote on it",
            "Start a vote" to "Ask the group to decide between options",
            "Take a photo" to "Saved with its place and shared with the group",
        )
        .forEach { (title, hint) ->
          composeRule.onNodeWithText(title).assertIsDisplayed()
          composeRule.onNodeWithText(hint).assertIsDisplayed()
        }
  }

  @Test
  fun choosingAnActionClosesTheSheetThenRunsIt() {
    show()

    composeRule.onNodeWithText("Start a vote").performClick()

    composeRule.runOnIdle { assertEquals(listOf("dismiss", "vote"), events) }
  }
}
