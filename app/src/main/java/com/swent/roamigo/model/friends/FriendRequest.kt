// AI assistance was used for implementation support.
package com.swent.roamigo.model.friends

import java.time.Instant

/** The persisted state of a friend request; incoming and outgoing depend on the current user. */
enum class FriendRequestStatus {
  PENDING,
  ACCEPTED,
  DECLINED,
}

/**
 * A request between two distinct users, including its outcome when it has been answered.
 *
 * [uid] identifies this particular request. A fresh request after decline or friendship removal
 * must use a different identifier so actions targeting the old request cannot affect the new one.
 * [respondedAt] is absent while pending and records acceptance or decline otherwise.
 */
data class FriendRequest(
    val uid: String,
    val senderUserId: String,
    val recipientUserId: String,
    val status: FriendRequestStatus,
    val createdAt: Instant,
    val respondedAt: Instant? = null,
) {
  init {
    require(uid.isNotBlank()) { "Request identifier must not be blank" }
    require(senderUserId.isNotBlank() && recipientUserId.isNotBlank()) {
      "Request participants must not be blank"
    }
    require(senderUserId != recipientUserId) { "Cannot send a friend request to oneself" }
    require((status == FriendRequestStatus.PENDING) == (respondedAt == null)) {
      "Only pending requests may have no response time"
    }
    require(respondedAt == null || !respondedAt.isBefore(createdAt)) {
      "Response time must not precede creation time"
    }
  }
}
