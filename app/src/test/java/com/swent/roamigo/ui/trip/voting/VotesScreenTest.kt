// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.voting

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.model.trip.voting.VoteStatus
import com.swent.roamigo.ui.isNotImplemented
import com.swent.roamigo.ui.theme.RoamigoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowToast

@RunWith(AndroidJUnit4::class)
class VotesScreenTest {
  @get:Rule val composeRule = createComposeRule()
  private val live = interactionVote()
  private val other = live.copy(uid = "other", question = "Where next?")
  private val closed = live.copy(uid = "closed", question = "Museum?", status = VoteStatus.CLOSED)

  private fun content(
      state: VotesUiState,
      onOpenVote: (String) -> Unit = {},
      onRetry: () -> Unit = {},
      onCreateVote: () -> Unit = {},
      onTripsClick: () -> Unit = {},
  ) {
    composeRule.setContent {
      RoamigoTheme {
        VotesScreenContent(state, "Lisbon & Porto", onOpenVote, onCreateVote, onRetry, onTripsClick)
      }
    }
  }

  @Test
  fun loadingShowsProgressAndCurrentTrip() {
    content(VotesUiState.Loading)
    composeRule.onNodeWithText("New vote").assertDoesNotExist()
    composeRule.onNodeWithText("Loading votes…").assertIsDisplayed()
    composeRule.onNodeWithText("Lisbon & Porto").assertIsDisplayed()
    composeRule
        .onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
        .assertIsDisplayed()
    composeRule.onNodeWithText("No votes yet").assertDoesNotExist()
  }

  @Test
  fun emptyStateExplainsTheListAndOffersCreation() {
    var creations = 0
    content(VotesUiState.Empty, onCreateVote = { creations++ })
    composeRule.onNodeWithText("No votes yet").assertIsDisplayed()
    composeRule
        .onNodeWithText("Start a vote to decide together with your trip members.")
        .assertIsDisplayed()
    composeRule.onNodeWithText("New vote").performClick()
    composeRule.runOnIdle { assertEquals(1, creations) }
    composeRule.onNodeWithText("Live").assertDoesNotExist()
    composeRule.onNodeWithText("Decided").assertDoesNotExist()
  }

  @Test
  fun errorShowsRetryAndInvokesCallback() {
    var retries = 0
    content(VotesUiState.Error, onRetry = { retries++ })
    composeRule.onNodeWithText("New vote").assertDoesNotExist()
    composeRule.onNodeWithText("Unable to load votes. Please try again.").assertIsDisplayed()
    composeRule.onNodeWithText("Retry").performClick()
    composeRule.runOnIdle { assertEquals(1, retries) }
    composeRule.onNodeWithText("No votes yet").assertDoesNotExist()
  }

  @Test
  fun multipleVotesRenderWithLiveLabelsAndCompactDecidedRows() {
    content(VotesUiState.Ready(listOf(live, other), listOf(closed)))
    composeRule.onNodeWithText("New vote").assertIsDisplayed()
    composeRule.onNodeWithText("Lisbon & Porto · 2 open").assertIsDisplayed()
    composeRule.onNodeWithText("Live").assertDoesNotExist()
    composeRule.onAllNodesWithText("Live vote").assertCountEquals(2)
    composeRule.onNodeWithText(live.question).assertIsDisplayed()
    composeRule.onNodeWithText(other.question).assertIsDisplayed()
    composeRule.onNodeWithText("Decided").assertIsDisplayed()
    composeRule.onNodeWithText(closed.question).assertIsDisplayed()
    composeRule.onNode(hasText(closed.question) and hasText("Closed vote")).assertIsDisplayed()
  }

  @Test
  fun openingEachCardPassesItsOwnVoteId() {
    val opened = mutableListOf<String>()
    content(VotesUiState.Ready(listOf(live, other), listOf(closed)), onOpenVote = opened::add)
    composeRule.onNodeWithText(live.question).performClick()
    composeRule.onNodeWithText(other.question).performClick()
    composeRule.onNodeWithText(closed.question).performClick()
    composeRule.runOnIdle { assertEquals(listOf(live.uid, other.uid, closed.uid), opened) }
  }

