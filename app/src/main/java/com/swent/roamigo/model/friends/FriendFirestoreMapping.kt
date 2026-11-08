// AI assistance was used for implementation support.
package com.swent.roamigo.model.friends

import com.google.firebase.Timestamp
import java.security.MessageDigest
import java.time.Instant

private val friendshipFields =
    setOf(
        "requestId",
        "senderUserId",
        "recipientUserId",
        "participantIds",
        "status",
        "createdAt",
        "respondedAt",
    )

/** Matches the sorted, lowercase SHA-256 document name enforced by `firestore.rules`. */
internal fun friendshipDocumentId(firstUserId: String, secondUserId: String): String {
  requireFriendIdentifier(firstUserId)
  requireFriendIdentifier(secondUserId)
  require(firstUserId != secondUserId) { "A friendship requires two distinct users" }
  return listOf(firstUserId, secondUserId)
      .map { userId ->
        MessageDigest.getInstance("SHA-256")
            .digest(userId.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
      }
      .sorted()
      .joinToString("_")
}

/**
 * Parses a complete stored request, reporting malformed fields or an inconsistent document name as
 * [FriendRepositoryException.InvalidData]. The request's [FriendRequest.uid] comes from
 * `requestId`, rather than the document name that is shared across request generations.
 */
internal fun friendRequestFromFirestore(
    documentId: String,
    data: Map<String, Any?>?,
): FriendRequest {
  try {
    val fields = requireNotNull(data) { "Missing friendship data" }
    require(fields.keys == friendshipFields) { "Unexpected friendship fields" }
    val senderId = fields.requiredIdentifier("senderUserId")
    val recipientId = fields.requiredIdentifier("recipientUserId")
    require(documentId == friendshipDocumentId(senderId, recipientId)) {
      "Friendship document does not match its participants"
    }
    require(fields["participantIds"] == listOf(senderId, recipientId)) {
      "Friendship participants do not match its sender and recipient"
    }
    val createdAt = requireNotNull(fields["createdAt"] as? Timestamp) { "Invalid creation time" }
    val respondedAt = fields["respondedAt"]
    require(respondedAt == null || respondedAt is Timestamp) { "Invalid response time" }
    val status = requireNotNull(fields["status"] as? String) { "Invalid request status" }
    return FriendRequest(
        uid = fields.requiredIdentifier("requestId"),
        senderUserId = senderId,
        recipientUserId = recipientId,
        status = FriendRequestStatus.valueOf(status),
        createdAt = createdAt.asInstant(),
        respondedAt = respondedAt?.asInstant(),
    )
  } catch (error: IllegalArgumentException) {
    throw FriendRepositoryException.InvalidData(error)
  }
}

/** Parses an established friendship, rejecting pending and declined records as invalid data. */
internal fun friendshipFromFirestore(documentId: String, data: Map<String, Any?>?): Friendship {
  val request = friendRequestFromFirestore(documentId, data)
  if (request.status != FriendRequestStatus.ACCEPTED) {
    throw FriendRepositoryException.InvalidData(
        IllegalArgumentException("An established friendship requires acceptance")
    )
  }
  return Friendship(
      uid = request.uid,
      firstUserId = request.senderUserId,
      secondUserId = request.recipientUserId,
      acceptedAt = requireNotNull(request.respondedAt),
  )
}

private fun Map<String, Any?>.requiredIdentifier(field: String): String {
  val value = requireNotNull(this[field] as? String) { "Missing or invalid $field" }
  requireFriendIdentifier(value)
  return value
}

// Going through Date would discard sub-millisecond precision.
private fun Timestamp.asInstant(): Instant = Instant.ofEpochSecond(seconds, nanoseconds.toLong())
