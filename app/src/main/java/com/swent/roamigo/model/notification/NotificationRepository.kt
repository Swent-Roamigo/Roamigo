// AI assistance was used for implementation support.
package com.swent.roamigo.model.notification

import kotlinx.coroutines.flow.Flow

/** In-app notifications for the authenticated user. All operations are safe on the main thread. */
interface NotificationRepository {

  /**
   * Emits the current inbox and subsequent updates in newest-first order, including an empty list.
   * Collection ends on sign-out or account change and propagates observation failures.
   */
  fun observeNotifications(): Flow<List<Notification>>

  /** Emits the current unread count and updates for the same session as the inbox. */
  fun observeUnreadCount(): Flow<Int>

  /** Marks the entry with [notificationId] as read. Repeating a successful call is safe. */
  suspend fun markAsRead(notificationId: String)
}
