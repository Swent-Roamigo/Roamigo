// AI assistance was used for implementation support.
package com.swent.roamigo.model.trip.voting

import com.google.firebase.Timestamp
import com.swent.roamigo.ui.trip.voting.interactionVote
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class InMemoryVoteRepositoryTest {
  private val original = interactionVote().copy(status = VoteStatus.CLOSED)
  private val ballots =
      listOf(VoteBallot("one", "a", Timestamp(1, 0)), VoteBallot("two", "b", Timestamp(1, 0)))

  private fun repository() =
      InMemoryVoteRepository(listOf(original)).also {
        it.emitBallots("trip", "vote", ballots)
        it.tripOwnerIds["trip"] = "owner"
      }

  @Test
  fun revoteIsLinkedIdempotentAndCanContinueAfterAnotherTieWithoutChangingParentBallots() =
      runTest {
        val repository = repository()
        val next =
            original.copy(
                uid = "round2",
                status = VoteStatus.OPEN,
                parentVoteId = "vote",
                round = 2,
            )
        repository.createRevote(next)
        repository.createRevote(next)
        assertEquals(2, repository.getVotes("trip").size)
        assertEquals("round2", repository.getVote("trip", "vote")?.nextRoundVoteId)
        assertEquals(ballots, repository.observeFinalBallots("trip", "vote").first())
        repository.emitBallots("trip", "round2", ballots)
        repository.closeVote("trip", "round2")
        repository.createRevote(next.copy(uid = "round3", parentVoteId = "round2", round = 3))
        assertEquals("round3", repository.getVote("trip", "round2")?.nextRoundVoteId)
        assertTrue(runCatching { repository.createRevote(next.copy(uid = "different")) }.isFailure)
      }

  @Test
  fun onlyOwnerCanResolveATiedOptionAndSameChoiceRetriesPreserveBallots() = runTest {
    val repository = repository()
    assertTrue(runCatching { repository.resolveByOwner("trip", "vote", "a", "member") }.isFailure)
    assertTrue(
        runCatching { repository.resolveByOwner("trip", "vote", "unknown", "owner") }.isFailure
    )
    repository.resolveByOwner("trip", "vote", "a", "owner")
    repository.resolveByOwner("trip", "vote", "a", "owner")
    assertEquals("a", repository.getVote("trip", "vote")?.resolvedOptionId)
    assertEquals(ballots, repository.observeFinalBallots("trip", "vote").first())
    assertTrue(runCatching { repository.resolveByOwner("trip", "vote", "b", "owner") }.isFailure)
  }
}
