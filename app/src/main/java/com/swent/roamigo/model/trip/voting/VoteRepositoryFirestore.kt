// Voting repository implemented with assistance from Codex.
package com.swent.roamigo.model.trip.voting

import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import com.swent.roamigo.model.trip.isBeforeLocalDate
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Firestore implementation of [VoteRepository], using the existing trip subcollections. */
class VoteRepositoryFirestore(
    private val db: FirebaseFirestore,
    private val resolutionService: VoteResolutionService = VoteResolutionServiceFunctions(),
    private val clock: Clock = Clock.systemDefaultZone(),
) : VoteRepository {
  override suspend fun createVote(vote: Vote) {
    requireDocumentId(vote.uid)
    val document = votes(vote.tripId).document(vote.uid)
    try {
      db.runTransaction { transaction ->
            if (transaction.get(document).exists()) {
              throw FirebaseFirestoreException(
                  "Vote ${vote.uid} already exists in trip ${vote.tripId}",
                  FirebaseFirestoreException.Code.ALREADY_EXISTS,
              )
            }
            transaction.set(document, vote.toFirestoreData())
            Unit
          }
          .await()
    } catch (error: FirebaseFirestoreException) {
      if (error.code != FirebaseFirestoreException.Code.ALREADY_EXISTS) throw error
      val existing = getVote(vote.tripId, vote.uid)
      if (existing == null || !existing.matchesCreationRequest(vote)) throw error
    }
  }

  // Client preflight for feedback. The trusted callable independently recomputes and
  // validates the tie inside its transaction; security does not depend on this check.
  private data class ResolutionPreflight(
      val vote: Vote,
      val result: VoteResult,
      val trip: DocumentSnapshot,
  )

  private suspend fun resolutionPreflight(tripId: String, voteId: String): ResolutionPreflight =
      coroutineScope {
        val voteRead = async { votes(tripId).document(voteId).get(Source.SERVER).await() }
        val ballotsRead = async { ballots(tripId, voteId).get(Source.SERVER).await() }
        val tripRead = async { db.collection("trips").document(tripId).get(Source.SERVER).await() }
        val snapshot = voteRead.await()
        require(snapshot.exists()) { "Vote not found" }
        val vote = snapshot.toVote(tripId)
        require(vote.status == VoteStatus.CLOSED)
        val result =
            calculateVoteResult(
                vote.options,
                requireNotNull(ballotsRead.await().confirmedBallots(serverOnly = true)) {
                  "Final ballots are not server confirmed"
                },
            )
        require(result.isTie)
        val trip = tripRead.await()
        requireWritableTrip(trip)
        ResolutionPreflight(vote, result, trip)
      }

  private fun requireWritableTrip(snapshot: DocumentSnapshot) {
    val end = requireNotNull(snapshot.getTimestamp("endDate"))
    require(!end.isBeforeLocalDate(LocalDate.now(clock), clock.zone))
  }

  override suspend fun createRevote(vote: Vote) {
    vote.requireValidRound()
    val parentId = requireNotNull(vote.parentVoteId)
    requireDocumentId(parentId)
    requireDocumentId(vote.uid)
    require(vote.uid != parentId && vote.status == VoteStatus.OPEN)
    val (parent, result) = resolutionPreflight(vote.tripId, parentId)
    // Retrying the same child ID after an ambiguous success remains idempotent.
    require(parent.nextRoundVoteId == vote.uid || parent.nextRoundVoteId == null)
    require(parent.resolvedOptionId == null)
    require(parent.round < Int.MAX_VALUE)
    require(vote.question == parent.question && vote.round == parent.round + 1)
    require(vote.options == parent.options.filter { it.uid in result.winningOptionIds })
    resolutionService.createRevote(vote.tripId, parentId, vote.uid)
  }

  override suspend fun resolveByOwner(
      tripId: String,
      voteId: String,
      optionId: String,
      userId: String,
  ) {
    val (vote, result, trip) = resolutionPreflight(tripId, voteId)
    require(optionId in result.winningOptionIds)
    require(vote.nextRoundVoteId == null && vote.resolvedOptionId == null)
    require(trip.getString("ownerId") == userId)
    resolutionService.resolveByOwner(tripId, voteId, optionId)
  }

  override suspend fun getVote(tripId: String, voteId: String): Vote? {
    requireDocumentId(voteId)
    val snapshot = votes(tripId).document(voteId).get().await()
    return if (snapshot.exists()) snapshot.toVote(tripId) else null
  }

  override fun observeVote(tripId: String, voteId: String): Flow<Vote?> = callbackFlow {
    requireDocumentId(voteId)
    val registration =
        votes(tripId).document(voteId).addSnapshotListener { snapshot, error ->
          if (error != null) close(error)
          else {
            try {
              val document = requireNotNull(snapshot) { "Missing vote snapshot" }
              trySend(if (document.exists()) document.toVote(tripId) else null)
            } catch (failure: Exception) {
              close(failure)
            }
          }
        }
    awaitClose { registration.remove() }
  }

  override suspend fun closeVote(tripId: String, voteId: String) {
    requireDocumentId(voteId)
    val document = votes(tripId).document(voteId)
    db.runTransaction { transaction ->
          val snapshot = transaction.get(document)
          if (!snapshot.exists())
              throw FirebaseFirestoreException(
                  "Vote not found",
                  FirebaseFirestoreException.Code.NOT_FOUND,
              )
          if (snapshot.getString("status") != VoteStatus.OPEN.name)
              throw FirebaseFirestoreException(
                  "Vote is not open",
                  FirebaseFirestoreException.Code.FAILED_PRECONDITION,
              )
          transaction.update(document, "status", VoteStatus.CLOSED.name)
          Unit
        }
        .await()
  }

  override suspend fun getVotes(tripId: String): List<Vote> =
      votes(tripId).get().await().documents.map { it.toVote(tripId) }

  override fun observeVotes(tripId: String): Flow<List<Vote>> = callbackFlow {
    val registration =
        votes(tripId).addSnapshotListener { snapshot, error ->
          if (error != null) {
            close(error)
          } else {
            val updates =
                try {
                  requireNotNull(snapshot) { "Missing votes snapshot" }
                      .documents
                      .map { it.toVote(tripId) }
                } catch (error: Exception) {
                  close(error)
                  return@addSnapshotListener
                }
            trySend(updates)
          }
        }
    awaitClose { registration.remove() }
  }

  override suspend fun submitBallot(tripId: String, voteId: String, ballot: VoteBallot) {
    requireDocumentId(ballot.userId)
    ballots(tripId, voteId)
        .document(ballot.userId)
        .set(
            mapOf(
                "userId" to ballot.userId,
                "optionId" to ballot.optionId,
                "updatedAt" to ballot.updatedAt,
            )
        )
        .await()
  }

  override fun observeBallots(tripId: String, voteId: String): Flow<List<VoteBallot>> =
      observeBallotSnapshots(tripId, voteId, serverOnly = false)

  override fun observeFinalBallots(tripId: String, voteId: String): Flow<List<VoteBallot>> =
      observeBallotSnapshots(tripId, voteId, serverOnly = true)

  private fun observeBallotSnapshots(
      tripId: String,
      voteId: String,
      serverOnly: Boolean,
  ): Flow<List<VoteBallot>> = callbackFlow {
    val listener =
        EventListener<QuerySnapshot> { snapshot, error ->
          if (error != null) {
            close(error)
            return@EventListener
          }
          runCatching { requireNotNull(snapshot).confirmedBallots(serverOnly) }
              .onSuccess { updates -> if (updates != null) trySend(updates) }
              .onFailure { close(it) }
        }
    // Metadata updates matter when the documents are identical but become server-confirmed.
    val registration =
        if (serverOnly)
            ballots(tripId, voteId).addSnapshotListener(MetadataChanges.INCLUDE, listener)
        else ballots(tripId, voteId).addSnapshotListener(listener)
    awaitClose { registration.remove() }
  }

  private fun votes(tripId: String): CollectionReference {
    requireDocumentId(tripId)
    return db.collection("trips").document(tripId).collection("votes")
  }

  private fun ballots(tripId: String, voteId: String): CollectionReference {
    requireDocumentId(voteId)
    return votes(tripId).document(voteId).collection("ballots")
  }
}

