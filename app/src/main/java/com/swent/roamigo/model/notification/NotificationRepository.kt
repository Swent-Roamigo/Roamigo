// AI assistance was used for implementation support.
package com.swent.roamigo.model.notification

import kotlinx.coroutines.flow.Flow

/** In-app notifications for the authenticated user. All operations are safe on the main thread. */
interface NotificationRepository {

  /**
   * Emits the newest inbox entries and subsequent updates, including an empty list. Unreadable
   * records are skipped. Collection ends on sign-out or account change and propagates query
   * failures and recipient mismatches.
   */
  fun observeNotifications(): Flow<List<Notification>>

  /** Counts unread entries in the same recent window returned by [observeNotifications]. */
  fun observeUnreadCount(): Flow<Int>

  /**
   * Marks the entry with [notificationId] as read. The inbox and unread count reflect the change
   * after the server acknowledges it. While offline the write may remain pending. Repeating a
   * successful call is safe.
   */
  suspend fun markAsRead(notificationId: String)
}
