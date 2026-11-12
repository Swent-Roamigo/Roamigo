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
import com.swent.roamigo.model.trip.voting.VoteResult
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
      readOnly: Boolean = false,
      showHint: Boolean = false,
      onDismissHint: () -> Unit = {},
  ) {
    composeRule.setContent {
      RoamigoTheme {
        VotesScreen(
            state = state,
            tripName = "Lisbon & Porto",
            onOpenVote = onOpenVote,
            onCreateVote = onCreateVote,
            onRetry = onRetry,
            readOnly = readOnly,
            showHint = showHint,
            onDismissHint = onDismissHint,
        )
      }
    }
  }

  @Test
  fun eligibilityFeedbackRequiresAConfirmedUnresolvedTieAndRetainsRetry() {
    val tie = VoteResult(mapOf("a" to 1, "b" to 1), setOf("a", "b"))
    val initial =
        VotesUiState.Ready(emptyList(), listOf(closed), finalSummaries = mapOf(closed.uid to tie))
    var state by mutableStateOf<VotesUiState>(initial)
    var eligibility by mutableStateOf<ResolutionEligibility>(ResolutionEligibility.Loading)
    var retries = 0
    composeRule.setContent {
      RoamigoTheme {
        VotesScreen(
            state,
            "Trip",
            {},
            {},
            {},
            eligibility = eligibility,
            onRetryEligibility = { retries++ },
        )
      }
    }
    val messages =
        listOf(
            ResolutionEligibility.Loading to "Checking vote-resolution permissions…",
            ResolutionEligibility.Failed() to
                "Unable to check vote-resolution permissions. Please retry.",
            ResolutionEligibility.Ready(false, ResolutionRestriction.ENDED) to
                "This trip has ended. Vote decisions are unavailable.",
        )
    for ((access, message) in messages) {
      composeRule.runOnIdle {
        state = initial
        eligibility = access
      }
      composeRule.onNodeWithText(message).assertIsDisplayed()
      if (access is ResolutionEligibility.Failed) composeRule.onNodeWithText("Retry").performClick()
      for (withoutTie in
          listOf(
              VotesUiState.Loading,
              VotesUiState.Empty,
              VotesUiState.Error,
              VotesUiState.Ready(listOf(live), emptyList()),
              initial.copy(finalSummaries = emptyMap()),
              initial.copy(
                  finalSummaries =
                      mapOf(closed.uid to VoteResult(mapOf("a" to 0, "b" to 0), emptySet()))
              ),
              initial.copy(
                  finalSummaries =
                      mapOf(closed.uid to VoteResult(mapOf("a" to 2, "b" to 1), setOf("a")))
              ),
              initial.copy(closedVotes = listOf(closed.copy(resolvedOptionId = "missing"))),
              initial.copy(closedVotes = listOf(closed.copy(nextRoundVoteId = "round2"))),
          )) {
        composeRule.runOnIdle { state = withoutTie }
        composeRule.onNodeWithText(message).assertDoesNotExist()
      }
    }
    composeRule.runOnIdle { assertEquals(1, retries) }
  }

  @Test
  fun onlyConfirmedUnresolvedTiesExposeListActionsAndLinkedParentsStayHidden() {
    val tie = VoteResult(mapOf("a" to 1, "b" to 1), setOf("a", "b"))
    val initial =
        VotesUiState.Ready(emptyList(), listOf(closed), finalSummaries = mapOf(closed.uid to tie))
    var state by mutableStateOf(initial)
    val revotes = mutableListOf<VoteUiState.Closed>()
    val decisions = mutableListOf<VoteUiState.Closed>()
    val opened = mutableListOf<String>()
    composeRule.setContent {
      RoamigoTheme {
        VotesScreen(
            state,
            "Lisbon",
            opened::add,
            {},
            {},
            onRevote = revotes::add,
            onOwnerDecides = decisions::add,
        )
      }
    }
    composeRule.onNodeWithText("Revote").performScrollTo().performClick()
    composeRule.onNodeWithText("Owner decides").performScrollTo().performClick()
    composeRule.runOnIdle {
      assertEquals(listOf(VoteUiState.Closed(closed, tie)), revotes)
      assertEquals(revotes, decisions)
      assertEquals(emptyList<String>(), opened)
    }
    for (next in
        listOf(
            initial.copy(finalSummaries = emptyMap()),
            initial.copy(
                finalSummaries =
                    mapOf(closed.uid to VoteResult(mapOf("a" to 2, "b" to 1), setOf("a")))
            ),
            initial.copy(closedVotes = listOf(closed.copy(resolvedOptionId = "a"))),
            initial.copy(closedVotes = listOf(closed.copy(nextRoundVoteId = "round2"))),
        )) {
      composeRule.runOnIdle { state = next }
      composeRule.onNodeWithText("Revote").assertDoesNotExist()
      composeRule.onNodeWithText("Owner decides").assertDoesNotExist()
    }
    composeRule.onNodeWithText(closed.question).assertDoesNotExist()
    composeRule.onNodeWithText("Unresolved").assertDoesNotExist()
    composeRule.onNodeWithText("Decided").assertDoesNotExist()
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
    composeRule.onNodeWithTag(C.Tag.closeVote(other.uid)).performScrollTo().performClick()
    composeRule.onNodeWithTag(C.Tag.vote_close_dialog).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.vote_dialog).assertDoesNotExist()
    composeRule.onNodeWithText("Keep it open").performClick()
    composeRule.onNodeWithTag(C.Tag.closeVote(live.uid)).performScrollTo().performClick()
    composeRule.onNodeWithTag(C.Tag.vote_dialog).assertDoesNotExist()
    composeRule.onNodeWithText("Keep it open").performClick()
    composeRule.runOnIdle { assertEquals(listOf(other.uid, live.uid), requested) }
    composeRule.onNodeWithTag(C.Tag.vote_close_dialog).assertDoesNotExist()
    composeRule.onNodeWithTag(C.Tag.closeVote(live.uid)).assertIsDisplayed()
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
        .onNodeWithTag(C.Tag.voteCount(live.uid, "a"), useUnmergedTree = true)
        .assertTextEquals("0 votes")
    composeRule
        .onNodeWithTag(C.Tag.voteCount(live.uid, "b"), useUnmergedTree = true)
        .assertTextEquals("2 votes")
    composeRule
        .onNodeWithTag(C.Tag.voteCount(other.uid, "a"), useUnmergedTree = true)
        .assertTextEquals("1 vote")
    composeRule
        .onNodeWithTag(C.Tag.voteCount(other.uid, "b"), useUnmergedTree = true)
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
        .onNodeWithText(
            "Can't agree on lunch or a day trip? Start a vote. Everyone picks, and the winner joins the plan."
        )
        .assertIsDisplayed()
    composeRule.onNodeWithText("Start a vote").performClick()
    composeRule.runOnIdle { assertEquals(1, creations) }
    composeRule.onNodeWithText("Live").assertDoesNotExist()
    composeRule.onNodeWithText("Decided").assertDoesNotExist()
  }

  @Test
  fun firstVisitHintExplainsTheStepsAndIsDismissedWithGotIt() {
    var dismissals = 0
    content(
        VotesUiState.Ready(listOf(live), emptyList()),
        showHint = true,
        onDismissHint = { dismissals++ },
    )
    composeRule.onNodeWithText("How the group decides").assertIsDisplayed()
    composeRule
        .onNodeWithText(
            "1 Someone suggests a place · 2 Everyone votes · 3 The winner joins the plan"
        )
        .assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.votes_hint_dismiss).performClick()
    composeRule.runOnIdle { assertEquals(1, dismissals) }
  }

  @Test
  fun hintIsNotRepeatedOnTheEmptyScreenThatAlreadyExplainsVotes() {
    content(VotesUiState.Empty, showHint = true)
    composeRule.onNodeWithTag(C.Tag.votes_hint).assertDoesNotExist()
    composeRule.onNodeWithText("Start a vote").assertIsDisplayed()
  }

  @Test
  fun hintIsHiddenOnceSeen() {
    content(VotesUiState.Ready(listOf(live), emptyList()), showHint = false)
    composeRule.onNodeWithTag(C.Tag.votes_hint).assertDoesNotExist()
  }

  @Test
  fun hintIsNotShownInReadOnlyTrips() {
    content(VotesUiState.Ready(listOf(live), emptyList()), showHint = true, readOnly = true)
    composeRule.onNodeWithTag(C.Tag.votes_hint).assertDoesNotExist()
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
