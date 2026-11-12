// Voting repository implemented with assistance from Codex.
package com.swent.roamigo.model.trip.voting

import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.Flow

/**
 * Data access for trip votes and live ballots backed by Firestore. One-shot operations suspend
 * until completion and propagate failures to the caller. Authorization is enforced by Firestore
 * rules and trusted resolution callables. Document IDs must be non-blank, contain no slash, and
 * differ from "." and "..".
 */
interface VoteRepository {
  /**
   * Creates a vote under its trip using an online transaction. Retrying the same creation request
   * succeeds without overwriting an existing vote, even if its status or winner has changed.
   *
   * @param vote Vote to persist, including its trip and document IDs.
   * @throws IllegalArgumentException If the vote or trip ID is invalid.
   * @throws FirebaseFirestoreException If creation fails, with code ALREADY_EXISTS for a
   *   conflicting reused ID.
   */
  suspend fun createVote(vote: Vote)

  /**
   * Delegates a closed tie to a new round through the trusted backend; never rewrites ballots. The
   * repository enforces the app's viewer-local calendar cutoff; the backend revalidates membership,
   * the final tie and the atomic linkage independently of client input.
   */
  suspend fun createRevote(vote: Vote)

  /**
   * Resolves a tie through the trusted backend using the authenticated trip owner, not creator.
   * Viewer-local ended-trip checks are repository policy, not a server authorization claim.
   */
  suspend fun resolveByOwner(tripId: String, voteId: String, optionId: String, userId: String)

  /**
   * Reads a vote directly from its trip and document IDs.
   *
   * @return The vote, or null if the document does not exist.
   * @throws IllegalArgumentException If either ID or stored vote data is invalid.
   * @throws FirebaseFirestoreException If the read fails.
   */
  suspend fun getVote(tripId: String, voteId: String): Vote?

  /** Observes exactly one vote; emits null when it is missing or deleted. */
  fun observeVote(tripId: String, voteId: String): Flow<Vote?>

  /**
   * Closes an OPEN vote by updating only its status. Firestore rules require the authenticated
   * creator. Online transactions reject missing or non-OPEN votes and fail offline.
   *
   * @throws IllegalArgumentException If either document ID is invalid.
   * @throws FirebaseFirestoreException If closing fails.
   */
  suspend fun closeVote(tripId: String, voteId: String)

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
   * Observes all votes in the specified trip, including the initial empty list and subsequent
   * additions, updates, and removals. Each collector owns a listener removed when collection ends.
   * Listener or mapping errors terminate collection without emitting partial data.
   *
   * @param tripId ID of the trip whose vote subcollection is observed.
   * @return A cold flow of complete vote lists in unspecified order.
   * @throws IllegalArgumentException During collection if the trip ID or stored data is invalid.
   * @throws FirebaseFirestoreException During collection if registration or observation fails.
   */
  fun observeVotes(tripId: String): Flow<List<Vote>>

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

  /**
   * Observes committed ballots for a CLOSED vote. Remote implementations must wait for server
   * snapshots without pending local writes, so cached live counts cannot be announced as final.
   * In-memory sources can reuse [observeBallots]. Listener lifecycle and errors follow that API.
   */
  fun observeFinalBallots(tripId: String, voteId: String): Flow<List<VoteBallot>> =
      observeBallots(tripId, voteId)
}
