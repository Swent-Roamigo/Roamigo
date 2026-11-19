// Implemented with assistance from Claude (Anthropic) and OpenAI Codex.
package com.swent.roamigo.ui.map

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swent.roamigo.model.photo.PhotoRepository
import com.swent.roamigo.model.trip.TripRepository
import com.swent.roamigo.model.trip.activity.ActivityRepository
import com.swent.roamigo.model.users.User
import com.swent.roamigo.model.users.UserRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Loads a trip and independently observes its activities and gallery photos.
 *
 * The viewport covers destinations, activities and suggestions. Member profiles may load later.
 * Gallery photos load only once the map has been shown, see [onMapShown], since the trip screen
 * creates this ViewModel for every tab.
 *
 * @param tripId identifier of the trip to show.
 * @param tripRepository the repository providing the user's trips.
 * @param activityRepository the repository providing live activity and suggestion updates.
 * @param userRepository the repository providing the members' profiles.
 * @param photoRepository the repository providing the trip's gallery photos.
 */
class TripMapViewModel(
    private val tripId: String,
    private val tripRepository: TripRepository,
    private val activityRepository: ActivityRepository,
    private val userRepository: UserRepository,
    private val photoRepository: PhotoRepository,
) : ViewModel() {
  private val _uiState = MutableStateFlow<TripMapUiState>(TripMapUiState.Loading)
  val uiState: StateFlow<TripMapUiState> = _uiState.asStateFlow()

  private var tripLoadJob: Job? = null
  private var activitiesObserveJob: Job? = null
  private var photosObserveJob: Job? = null
  private var photosRequested = false

  init {
    retryTrip()
  }

  /**
   * Loads the trip again, cancelling any running load so only the latest one updates the UI.
   *
   * The trip is shown as soon as it is fetched, with fallback information for every member.
   * Activities and member profiles load independently. Activity failures leave the trip usable and
   * can be retried separately.
   */
  fun retryTrip() {
    tripLoadJob?.cancel()
    activitiesObserveJob?.cancel()
    photosObserveJob?.cancel()
    _uiState.value = TripMapUiState.Loading
    tripLoadJob = viewModelScope.launch {
      try {
        val trip = tripRepository.getTrips().find { it.uid == tripId }
        if (trip == null) {
          _uiState.value = TripMapUiState.Error(TRIP_NOT_FOUND_MESSAGE)
          return@launch
        }
        currentCoroutineContext().ensureActive()
        val loaded =
            TripMapUiState.Loaded(
                trip = trip,
                viewport =
                    tripMapViewportOf(
                        trip.destinations.map { MapCoordinate(it.latitude, it.longitude) }
                    ),
                members =
                    trip.memberIds.associateWith { userId -> memberDisplayInfo(userId, null) },
            )
        _uiState.value = loaded
        observeActivities(loaded)
        if (photosRequested) observePhotos()

        val profiles = loadProfiles(trip.memberIds)
        val members =
            trip.memberIds.associateWith { userId -> memberDisplayInfo(userId, profiles[userId]) }
        val photographerNames =
            profiles
                .mapNotNull { (userId, user) ->
                  user.displayName.trim().takeIf { it.isNotEmpty() }?.let { userId to it }
                }
                .toMap()
        updateLoaded { it.copy(members = members, photographerNames = photographerNames) }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        // Repository messages can expose backend details, so users only see a fixed message.
        Log.w(TAG, "Could not load trip $tripId", e)
        _uiState.value = TripMapUiState.Error(LOAD_FAILED_MESSAGE)
      }
    }
  }

  /**
   * Restarts activity and suggestion observation without reloading the trip.
   *
   * Cancels the previous observer and preserves the trip, members and existing markers while
   * loading or on failure. Calls made before the trip is loaded have no effect.
   */
  fun retryActivities() {
    val state = _uiState.value as? TripMapUiState.Loaded ?: return
    observeActivities(state)
  }

  private fun observeActivities(initialState: TripMapUiState.Loaded) {
    activitiesObserveJob?.cancel()
    activitiesObserveJob = viewModelScope.launch {
      _uiState.update { state ->
        if (state is TripMapUiState.Loaded && state.trip.uid == tripId) {
          state.copy(activitiesLoadState = ActivitiesLoadState.LOADING)
        } else {
          state
        }
      }
      try {
        activityRepository.observeActivities(tripId).collect { activities ->
          currentCoroutineContext().ensureActive()
          val activityMarkers = activities.toTripMarkerUiStates()
          _uiState.update { state ->
            val loaded = state as? TripMapUiState.Loaded ?: initialState
            val coordinates =
                (loaded.trip.destinations.map { MapCoordinate(it.latitude, it.longitude) } +
                        activityMarkers.map { MapCoordinate(it.latitude, it.longitude) })
                    .distinct()
            loaded.copy(
                activityMarkers = activityMarkers,
                viewport = tripMapViewportOf(coordinates),
                activitiesLoadState = ActivitiesLoadState.LOADED,
            )
          }
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        currentCoroutineContext().ensureActive()
        Log.w(TAG, "Could not load activities for trip $tripId", e)
        _uiState.update { state ->
          val loaded = state as? TripMapUiState.Loaded ?: initialState
          loaded.copy(activitiesLoadState = ActivitiesLoadState.ERROR)
        }
      }
    }
  }

  /**
   * Restarts photo observation, retaining current markers during loading and failure.
   *
   * Calls before the trip loads have no effect.
   */
  fun retryPhotos() {
    if (_uiState.value !is TripMapUiState.Loaded) return
    observePhotos()
  }

  /**
   * Starts observing the gallery the first time the map is shown, so opening another tab of the
   * trip does not download its photos. Later calls have no effect: once started, photos are
   * restarted only by [retryPhotos] or [retryTrip].
   */
  fun onMapShown() {
    photosRequested = true
    if (_uiState.value is TripMapUiState.Loaded && photosObserveJob == null) {
      observePhotos()
    }
  }

  /**
   * Opens the photo group at [coordinate] in the viewer. Coordinates without a current group are
   * ignored, e.g. a tap on a marker removed by a gallery update.
   */
  fun selectPhotoGroup(coordinate: MapCoordinate) {
    updateLoaded { state ->
      if (state.photoMarkers.any { it.coordinate == coordinate }) {
        state.copy(selectedPhotoCoordinate = coordinate)
      } else {
        state
      }
    }
  }

  /** Closes the photo viewer and returns to the map. */
  fun closePhotoViewer() {
    updateLoaded { it.copy(selectedPhotoCoordinate = null) }
  }

  private fun observePhotos() {
    photosObserveJob?.cancel()
    photosObserveJob = viewModelScope.launch {
      updateLoaded { it.copy(photosLoadState = PhotosLoadState.LOADING) }
      try {
        photoRepository.tripPhotos(tripId).collect { photos ->
          currentCoroutineContext().ensureActive()
          val markers = photos.toPhotoMarkerUiStates()
          updateLoaded { it.withPhotoMarkers(markers) }
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        currentCoroutineContext().ensureActive()
        Log.w(TAG, "Could not load photos for trip $tripId", e)
        updateLoaded { it.copy(photosLoadState = PhotosLoadState.ERROR) }
      }
    }
  }

  /** Applies [transform] to the state while it shows this trip, and leaves other states alone. */
  private inline fun updateLoaded(
      crossinline transform: (TripMapUiState.Loaded) -> TripMapUiState.Loaded
  ) {
    _uiState.update { state ->
      if (state is TripMapUiState.Loaded && state.trip.uid == tripId) transform(state) else state
    }
  }

  private suspend fun loadProfiles(memberIds: List<String>): Map<String, User> =
      try {
        userRepository.getUsers(memberIds.toSet()).associateBy { it.uid }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        // Profiles only decorate the markers: the map stays usable with the fallback names.
        emptyMap()
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
     * @param activityRepository the repository providing live activity and suggestion updates.
     * @param userRepository the repository providing the members' profiles.
     * @param photoRepository the repository providing the trip's gallery photos.
     * @return a factory capable of creating [TripMapViewModel] instances.
     */
    fun factory(
        tripId: String,
        tripRepository: TripRepository,
        activityRepository: ActivityRepository,
        userRepository: UserRepository,
        photoRepository: PhotoRepository,
    ): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {

          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(TripMapViewModel::class.java)) {
              return TripMapViewModel(
                  tripId = tripId,
                  tripRepository = tripRepository,
                  activityRepository = activityRepository,
                  userRepository = userRepository,
                  photoRepository = photoRepository,
              )
                  as T
            }

            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
          }
        }
  }
}

/**
 * Shows the latest gallery [markers]. The open group stays selected only while it is still in the
 * gallery, so the viewer closes when its photos are removed.
 */
private fun TripMapUiState.Loaded.withPhotoMarkers(
    markers: List<PhotoMarkerUiState>
): TripMapUiState.Loaded =
    copy(
        photoMarkers = markers,
        photosLoadState = PhotosLoadState.LOADED,
        selectedPhotoCoordinate =
            selectedPhotoCoordinate?.takeIf { selected ->
              markers.any { it.coordinate == selected }
            },
    )

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
