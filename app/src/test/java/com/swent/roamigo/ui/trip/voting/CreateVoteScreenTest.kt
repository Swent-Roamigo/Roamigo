// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.voting

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
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
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.RoamigoTheme
import kotlinx.coroutines.CompletableDeferred
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
    val repository = FakeVoteRepository()
    composeRule.setContent {
      RoamigoTheme {
        CreateVoteRoute(
            voteRepository = repository,
            tripId = "trip",
            currentUserId = "member",
            onBack = {},
            onVoteCreated = {},
        )
      }
    }
    composeRule.onNodeWithTag(C.Tag.vote_submit).assertIsNotEnabled()
    composeRule.onNodeWithTag(C.Tag.vote_question).performTextInput("Dinner?")
    composeRule.onNodeWithTag(C.Tag.vote_option(0)).performTextInput("Market")
    composeRule.onNodeWithTag(C.Tag.vote_option(1)).performTextInput("Restaurant")
    composeRule.onNodeWithTag(C.Tag.vote_submit).assertIsEnabled()
    composeRule.onNodeWithText("+ Add option").performScrollTo().performClick()
    composeRule.onNodeWithTag(C.Tag.vote_submit).assertIsNotEnabled()
    composeRule.onNodeWithContentDescription("Remove option 3").performScrollTo().performClick()
    composeRule.onNodeWithTag(C.Tag.vote_submit).assertIsEnabled()
    composeRule.onNodeWithTag(C.Tag.vote_question).performScrollTo().performTextClearance()
    composeRule.onNodeWithTag(C.Tag.vote_submit).assertIsNotEnabled()
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
      // Let the Route retrieve this prefilled ViewModel from the test owner's store.
      composeViewModel { viewModel }
      RoamigoTheme {
        if (showScreen) {
          CreateVoteRoute(
              voteRepository = repository,
              tripId = "trip",
              currentUserId = "member",
              onBack = { cancelled = true },
              onVoteCreated = { successes += it },
          )
        }
      }
    }
    composeRule.onNodeWithTag(C.Tag.vote_submit).performClick()
    composeRule.waitForIdle()
    composeRule.runOnIdle { assertEquals(listOf(repository.created.single().uid), successes) }
    composeRule.runOnIdle { showScreen = false }
    composeRule.waitForIdle()
    composeRule.runOnIdle { showScreen = true }
    composeRule.waitForIdle()
    composeRule.runOnIdle { assertEquals(listOf(repository.created.single().uid), successes) }
    composeRule.onNodeWithTag(C.Tag.vote_question).assertIsNotEnabled()
    composeRule.onNodeWithTag(C.Tag.vote_submit).assertIsNotEnabled()
    composeRule.onNodeWithText("Cancel").performClick()
    composeRule.runOnIdle { assertTrue(cancelled) }
  }

  @Test
  fun systemBackWaitsForPendingSubmissionAndIsReleasedAfterSuccess() {
    val pendingSubmission = CompletableDeferred<Unit>()
    val repository = FakeVoteRepository().apply { gate = pendingSubmission }
    val successes = mutableListOf<String>()
    var backRequests = 0
    lateinit var backDispatcher: OnBackPressedDispatcher
    composeRule.setContent {
      backDispatcher =
          requireNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
      BackHandler { backRequests++ }
      RoamigoTheme {
        CreateVoteRoute(
            voteRepository = repository,
            tripId = "trip",
            currentUserId = "member",
            onBack = { backRequests++ },
            onVoteCreated = { successes += it },
        )
      }
    }
    composeRule.onNodeWithTag(C.Tag.vote_question).performTextInput("Dinner?")
    composeRule.onNodeWithTag(C.Tag.vote_option(0)).performTextInput("Market")
    composeRule.onNodeWithTag(C.Tag.vote_option(1)).performTextInput("Restaurant")
    composeRule.onNodeWithTag(C.Tag.vote_submit).performClick()
    composeRule.onNodeWithText("Creating vote…").assertIsDisplayed()

    composeRule.runOnUiThread { backDispatcher.onBackPressed() }

    composeRule.runOnIdle {
      assertEquals(0, backRequests)
      assertEquals(1, repository.createCalls.size)
      assertTrue(successes.isEmpty())
      pendingSubmission.complete(Unit)
    }
    composeRule.waitForIdle()
    composeRule.runOnIdle { assertEquals(listOf(repository.created.single().uid), successes) }

    composeRule.runOnUiThread { backDispatcher.onBackPressed() }

    composeRule.runOnIdle {
      assertEquals(1, backRequests)
      assertEquals(1, repository.createCalls.size)
    }
  }

  @Test
  fun loadingDisablesEditingAndShowsProgress() {
    content(
        CreateVoteUiState(question = "Dinner?", options = listOf("A", "B"), isSubmitting = true)
    )
    composeRule.onNodeWithTag(C.Tag.vote_submit).assertIsNotEnabled()
    composeRule.onNodeWithTag(C.Tag.vote_question).assertIsNotEnabled()
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
    composeRule.onNodeWithTag(C.Tag.vote_submit).assertIsEnabled()
  }

  @Test
  fun addingUpToTenDisablesAddAndAllowsCreatingTenValidOptions() {
    val repository = FakeVoteRepository()
    val viewModel = CreateVoteViewModel(repository, "trip", "member")
    viewModel.updateQuestion("Where next?")
    viewModel.updateOption(0, "First")
    viewModel.updateOption(1, "Second")
    composeRule.setContent {
      composeViewModel { viewModel }
      RoamigoTheme {
        CreateVoteRoute(
            voteRepository = repository,
            tripId = "trip",
            currentUserId = "member",
            onBack = {},
            onVoteCreated = {},
        )
      }
    }
    for (index in 2..9) {
      composeRule.onNodeWithText("+ Add option").performScrollTo().assertIsEnabled().performClick()
      composeRule
          .onNodeWithTag(C.Tag.vote_option(index))
          .performScrollTo()
          .performTextInput("Option $index")
      composeRule.onNodeWithTag(C.Tag.vote_submit).assertIsEnabled()
    }
    composeRule.onNodeWithText("+ Add option").performScrollTo().assertIsNotEnabled().performClick()
    composeRule.runOnIdle { assertEquals(10, viewModel.uiState.value.options.size) }
    composeRule.onNodeWithContentDescription("Remove option 10").performScrollTo().performClick()
    composeRule.onNodeWithText("+ Add option").performScrollTo().assertIsEnabled().performClick()
    composeRule.onNodeWithTag(C.Tag.vote_option(9)).performScrollTo().performTextInput("Last")
    composeRule.onNodeWithTag(C.Tag.vote_submit).performClick()
    composeRule.waitForIdle()
    composeRule.runOnIdle { assertEquals(10, repository.created.single().options.size) }
  }

  private fun content(state: CreateVoteUiState) {
    composeRule.setContent {
      RoamigoTheme { CreateVoteScreen(state, {}, {}, { _, _ -> }, {}, {}, {}) }
    }
  }
}
