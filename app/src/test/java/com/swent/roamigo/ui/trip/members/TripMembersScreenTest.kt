// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.members

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
class TripMembersScreenTest {
  @get:Rule val composeRule = createComposeRule()
  private var backs = 0
  private var retries = 0

  private fun show(state: TripMembersUiState) {
    composeRule.setContent {
      RoamigoTheme {
        TripMembersScreen(
            state,
            currentUserId = "alex",
            onBack = { backs++ },
            onRetry = { retries++ },
        )
      }
    }
  }

  @Test
  fun anEmojiAtTheStartOfANameIsShownWholeAsTheInitial() {
    show(
        TripMembersUiState.Loaded(
            listOf(TripMemberUiModel("sam", "\uD83D\uDE00 Sam", isOrganiser = false))
        )
    )

    // The initial is the whole emoji, not half of its surrogate pair.
    composeRule.onNodeWithText("\uD83D\uDE00", useUnmergedTree = true).assertIsDisplayed()
  }

  @Test
  fun listsMembersWithRolesAndMarksTheCurrentUser() {
    show(
        TripMembersUiState.Loaded(
            listOf(
                TripMemberUiModel("alex", "Alex", isOrganiser = true),
                TripMemberUiModel("sam", "Sam", isOrganiser = false),
                TripMemberUiModel("ghost", "", isOrganiser = false),
            )
        )
    )

    composeRule.onNodeWithText("Alex (you)").assertIsDisplayed()
    composeRule.onNodeWithText("Organiser").assertIsDisplayed()
    composeRule.onNodeWithText("Sam").assertIsDisplayed()
    composeRule.onNodeWithText("Unknown traveler").assertIsDisplayed()
    composeRule.onNodeWithText("3 members").assertIsDisplayed()
  }

  @Test
  fun backAndRetryAreForwardedToTheCaller() {
    show(TripMembersUiState.Error)

    composeRule.onNodeWithText("Unable to load members. Please try again.").assertIsDisplayed()
    composeRule.onNodeWithText("Retry").performClick()
    composeRule.onNodeWithTag(C.Tag.trip_members_back).performClick()

    composeRule.runOnIdle {
      assertEquals(1, retries)
      assertEquals(1, backs)
    }
  }
}
