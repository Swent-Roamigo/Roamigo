// Implemented with assistance from OpenAI Codex.
package com.swent.roamigo.ui.trip.voting

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Timestamp
import com.swent.roamigo.model.trip.TripRepository
import com.swent.roamigo.model.trip.voting.InMemoryVoteRepository
import com.swent.roamigo.model.trip.voting.VoteStatus
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.RoamigoTheme
import com.swent.roamigo.ui.trip.details.detailsTestTrip
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@RunWith(AndroidJUnit4::class)
class VotesRouteTest {
  @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()
  private val live = interactionVote()
  private val closed =
      live.copy(uid = "closed", question = "Breakfast?", status = VoteStatus.CLOSED)
  private val repository = InMemoryVoteRepository(listOf(live, closed))
  private val trips =
      mock(TripRepository::class.java).also {
        runBlocking {
          `when`(it.getTrip(live.tripId))
              .thenReturn(
                  detailsTestTrip()
                      .copy(
                          endDate = Timestamp(4102444800L, 0),
                          memberIds = listOf(live.createdByUserId),
                      )
              )
        }
      }
  private val openedVotes = mutableListOf<String>()
  private val openedDialogs = mutableListOf<String>()
  private val closeRequests = mutableListOf<String>()

  private fun show(readOnly: Boolean = false) {
    composeRule.setContent {
      RoamigoTheme {
        VotesRoute(
            voteRepository = repository,
            tripRepository = trips,
            tripId = live.tripId,
            currentUserId = live.createdByUserId,
            tripName = "Lisbon",
            onOpenVote = { openedVotes += it },
            onOpenLiveVote = { openedDialogs += it },
            onCreateVote = {},
            onCloseVote = { closeRequests += it },
            readOnly = readOnly,
        )
      }
    }
  }

  @Test
  fun routesLiveAndClosedVotesToTheirRespectiveActions() {
    show()

    composeRule.onNodeWithTag(C.Tag.voteAction(live.uid)).performScrollTo().performClick()
    composeRule.onNodeWithText(closed.question).performScrollTo().performClick()

    composeRule.runOnIdle {
      assertEquals(listOf(live.uid), openedDialogs)
      assertEquals(listOf(closed.uid), openedVotes)
      assertEquals(listOf(live.tripId), repository.observedTrips)
    }
  }

  @Test
  fun readOnlyLiveVoteOpensDetailsWithoutExposingMutations() {
    show(readOnly = true)

    composeRule.onNodeWithText("New vote").assertDoesNotExist()
    composeRule.onNodeWithTag(C.Tag.closeVote(live.uid)).assertDoesNotExist()
    composeRule.onNodeWithTag(C.Tag.voteAction(live.uid)).performScrollTo().performClick()

    composeRule.runOnIdle {
      assertEquals(listOf(live.uid), openedVotes)
      assertTrue(openedDialogs.isEmpty())
      assertTrue(closeRequests.isEmpty())
      assertTrue(repository.submissions.isEmpty())
    }
  }

  @Test
  fun retriesTheInjectedRepositoryAfterAnInitialFailure() {
    repository.snapshots.value = Result.failure(IOException("Backend unavailable"))
    show()
    composeRule.onNodeWithText("Retry").assertIsDisplayed()

    composeRule.runOnIdle { repository.snapshots.value = Result.success(listOf(live)) }
    composeRule.onNodeWithText("Retry").performClick()

    composeRule.onNodeWithText(live.question).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.voteAction(live.uid)).performScrollTo().performClick()
    composeRule.runOnIdle {
      assertEquals(listOf(live.tripId, live.tripId), repository.observedTrips)
      assertEquals(listOf(live.uid), openedDialogs)
    }
  }
}
