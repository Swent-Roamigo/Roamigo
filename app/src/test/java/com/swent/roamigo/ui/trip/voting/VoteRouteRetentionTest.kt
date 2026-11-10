// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.voting

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.RoamigoTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A retained navigation scope can outlive the composition displaying its route. */
@RunWith(AndroidJUnit4::class)
class VoteRouteRetentionTest {
  @get:Rule val composeRule = createComposeRule()
  private val owner =
      object : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
      }

  private val otherOwner =
      object : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
      }

  @After
  fun clearRetainedScopes() {
    composeRule.runOnUiThread {
      owner.viewModelStore.clear()
      otherOwner.viewModelStore.clear()
    }
  }

  @Test
  fun switchingVoteScopesKeepsDraftsSeparateAndSubmitsToTheCorrectTripAndMember() {
    val repository = InteractionVoteRepository()
    val otherVote =
        requireNotNull(repository.vote)
            .copy(uid = "other-vote", tripId = "other-trip", question = "Lunch?")
    val otherRepository = InteractionVoteRepository().apply { vote = otherVote }
    var showOther by mutableStateOf(false)
    var backRequests = 0
    composeRule.setContent {
      CompositionLocalProvider(
          LocalViewModelStoreOwner provides if (showOther) otherOwner else owner
      ) {
        RoamigoTheme {
          VoteRoute(
              voteRepository = if (showOther) otherRepository else repository,
              tripId = if (showOther) otherVote.tripId else "trip",
              voteId = if (showOther) otherVote.uid else "vote",
              currentUserId = if (showOther) "other-member" else "member",
              onBack = { backRequests++ },
              modifier = Modifier.testTag("retained_vote"),
          )
        }
      }
    }

    composeRule.onNodeWithTag("retained_vote").assertIsDisplayed()
    composeRule.onNodeWithText("Restaurant").performClick().assertIsSelected()
    composeRule.runOnIdle {
      assertTrue(repository.calls.isEmpty())
      showOther = true
    }

    composeRule.onNodeWithText("Lunch?").assertIsDisplayed()
    composeRule.onNodeWithText("Restaurant").assertIsNotSelected()
    composeRule.onNodeWithText("Submit vote").assertIsNotEnabled()
    composeRule.onNodeWithText("Market").performClick()
    composeRule.onNodeWithText("Submit vote").performClick()
    composeRule.runOnIdle {
      val submission = otherRepository.calls.single()
      assertEquals(otherVote.tripId, submission.first)
      assertEquals(otherVote.uid, submission.second)
      assertEquals("other-member", submission.third.userId)
      assertEquals("a", submission.third.optionId)
      assertTrue(repository.calls.isEmpty())
      showOther = false
    }

    composeRule.onNodeWithText("Dinner?").assertIsDisplayed()
    composeRule.onNodeWithText("Restaurant").assertIsSelected()
    composeRule.onNodeWithText("Submit vote").assertIsEnabled()
    composeRule.runOnIdle {
      assertEquals(1, repository.lookupCount)
      assertEquals(1, repository.activeObservers)
      assertEquals(1, repository.selectedVoteEmissions)
    }

    composeRule.onNodeWithText("Submit vote").performClick()
    composeRule.onNodeWithText("Back").performClick()
    composeRule.runOnIdle {
      val submission = repository.calls.single()
      assertEquals("trip", submission.first)
      assertEquals("vote", submission.second)
      assertEquals("member", submission.third.userId)
      assertEquals("b", submission.third.optionId)
      assertEquals(1, backRequests)
    }
  }

  @Test
  fun creationFinishingWhileRouteIsHiddenDeliversResultOnceWhenItReturns() {
    val pendingWrite = CompletableDeferred<Unit>()
    val repository = FakeVoteRepository().apply { gate = pendingWrite }
    val results = mutableListOf<String>()
    var visible by mutableStateOf(true)
    composeRule.setContent {
      CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
        RoamigoTheme {
          if (visible) {
            CreateVoteRoute(
                voteRepository = repository,
                tripId = "retained-trip",
                currentUserId = "member",
                onBack = {},
                onVoteCreated = results::add,
            )
          }
        }
      }
    }

    composeRule.onNodeWithTag(C.Tag.vote_question).performTextInput("Where tomorrow?")
    composeRule.onNodeWithTag(C.Tag.voteOption(0)).performTextInput("Park")
    composeRule.onNodeWithTag(C.Tag.voteOption(1)).performTextInput("Beach")
    composeRule.onNodeWithTag(C.Tag.vote_submit).performClick()
    composeRule.onNodeWithText("Creating vote…").assertIsDisplayed()
    composeRule.runOnIdle { visible = false }
    composeRule.onNodeWithTag(C.Tag.vote_submit).assertDoesNotExist()
    composeRule.runOnIdle { pendingWrite.complete(Unit) }
    composeRule.waitForIdle()
    composeRule.runOnIdle {
      assertEquals(1, repository.created.size)
      assertTrue(results.isEmpty())
      visible = true
    }

    composeRule.onNodeWithTag(C.Tag.vote_question).assertTextEquals("Question", "Where tomorrow?")
    composeRule.onNodeWithTag(C.Tag.vote_question).assertIsNotEnabled()
    composeRule.runOnIdle {
      val created = repository.created.single()
      assertEquals("retained-trip", created.tripId)
      assertEquals("member", created.createdByUserId)
      assertEquals(listOf("Park", "Beach"), created.options.map { it.name })
      assertEquals(listOf(created.uid), results)
      visible = false
    }
    composeRule.onNodeWithTag(C.Tag.vote_submit).assertDoesNotExist()
    composeRule.runOnIdle { visible = true }
    composeRule.onNodeWithTag(C.Tag.vote_submit).assertIsNotEnabled()
    composeRule.runOnIdle {
      assertEquals(listOf(repository.created.single().uid), results)
      assertEquals(1, repository.createCalls.size)
    }
  }
}
