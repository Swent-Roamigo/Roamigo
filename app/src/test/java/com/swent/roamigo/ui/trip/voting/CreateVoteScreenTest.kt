// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.voting

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.ui.theme.RoamigoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CreateVoteScreenTest {
  @get:Rule val composeRule = createComposeRule()

  @Test
  fun enteringQuestionAndOptionsEnablesSubmissionAndEmptyAddedOptionDisablesIt() {
    val viewModel = CreateVoteViewModel(FakeVoteRepository(), "trip", "member")
    composeRule.setContent {
      RoamigoTheme { CreateVoteScreen(viewModel, onBack = {}, onVoteCreated = {}) }
    }
    composeRule.onNodeWithTag("vote_submit").assertIsNotEnabled()
    composeRule.onNodeWithTag("vote_question").performTextInput("Dinner?")
    composeRule.onNodeWithTag("vote_option_0").performTextInput("Market")
    composeRule.onNodeWithTag("vote_option_1").performTextInput("Restaurant")
    composeRule.onNodeWithTag("vote_submit").assertIsEnabled()
    composeRule.onNodeWithText("+ Add option").performScrollTo().performClick()
    composeRule.onNodeWithTag("vote_submit").assertIsNotEnabled()
    composeRule.onNodeWithContentDescription("Remove option 3").performScrollTo().performClick()
    composeRule.onNodeWithTag("vote_submit").assertIsEnabled()
    composeRule.onNodeWithTag("vote_question").performScrollTo().performTextClearance()
    composeRule.onNodeWithTag("vote_submit").assertIsNotEnabled()
  }

  @Test
  fun startCallsRepositoryAndSuccessCallbackOnceAndCancelCallsBack() {
    val repository = FakeVoteRepository()
    val viewModel = CreateVoteViewModel(repository, "trip", "member")
    viewModel.updateQuestion("Dinner?")
    viewModel.updateOption(0, "Market")
    viewModel.updateOption(1, "Restaurant")
    val successes = mutableListOf<String>()
    var cancelled = false
    var showScreen by mutableStateOf(true)
    composeRule.setContent {
      RoamigoTheme {
        if (showScreen) {
          CreateVoteScreen(
              viewModel,
              onBack = { cancelled = true },
              onVoteCreated = { successes += it },
          )
        }
      }
    }
    composeRule.onNodeWithTag("vote_submit").performClick()
    composeRule.waitForIdle()
    composeRule.runOnIdle { assertEquals(listOf(repository.created.single().uid), successes) }
    composeRule.runOnIdle { showScreen = false }
    composeRule.waitForIdle()
    composeRule.runOnIdle { showScreen = true }
    composeRule.waitForIdle()
    composeRule.runOnIdle { assertEquals(listOf(repository.created.single().uid), successes) }
    composeRule.onNodeWithTag("vote_question").assertIsNotEnabled()
    composeRule.onNodeWithTag("vote_submit").assertIsNotEnabled()
    composeRule.onNodeWithText("Cancel").performClick()
    composeRule.runOnIdle { assertTrue(cancelled) }
  }

  @Test
  fun loadingDisablesEditingAndShowsProgress() {
    content(
        CreateVoteUiState(question = "Dinner?", options = listOf("A", "B"), isSubmitting = true)
    )
    composeRule.onNodeWithTag("vote_submit").assertIsNotEnabled()
    composeRule.onNodeWithTag("vote_question").assertIsNotEnabled()
    composeRule.onNodeWithContentDescription("Remove option 1").assertIsNotEnabled()
    composeRule.onNodeWithText("+ Add option").assertIsNotEnabled()
    composeRule.onNodeWithText("Cancel").assertIsNotEnabled()
    composeRule.onNodeWithText("Creating vote…").assertIsDisplayed()
    composeRule
        .onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
        .assertIsDisplayed()
  }

  @Test
  fun submissionFailureDisplaysErrorAndRetryAction() {
    content(
        CreateVoteUiState(
            question = "Dinner?",
            options = listOf("A", "B"),
            error = CreateVoteError.SUBMISSION_FAILED,
        )
    )
    composeRule.onNodeWithText("Unable to create the vote. Please try again.").assertIsDisplayed()
    composeRule.onNodeWithTag("vote_submit").assertIsEnabled()
  }

  private fun content(state: CreateVoteUiState) {
    composeRule.setContent {
      RoamigoTheme { CreateVoteScreenContent(state, {}, {}, { _, _ -> }, {}, {}, {}) }
    }
  }
}
