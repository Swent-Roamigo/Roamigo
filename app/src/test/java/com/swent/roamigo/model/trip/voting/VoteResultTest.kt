// AI assistance was used for implementation support.
package com.swent.roamigo.model.trip.voting

import com.google.firebase.Timestamp
import org.junit.Assert.*
import org.junit.Test

class VoteResultTest {
  private val options =
      listOf(VoteOption("a", "Market"), VoteOption("b", "Restaurant"), VoteOption("c", "Cafe"))

  private fun ballots(vararg choices: String) = choices.mapIndexed { index, choice ->
    VoteBallot("member-$index", choice, Timestamp(1, 0))
  }

  @Test
  fun clearWinnerCountsEveryBallotExactlyOnceAndIncludesZeroOptions() {
    val result = calculateVoteResult(options, ballots("a", "a", "b", "a"))
    assertEquals(mapOf("a" to 3, "b" to 1, "c" to 0), result.counts)
    assertEquals(4, result.counts.values.sum())
    assertEquals(setOf("a"), result.winningOptionIds)
    assertTrue(result.hasUniqueWinner)
    assertFalse(result.isTie)
    assertFalse(result.hasNoVotes)
  }

  @Test
  fun twoAndThreeWayTiesIncludeAllLeaders() {
    val two = calculateVoteResult(options, ballots("a", "b"))
    assertEquals(setOf("a", "b"), two.winningOptionIds)
    assertEquals(0, two.counts["c"])
    assertTrue(two.isTie)
    assertFalse(two.hasUniqueWinner)
    val three = calculateVoteResult(options, ballots("a", "b", "c"))
    assertEquals(setOf("a", "b", "c"), three.winningOptionIds)
    assertTrue(three.isTie)
    assertFalse(three.hasUniqueWinner)
  }

  @Test
  fun noBallotsHasAllZeroCountsAndNoWinnerOrTie() {
    val result = calculateVoteResult(options, emptyList())
    assertEquals(mapOf("a" to 0, "b" to 0, "c" to 0), result.counts)
    assertTrue(result.hasNoVotes)
    assertFalse(result.hasUniqueWinner)
    assertTrue(result.winningOptionIds.isEmpty())
    assertFalse(result.isTie)
  }

  @Test
  fun outcomeDoesNotDependOnOptionOrBallotOrderOrUnknownOptions() {
    val ballots = ballots("b", "a", "b", "removed")
    val result = calculateVoteResult(options, ballots)
    assertEquals(result, calculateVoteResult(options.reversed(), ballots.reversed()))
    assertEquals(mapOf("a" to 1, "b" to 2, "c" to 0), result.counts)
    assertEquals(setOf("b"), result.winningOptionIds)
  }

  @Test
  fun currentUserChoiceComesFromPersistedBallotAndIsOptional() {
    val ballots = ballots("a", "b")
    val result = calculateVoteResult(options, ballots, "member-1")
    assertEquals("b", result.currentUserOptionId)
    assertNull(calculateVoteResult(options, ballots, "absent").currentUserOptionId)
    assertNull(calculateVoteResult(options, ballots).currentUserOptionId)
    assertEquals(result.counts, calculateVoteResult(options, ballots).counts)
    assertEquals(result.winningOptionIds, calculateVoteResult(options, ballots).winningOptionIds)
  }
}
