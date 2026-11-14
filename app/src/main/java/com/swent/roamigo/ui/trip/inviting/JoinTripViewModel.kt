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
  data object Loading : JoinTripUiState

  /** The invitation is valid. [joinFailed] is set after a failed attempt to join. */
  data class Ready(val preview: InvitationPreview, val joinFailed: Boolean = false) :
      JoinTripUiState

  data class Joining(val preview: InvitationPreview) : JoinTripUiState

  data class Joined(val tripId: String) : JoinTripUiState

  /** Unknown, revoked or expired. */
  data object Invalid : JoinTripUiState

  data class Full(val preview: InvitationPreview) : JoinTripUiState

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

  fun load() {
    job?.cancel()
    _uiState.value = JoinTripUiState.Loading
    job = viewModelScope.launch {
      _uiState.value =
          try {
            JoinTripUiState.Ready(service.preview(token))
          } catch (exception: CancellationException) {
            throw exception
          } catch (exception: InvitationException.Invalid) {
            JoinTripUiState.Invalid
          } catch (exception: Exception) {
            JoinTripUiState.Failed
          }
    }
  }

  /** Joins the trip. A second tap while joining, or after joining, does nothing. */
  fun join() {
    val preview =
        when (val state = _uiState.value) {
          is JoinTripUiState.Ready -> state.preview
          else -> return
        }
    _uiState.value = JoinTripUiState.Joining(preview)
    job = viewModelScope.launch {
      _uiState.value =
          try {
            JoinTripUiState.Joined(service.accept(token))
          } catch (exception: CancellationException) {
            throw exception
          } catch (exception: InvitationException.Invalid) {
            JoinTripUiState.Invalid
          } catch (exception: InvitationException.Full) {
            JoinTripUiState.Full(preview)
          } catch (exception: Exception) {
            JoinTripUiState.Ready(preview, joinFailed = true)
          }
    }
  }

  companion object {
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
