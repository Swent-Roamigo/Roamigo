// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.inviting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swent.roamigo.model.trip.inviting.InvitationException
import com.swent.roamigo.model.trip.inviting.InvitationLinks
import com.swent.roamigo.model.trip.inviting.TripInvitationService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Presentation state of the sheet an organiser uses to invite people. */
sealed interface InviteUiState {
  data object Loading : InviteUiState

  /** [link] is what is shared, and [token] is the code inside it, for chats that block links. */
  data class Ready(val link: String, val token: String, val expiresAtMillis: Long?) : InviteUiState

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
  fun load() {
    if (job?.isActive == true) return
    _uiState.value = InviteUiState.Loading
    job = viewModelScope.launch {
      _uiState.value =
          try {
            val link = service.createLink(tripId)
            InviteUiState.Ready(
                link = InvitationLinks.linkFor(link.token),
                token = link.token,
                expiresAtMillis = link.expiresAtMillis,
            )
          } catch (exception: CancellationException) {
            throw exception
          } catch (exception: InvitationException) {
            InviteUiState.Failed(notAllowed = exception is InvitationException.NotAllowed)
          } catch (exception: Exception) {
            InviteUiState.Failed(notAllowed = false)
          }
    }
  }

  /**
   * Stops every link of the trip. People who already joined stay on it. A second tap while the
   * request is running does nothing.
   */
  fun stopSharing() {
    if (job?.isActive == true) return
    _uiState.value = InviteUiState.Loading
    job = viewModelScope.launch {
      _uiState.value =
          try {
            service.revokeLinks(tripId)
            InviteUiState.Stopped
          } catch (exception: CancellationException) {
            throw exception
          } catch (exception: InvitationException) {
            InviteUiState.Failed(notAllowed = exception is InvitationException.NotAllowed)
          } catch (exception: Exception) {
            InviteUiState.Failed(notAllowed = false)
          }
    }
  }

  companion object {
    fun factory(tripId: String, service: TripInvitationService): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(InviteViewModel::class.java))
            return InviteViewModel(tripId, service) as T
          }
        }
  }
}