private fun requireDocumentId(id: String) {
  require(id.isNotBlank() && '/' !in id && id != "." && id != "..") {
    "Expected a non-blank Firestore document ID without path separators"
  }
}

// Explicit mapping preserves the immutable models, which have no Firestore no-argument constructor.
private fun Vote.toFirestoreData(): Map<String, Any?> {
  requireValidRound()
  return mapOf(
      "uid" to uid,
      "tripId" to tripId,
      "question" to question,
      "options" to options.map { mapOf("uid" to it.uid, "name" to it.name) },
      "createdByUserId" to createdByUserId,
      "status" to status.name,
      "creationTime" to creationTime,
      "closingTime" to closingTime,
      "winningOptionId" to winningOptionId,
      // Optional metadata is omitted for legacy-shaped ordinary votes.
      *listOfNotNull(
              parentVoteId?.let { "parentVoteId" to it },
              nextRoundVoteId?.let { "nextRoundVoteId" to it },
              if (parentVoteId != null) "round" to round else null,
              resolvedOptionId?.let { "resolvedOptionId" to it },
              resolvedByUserId?.let { "resolvedByUserId" to it },
          )
          .toTypedArray(),
  )
}

private fun Vote.requireValidRound() {
  require(round >= 1 && if (parentVoteId == null) round == 1 else round >= 2) {
    "Invalid vote round linkage"
  }
  parentVoteId?.let {
    requireDocumentId(it)
    require(it != uid) { "A vote cannot be its own parent" }
  }
}

