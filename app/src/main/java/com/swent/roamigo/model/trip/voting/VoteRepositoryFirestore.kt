// Voting repository implemented with assistance from Codex.
package com.swent.roamigo.model.trip.voting

import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

/** Firestore implementation of [VoteRepository], using the existing trip subcollections. */
class VoteRepositoryFirestore(private val db: FirebaseFirestore) : VoteRepository {
  override fun createVote(vote: Vote, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
    val task = runCatching {
      requireDocumentId(vote.uid)
      votes(vote.tripId).document(vote.uid).set(vote.toFirestoreData())
    }
        .getOrElse {
          onFailure(it.asException())
          return
        }
    task.addOnSuccessListener { onSuccess() }.addOnFailureListener(onFailure)
  }

  override fun getVotes(
      tripId: String,
      onSuccess: (List<Vote>) -> Unit,
      onFailure: (Exception) -> Unit,
  ) {
    val task = runCatching {
      votes(tripId).get()
    }
        .getOrElse {
          onFailure(it.asException())
          return
        }
    task
        .addOnSuccessListener { snapshot ->
          runCatching { snapshot.documents.map { it.toVote(tripId) } }
              .fold(onSuccess, { onFailure(it.asException()) })
        }
        .addOnFailureListener(onFailure)
  }

  override fun submitBallot(
      tripId: String,
      voteId: String,
      ballot: VoteBallot,
      onSuccess: () -> Unit,
      onFailure: (Exception) -> Unit,
  ) {
    val task = runCatching {
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
    }
        .getOrElse {
          onFailure(it.asException())
          return
        }
    task.addOnSuccessListener { onSuccess() }.addOnFailureListener(onFailure)
  }

  override fun observeBallots(
      tripId: String,
      voteId: String,
      onChange: (List<VoteBallot>) -> Unit,
      onFailure: (Exception) -> Unit,
  ): VoteSubscription {
    val registration = runCatching {
      ballots(tripId, voteId).addSnapshotListener { snapshot, error ->
        if (error != null) {
          onFailure(error)
        } else {
          runCatching {
                requireNotNull(snapshot) { "Missing ballot snapshot" }
                    .documents
                    .map { it.toBallot() }
              }
              .fold(onChange, { onFailure(it.asException()) })
        }
      }
    }
        .getOrElse {
          onFailure(it.asException())
          return VoteSubscription {}
        }
    return VoteSubscription { registration.remove() }
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

private fun Throwable.asException(): Exception = this as? Exception ?: RuntimeException(this)

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