  @Test
  fun closedOnlySnapshotDoesNotShowLiveSectionOrInventResults() {
    content(VotesUiState.Ready(emptyList(), listOf(closed)))
    composeRule.onNodeWithText("Lisbon & Porto · 0 open").assertIsDisplayed()
    composeRule.onNodeWithText("Live").assertDoesNotExist()
    composeRule.onNodeWithText("Decided").assertIsDisplayed()
    composeRule.onNodeWithText(closed.question).assertIsDisplayed()
    composeRule.onNodeWithText("agreed", substring = true).assertDoesNotExist()
    composeRule.onNodeWithText("majority", substring = true).assertDoesNotExist()
  }

  @Test
  fun liveOnlySnapshotShowsOptionsAndOmitsDecidedSection() {
    content(VotesUiState.Ready(listOf(live), emptyList()))
    composeRule.onNodeWithText("Lisbon & Porto · 1 open").assertIsDisplayed()
    composeRule.onNodeWithText("Market").assertIsDisplayed()
    composeRule.onNodeWithText("Restaurant").assertIsDisplayed()
    composeRule.onNodeWithText("Decided").assertDoesNotExist()
  }

  @Test
  fun synchronizationFailureKeepsCardsVisibleAndOffersRetry() {
    var retries = 0
    var creations = 0
    content(
        VotesUiState.Ready(listOf(live), emptyList(), VotesSyncState.FAILED),
        onRetry = { retries++ },
        onCreateVote = { creations++ },
    )
    composeRule.onNodeWithText("New vote").performClick()
    composeRule.runOnIdle { assertEquals(1, creations) }
    composeRule
        .onNodeWithText("Unable to update votes. Showing the last available votes.")
        .assertIsDisplayed()
    composeRule.onNodeWithText(live.question).assertIsDisplayed()
    composeRule.onNodeWithText("Retry").performClick()
    composeRule.runOnIdle { assertEquals(1, retries) }
  }

  @Test
  fun reconnectingKeepsCardsWithoutShowingInitialLoadingOrAnotherRetry() {
    content(VotesUiState.Ready(listOf(live), emptyList(), VotesSyncState.RETRYING))
    composeRule.onNodeWithText(live.question).assertIsDisplayed()
    composeRule.onNodeWithText("Reconnecting…").assertIsDisplayed()
    composeRule.onNodeWithText("Loading votes…").assertDoesNotExist()
    composeRule.onNodeWithText("Retry").assertDoesNotExist()
  }

  @Test
  fun stateUpdatesReplaceTheList() {
    var state by mutableStateOf<VotesUiState>(VotesUiState.Empty)
    composeRule.setContent { RoamigoTheme { VotesScreenContent(state, "Trip", {}, {}, {}, {}) } }
    composeRule.onNodeWithText("No votes yet").assertIsDisplayed()
    composeRule.runOnIdle { state = VotesUiState.Ready(listOf(live), emptyList()) }
    composeRule.onNodeWithText(live.question).assertIsDisplayed()
    composeRule.onNodeWithText("No votes yet").assertDoesNotExist()
    composeRule.runOnIdle { state = VotesUiState.Ready(emptyList(), listOf(closed)) }
    composeRule.onNodeWithText(live.question).assertDoesNotExist()
    composeRule.onNodeWithText(closed.question).assertIsDisplayed()
  }

  @Test
  fun votesTabIsSelectedAndTripsNavigationInvokesCallback() {
    var backs = 0
    content(VotesUiState.Empty, onTripsClick = { backs++ })
    composeRule
        .onNode(hasText("Votes") and androidx.compose.ui.test.isSelectable())
        .assertIsSelected()
    for (label in listOf("Overview", "Itinerary")) {
      ShadowToast.reset()
      composeRule.onNodeWithText(label).assertIsEnabled().assert(isNotImplemented).performClick()
      composeRule.runOnIdle {
        assertEquals("Not implemented yet", ShadowToast.getTextOfLatestToast())
      }
      composeRule
          .onNode(hasText("Votes") and androidx.compose.ui.test.isSelectable())
          .assertIsSelected()
          .assert(isNotImplemented.not())
    }
    composeRule.onNodeWithText("Trips").performClick()
    composeRule.runOnIdle { assertEquals(1, backs) }
  }
}
