// AI assistance was used for implementation support.
package com.swent.roamigo.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swent.roamigo.model.authentication.AuthRepository
import com.swent.roamigo.model.notification.Notification
import com.swent.roamigo.model.notification.NotificationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Owns the inbox for [currentUserId]. A different account needs its own ViewModel. */
class NotificationsViewModel(
    private val notificationRepository: NotificationRepository,
    private val authRepository: AuthRepository,
    private val currentUserId: String,
) : ViewModel() {
  private val _uiState = MutableStateFlow(NotificationsUiState())
  val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()
  private val sessionJob = SupervisorJob(viewModelScope.coroutineContext[Job])
  private val sessionScope = CoroutineScope(viewModelScope.coroutineContext + sessionJob)

  init {
    sessionScope.launch {
      authRepository
          .observeCurrentUser()
          .catch { emit(null) }
          .firstOrNull { it?.uid != currentUserId }
      endSession()
    }
    observeInbox()
  }

  fun retry() {
    if (_uiState.value.loadState == NotificationsLoadState.FAILED) observeInbox()
  }

  /**
   * Requests a read update for a visible unread entry. Observation supplies confirmed read state.
   */
  fun markAsRead(notificationId: String) {
    if (!ensureSession()) return
    val state = _uiState.value
    if (notificationId in state.markingReadIds || !state.hasUnread(notificationId)) return
    _uiState.update {
      it.copy(
          markingReadIds = it.markingReadIds + notificationId,
          failedReadIds = it.failedReadIds - notificationId,
      )
    }
    sessionScope.launch {
      try {
        if (!ensureSession()) return@launch
        notificationRepository.markAsRead(notificationId)
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: Exception) {
        if (ensureSession()) {
          _uiState.update {
            if (it.hasUnread(notificationId))
                it.copy(failedReadIds = it.failedReadIds + notificationId)
            else it
          }
        }
      } finally {
        _uiState.update { it.copy(markingReadIds = it.markingReadIds - notificationId) }
      }
    }
  }

  private fun observeInbox() {
    if (!ensureSession()) return
    _uiState.update { it.copy(loadState = NotificationsLoadState.LOADING) }
    sessionScope.launch {
      try {
        notificationRepository.observeNotifications().collect { entries ->
          if (ensureSession()) publishInbox(entries)
        }
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: Exception) {
        // A failed listener uses the same retry state as a listener that ends unexpectedly.
      }
      if (ensureSession()) _uiState.update { it.copy(loadState = NotificationsLoadState.FAILED) }
    }
  }

  private fun publishInbox(entries: List<Notification>) {
    _uiState.update { state ->
      state.copy(
          loadState = NotificationsLoadState.READY,
          notifications = entries,
          failedReadIds =
              entries
                  .filter { !it.isRead && it.uid in state.failedReadIds }
                  .mapTo(mutableSetOf()) { it.uid },
      )
    }
  }

  private fun isSessionActive(): Boolean =
      sessionJob.isActive && authRepository.getCurrentUser()?.uid == currentUserId

  /** Ends the session if the account changed and returns whether inbox work may proceed. */
  private fun ensureSession(): Boolean {
    if (isSessionActive()) return true
    if (sessionJob.isActive) endSession()
    return false
  }

  private fun endSession() {
    _uiState.value = NotificationsUiState(loadState = NotificationsLoadState.SESSION_ENDED)
    sessionJob.cancel()
  }

  companion object {
    fun factory(
        notificationRepository: NotificationRepository,
        authRepository: AuthRepository,
        currentUserId: String,
    ): ViewModelProvider.Factory = viewModelFactory {
      initializer { NotificationsViewModel(notificationRepository, authRepository, currentUserId) }
    }
  }
}

private fun NotificationsUiState.hasUnread(notificationId: String): Boolean = notifications.any {
  it.uid == notificationId && !it.isRead
}
