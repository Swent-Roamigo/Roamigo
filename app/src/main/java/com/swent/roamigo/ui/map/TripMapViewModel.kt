// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swent.roamigo.model.trip.TripRepository
import com.swent.roamigo.model.users.User
import com.swent.roamigo.model.users.UserRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Loads a single trip for the trip map and exposes it with the viewport showing its destinations
 * and the display information of its members.
 *
 * @param tripId identifier of the trip to show.
 * @param tripRepository the repository providing the user's trips.
 * @param userRepository the repository providing the members' profiles.
 */
class TripMapViewModel(
    private val tripId: String,
    private val tripRepository: TripRepository,
    private val userRepository: UserRepository,
) : ViewModel() {
  private val _uiState = MutableStateFlow<TripMapUiState>(TripMapUiState.Loading)
  val uiState: StateFlow<TripMapUiState> = _uiState.asStateFlow()

  private var loadJob: Job? = null

  init {
    retry()
  }

  /** Loads the trip again, cancelling any running load so only the latest one updates the UI. */
  fun retry() {
    loadJob?.cancel()
    loadJob = viewModelScope.launch {
      _uiState.value = TripMapUiState.Loading
      // The repository has no single-trip getter, so the trip is picked among the user's trips.
      val result = runCatching { tripRepository.getTrips().find { it.uid == tripId } }
      _uiState.value =
          result.fold(
              onSuccess = { trip ->
                if (trip == null) {
                  TripMapUiState.Error(TRIP_NOT_FOUND_MESSAGE)
                } else {
                  val coordinates =
                      trip.destinations.map { MapCoordinate(it.latitude, it.longitude) }
                  TripMapUiState.Loaded(
                      trip = trip,
                      viewport = tripMapViewportOf(coordinates),
                      members = loadMembers(trip.memberIds),
                  )
                }
              },
              onFailure = { error ->
                if (error is CancellationException) throw error
                TripMapUiState.Error(error.localizedMessage ?: LOAD_FAILED_MESSAGE)
              },
          )
    }
  }

  private suspend fun loadMembers(memberIds: List<String>): Map<String, MemberDisplayInfo> {
    val usersById =
        try {
          userRepository.getUsers(memberIds.toSet()).associateBy { it.uid }
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          // Profiles only decorate the markers: the map stays usable with fallback names.
          emptyMap()
        }
    return memberIds.associateWith { userId -> memberDisplayInfo(userId, usersById[userId]) }
  }

  companion object {

    internal const val TRIP_NOT_FOUND_MESSAGE = "This trip could not be found."

    internal const val LOAD_FAILED_MESSAGE = "Unable to load the trip."

    /**
     * Creates a factory that supplies the dependencies required by [TripMapViewModel].
     *
     * @param tripId identifier of the trip to show.
     * @param tripRepository the repository providing the user's trips.
     * @param userRepository the repository providing the members' profiles.
     * @return a factory capable of creating [TripMapViewModel] instances.
     */
    fun factory(
        tripId: String,
        tripRepository: TripRepository,
        userRepository: UserRepository,
    ): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {

          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(TripMapViewModel::class.java)) {
              return TripMapViewModel(
                  tripId = tripId,
                  tripRepository = tripRepository,
                  userRepository = userRepository,
              )
                  as T
            }

            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
          }
        }
  }
}

/**
 * Builds the display information of a trip member.
 *
 * @param userId identifier of the member.
 * @param user profile of the member, or null if it could not be found.
 * @return the member's name and avatar, falling back to their user ID's initial and the default
 *   avatar.
 */
internal fun memberDisplayInfo(userId: String, user: User?): MemberDisplayInfo {
  val displayName =
      user?.displayName?.trim()?.takeIf { it.isNotEmpty() } ?: userId.take(1).uppercase()
  return MemberDisplayInfo(
      displayName = displayName,
      initial = displayName.take(1).uppercase(),
      avatarUrl = user?.profilePictureUrl?.takeIf { it.isNotBlank() },
  )
}
