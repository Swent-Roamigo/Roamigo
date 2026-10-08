// Voting repository implemented with assistance from Codex.
package com.swent.roamigo.model.trip.voting

import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Firestore implementation of [VoteRepository], using the existing trip subcollections. */
class VoteRepositoryFirestore(private val db: FirebaseFirestore) : VoteRepository {
  override suspend fun createVote(vote: Vote) {
    requireDocumentId(vote.uid)
    val document = votes(vote.tripId).document(vote.uid)
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
  }

  override suspend fun getVotes(tripId: String): List<Vote> =
      votes(tripId).get().await().documents.map { it.toVote(tripId) }

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
      callbackFlow {
        val registration =
            ballots(tripId, voteId).addSnapshotListener { snapshot, error ->
              if (error != null) {
                close(error)
              } else {
                val updates =
                    try {
                      requireNotNull(snapshot) { "Missing ballot snapshot" }
                          .documents
                          .map { it.toBallot() }
                    } catch (error: Exception) {
                      close(error)
                      return@addSnapshotListener
                    }
                trySend(updates)
              }
            }
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
private fun Vote.toFirestoreData(): Map<String, Any?> =
    mapOf(
        "uid" to uid,
        "tripId" to tripId,
        "question" to question,
        "options" to options.map { mapOf("uid" to it.uid, "name" to it.name) },
        "createdByUserId" to createdByUserId,
        "status" to status.name,
        "creationTime" to creationTime,
        "closingTime" to closingTime,
        "winningOptionId" to winningOptionId,
    )

private fun DocumentSnapshot.toVote(tripId: String): Vote {
  val data = requireNotNull(data) { "Missing vote data at $reference" }
  val options = requireNotNull(data["options"] as? List<*>) { "Missing or invalid vote options" }
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
  )
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
