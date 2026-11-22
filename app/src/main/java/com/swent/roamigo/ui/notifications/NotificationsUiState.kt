// AI assistance was used for implementation support.
package com.swent.roamigo.ui.notifications

import com.swent.roamigo.model.notification.Notification

enum class NotificationsLoadState {
  LOADING,
  READY,
  FAILED,
  SESSION_ENDED,
}

/** A ready inbox with no entries is empty. A refresh may retain entries from the same session. */
data class NotificationsUiState(
    val loadState: NotificationsLoadState = NotificationsLoadState.LOADING,
    val notifications: List<Notification> = emptyList(),
    val markingReadIds: Set<String> = emptySet(),
    val failedReadIds: Set<String> = emptySet(),
)
