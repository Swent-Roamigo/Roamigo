// AI assistance was used for implementation support.
package com.swent.roamigo.ui.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swent.roamigo.model.authentication.AuthRepository
import com.swent.roamigo.model.notification.Notification
import com.swent.roamigo.model.notification.NotificationRepository

/** Connects the inbox to the account that opened it and forwards navigation to the app. */
@Composable
internal fun NotificationsRoute(
    notificationRepository: NotificationRepository,
    authRepository: AuthRepository,
    currentUserId: String,
    onBack: () -> Unit,
    onSessionEnded: () -> Unit,
    onOpenNotification: (Notification) -> Unit,
) {
  val model: NotificationsViewModel =
      viewModel(
          key = "notifications_$currentUserId",
          factory =
              NotificationsViewModel.factory(notificationRepository, authRepository, currentUserId),
      )
  val state by model.uiState.collectAsStateWithLifecycle()
  LaunchedEffect(state.loadState) {
    if (state.loadState == NotificationsLoadState.SESSION_ENDED) onSessionEnded()
  }

  NotificationsScreen(
      uiState = state,
      onBack = onBack,
      onRetry = model::retry,
      onOpenNotification = onOpenNotification,
      onMarkAsRead = model::markAsRead,
  )
}
