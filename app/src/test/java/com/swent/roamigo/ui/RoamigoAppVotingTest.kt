// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Timestamp
import com.swent.roamigo.model.authentication.AuthenticatedUser
import com.swent.roamigo.model.authentication.FakeAuthRepository
import com.swent.roamigo.model.trip.voting.Vote
import com.swent.roamigo.model.trip.voting.VoteBallot
import com.swent.roamigo.model.trip.voting.VoteOption
import com.swent.roamigo.model.trip.voting.VoteRepository
import com.swent.roamigo.model.trip.voting.VoteStatus
import com.swent.roamigo.ui.navigation.Route
import com.swent.roamigo.ui.theme.RoamigoTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Checks that the vote screens are reachable through their routes and connected to each other. */
@RunWith(AndroidJUnit4::class)
class RoamigoAppVotingTest {
  @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

  private val repository = InMemoryVoteRepository()
  private lateinit var navController: NavHostController

  @Before
  fun setUp() {
    val authRepository =
        FakeAuthRepository(AuthenticatedUser("alex", "Alex", null, null), initiallySignedIn = true)
    composeRule.setContent {
      navController = rememberNavController()
      RoamigoTheme {
        RoamigoApp(
            authRepository = authRepository,
            navController = navController,
            voteRepository = lazy { repository },
        )
      }
    }
  }

  private fun navigate(route: String) {
    composeRule.runOnUiThread { navController.navigate(route) }
  }

  private fun pressBack() {
    composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
  }

  @Test
  fun createVoteRouteOpensTheCreationForm() {
    navigate(Route.createVote("trip1"))

    composeRule.onNodeWithTag("vote_question").assertIsDisplayed()
  }

  @Test
  fun submittingANewVoteOpensItAndBackSkipsTheForm() {
    navigate(Route.createVote("trip1"))
    composeRule.onNodeWithTag("vote_question").performTextInput("Dinner tonight?")
    composeRule.onNodeWithTag("vote_option_0").performTextInput("Time Out Market")
    composeRule.onNodeWithTag("vote_option_1").performTextInput("Taberna das Flores")

    composeRule.onNodeWithTag("vote_submit").performClick()

    composeRule.waitUntil {
      composeRule.onAllNodesWithText("Dinner tonight?").fetchSemanticsNodes().isNotEmpty()
    }
    assertEquals("trip1", repository.created.single().tripId)
    assertEquals("alex", repository.created.single().createdByUserId)
    pressBack()
    composeRule.onAllNodesWithTag("vote_question").assertCountEquals(0)
  }

  @Test
  fun voteRouteShowsTheStoredVote() {
    repository.created += vote("v1", "Where to next?")

    navigate(Route.vote("trip1", "v1"))

    composeRule.waitUntil {
      composeRule.onAllNodesWithText("Where to next?").fetchSemanticsNodes().isNotEmpty()
    }
  }

  private fun vote(id: String, question: String) =
      Vote(
          uid = id,
          tripId = "trip1",
          question = question,
          options = listOf(VoteOption("a", "Rome"), VoteOption("b", "Oslo")),
          createdByUserId = "alex",
          status = VoteStatus.OPEN,
          creationTime = Timestamp.now(),
          closingTime = null,
          winningOptionId = null,
      )
}

/** Keeps votes in memory so the navigation tests do not need Firestore. */
private class InMemoryVoteRepository : VoteRepository {
  val created = mutableListOf<Vote>()

  override suspend fun createVote(vote: Vote) {
    created += vote
  }

  override suspend fun getVote(tripId: String, voteId: String): Vote? = created.find {
    it.tripId == tripId && it.uid == voteId
  }

  override suspend fun getVotes(tripId: String): List<Vote> = created.filter { it.tripId == tripId }

  override suspend fun submitBallot(tripId: String, voteId: String, ballot: VoteBallot) = Unit

  override fun observeBallots(tripId: String, voteId: String): Flow<List<VoteBallot>> =
      flowOf(emptyList())
}
