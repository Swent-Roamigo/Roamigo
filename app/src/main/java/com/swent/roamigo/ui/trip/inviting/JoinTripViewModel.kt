// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.inviting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swent.roamigo.model.trip.inviting.InvitationException
import com.swent.roamigo.model.trip.inviting.InvitationPreview
import com.swent.roamigo.model.trip.inviting.TripInvitationService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Presentation state of the screen opened from an invitation. */
sealed interface JoinTripUiState {
  /** The invitation is being checked. */
  data object Loading : JoinTripUiState

  /** The invitation is valid. [joinFailed] is set after a failed attempt to join. */
  data class Ready(val preview: InvitationPreview, val joinFailed: Boolean = false) :
      JoinTripUiState

  /** The user accepted and the request is running. */
  data class Joining(val preview: InvitationPreview) : JoinTripUiState

  /** The user is on the trip, which the screen now opens. */
  data class Joined(val tripId: String) : JoinTripUiState

  /** Unknown, revoked or expired. */
  data object Invalid : JoinTripUiState

  /** The trip has its maximum number of members. */
  data class Full(val preview: InvitationPreview) : JoinTripUiState

  /** The server refused this user, so trying again cannot help. */
  data object NotAllowed : JoinTripUiState

  /** The invitation could not be checked, for example offline. */
  data object Failed : JoinTripUiState
}

/** Shows who invited the user to which trip, and joins it when they accept. */
class JoinTripViewModel(
    private val token: String,
    private val service: TripInvitationService,
) : ViewModel() {
  private val _uiState = MutableStateFlow<JoinTripUiState>(JoinTripUiState.Loading)
  val uiState: StateFlow<JoinTripUiState> = _uiState.asStateFlow()
  private var job: Job? = null

  init {
    load()
  }

  /** Checks the invitation again, for example after the user was offline. */
  fun load() {
    _uiState.value = JoinTripUiState.Loading
    launchState(
        onError = {
          when (it) {
            is InvitationException.Invalid -> JoinTripUiState.Invalid
            is InvitationException.NotAllowed -> JoinTripUiState.NotAllowed
            else -> JoinTripUiState.Failed
          }
        }
    ) {
      JoinTripUiState.Ready(service.preview(token))
    }
  }

  /**
   * Joins the trip. A second tap while joining, or after joining, does nothing. Someone who is
   * already on the trip is not sent to the server again, so they reach their trip even if the link
   * was stopped or the phone went offline since the preview.
   */
  fun join() {
    val preview =
        when (val state = _uiState.value) {
          is JoinTripUiState.Ready -> state.preview
          else -> return
        }
    if (preview.alreadyMember) {
      _uiState.value = JoinTripUiState.Joined(preview.tripId)
      return
    }
    _uiState.value = JoinTripUiState.Joining(preview)
    launchState(
        onError = {
          when (it) {
            is InvitationException.Invalid -> JoinTripUiState.Invalid
            is InvitationException.Full -> JoinTripUiState.Full(preview)
            is InvitationException.NotAllowed -> JoinTripUiState.NotAllowed
            else -> JoinTripUiState.Ready(preview, joinFailed = true)
          }
        }
    ) {
      JoinTripUiState.Joined(service.accept(token))
    }
  }

  /**
   * Replaces the running request: [block] gives the next state, or [onError] does when it fails.
   */
  private fun launchState(
      onError: (Exception) -> JoinTripUiState,
      block: suspend () -> JoinTripUiState,
  ) {
    job?.cancel()
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

  companion object {
    /** Builds the ViewModel of the invitation [token] with its [service]. */
    fun factory(token: String, service: TripInvitationService): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(JoinTripViewModel::class.java))
            return JoinTripViewModel(token, service) as T
          }
        }
  }
}
