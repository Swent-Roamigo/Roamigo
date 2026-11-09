// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.voting

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.model.trip.voting.VoteStatus
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.RoamigoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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
  ) {
    composeRule.setContent {
      RoamigoTheme {
        VotesScreen(
            state = state,
            tripName = "Lisbon & Porto",
            onOpenVote = onOpenVote,
            onCreateVote = onCreateVote,
            onRetry = onRetry,
        )
      }
    }
  }

  @Test
  fun multipleCreatorCardsCloseOnlyTheirOwnVoteAndCancelLeavesListUnchanged() {
    var selected by mutableStateOf<String?>(null)
    val requested = mutableListOf<String>()
    val state =
        VotesUiState.Ready(
            listOf(live, other),
            emptyList(),
            liveSummaries =
                mapOf(
                    live.uid to LiveVoteSummary(emptyMap(), false, true),
                    other.uid to LiveVoteSummary(emptyMap(), true, true),
                ),
        )
    composeRule.setContent {
      RoamigoTheme {
        VotesScreen(
            state,
            "Trip",
            {},
            {},
            {},
            onCloseVote = {
              requested += it
              selected = it
            },
        )
        selected?.let {
          VoteCloseDialogContent(
              VoteUiState.Ready(if (it == live.uid) live else other, isCreator = true),
              {},
              {},
              { selected = null },
          )
        }
      }
    }
    composeRule.onNodeWithTag(C.Tag.close_vote(other.uid)).performScrollTo().performClick()
    composeRule.onNodeWithTag(C.Tag.vote_close_dialog).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.vote_dialog).assertDoesNotExist()
    composeRule.onNodeWithText("Cancel").performClick()
    composeRule.onNodeWithTag(C.Tag.close_vote(live.uid)).performScrollTo().performClick()
    composeRule.onNodeWithTag(C.Tag.vote_dialog).assertDoesNotExist()
    composeRule.onNodeWithText("Cancel").performClick()
    composeRule.runOnIdle { assertEquals(listOf(other.uid, live.uid), requested) }
    composeRule.onNodeWithTag(C.Tag.vote_close_dialog).assertDoesNotExist()
    composeRule.onNodeWithTag(C.Tag.close_vote(live.uid)).assertIsDisplayed()
  }

  @Test
  fun cardsRenderOwnCountsAndVoteOrChangeVoteActions() {
    val opened = mutableListOf<String>()
    content(
        VotesUiState.Ready(
            listOf(live, other),
            emptyList(),
            liveSummaries =
                mapOf(
                    live.uid to LiveVoteSummary(mapOf("a" to 0, "b" to 2), false),
                    other.uid to LiveVoteSummary(mapOf("a" to 1, "b" to 0), true),
                ),
        ),
        onOpenVote = opened::add,
    )
    composeRule
        .onNodeWithTag(C.Tag.vote_count(live.uid, "a"), useUnmergedTree = true)
        .assertTextEquals("0 votes")
    composeRule
        .onNodeWithTag(C.Tag.vote_count(live.uid, "b"), useUnmergedTree = true)
        .assertTextEquals("2 votes")
    composeRule
        .onNodeWithTag(C.Tag.vote_count(other.uid, "a"), useUnmergedTree = true)
        .assertTextEquals("1 vote")
    composeRule
        .onNodeWithTag(C.Tag.vote_count(other.uid, "b"), useUnmergedTree = true)
        .assertTextEquals("0 votes")
    for (vote in listOf(live, other)) {
      composeRule.onNodeWithText(vote.question).assertHasNoClickAction()
      composeRule.onNodeWithText(vote.question).onParent().assertHasNoClickAction()
    }
    composeRule.onNodeWithText("Vote").performClick()
    composeRule.onNodeWithText("Change vote").performClick()
    composeRule.runOnIdle { assertEquals(listOf(live.uid, other.uid), opened) }
  }

  @Test
  fun loadingShowsProgressAndCurrentTrip() {
    content(VotesUiState.Loading)
    composeRule.onNodeWithText("New vote").assertDoesNotExist()
    composeRule.onNodeWithText("Loading votes…").assertIsDisplayed()
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
  fun multipleVotesRenderLiveAndUnresolvedClosedCards() {
    content(
        VotesUiState.Ready(
            listOf(live, other),
            listOf(closed),
            finalSummaries =
                mapOf(
                    closed.uid to
                        com.swent.roamigo.model.trip.voting.calculateVoteResult(
                            closed.options,
                            emptyList(),
                        )
                ),
        )
    )
    composeRule.onNodeWithText("New vote").assertIsDisplayed()
    composeRule.onNodeWithText("Live").assertDoesNotExist()
    composeRule.onAllNodesWithText("Live vote").assertCountEquals(2)
    composeRule.onNodeWithText(live.question).assertIsDisplayed()
    composeRule.onNodeWithText(other.question).assertIsDisplayed()
    composeRule.onNodeWithText("Decided").assertDoesNotExist()
    composeRule.onNodeWithText(closed.question).performScrollTo().assertIsDisplayed()
    composeRule
        .onNode(hasText(closed.question) and hasText("Vote closed · no votes"))
        .assertIsDisplayed()
  }

  @Test
  fun openingEachVoteThroughItsActionOrClosedRowPassesItsOwnVoteId() {
    val opened = mutableListOf<String>()
    content(
        VotesUiState.Ready(
            listOf(live, other),
            listOf(closed),
            liveSummaries = mapOf(other.uid to LiveVoteSummary(emptyMap(), true)),
        ),
        onOpenVote = opened::add,
    )
    composeRule.onNodeWithText("Vote").performClick()
    composeRule.onNodeWithText("Change vote").performClick()
    composeRule.onNodeWithText(closed.question).performScrollTo().performClick()
    composeRule.runOnIdle { assertEquals(listOf(live.uid, other.uid, closed.uid), opened) }
  }

  @Test
  fun closedOnlySnapshotDoesNotShowLiveSectionOrInventResults() {
    content(VotesUiState.Ready(emptyList(), listOf(closed)))
    composeRule.onNodeWithText("Live").assertDoesNotExist()
    composeRule.onNodeWithText("Decided").assertDoesNotExist()
    composeRule.onNodeWithText("Vote closed · loading results…").assertIsDisplayed()
    composeRule.onNodeWithText(closed.question).assertIsDisplayed()
    composeRule.onNodeWithText("agreed", substring = true).assertDoesNotExist()
    composeRule.onNodeWithText("majority", substring = true).assertDoesNotExist()
  }

  @Test
  fun liveOnlySnapshotShowsOptionsAndOmitsDecidedSection() {
    content(VotesUiState.Ready(listOf(live), emptyList()))
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
    composeRule.setContent {
      RoamigoTheme {
        VotesScreen(
            state = state,
            tripName = "Trip",
            onOpenVote = {},
            onCreateVote = {},
            onRetry = {},
        )
      }
    }
    composeRule.onNodeWithText("No votes yet").assertIsDisplayed()
    composeRule.runOnIdle { state = VotesUiState.Ready(listOf(live), emptyList()) }
    composeRule.onNodeWithText(live.question).assertIsDisplayed()
    composeRule.onNodeWithText("No votes yet").assertDoesNotExist()
    composeRule.runOnIdle { state = VotesUiState.Ready(emptyList(), listOf(closed)) }
    composeRule.onNodeWithText(live.question).assertDoesNotExist()
    composeRule.onNodeWithText(closed.question).assertIsDisplayed()
  }
}
