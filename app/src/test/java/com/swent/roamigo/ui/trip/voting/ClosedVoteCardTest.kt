// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.voting

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Timestamp
import com.swent.roamigo.model.trip.voting.InMemoryVoteRepository
import com.swent.roamigo.model.trip.voting.VoteBallot
import com.swent.roamigo.model.trip.voting.VoteOption
import com.swent.roamigo.model.trip.voting.VoteRepository
import com.swent.roamigo.model.trip.voting.VoteResult
import com.swent.roamigo.model.trip.voting.VoteStatus
import com.swent.roamigo.model.trip.voting.calculateVoteResult
import com.swent.roamigo.ui.theme.RoamigoTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class ClosedVoteCardTest {
  @get:Rule val composeRule = createComposeRule()
  private val vote =
      interactionVote()
          .copy(
              status = VoteStatus.CLOSED,
              options = interactionVote().options + VoteOption("c", "Cafe"),
          )

  private fun ballots(vararg choices: String) = choices.mapIndexed { index, option ->
    VoteBallot("member-$index", option, Timestamp(1, 0))
  }

  private fun result(vararg choices: String, userId: String? = "member-0") =
      calculateVoteResult(vote.options, ballots(*choices), userId)

  private fun content(result: VoteResult?, onOpen: (String) -> Unit = {}) {
    composeRule.setContent {
      RoamigoTheme {
        VotesScreen(
            VotesUiState.Ready(
                emptyList(),
                listOf(vote),
                finalSummaries = result?.let { mapOf(vote.uid to it) }.orEmpty(),
            ),
            "Trip",
            onOpen,
            {},
            {},
        )
      }
    }
  }

  private fun count(id: String, expected: String) {
    composeRule
        .onNodeWithTag("vote_count_${vote.uid}_$id", useUnmergedTree = true)
        .performScrollTo()
        .assertIsDisplayed()
        .assertTextEquals(expected)
  }

  private fun noControls() {
    listOf(
            "Vote",
            "Change vote",
            "Close vote",
            "Submit vote",
            "Update vote",
            "Revote",
            "Owner decides",
        )
        .forEach { composeRule.onNodeWithText(it).assertDoesNotExist() }
    composeRule.onNodeWithTag("vote_action_${vote.uid}").assertDoesNotExist()
    composeRule.onNodeWithTag("close_vote_${vote.uid}").assertDoesNotExist()
  }

  @Test
  fun winnerUsesCompactDecidedRowWithCheckResultAndOpensDetails() {
    val opened = mutableListOf<String>()
    content(result("a", "a", "b"), opened::add)
    composeRule.onNodeWithText("Decided").assertIsDisplayed()
    composeRule.onNodeWithText(vote.question).assertIsDisplayed()
    composeRule.onNodeWithText("Decided · Market · your vote").assertIsDisplayed()
    composeRule
        .onNodeWithTag("decided_check_${vote.uid}", useUnmergedTree = true)
        .assertIsDisplayed()
    composeRule
        .onNodeWithTag("decided_vote_count_${vote.uid}", useUnmergedTree = true)
        .assertTextEquals("2 votes")
    composeRule.onNodeWithTag("closed_vote_${vote.uid}").assertDoesNotExist()
    composeRule
        .onNodeWithTag("vote_count_${vote.uid}_c", useUnmergedTree = true)
        .assertDoesNotExist()
    noControls()
    val row = composeRule.onNodeWithTag("decided_vote_${vote.uid}")
    assertTrue(
        row.fetchSemanticsNode().boundsInRoot.height <= with(composeRule.density) { 96.dp.toPx() }
    )
    row.performClick()
    composeRule.runOnIdle { assertEquals(listOf(vote.uid), opened) }
  }

  @Test
  fun tieHighlightsAllThreeLeadersAndHasUsefulCalloutWithoutUnsupportedActions() {
    content(result("a", "b", "c"))
    composeRule.onNodeWithText("Vote closed · tied").assertIsDisplayed()
    composeRule.onNodeWithText("Decided").assertDoesNotExist()
    composeRule.onNodeWithTag("decided_vote_${vote.uid}").assertDoesNotExist()
    composeRule.onAllNodesWithText("Tied for first", useUnmergedTree = true).assertCountEquals(3)
    count("a", "1 vote")
    count("b", "1 vote")
    count("c", "1 vote")
    composeRule.onNodeWithTag("vote_tie_${vote.uid}").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithText("It’s a tie", useUnmergedTree = true).assertIsDisplayed()
    composeRule
        .onNodeWithText(
            "The highlighted options received the same highest number of votes. There is no single winner.",
            useUnmergedTree = true,
        )
        .assertIsDisplayed()
    composeRule.onNodeWithText("Winner", useUnmergedTree = true).assertDoesNotExist()
    noControls()
  }

  @Test
  fun noVotesShowsZerosWithoutWinnerTieOrYourVote() {
    content(result())
    composeRule.onNodeWithText("Vote closed · no votes").assertIsDisplayed()
    composeRule.onNodeWithText("Decided").assertDoesNotExist()
    listOf("a", "b", "c").forEach { count(it, "0 votes") }
    composeRule.onNodeWithText("your vote", substring = true).assertDoesNotExist()
    composeRule.onNodeWithText("Winner", useUnmergedTree = true).assertDoesNotExist()
    composeRule.onNodeWithTag("vote_tie_${vote.uid}").assertDoesNotExist()
    noControls()
  }

  @Test
  fun memberWhoDidNotVoteSeesCompactWinnerWithoutYourVoteMarker() {
    content(result("b", userId = "absent"))
    composeRule.onNodeWithText("your vote", substring = true).assertDoesNotExist()
    composeRule.onNodeWithText("Decided · Restaurant").assertIsDisplayed()
    composeRule
        .onNodeWithTag("decided_vote_count_${vote.uid}", useUnmergedTree = true)
        .assertTextEquals("1 vote")
    composeRule.onNodeWithTag("closed_vote_${vote.uid}").assertDoesNotExist()
  }

  @Test
  fun pendingResultDoesNotInventZeroCountsOrWinner() {
    content(null)
    composeRule.onNodeWithText("Vote closed · loading results…").assertIsDisplayed()
    composeRule.onNodeWithText("Decided").assertDoesNotExist()
    listOf("a", "b", "c").forEach { count(it, "—") }
    composeRule.onNodeWithText("Winner", useUnmergedTree = true).assertDoesNotExist()
    composeRule.onNodeWithText("Vote closed · no votes").assertDoesNotExist()
    noControls()
  }

  @Test
  fun remoteClosureMovesToDecidedAndWaitsForFinalBallots() {
    val backend = InMemoryVoteRepository(listOf(vote.copy(status = VoteStatus.OPEN)))
    val finalSnapshot = CompletableDeferred<Unit>()
    var closed = false
    val repository =
        object : VoteRepository by backend {
          override fun observeFinalBallots(tripId: String, voteId: String) =
              observeBallots(tripId, voteId)

          override fun observeBallots(tripId: String, voteId: String) = flow {
            if (closed) finalSnapshot.await()
            backend.observeBallots(tripId, voteId).collect { emit(it) }
          }
        }
    backend.emitBallots("trip", vote.uid, ballots("a"))
    composeRule.setContent {
      RoamigoTheme { VotesRoute(repository, "trip", "member-0", "Trip", {}, {}, {}) }
    }
    composeRule.onNodeWithText("Change vote").assertIsDisplayed()
    composeRule.runOnIdle {
      closed = true
      backend.snapshots.value = Result.success(listOf(vote))
    }
    composeRule.onNodeWithText("Decided").assertDoesNotExist()
    composeRule.onNodeWithText("Vote closed · loading results…").assertIsDisplayed()
    composeRule.onNodeWithText("Winner", useUnmergedTree = true).assertDoesNotExist()
    noControls()
    composeRule.runOnIdle {
      backend.emitBallots("trip", vote.uid, ballots("b", "b"))
      finalSnapshot.complete(Unit)
    }
    composeRule.onNodeWithText("Decided").assertIsDisplayed()
    composeRule.onNodeWithText("Decided · Restaurant · your vote").assertIsDisplayed()
    composeRule
        .onNodeWithTag("decided_vote_count_${vote.uid}", useUnmergedTree = true)
        .assertTextEquals("2 votes")
    composeRule.onNodeWithTag("closed_vote_${vote.uid}").assertDoesNotExist()
    composeRule.runOnIdle { backend.emitBallots("trip", vote.uid, ballots("b", "b", "a")) }
    composeRule
        .onNodeWithTag("decided_vote_count_${vote.uid}", useUnmergedTree = true)
        .assertTextEquals("2 votes")
    // A delayed snapshot that changes the outcome must also update its presentation group.
    composeRule.runOnIdle { backend.emitBallots("trip", vote.uid, ballots("b", "b", "a", "a")) }
    composeRule.onNodeWithText("Decided").assertDoesNotExist()
    composeRule.onNodeWithText("Vote closed · tied").assertIsDisplayed()
    composeRule.onNodeWithTag("decided_vote_${vote.uid}").assertDoesNotExist()
    count("a", "2 votes")
    count("b", "2 votes")
    count("c", "0 votes")
    noControls()
    composeRule.runOnIdle { assertEquals(1, backend.activeBallotObservers[vote.uid]) }
  }

  @Test
  fun remoteOpenToClosedTieRemainsExpandedOutsideDecided() {
    val backend = InMemoryVoteRepository(listOf(vote.copy(status = VoteStatus.OPEN)))
    backend.emitBallots("trip", vote.uid, ballots("a", "b"))
    composeRule.setContent {
      RoamigoTheme { VotesRoute(backend, "trip", "member-0", "Trip", {}, {}, {}) }
    }
    composeRule.onNodeWithText("Change vote").assertIsDisplayed()
    composeRule.runOnIdle { backend.snapshots.value = Result.success(listOf(vote)) }
    composeRule.onNodeWithText("Vote closed · tied").assertIsDisplayed()
    composeRule.onNodeWithText("Decided").assertDoesNotExist()
    composeRule.onNodeWithText("Live vote").assertDoesNotExist()
    count("a", "1 vote")
    count("b", "1 vote")
    count("c", "0 votes")
    noControls()
  }

  @Test
  @Config(qualifiers = "w390dp-h1000dp")
  fun mixedResultsPlaceTieAboveDecidedHeadingAndCompactWinnerRow() {
    val decided = vote.copy(uid = "decided", question = "Museum visit?")
    val opened = mutableListOf<String>()
    composeRule.setContent {
      RoamigoTheme {
        VotesScreen(
            VotesUiState.Ready(
                emptyList(),
                listOf(decided, vote),
                finalSummaries =
                    mapOf(vote.uid to result("a", "b"), decided.uid to result("b", "b", "a")),
            ),
            "Trip",
            opened::add,
            {},
            {},
        )
      }
    }
    val tieBounds =
        composeRule.onNodeWithTag("closed_vote_${vote.uid}").fetchSemanticsNode().boundsInRoot
    val headingBounds =
        composeRule.onNodeWithTag("votes_decided_heading").fetchSemanticsNode().boundsInRoot
    val decidedBounds =
        composeRule.onNodeWithTag("decided_vote_${decided.uid}").fetchSemanticsNode().boundsInRoot
    assertTrue(tieBounds.bottom < headingBounds.top)
    assertTrue(headingBounds.bottom <= decidedBounds.top)
    composeRule
        .onNodeWithTag("decided_check_${decided.uid}", useUnmergedTree = true)
        .assertIsDisplayed()
    composeRule.onNodeWithTag("closed_vote_${vote.uid}").performClick()
    composeRule.onNodeWithTag("decided_vote_${decided.uid}").performClick()
    composeRule.runOnIdle { assertEquals(listOf(vote.uid, decided.uid), opened) }
    noControls()
  }

  @Test
  fun narrowLargeFontCardAllowsLongOptionsAndCalloutToBeScrolledIntoView() {
    val longVote =
        vote.copy(
            options =
                vote.options +
                    VoteOption(
                        "d",
                        "Riverside restaurant with a terrace overlooking the entire old town",
                    )
        )
    val result = calculateVoteResult(longVote.options, ballots("a", "d"), "member-0")
    composeRule.setContent {
      RoamigoTheme {
        CompositionLocalProvider(
            LocalDensity provides Density(LocalDensity.current.density, 1.5f)
        ) {
          Box(Modifier.width(260.dp)) {
            VotesScreen(
                VotesUiState.Ready(
                    emptyList(),
                    listOf(longVote),
                    finalSummaries = mapOf(longVote.uid to result),
                ),
                "Trip",
                {},
                {},
                {},
            )
          }
        }
      }
    }
    composeRule
        .onNodeWithText(longVote.options.last().name, useUnmergedTree = true)
        .performScrollTo()
        .assertIsDisplayed()
    count("d", "1 vote")
    composeRule.onNodeWithTag("vote_tie_${vote.uid}").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithText("New vote").assertIsDisplayed()
  }
}
