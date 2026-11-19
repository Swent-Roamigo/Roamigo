// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.inviting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swent.roamigo.model.trip.inviting.InvitationException
import com.swent.roamigo.model.trip.inviting.InvitationLinks
import com.swent.roamigo.model.trip.inviting.TripInvitationService
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Presentation state of the sheet an organiser uses to invite people. */
sealed interface InviteUiState {
  /** The link is being created or stopped. */
  data object Loading : InviteUiState

  /**
   * [link] is what is shared, and [token] is the code inside it, for chats that block links.
   * [expiresAt] is when the link stops working, if the backend said. [stopFailed] is set when the
   * organiser asked to stop the link and that did not work, so the link is still valid and still
   * shown.
   */
  data class Ready(
      val link: String,
      val token: String,
      val expiresAt: Instant? = null,
      val stopFailed: Boolean = false,
  ) : InviteUiState

  /** The link was stopped, so nobody can join with it any more. */
  data object Stopped : InviteUiState

  /** [notAllowed] is true when the user is not the organiser, so retrying cannot help. */
  data class Failed(val notAllowed: Boolean) : InviteUiState
}

/** Creates the invitation link of a trip, and stops it when the organiser asks. */
class InviteViewModel(
    private val tripId: String,
    private val service: TripInvitationService,
) : ViewModel() {
  private val _uiState = MutableStateFlow<InviteUiState>(InviteUiState.Loading)
  val uiState: StateFlow<InviteUiState> = _uiState.asStateFlow()
  private var job: Job? = null

  init {
    load()
  }

  /**
   * Creates the link again, after a failure or after it was stopped. Does nothing while another
   * request is running, so a late retry cannot cancel a revoke that the server is already handling.
   */
  fun load() =
      request({ InviteUiState.Failed(notAllowed = it is InvitationException.NotAllowed) }) {
        val link = service.createLink(tripId)
        InviteUiState.Ready(
            link = InvitationLinks.linkFor(link.token),
            token = link.token,
            expiresAt = link.expiresAt,
        )
      }

  /**
   * Stops every link of the trip. People who already joined stay on it. A second tap while the
   * request is running does nothing.
   */
  fun stopSharing() {
    // A link that could not be stopped is still valid, whatever the cause, so it stays on screen
    // with the warning instead of disappearing as if it had been stopped.
    val shown = _uiState.value as? InviteUiState.Ready
    request(
        onError = {
          shown?.copy(stopFailed = true)
              ?: InviteUiState.Failed(notAllowed = it is InvitationException.NotAllowed)
        }
    ) {
      service.revokeLinks(tripId)
      InviteUiState.Stopped
    }
  }

  private fun request(onError: (Exception) -> InviteUiState, block: suspend () -> InviteUiState) {
    if (job?.isActive == true) return
    _uiState.value = InviteUiState.Loading
    job = viewModelScope.launch {
      _uiState.value =
          try {
            block()
          } catch (exception: CancellationException) {
            throw exception
          } catch (exception: Exception) {
            onError(exception)
          }
    }
  }
}
