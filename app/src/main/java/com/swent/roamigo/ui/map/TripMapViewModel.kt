// Implemented with assistance from Claude (Anthropic) and OpenAI Codex.
package com.swent.roamigo.ui.map

import android.util.Log
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Loads a single trip and its activity markers for the trip map, with a viewport covering its
 * destinations, activities and suggestions. The trip is shown without waiting for the members'
 * profiles, which may load more slowly.
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

  /**
   * Loads the trip again, cancelling any running load so only the latest one updates the UI.
   *
   * The map is shown once the trip and its activities are loaded, with fallback information for
   * every member. The members' profiles are filled in once they are fetched.
   */
  fun retry() {
    loadJob?.cancel()
    loadJob = viewModelScope.launch {
      _uiState.value = TripMapUiState.Loading
      try {
        val trip = tripRepository.getTrips().find { it.uid == tripId }
        if (trip == null) {
          _uiState.value = TripMapUiState.Error(TRIP_NOT_FOUND_MESSAGE)
          return@launch
        }

        val activityMarkers = tripRepository.getActivitiesForTrip(tripId).toTripMarkerUiStates()
        val coordinates =
            (trip.destinations.map { MapCoordinate(it.latitude, it.longitude) } +
                    activityMarkers.map { MapCoordinate(it.latitude, it.longitude) })
                .distinct()
        _uiState.value =
            TripMapUiState.Loaded(
                trip = trip,
                viewport = tripMapViewportOf(coordinates),
                members =
                    trip.memberIds.associateWith { userId -> memberDisplayInfo(userId, null) },
                activityMarkers = activityMarkers,
            )

        val members = loadMembers(trip.memberIds)
        _uiState.update { state ->
          if (state is TripMapUiState.Loaded && state.trip.uid == trip.uid) {
            state.copy(members = members)
          } else {
            state
          }
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        // Repository messages can expose backend details, so users only see a fixed message.
        Log.w(TAG, "Could not load trip $tripId", e)
        _uiState.value = TripMapUiState.Error(LOAD_FAILED_MESSAGE)
      }
    }
  }

  private suspend fun loadMembers(memberIds: List<String>): Map<String, MemberDisplayInfo> {
    val usersById =
        try {
          userRepository.getUsers(memberIds.toSet()).associateBy { it.uid }
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          // Profiles only decorate the markers: the map stays usable with the fallback names.
          emptyMap()
        }
    return memberIds.associateWith { userId -> memberDisplayInfo(userId, usersById[userId]) }
  }

  companion object {

    internal const val TRIP_NOT_FOUND_MESSAGE = "This trip could not be found."

    internal const val LOAD_FAILED_MESSAGE = "Unable to load the trip."

    private const val TAG = "TripMapViewModel"

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
