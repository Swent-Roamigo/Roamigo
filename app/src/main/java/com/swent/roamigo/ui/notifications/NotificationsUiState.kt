// AI assistance was used for implementation support.
package com.swent.roamigo.ui.notifications

import com.swent.roamigo.model.notification.Notification

/** Tracks whether the inbox can be displayed, retried, or must be closed. */
enum class NotificationsLoadState {
  /** Waiting for the first snapshot from the initial or retried listener. */
  LOADING,

  /** The listener returned a snapshot, which may contain no entries. */
  READY,

  /** Observation failed or ended and can be retried for this account. */
  FAILED,

  /** The ViewModel cleared private state and the route should leave the inbox. */
  SESSION_ENDED,
}

/** A ready inbox with no entries is empty. A refresh may retain entries from the same session. */
data class NotificationsUiState(
    val loadState: NotificationsLoadState = NotificationsLoadState.LOADING,
    val notifications: List<Notification> = emptyList(),
    val markingReadIds: Set<String> = emptySet(),
    val failedReadIds: Set<String> = emptySet(),
)
