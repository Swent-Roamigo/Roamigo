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
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Timestamp
import com.swent.roamigo.model.trip.voting.VoteBallot
import com.swent.roamigo.model.trip.voting.VoteStatus
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
      composeRule.onNodeWithText("Restaurant").performClick().assertIsSelected()
      composeRule.onNodeWithText("Update vote").performClick()
      composeRule.runOnIdle {
        assertEquals("member", repository.calls.single().third.userId)
        assertEquals("b", repository.calls.single().third.optionId)
      }
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
    composeRule.onNodeWithText("Submit vote").assertIsNotEnabled()
    composeRule
        .onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
        .assertIsDisplayed()
  }

  @Test
  fun submissionErrorKeepsChoiceAndEnabledRetrySubmission() {
    content(loaded().copy(selectedOptionId = "a", submissionState = VoteSubmissionState.Failed))
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
    composeRule
        .onNodeWithText("Unable to load your vote selection. Please try again.")
        .assertIsDisplayed()
    composeRule.onNodeWithText("Submit vote").assertIsNotEnabled()
    composeRule.onNodeWithText("Retry").performClick()
    composeRule.runOnIdle { assertEquals(1, retries) }
  }

  @Test
  fun listenerFailureKeepsQuestionAndDraftVisibleButDisablesInteraction() {
    content(
        loaded()
            .copy(
                persistedOptionId = "a",
                selectedOptionId = "b",
                observationState = BallotObservationState.Failed,
            )
    )
    composeRule.onNodeWithText("Dinner?").assertIsDisplayed()
    composeRule.onNodeWithText("Restaurant").assertIsSelected().assertIsNotEnabled()
    composeRule.onNodeWithText("Market").assertIsNotEnabled()
    composeRule.onNodeWithText("Update vote").assertIsNotEnabled()
    composeRule.onNodeWithText("Retry").assertIsEnabled()
    composeRule
        .onNodeWithText("Unable to load your vote selection. Please try again.")
        .assertIsDisplayed()
  }

  @Test
  fun listenerRetryRetainsSelectionAndShowsLoading() {
    content(
        loaded()
            .copy(
                persistedOptionId = "a",
                selectedOptionId = "b",
                observationState = BallotObservationState.Retrying,
            )
    )
    composeRule.onNodeWithText("Dinner?").assertIsDisplayed()
    composeRule.onNodeWithText("Restaurant").assertIsSelected().assertIsNotEnabled()
    composeRule.onNodeWithText("Update vote").assertIsNotEnabled()
    composeRule.onNodeWithText("Loading vote…").assertIsDisplayed()
  }

  @Test
  fun closedVoteShowsSavedSelectionAndBlocksVoting() {
    content(
        loaded()
            .copy(
                vote = interactionVote().copy(status = VoteStatus.CLOSED),
                persistedOptionId = "a",
                selectedOptionId = "a",
            )
    )
    composeRule.onNodeWithText("This vote is closed.").assertIsDisplayed()
    composeRule.onNodeWithText("Market").assertIsSelected().assertIsNotEnabled()
    composeRule.onNodeWithText("Restaurant").assertIsNotEnabled()
    composeRule.onNodeWithText("Update vote").assertIsNotEnabled()
  }

  private fun content(state: VoteUiState) {
    composeRule.setContent { RoamigoTheme { VoteScreenContent(state, {}, {}, {}, {}) } }
  }
}
