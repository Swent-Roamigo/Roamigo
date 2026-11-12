// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.voting

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.resources.C
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
  fun failedCloseCanBeCancelledWithoutVoting() {
    var dismissed = 0
    var closes = 0
    composeRule.setContent {
      RoamigoTheme {
        VoteCloseDialogContent(
            creator().copy(closingState = VoteSubmissionState.Failed),
            { closes++ },
            {},
            { dismissed++ },
        )
      }
    }
    composeRule.onNodeWithText("Could not close the vote. Please try again.").assertIsDisplayed()
    composeRule.onNodeWithText("Keep it open").assertIsEnabled().performClick()
    composeRule.runOnIdle {
      assertEquals(1, dismissed)
      assertEquals(0, closes)
    }
    composeRule.onNodeWithTag(C.Tag.vote_dialog).assertDoesNotExist()
  }

  @Test
  fun backDismissesCardConfirmationButIsBlockedDuringClosing() {
    var state by mutableStateOf(creator())
    var dismissals = 0
    composeRule.setContent {
      RoamigoTheme { VoteCloseDialogContent(state, {}, {}, { dismissals++ }) }
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
  fun cardCloseFailureCanRetryThroughExistingViewModel() {
    val repository = InteractionVoteRepository()
    repository.vote = interactionVote().copy(createdByUserId = "member")
    repository.closeFailure = IllegalStateException("offline")
    var dismissals = 0
    composeRule.setContent {
      RoamigoTheme {
        VoteCloseDialog(
            "vote",
            { VoteViewModel(repository, "trip", it, "member") },
            onDismiss = { dismissals++ },
        )
      }
    }
    composeRule.onNodeWithTag(C.Tag.vote_close_confirm).performClick()
    composeRule.onNodeWithText("Could not close the vote. Please try again.").assertIsDisplayed()
    composeRule.runOnIdle {
      assertEquals(1, repository.closeCalls)
      assertEquals(0, dismissals)
      repository.closeFailure = null
    }
    composeRule.onNodeWithTag(C.Tag.vote_close_confirm).performClick()
    composeRule.runOnIdle {
      assertEquals(2, repository.closeCalls)
      assertEquals(1, dismissals)
      assertEquals(com.swent.roamigo.model.trip.voting.VoteStatus.CLOSED, repository.vote?.status)
      assertEquals(0, repository.calls.size)
    }
  }

  @Test
  fun cardConfirmationHasNoVotingDialogAndCancelDismisses() {
    var dismissals = 0
    composeRule.setContent {
      RoamigoTheme { VoteCloseDialogContent(creator(), {}, {}, { dismissals++ }) }
    }
    composeRule.onNodeWithTag(C.Tag.vote_close_dialog).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.vote_dialog).assertDoesNotExist()
    composeRule.onNodeWithText("Market").assertDoesNotExist()
    composeRule.onNodeWithText("Update vote").assertDoesNotExist()
    composeRule.onNodeWithText("Keep it open").performClick()
    composeRule.runOnIdle { assertEquals(1, dismissals) }
  }

  @Test
  fun confirmationStaysVisibleWhileClosingThenOffersRecovery() {
    var state by mutableStateOf(creator())
    var closes = 0
    composeRule.setContent {
      RoamigoTheme {
        VoteCloseDialogContent(
            state,
            onConfirm = {
              closes++
              state = state.copy(closingState = VoteSubmissionState.Submitting)
            },
            onRetry = {},
            onDismiss = {},
        )
      }
    }
    composeRule.onNodeWithTag(C.Tag.vote_close_confirm).performClick()
    composeRule.onNodeWithText("Closing vote…").assertIsDisplayed()
    composeRule.onNodeWithText("Submitting vote…").assertDoesNotExist()
    composeRule.onNodeWithTag(C.Tag.vote_dialog).assertDoesNotExist()
    composeRule.onNodeWithTag(C.Tag.vote_close_confirm).assertDoesNotExist()
    composeRule.onNodeWithText("Keep it open").assertIsNotEnabled().performClick()
    composeRule.runOnIdle {
      assertEquals(1, closes)
      state = state.copy(closingState = VoteSubmissionState.Failed)
    }
    composeRule.onNodeWithText("Could not close the vote. Please try again.").assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.vote_close_confirm).assertIsEnabled().performClick()
    composeRule.runOnIdle { assertEquals(2, closes) }
  }

  @Test
  fun nonCreatorCannotConfirmEvenIfCardRequestIsStale() {
    composeRule.setContent {
      RoamigoTheme {
        VoteCloseDialogContent(
            creator().copy(isCreator = false),
            {},
            {},
            {},
        )
      }
    }
    composeRule.onNodeWithText("Only the creator can close an open vote.").assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.vote_close_confirm).assertDoesNotExist()
    composeRule.onNodeWithTag(C.Tag.vote_dialog).assertDoesNotExist()
    composeRule.onNodeWithText("Keep it open").assertIsEnabled()
  }

  @Test
  fun cardConfirmationWaitsForLoadingAndRetriesLoadFailure() {
    var state: VoteUiState by mutableStateOf(VoteUiState.Loading)
    composeRule.setContent {
      RoamigoTheme {
        VoteCloseDialogContent(
            state,
            {},
            { state = creator() },
            {},
        )
      }
    }
    composeRule.onNodeWithText("Loading vote…").assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.vote_close_confirm).assertDoesNotExist()
    composeRule.runOnIdle { state = VoteUiState.LoadError(VoteLoadError.VOTE_FAILED) }
    composeRule.onNodeWithText("Retry").performClick()
    composeRule.onNodeWithTag(C.Tag.vote_close_confirm).assertIsEnabled()
    composeRule.onNodeWithTag(C.Tag.vote_dialog).assertDoesNotExist()
  }

  @Test
  fun missingVoteOffersDismissalWithoutRetryOrConfirm() {
    composeRule.setContent {
      RoamigoTheme {
        VoteCloseDialogContent(
            VoteUiState.LoadError(VoteLoadError.NOT_FOUND),
            {},
            {},
            {},
        )
      }
    }
    composeRule.onNodeWithText("Retry").assertDoesNotExist()
    composeRule.onNodeWithTag(C.Tag.vote_close_confirm).assertDoesNotExist()
    composeRule.onNodeWithText("Keep it open").assertIsEnabled()
  }

  @Test
  fun confirmationRequiresFreshSyncAndAllowsListenerRetry() {
    var state by mutableStateOf(creator().copy(syncState = VoteSyncState.Failed))
    composeRule.setContent {
      RoamigoTheme {
        VoteCloseDialogContent(
            state,
            {},
            { state = state.copy(syncState = VoteSyncState.Retrying) },
            {},
        )
      }
    }
    composeRule.onNodeWithTag(C.Tag.vote_close_confirm).assertIsNotEnabled()
    composeRule.onNodeWithText("Retry").performClick()
    composeRule.onNodeWithText("Reconnecting…").assertIsDisplayed()
    composeRule.runOnIdle { state = state.copy(syncState = VoteSyncState.Synced) }
    composeRule.onNodeWithTag(C.Tag.vote_close_confirm).assertIsEnabled()
  }
}
