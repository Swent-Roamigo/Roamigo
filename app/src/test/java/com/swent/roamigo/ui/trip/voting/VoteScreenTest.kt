// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.voting

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Timestamp
import com.swent.roamigo.model.trip.voting.VoteBallot
import com.swent.roamigo.model.trip.voting.VoteOption
import com.swent.roamigo.ui.theme.RoamigoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VoteScreenTest {
  @get:Rule val composeRule = createComposeRule()

  private fun loaded() = VoteUiState.Ready(vote = interactionVote())

  @Test
  fun displaysQuestionOptionsAndSelectionEnablesSubmit() {
    var state by mutableStateOf(loaded())
    var submissions = 0
    composeRule.setContent {
      RoamigoTheme {
        VoteScreenContent(
            state,
            { state = state.copy(selectedOptionId = it) },
            { submissions++ },
            {},
            {},
        )
      }
    }
    composeRule.onNodeWithText("Dinner?").assertIsDisplayed()
    composeRule.onNodeWithText("Market").assertIsDisplayed().assertIsNotSelected()
    composeRule.onNodeWithText("Restaurant").assertIsDisplayed().assertIsNotSelected()
    composeRule.onNodeWithText("Submit vote").assertIsNotEnabled()
    composeRule.onNodeWithText("Market").performClick().assertIsSelected()
    composeRule.onNodeWithText("Submit vote").assertIsEnabled()
    composeRule.onNodeWithText("Restaurant").performClick().assertIsSelected()
    composeRule.onNodeWithText("Market").assertIsNotSelected()
    composeRule.onNodeWithText("Submit vote").performClick()
    composeRule.runOnIdle { assertEquals(1, submissions) }
  }

  @Test
  fun existingBallotAppearsSelectedAndOffersUpdate() {
    content(
        loaded()
            .copy(
                selectedOptionId = "b",
                persistedOptionId = "b",
            )
    )
    composeRule.onNodeWithText("Restaurant").assertIsSelected()
    composeRule.onNodeWithText("Market").assertIsNotSelected()
    composeRule.onNodeWithText("Update vote").assertIsEnabled()
  }

  @Test
  fun screenRestoresRepositoryBallotAndSubmitsChangedChoice() {
    val repository = InteractionVoteRepository()
    repository.ballots.value = listOf(VoteBallot("member", "a", Timestamp(1, 0)))
    val store = ViewModelStore()
    lateinit var viewModel: VoteViewModel
    composeRule.runOnIdle {
      viewModel = VoteViewModel(repository, "trip", "vote", "member")
      store.put("vote", viewModel)
    }
    try {
      composeRule.setContent { RoamigoTheme { VoteScreen(viewModel, {}) } }
      composeRule.onNodeWithText("Market").assertIsSelected()
      assertCount("Market", "1 vote")
      assertCount("Restaurant", "0 votes")
      composeRule.runOnIdle {
        repository.ballots.value =
            repository.ballots.value + VoteBallot("other", "b", Timestamp(1, 0))
      }
      assertCount("Restaurant", "1 vote")
      composeRule.onNodeWithText("Restaurant").performClick().assertIsSelected()
      composeRule.onNodeWithText("Update vote").performClick()
      composeRule.runOnIdle {
        assertEquals("member", repository.calls.single().third.userId)
        assertEquals("b", repository.calls.single().third.optionId)
      }
      assertCount("Market", "0 votes")
      assertCount("Restaurant", "2 votes")
    } finally {
      composeRule.runOnIdle { store.clear() }
    }
  }

  @Test
  fun submittingDisablesOptionsAndActionAndShowsProgress() {
    content(loaded().copy(selectedOptionId = "a", submissionState = VoteSubmissionState.Submitting))
    composeRule.onNodeWithText("Market").assertIsSelected().assertIsNotEnabled()
    composeRule.onNodeWithText("Restaurant").assertIsNotEnabled()
    composeRule.onNodeWithText("Submitting vote…").assertIsNotEnabled()
    composeRule.onNodeWithText("Back").assertIsNotEnabled()
    composeRule
        .onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
        .assertIsDisplayed()
  }

  @Test
  fun loadingBlocksSubmissionAndShowsProgress() {
    content(VoteUiState.Loading)
    composeRule.onNodeWithText("Loading vote…").assertIsDisplayed()
    composeRule.onNodeWithText("Submit vote").assertDoesNotExist()
    composeRule
        .onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
        .assertIsDisplayed()
  }

  @Test
  fun submissionErrorKeepsChoiceAndEnabledRetrySubmission() {
    content(
        loaded()
            .copy(
                selectedOptionId = "a",
                submissionState = VoteSubmissionState.Failed,
                voteCounts = mapOf("a" to 2, "b" to 1),
            )
    )
    assertCount("Market", "2 votes")
    assertCount("Restaurant", "1 vote")
    composeRule.onNodeWithText("Unable to submit your vote. Please try again.").assertIsDisplayed()
    composeRule.onNodeWithText("Market").assertIsSelected()
    composeRule.onNodeWithText("Submit vote").assertIsEnabled()
  }

  @Test
  fun loadingErrorOffersRetry() {
    var retries = 0
    composeRule.setContent {
      RoamigoTheme {
        VoteScreenContent(
            VoteUiState.LoadError(VoteLoadError.BALLOTS_FAILED),
            {},
            {},
            { retries++ },
            {},
        )
      }
    }
    composeRule.onNodeWithText("Unable to load the vote. Please try again.").assertIsDisplayed()
    composeRule.onNodeWithText("Submit vote").assertDoesNotExist()
    composeRule.onNodeWithText("Retry").performClick()
    composeRule.runOnIdle { assertEquals(1, retries) }
  }

  @Test
  fun voteLookupErrorUsesSameInitialErrorContent() {
    content(VoteUiState.LoadError(VoteLoadError.VOTE_FAILED))
    composeRule.onNodeWithText("Unable to load the vote. Please try again.").assertIsDisplayed()
    composeRule.onNodeWithText("Retry").assertIsEnabled()
    composeRule.onNodeWithText("Dinner?").assertDoesNotExist()
  }

  @Test
  fun listenerFailureKeepsQuestionAndDraftVisibleButDisablesInteraction() {
    content(
        loaded()
            .copy(
                persistedOptionId = "a",
                selectedOptionId = "b",
                syncState = VoteSyncState.Failed,
                voteCounts = mapOf("a" to 2, "b" to 1),
            )
    )
    composeRule.onNodeWithText("Dinner?").assertIsDisplayed()
    composeRule.onNodeWithText("Loading vote…").assertDoesNotExist()
    composeRule.onNodeWithText("Restaurant").assertIsSelected().assertIsNotEnabled()
    composeRule.onNodeWithText("Market").assertIsNotEnabled()
    composeRule.onNodeWithText("Update vote").assertIsNotEnabled()
    assertCount("Market", "2 votes")
    assertCount("Restaurant", "1 vote")
    composeRule.onNodeWithText("Retry").assertIsEnabled()
    composeRule
        .onNodeWithText("Unable to load your vote selection. Please try again.")
        .assertIsDisplayed()
  }

  @Test
  fun listenerRetryRetainsSelectionWithoutInitialLoading() {
    content(
        loaded()
            .copy(
                persistedOptionId = "a",
                selectedOptionId = "b",
                syncState = VoteSyncState.Retrying,
            )
    )
    composeRule.onNodeWithText("Dinner?").assertIsDisplayed()
    composeRule.onNodeWithText("Restaurant").assertIsSelected().assertIsNotEnabled()
    composeRule.onNodeWithText("Update vote").assertIsNotEnabled()
    composeRule.onNodeWithText("Loading vote…").assertDoesNotExist()
    composeRule.onNodeWithText("Reconnecting…").assertIsDisplayed()
  }

  @Test
  fun closedVoteShowsOnlyTerminalContentAndBack() {
    content(VoteUiState.Closed)
    composeRule.onNodeWithText("This vote is closed.").assertIsDisplayed()
    composeRule.onNodeWithText("Dinner?").assertDoesNotExist()
    composeRule.onNodeWithText("Market").assertDoesNotExist()
    composeRule.onNodeWithText("Restaurant").assertDoesNotExist()
    composeRule.onNodeWithText("Update vote").assertDoesNotExist()
    composeRule.onNodeWithText("Submit vote").assertDoesNotExist()
    composeRule.onNodeWithText("Back").assertIsEnabled()
  }

  @Test
  fun notFoundHasNoRetryOrVotingControls() {
    content(VoteUiState.LoadError(VoteLoadError.NOT_FOUND))
    composeRule.onNodeWithText("This vote could not be found.").assertIsDisplayed()
    composeRule.onNodeWithText("Retry").assertDoesNotExist()
    composeRule.onNodeWithText("Submit vote").assertDoesNotExist()
    composeRule.onNodeWithText("Back").assertIsEnabled()
  }

  @Test
  fun everyOptionShowsItsCountWithCorrectPluralsAndKeepsSelection() {
    content(
        loaded()
            .copy(
                vote =
                    interactionVote()
                        .copy(options = interactionVote().options + VoteOption("c", "Cafe")),
                persistedOptionId = "b",
                selectedOptionId = "b",
                voteCounts = mapOf("a" to 0, "b" to 1, "c" to 3),
            )
    )
    assertCount("Market", "0 votes")
    assertCount("Restaurant", "1 vote")
    assertCount("Cafe", "3 votes")
    composeRule.onNodeWithText("Restaurant").assertIsSelected()
    composeRule.onNodeWithText("Update vote").assertIsEnabled()
  }

  @Test
  fun resultStateUpdateChangesCountsWithoutDiscardingLocalSelection() {
    var state by
        mutableStateOf(
            loaded().copy(selectedOptionId = "b", voteCounts = mapOf("a" to 1, "b" to 0))
        )
    composeRule.setContent { RoamigoTheme { VoteScreenContent(state, {}, {}, {}, {}) } }
    assertCount("Market", "1 vote")
    assertCount("Restaurant", "0 votes")
    composeRule.runOnIdle { state = state.copy(voteCounts = mapOf("a" to 0, "b" to 2)) }
    assertCount("Market", "0 votes")
    assertCount("Restaurant", "2 votes")
    composeRule.onNodeWithText("Restaurant").assertIsSelected()
    composeRule.onNodeWithText("Submit vote").assertIsEnabled()
    composeRule.onNodeWithText("Loading vote…").assertDoesNotExist()
  }

  private fun assertCount(option: String, count: String) {
    composeRule.onNode(hasText(option) and hasText(count)).assertIsDisplayed()
  }

  private fun content(state: VoteUiState) {
    composeRule.setContent { RoamigoTheme { VoteScreenContent(state, {}, {}, {}, {}) } }
  }
}
