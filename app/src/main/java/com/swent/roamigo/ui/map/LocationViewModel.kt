// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.map

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swent.roamigo.model.location.DeviceLocation
import com.swent.roamigo.model.location.LocationRepository
import com.swent.roamigo.model.location.LocationSharingException
import com.swent.roamigo.model.location.MemberLocation
import java.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Provides the location sharing state of a trip to the trip map.
 *
 * Sharing itself is handled by [LocationRepository]: this ViewModel only asks it to start or stop,
 * mirrors whether sharing is on, and refreshes the user's own position while it is.
 *
 * @property tripId identifier of the trip whose locations are managed.
 * @property currentUserId identifier of the signed-in user, used for their own position.
 * @property locationRepository the repository providing device and member locations.
 * @property clock the clock used to date a position the device did not timestamp.
 * @property cleanupScope outlives the ViewModel, so that sharing can still be stopped once it is
 *   cleared.
 */
class LocationViewModel(
    private val tripId: String,
    private val currentUserId: String,
    private val locationRepository: LocationRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val cleanupScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : ViewModel() {

  private val _uiState =
      MutableStateFlow(
          LocationUiState(
              permissionState =
                  if (locationRepository.hasLocationPermission()) {
                    LocationPermissionState.Granted
                  } else {
                    LocationPermissionState.NotRequested
                  }
          )
      )

  /** State currently rendered by the trip map. */
  val uiState: StateFlow<LocationUiState> = _uiState.asStateFlow()

  // Last start or stop request. Each request waits for the previous one, so they reach the
  // repository in the order the user made them.
  private var sharingJob: Job? = null

  private var refreshJob: Job? = null

  // Whether the user's last request was to share. Lets onCleared stop a sharing that the
  // repository has not reported yet.
  private var wantsSharing = false

  init {
    viewModelScope.launch {
      locationRepository.isSharing(tripId).distinctUntilChanged().collect { sharing ->
        _uiState.update { state -> state.copy(isSharing = sharing) }
        if (sharing) startRefreshingMyLocation() else stopRefreshingMyLocation()
      }
    }
  }

  /**
   * Updates the permission state after the user answers a permission request or the permission is
   * checked again. Granting the permission does not start sharing: sharing stays opt-in.
   *
   * @param granted whether the app now holds a location permission.
   */
  fun onPermissionResult(granted: Boolean) {
    _uiState.update { state ->
      state.copy(permissionState = nextPermissionState(state.permissionState, granted))
    }
  }

  /**
   * Starts sharing the user's location with the trip. Without a granted location permission,
   * nothing is shared and [LocationUiState.errorMessage] explains why.
   */
  fun startSharing() {
    if (_uiState.value.permissionState != LocationPermissionState.Granted) {
      _uiState.update { state -> state.copy(errorMessage = PERMISSION_REQUIRED_MESSAGE) }
      return
    }
    wantsSharing = true
    runSharingRequest { locationRepository.startSharing(tripId) }
  }

  /** Stops sharing the user's location with the trip. The own position stops refreshing at once. */
  fun stopSharing() {
    wantsSharing = false
    stopRefreshingMyLocation()
    runSharingRequest { locationRepository.stopSharing(tripId) }
  }

  /** Stops sharing when the user leaves the trip, since they must no longer see its members. */
  fun onLeaveTrip() {
    stopSharing()
  }

  override fun onCleared() {
    // viewModelScope is already cancelled here, which cancels every collection and request.
    if (wantsSharing || _uiState.value.isSharing) {
      cleanupScope.launch {
        try {
          locationRepository.stopSharing(tripId)
        } catch (exception: LocationSharingException) {
          Log.w(TAG, "Could not stop the location sharing", exception)
        }
      }
    }
  }

  private fun runSharingRequest(request: suspend () -> Unit) {
    val previousRequest = sharingJob
    sharingJob = viewModelScope.launch {
      previousRequest?.join()
      _uiState.update { state -> state.copy(errorMessage = null) }
      try {
        request()
      } catch (exception: LocationSharingException) {
        _uiState.update { state -> state.copy(errorMessage = exception.message) }
      } catch (exception: IllegalStateException) {
        // Thrown when the permission was withdrawn outside the app or the user signed out.
        _uiState.update { state ->
          state.copy(errorMessage = exception.message ?: SHARING_FAILED_MESSAGE)
        }
      }
    }
  }

  private fun startRefreshingMyLocation() {
    if (refreshJob?.isActive == true) return
    refreshJob = viewModelScope.launch {
      while (isActive) {
        // Without a GPS signal there is no position, so the last known one is kept.
        locationRepository.getCurrentLocation()?.let { location ->
          _uiState.update { state -> state.copy(myLocation = location.toMemberLocation()) }
        }
        delay(MY_LOCATION_REFRESH_INTERVAL)
      }
    }
  }

  private fun stopRefreshingMyLocation() {
    refreshJob?.cancel()
    refreshJob = null
  }

  private fun DeviceLocation.toMemberLocation(): MemberLocation =
      MemberLocation(
          userId = currentUserId,
          latitude = latitude,
          longitude = longitude,
          updatedAt = timestampMillis ?: clock.millis(),
      )

  companion object {

    /** Time between two refreshes of the user's own position while sharing. */
    val MY_LOCATION_REFRESH_INTERVAL: Duration = 5.seconds

    internal const val PERMISSION_REQUIRED_MESSAGE = "Allow location access to share your location."

    internal const val SHARING_FAILED_MESSAGE = "Unable to update location sharing."

    private const val TAG = "LocationViewModel"

    /**
     * Creates a factory that supplies the dependencies required by [LocationViewModel].
     *
     * @param tripId identifier of the trip whose locations are managed.
     * @param currentUserId identifier of the signed-in user.
     * @param locationRepository the repository providing device and member locations.
     * @param clock the clock used to date a position the device did not timestamp.
     * @return a factory capable of creating [LocationViewModel] instances.
     */
    fun factory(
        tripId: String,
        currentUserId: String,
        locationRepository: LocationRepository,
        clock: Clock = Clock.systemDefaultZone(),
    ): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {

          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LocationViewModel::class.java)) {
              return LocationViewModel(
                  tripId = tripId,
                  currentUserId = currentUserId,
                  locationRepository = locationRepository,
                  clock = clock,
              )
                  as T
            }

            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
          }
        }
  }
}

/**
 * Computes the permission state after a permission result.
 *
 * A refusal after the permission was granted means the user withdrew it, which the map reports
 * differently from a first refusal.
 *
 * @param current the permission state before the result.
 * @param granted whether the app now holds a location permission.
 * @return the permission state after the result.
 */
internal fun nextPermissionState(
    current: LocationPermissionState,
    granted: Boolean,
): LocationPermissionState =
    when {
      granted -> LocationPermissionState.Granted
      current == LocationPermissionState.Granted || current == LocationPermissionState.Revoked ->
          LocationPermissionState.Revoked
      else -> LocationPermissionState.Denied
    }
