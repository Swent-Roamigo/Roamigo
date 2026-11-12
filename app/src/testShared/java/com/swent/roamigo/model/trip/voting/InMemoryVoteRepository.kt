// AI assistance was used for implementation support and code review.
package com.swent.roamigo.model.trip.voting

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

/** Shared test backend with trip isolation and controllable realtime snapshots/failures. */
class InMemoryVoteRepository(initialVotes: List<Vote> = emptyList()) : VoteRepository {
  val tripOwnerIds = mutableMapOf<String, String>()
  val snapshots = MutableStateFlow<Result<List<Vote>>>(Result.success(initialVotes))
  val observedTrips = mutableListOf<String>()
  val lookups = mutableListOf<Pair<String, String>>()
  val submissions = mutableListOf<Triple<String, String, VoteBallot>>()
  var activeObservers = 0
    private set
  var maximumObservers = 0
    private set
  val ballotObserverStarts = mutableMapOf<String, Int>()
  val activeBallotObservers = mutableMapOf<String, Int>()
  private val ballots = mutableMapOf<Pair<String, String>, MutableStateFlow<List<VoteBallot>>>()

  override fun observeVote(tripId: String, voteId: String): Flow<Vote?> =
      snapshots.map { result -> result.getOrThrow().firstOrNull { it.tripId == tripId && it.uid == voteId } }.distinctUntilChanged()

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

  override suspend fun createRevote(vote: Vote) {
    val parent = requireNotNull(getVote(vote.tripId, requireNotNull(vote.parentVoteId)))
    val existing = getVote(vote.tripId, vote.uid)
    if (parent.nextRoundVoteId == vote.uid && existing?.parentVoteId == parent.uid &&
        existing.createdByUserId == vote.createdByUserId) return
    val tied = tiedOptions(parent)
    require(existing == null && vote.uid != parent.uid && vote.status == VoteStatus.OPEN)
    require(vote.question == parent.question && vote.options == tied && vote.round == parent.round + 1)
    snapshots.value = Result.success(snapshots.value.getOrThrow().map {
      if (it.tripId == parent.tripId && it.uid == parent.uid) it.copy(nextRoundVoteId = vote.uid) else it
    } + vote)
  }

  override suspend fun resolveByOwner(tripId: String, voteId: String, optionId: String, userId: String) {
    require(tripOwnerIds[tripId] == userId) { "Trip owner required; configure tripOwnerIds in the fixture" }
    val vote = requireNotNull(getVote(tripId, voteId))
    if (vote.resolvedOptionId == optionId && vote.resolvedByUserId == userId) return
    require(tiedOptions(vote).any { it.uid == optionId })
    snapshots.value = Result.success(snapshots.value.getOrThrow().map {
      if (it.tripId == tripId && it.uid == voteId) it.copy(resolvedOptionId = optionId, resolvedByUserId = userId) else it
    })
  }

  private fun tiedOptions(vote: Vote): List<VoteOption> {
    require(vote.status == VoteStatus.CLOSED && vote.nextRoundVoteId == null && vote.resolvedOptionId == null)
    val result = calculateVoteResult(vote.options, ballotStream(vote.tripId, vote.uid).value)
    require(result.isTie)
    return vote.options.filter { it.uid in result.winningOptionIds }
  }

  override suspend fun closeVote(tripId: String, voteId: String) {
    val vote = requireNotNull(getVote(tripId, voteId))
    check(vote.status == VoteStatus.OPEN)
    snapshots.value =
        Result.success(
            snapshots.value.getOrThrow().map {
              if (it.tripId == tripId && it.uid == voteId) it.copy(status = VoteStatus.CLOSED) else it
            }
        )
  }

  override suspend fun getVote(tripId: String, voteId: String): Vote? {
    lookups += tripId to voteId
    return getVotes(tripId).firstOrNull { it.uid == voteId }
  }

  override suspend fun getVotes(tripId: String): List<Vote> =
      snapshots.value.getOrThrow().filter { it.tripId == tripId }

  override fun observeBallots(tripId: String, voteId: String): Flow<List<VoteBallot>> =
      flow {
        ballotObserverStarts[voteId] = (ballotObserverStarts[voteId] ?: 0) + 1
        activeBallotObservers[voteId] = (activeBallotObservers[voteId] ?: 0) + 1
        try {
          ballotStream(tripId, voteId).collect { emit(it) }
        } finally {
          activeBallotObservers[voteId] = activeBallotObservers.getValue(voteId) - 1
        }
      }

  override suspend fun submitBallot(tripId: String, voteId: String, ballot: VoteBallot) {
    check(getVotes(tripId).firstOrNull { it.uid == voteId }?.status == VoteStatus.OPEN)
    submissions += Triple(tripId, voteId, ballot)
    val stream = ballotStream(tripId, voteId)
    stream.value = stream.value.filterNot { it.userId == ballot.userId } + ballot
  }

  /** Emits a controlled read snapshot, including delayed delivery of pre-close ballots. */
  fun emitBallots(tripId: String, voteId: String, snapshot: List<VoteBallot>) {
    ballotStream(tripId, voteId).value = snapshot
  }

  private fun ballotStream(tripId: String, voteId: String): MutableStateFlow<List<VoteBallot>> =
      ballots.getOrPut(tripId to voteId) { MutableStateFlow(emptyList()) }
}
