// Voting repository implemented with assistance from Codex.
package com.swent.roamigo.model.trip.voting

import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.Flow

/**
 * Data access for trip votes and live ballots backed by Firestore. One-shot operations suspend
 * until completion and propagate failures to the caller. Authorization is enforced by Firestore
 * rules. Document IDs must be non-blank, contain no slash, and differ from "." and "..".
 */
interface VoteRepository {
  /**
   * Creates a vote under its trip using an online transaction.
   *
   * @param vote Vote to persist, including its trip and document IDs.
   * @throws IllegalArgumentException If the vote or trip ID is invalid.
   * @throws FirebaseFirestoreException If creation fails, with code ALREADY_EXISTS for a reused ID.
   */
  suspend fun createVote(vote: Vote)

  /**
   * Retrieves all votes for a trip, using the trip and document IDs from their paths.
   *
   * @param tripId ID of the trip to read.
   * @return All votes in unspecified order, or an empty list if none exist.
   * @throws IllegalArgumentException If the trip ID or stored vote data is invalid.
   * @throws FirebaseFirestoreException If the read fails.
   */
  suspend fun getVotes(tripId: String): List<Vote>

  /**
   * Creates or replaces the user's ballot, preserving the caller-supplied update timestamp. The
   * caller must supply an existing trip/vote and a choice from that vote's options.
   *
   * @param tripId ID of the ballot's trip.
   * @param voteId ID of the vote within the trip.
   * @param ballot User ID, selected option ID, and update timestamp to persist.
   * @throws IllegalArgumentException If a trip, vote, or user document ID is invalid.
   * @throws FirebaseFirestoreException If the write fails.
   */
  suspend fun submitBallot(tripId: String, voteId: String, ballot: VoteBallot)

  /**
   * Observes the initial ballots and subsequent changes, including removals and empty lists. Each
   * collector owns a Firestore listener that is removed when collection ends. Listener or mapping
   * errors terminate collection without emitting partial data.
   *
   * @param tripId ID of the trip to observe.
   * @param voteId ID of the vote within the trip.
   * @return A cold flow of complete ballot lists in unspecified order.
   * @throws IllegalArgumentException During collection if IDs or stored ballot data are invalid.
   * @throws FirebaseFirestoreException During collection if listener registration or observation
   *   fails.
   */
  fun observeBallots(tripId: String, voteId: String): Flow<List<VoteBallot>>
}
