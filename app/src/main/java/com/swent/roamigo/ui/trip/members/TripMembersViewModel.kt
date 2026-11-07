// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.members

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swent.roamigo.model.trip.TripRepository
import com.swent.roamigo.model.users.UserRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One person on the trip. A blank [name] means the profile could not be resolved. */
data class TripMemberUiModel(val userId: String, val name: String, val isOrganiser: Boolean)

/** Presentation state of the Members screen. */
sealed interface TripMembersUiState {
  data object Loading : TripMembersUiState

  data object Error : TripMembersUiState

  data class Loaded(val members: List<TripMemberUiModel>) : TripMembersUiState
}

/**
 * Loads who belongs to a trip and their role.
 *
 * Location-sharing status is deliberately not exposed here, since it is not shared with other
 * members through this screen.
 */
class TripMembersViewModel(
    private val tripId: String,
    private val tripRepository: TripRepository,
    private val userRepository: UserRepository,
) : ViewModel() {
  private val _uiState = MutableStateFlow<TripMembersUiState>(TripMembersUiState.Loading)
  val uiState: StateFlow<TripMembersUiState> = _uiState.asStateFlow()
  private var loadJob: Job? = null

  init {
    load()
  }

  /** Retries a failed load, cancelling any previous request. */
  fun retry() = load()

  private fun load() {
    loadJob?.cancel()
    loadJob = viewModelScope.launch {
      _uiState.value = TripMembersUiState.Loading
      _uiState.value =
          try {
            val ownerId = tripRepository.getTrip(tripId)?.ownerId
            val ids = tripRepository.getMembersForTrip(tripId).map { it.userId }.distinct()
            val names = userRepository.getUsers(ids.toSet()).associate { it.uid to it.displayName }
            TripMembersUiState.Loaded(
                ids.map { TripMemberUiModel(it, names[it].orEmpty(), it == ownerId) }
            )
          } catch (exception: CancellationException) {
            throw exception
          } catch (exception: Exception) {
            TripMembersUiState.Error
          }
    }
  }

  companion object {
    fun factory(
        tripId: String,
        tripRepository: TripRepository,
        userRepository: UserRepository,
    ): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(TripMembersViewModel::class.java))
            return TripMembersViewModel(tripId, tripRepository, userRepository) as T
          }
        }
  }
}