private fun Map<*, *>.voteRound(parentId: String?): Int {
  if (!containsKey("round")) {
    require(parentId == null) { "Linked vote is missing its round" }
    return 1
  }
  val value = this["round"]
  require(value is Int || value is Long) { "Round must be an integer" }
  val number = (value as Number).toLong()
  require(number in 1..Int.MAX_VALUE.toLong()) { "Invalid vote round" }
  return number.toInt()
}

private fun DocumentSnapshot.toVote(tripId: String): Vote {
  val data = requireNotNull(data) { "Missing vote data at $reference" }
  val options = requireNotNull(data["options"] as? List<*>) { "Missing or invalid vote options" }
  val parentId = data.optionalString("parentVoteId")
  return Vote(
          uid = id,
          tripId = tripId,
          question = data.requiredString("question"),
          options =
              options.map {
                val option = requireNotNull(it as? Map<*, *>) { "Invalid vote option" }
                VoteOption(option.requiredString("uid"), option.requiredString("name"))
              },
          createdByUserId = data.requiredString("createdByUserId"),
          status = VoteStatus.valueOf(data.requiredString("status")),
          creationTime = data.requiredTimestamp("creationTime"),
          closingTime = data.optionalTimestamp("closingTime"),
          winningOptionId = data.optionalString("winningOptionId"),
          parentVoteId = parentId,
          nextRoundVoteId = data.optionalString("nextRoundVoteId"),
          round = data.voteRound(parentId),
          resolvedOptionId = data.optionalString("resolvedOptionId"),
          resolvedByUserId = data.optionalString("resolvedByUserId"),
      )
      .also { it.requireValidRound() }
}

private fun DocumentSnapshot.toBallot(): VoteBallot {
  val data = requireNotNull(data) { "Missing ballot data at $reference" }
  return VoteBallot(id, data.requiredString("optionId"), data.requiredTimestamp("updatedAt"))
}

private fun Map<*, *>.requiredString(field: String): String =
    requireNotNull(this[field] as? String) { "Missing or invalid $field" }

private fun Map<*, *>.requiredTimestamp(field: String): Timestamp =
    requireNotNull(this[field] as? Timestamp) { "Missing or invalid $field" }

private fun Map<*, *>.optionalTimestamp(field: String): Timestamp? {
  val value = this[field]
  require(value == null || value is Timestamp) { "Invalid $field" }
  return value
}

private fun Map<*, *>.optionalString(field: String): String? {
  val value = this[field]
  require(value == null || value is String) { "Invalid $field" }
  return value
}

// Status and the winning option can change after creation and do not identify the request.
private fun Vote.matchesCreationRequest(request: Vote): Boolean =
    uid == request.uid &&
        tripId == request.tripId &&
        question == request.question &&
        options == request.options && // Ordered option IDs and contents.
        createdByUserId == request.createdByUserId &&
        creationTime == request.creationTime &&
        closingTime == request.closingTime &&
        parentVoteId == request.parentVoteId &&
        round == request.round

// Cache and pending-write snapshots cannot establish a final result. Metadata-only changes
// are included by the listener so an identical server-confirmed snapshot can complete loading.
private fun QuerySnapshot.confirmedBallots(serverOnly: Boolean): List<VoteBallot>? {
  if (serverOnly && (metadata.isFromCache || metadata.hasPendingWrites())) return null
  return documents.map { it.toBallot() }
}
