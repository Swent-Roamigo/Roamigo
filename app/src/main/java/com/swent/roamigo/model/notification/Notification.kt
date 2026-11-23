// AI assistance was used for implementation support.
package com.swent.roamigo.model.notification

import java.time.Instant

data class Notification(
    val uid: String,
    val recipientUserId: String,
    val event: NotificationEvent,
    val createdAt: Instant,
    val isRead: Boolean,
)

sealed interface NotificationEvent {
  data class TripVoteCreated(
      val tripId: String,
      val voteId: String,
  ) : NotificationEvent
}
