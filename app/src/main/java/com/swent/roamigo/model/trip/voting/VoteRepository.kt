// Voting repository implemented with assistance from Codex.
package com.swent.roamigo.model.trip.voting

/** Data access for trip votes. Failures are delivered to the onFailure callbacks. */
interface VoteRepository {
  /**
   * Stores a vote at trips/{vote.tripId}/votes/{vote.uid}. The caller supplies a unique vote ID;
   * reusing an ID replaces the existing vote document.
   */
  fun createVote(vote: Vote, onSuccess: () -> Unit, onFailure: (Exception) -> Unit)

  /** Retrieves all votes for a trip; no ordering is guaranteed. */
  fun getVotes(tripId: String, onSuccess: (List<Vote>) -> Unit, onFailure: (Exception) -> Unit)

  /**
   * Creates or replaces ballots/{ballot.userId}, including its choice and caller-supplied update
   * timestamp. The caller supplies an existing trip/vote and a choice from that vote's options.
   * Authorization is enforced by Firestore rules.
   */
  fun submitBallot(
      tripId: String,
      voteId: String,
      ballot: VoteBallot,
      onSuccess: () -> Unit,
      onFailure: (Exception) -> Unit,
  )

  /**
   * Emits the initial ballot list and subsequent changes (including removals and empty lists). Call
   * [VoteSubscription.remove] when the consumer no longer needs updates. Firestore listener errors
   * terminate the subscription; malformed snapshots report a failure instead of partial data.
   */
  fun observeBallots(
      tripId: String,
      voteId: String,
      onChange: (List<VoteBallot>) -> Unit,
      onFailure: (Exception) -> Unit,
  ): VoteSubscription
}

/** A listener handle that does not expose Firebase to ViewModels. */
fun interface VoteSubscription {
  fun remove()
}
