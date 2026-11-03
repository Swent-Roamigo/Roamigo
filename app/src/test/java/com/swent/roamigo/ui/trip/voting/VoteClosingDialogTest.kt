// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.voting

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.ui.theme.RoamigoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VoteClosingDialogTest {
  @get:Rule val composeRule = createComposeRule()

  private fun creator() =
      VoteUiState.Ready(
          interactionVote(),
          isCreator = true,
          persistedOptionId = "a",
      )

  private fun pressDialogBack() {
    composeRule.waitForIdle()
    pressBack()
    composeRule.waitForIdle()
  }

  @Test
  fun backDismissesCardConfirmationButIsBlockedDuringClosing() {
    var state by mutableStateOf(creator())
    var dismissals = 0
    composeRule.setContent {
      RoamigoTheme {
        VoteDialogContent(state, {}, {}, {}, { dismissals++ }, confirmCloseOnOpen = true)
      }
    }
    pressDialogBack()
    composeRule.runOnIdle {
      assertEquals(1, dismissals)
      state = state.copy(closingState = VoteSubmissionState.Submitting)
    }
    pressDialogBack()
    composeRule.runOnIdle { assertEquals(1, dismissals) }
    composeRule.onNodeWithText("Closing vote…").assertIsDisplayed()
  }

  @Test
  fun backFromInnerConfirmationReturnsToDraftAndSubmissionPreventsDismissal() {
    var state by mutableStateOf(creator().copy(selectedOptionId = "b"))
    var dismissals = 0
    composeRule.setContent {
      RoamigoTheme { VoteDialogContent(state, {}, {}, {}, { dismissals++ }) }
    }
    composeRule.onNodeWithText("Close vote").performClick()
    pressDialogBack()
    composeRule.onNodeWithTag("vote_dialog").assertIsDisplayed()
    composeRule.onNodeWithText("Restaurant").assertIsSelected()
    composeRule.runOnIdle { state = state.copy(submissionState = VoteSubmissionState.Submitting) }
    pressDialogBack()
    composeRule.runOnIdle { assertEquals(0, dismissals) }
    composeRule.onNodeWithText("Submitting vote…").assertIsDisplayed()
    composeRule.runOnIdle { state = state.copy(submissionState = VoteSubmissionState.Idle) }
    pressDialogBack()
    composeRule.runOnIdle { assertEquals(1, dismissals) }
  }

  @Test
  fun cardCloseFailureCanRetryThroughExistingViewModel() {
    val repository = InteractionVoteRepository()
    repository.vote = interactionVote().copy(createdByUserId = "member")
    repository.closeFailure = IllegalStateException("offline")
    var dismissals = 0
    composeRule.setContent {
      RoamigoTheme {
        VoteDialog(
            "vote",
            { VoteViewModel(repository, "trip", it, "member") },
            confirmCloseOnOpen = true,
            onDismiss = { dismissals++ },
        )
      }
    }
    composeRule.onNodeWithTag("vote_close_confirm").performClick()
    composeRule.onNodeWithText("Could not close the vote. Please try again.").assertIsDisplayed()
    composeRule.runOnIdle {
      assertEquals(1, repository.closeCalls)
      assertEquals(0, dismissals)
      repository.closeFailure = null
    }
    composeRule.onNodeWithTag("vote_close_confirm").performClick()
    composeRule.runOnIdle {
      assertEquals(2, repository.closeCalls)
      assertEquals(1, dismissals)
      assertEquals(com.swent.roamigo.model.trip.voting.VoteStatus.CLOSED, repository.vote?.status)
      assertEquals(0, repository.calls.size)
    }
  }

  @Test
  fun cancellingAfterFailedInnerCloseRestoresVotingAndAllowsAnotherClose() {
    var state by mutableStateOf(creator())
    composeRule.setContent {
      RoamigoTheme {
        VoteDialogContent(
            state,
            {},
            {},
            {},
            {},
            onCloseVote = { state = state.copy(closingState = VoteSubmissionState.Failed) },
        )
      }
    }
    composeRule.onNodeWithText("Close vote").performClick()
    composeRule.onNodeWithTag("vote_close_confirm").performClick()
    composeRule.onNodeWithText("Cancel").performClick()
    composeRule.onNodeWithTag("vote_dialog").assertIsDisplayed()
    composeRule.onNodeWithText("Could not close the vote. Please try again.").assertIsDisplayed()
    composeRule.onNodeWithText("Update vote").assertIsEnabled()
    composeRule.onNodeWithText("Close vote").assertIsEnabled().performClick()
    composeRule.onNodeWithTag("vote_close_dialog").assertIsDisplayed()
  }

  @Test
  fun cardConfirmationHasNoVotingDialogAndCancelDismisses() {
    var dismissals = 0
    composeRule.setContent {
      RoamigoTheme {
        VoteDialogContent(creator(), {}, {}, {}, { dismissals++ }, confirmCloseOnOpen = true)
      }
    }
    composeRule.onNodeWithTag("vote_close_dialog").assertIsDisplayed()
    composeRule.onNodeWithTag("vote_dialog").assertDoesNotExist()
    composeRule.onNodeWithText("Market").assertDoesNotExist()
    composeRule.onNodeWithText("Update vote").assertDoesNotExist()
    composeRule.onNodeWithText("Cancel").performClick()
    composeRule.runOnIdle { assertEquals(1, dismissals) }
  }

  @Test
  fun inDialogCancellationRestoresUnsavedBallotDraft() {
    composeRule.setContent {
      RoamigoTheme { VoteDialogContent(creator().copy(selectedOptionId = "b"), {}, {}, {}, {}) }
    }
    composeRule.onNodeWithText("Close vote").performClick()
    composeRule.onNodeWithTag("vote_dialog").assertExists()
    composeRule.onNodeWithTag("vote_close_dialog").assertIsDisplayed()
    composeRule.onNodeWithText("Restaurant").assertIsSelected()
    composeRule.onNodeWithText("Cancel").performClick()
    composeRule.onNodeWithTag("vote_dialog").assertIsDisplayed()
    composeRule.onNodeWithText("Update vote").assertIsEnabled()
    composeRule.onNodeWithText("Restaurant").assertIsDisplayed().assertIsSelected()
  }

  @Test
  fun confirmationStaysVisibleWhileClosingThenOffersRecovery() {
    var state by mutableStateOf(creator())
    var closes = 0
    composeRule.setContent {
      RoamigoTheme {
        VoteDialogContent(
            state,
            {},
            {},
            {},
            {},
            onCloseVote = {
              closes++
              state = state.copy(closingState = VoteSubmissionState.Submitting)
            },
            confirmCloseOnOpen = true,
        )
      }
    }
    composeRule.onNodeWithTag("vote_close_confirm").performClick()
    composeRule.onNodeWithText("Closing vote…").assertIsDisplayed()
    composeRule.onNodeWithText("Submitting vote…").assertDoesNotExist()
    composeRule.onNodeWithTag("vote_dialog").assertDoesNotExist()
    composeRule.onNodeWithTag("vote_close_confirm").assertDoesNotExist()
    composeRule.onNodeWithText("Cancel").assertIsNotEnabled().performClick()
    composeRule.runOnIdle {
      assertEquals(1, closes)
      state = state.copy(closingState = VoteSubmissionState.Failed)
    }
    composeRule.onNodeWithText("Could not close the vote. Please try again.").assertIsDisplayed()
    composeRule.onNodeWithTag("vote_close_confirm").assertIsEnabled().performClick()
    composeRule.runOnIdle { assertEquals(2, closes) }
  }

  @Test
  fun innerCloseKeepsVotingComposedAndLocksItUntilSuccess() {
    var state: VoteUiState by mutableStateOf(creator().copy(selectedOptionId = "b"))
    var submissions = 0
    var dismissals = 0
    composeRule.setContent {
      RoamigoTheme {
        VoteDialogContent(
            state,
            {},
            { submissions++ },
            {},
            { dismissals++ },
            onCloseVote = {
              state =
                  (state as VoteUiState.Ready).copy(closingState = VoteSubmissionState.Submitting)
            },
        )
      }
    }
    composeRule.onNodeWithText("Close vote").performClick()
    composeRule.onNodeWithTag("vote_dialog").assertExists()
    composeRule.onNodeWithTag("vote_close_dialog").assertIsDisplayed()
    composeRule.onNodeWithText("Restaurant").assertIsSelected()
    composeRule.onNodeWithTag("vote_close_confirm").performClick()
    composeRule.onNodeWithText("Closing vote…").assertIsDisplayed()
    composeRule.onNodeWithText("Submitting vote…").assertDoesNotExist()
    composeRule.onNodeWithTag("vote_dialog").assertExists()
    composeRule.onNodeWithText("Update vote").assertIsNotEnabled().performClick()
    composeRule.onNodeWithText("Restaurant").assertIsNotEnabled()
    composeRule.onNodeWithText("Back").assertIsNotEnabled().performClick()
    composeRule.onNodeWithText("Cancel").assertIsNotEnabled()
    pressDialogBack()
    composeRule.runOnIdle {
      assertEquals(0, submissions)
      assertEquals(0, dismissals)
      state = VoteUiState.Closed
    }
    composeRule.onNodeWithTag("vote_dialog").assertDoesNotExist()
    composeRule.onNodeWithTag("vote_close_dialog").assertDoesNotExist()
    composeRule.onNodeWithText("This vote is closed.").assertDoesNotExist()
    composeRule.runOnIdle { assertEquals(1, dismissals) }
  }

  @Test
  fun closingLocksVotingWithoutBallotProgressOrSpinner() {
    var actions = 0
    composeRule.setContent {
      RoamigoTheme {
        VoteScreenContent(
            creator().copy(closingState = VoteSubmissionState.Submitting),
            { actions++ },
            { actions++ },
            {},
            { actions++ },
            onCloseVote = { actions++ },
        )
      }
    }
    composeRule.onNodeWithText("Closing vote…").assertIsDisplayed().assertIsNotEnabled()
    composeRule.onNodeWithText("Submitting vote…").assertDoesNotExist()
    composeRule
        .onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
        .assertCountEquals(0)
    composeRule.onNodeWithText("Update vote").assertIsNotEnabled().performClick()
    composeRule.onNodeWithText("Back").assertIsNotEnabled().performClick()
    composeRule.onNodeWithText("Market").assertIsNotEnabled().performClick()
    composeRule.runOnIdle { assertEquals(0, actions) }
  }

  @Test
  fun closingDisablesSubmitLabelWhenNoBallotExists() {
    composeRule.setContent {
      RoamigoTheme {
        VoteScreenContent(
            creator()
                .copy(
                    persistedOptionId = null,
                    selectedOptionId = "a",
                    closingState = VoteSubmissionState.Submitting,
                ),
            {},
            {},
            {},
            {},
            onCloseVote = {},
        )
      }
    }
    composeRule.onNodeWithText("Submit vote").assertIsNotEnabled()
    composeRule.onNodeWithText("Submitting vote…").assertDoesNotExist()
  }

  @Test
  fun nonCreatorCannotConfirmEvenIfCardRequestIsStale() {
    composeRule.setContent {
      RoamigoTheme {
        VoteDialogContent(
            creator().copy(isCreator = false),
            {},
            {},
            {},
            {},
            confirmCloseOnOpen = true,
        )
      }
    }
    composeRule.onNodeWithText("Only the creator can close an open vote.").assertIsDisplayed()
    composeRule.onNodeWithTag("vote_close_confirm").assertDoesNotExist()
    composeRule.onNodeWithTag("vote_dialog").assertDoesNotExist()
    composeRule.onNodeWithText("Cancel").assertIsEnabled()
  }

  @Test
  fun cardConfirmationWaitsForLoadingAndRetriesLoadFailure() {
    var state: VoteUiState by mutableStateOf(VoteUiState.Loading)
    composeRule.setContent {
      RoamigoTheme {
        VoteDialogContent(
            state,
            {},
            {},
            { state = creator() },
            {},
            confirmCloseOnOpen = true,
        )
      }
    }
    composeRule.onNodeWithText("Loading vote…").assertIsDisplayed()
    composeRule.onNodeWithTag("vote_close_confirm").assertDoesNotExist()
    composeRule.runOnIdle { state = VoteUiState.LoadError(VoteLoadError.VOTE_FAILED) }
    composeRule.onNodeWithText("Retry").performClick()
    composeRule.onNodeWithTag("vote_close_confirm").assertIsEnabled()
    composeRule.onNodeWithTag("vote_dialog").assertDoesNotExist()
  }

  @Test
  fun missingVoteOffersDismissalWithoutRetryOrConfirm() {
    composeRule.setContent {
      RoamigoTheme {
        VoteDialogContent(
            VoteUiState.LoadError(VoteLoadError.NOT_FOUND),
            {},
            {},
            {},
            {},
            confirmCloseOnOpen = true,
        )
      }
    }
    composeRule.onNodeWithText("Retry").assertDoesNotExist()
    composeRule.onNodeWithTag("vote_close_confirm").assertDoesNotExist()
    composeRule.onNodeWithText("Cancel").assertIsEnabled()
  }

  @Test
  fun remoteCloseDuringInDialogConfirmationRemovesAllVoteActions() {
    var state: VoteUiState by mutableStateOf(creator())
    var dismissals = 0
    composeRule.setContent {
      RoamigoTheme { VoteDialogContent(state, {}, {}, {}, { dismissals++ }) }
    }
    composeRule.onNodeWithText("Close vote").performClick()
    composeRule.runOnIdle { state = VoteUiState.Closed }
    composeRule.onNodeWithText("This vote is closed.").assertDoesNotExist()
    composeRule.onNodeWithTag("vote_dialog").assertDoesNotExist()
    composeRule.runOnIdle { assertEquals(1, dismissals) }
    composeRule.onNodeWithTag("vote_close_dialog").assertDoesNotExist()
    composeRule.onNodeWithText("Close vote").assertDoesNotExist()
    composeRule.onNodeWithText("Update vote").assertDoesNotExist()
  }

  @Test
  fun confirmationRequiresFreshSyncAndAllowsListenerRetry() {
    var state by mutableStateOf(creator().copy(syncState = VoteSyncState.Failed))
    composeRule.setContent {
      RoamigoTheme {
        VoteDialogContent(
            state,
            {},
            {},
            { state = state.copy(syncState = VoteSyncState.Retrying) },
            {},
            confirmCloseOnOpen = true,
        )
      }
    }
    composeRule.onNodeWithTag("vote_close_confirm").assertIsNotEnabled()
    composeRule.onNodeWithText("Retry").performClick()
    composeRule.onNodeWithText("Reconnecting…").assertIsDisplayed()
    composeRule.runOnIdle { state = state.copy(syncState = VoteSyncState.Synced) }
    composeRule.onNodeWithTag("vote_close_confirm").assertIsEnabled()
  }
}
