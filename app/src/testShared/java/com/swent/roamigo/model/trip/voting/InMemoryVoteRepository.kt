// AI assistance was used for implementation support and code review.
package com.swent.roamigo.model.trip.voting

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

/** Shared test backend with trip isolation and controllable realtime snapshots/failures. */
class InMemoryVoteRepository(initialVotes: List<Vote> = emptyList()) : VoteRepository {
  val snapshots = MutableStateFlow<Result<List<Vote>>>(Result.success(initialVotes))
  val observedTrips = mutableListOf<String>()
  val lookups = mutableListOf<Pair<String, String>>()
  val submissions = mutableListOf<Triple<String, String, VoteBallot>>()
  var activeObservers = 0
    private set
  var maximumObservers = 0
    private set
  private val ballots = mutableMapOf<Pair<String, String>, MutableStateFlow<List<VoteBallot>>>()

  override fun observeVotes(tripId: String): Flow<List<Vote>> = flow {
    observedTrips += tripId
    activeObservers++
    maximumObservers = maxOf(maximumObservers, activeObservers)
    try {
      snapshots.collect { result -> emit(result.getOrThrow().filter { it.tripId == tripId }) }
    } finally {
      activeObservers--
    }
  }

  override suspend fun createVote(vote: Vote) {
    snapshots.value = Result.success(snapshots.value.getOrThrow() + vote)
  }

  override suspend fun getVote(tripId: String, voteId: String): Vote? {
    lookups += tripId to voteId
    return getVotes(tripId).firstOrNull { it.uid == voteId }
  }

  override suspend fun getVotes(tripId: String): List<Vote> =
      snapshots.value.getOrThrow().filter { it.tripId == tripId }

  override fun observeBallots(tripId: String, voteId: String): Flow<List<VoteBallot>> =
      ballotStream(tripId, voteId)

  override suspend fun submitBallot(tripId: String, voteId: String, ballot: VoteBallot) {
    submissions += Triple(tripId, voteId, ballot)
    val stream = ballotStream(tripId, voteId)
    stream.value = stream.value.filterNot { it.userId == ballot.userId } + ballot
  }

  private fun ballotStream(tripId: String, voteId: String): MutableStateFlow<List<VoteBallot>> =
      ballots.getOrPut(tripId to voteId) { MutableStateFlow(emptyList()) }
}
