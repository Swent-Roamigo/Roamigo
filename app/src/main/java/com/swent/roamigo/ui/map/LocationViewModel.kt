// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.swent.roamigo.model.location.LocationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Provides the location sharing state of a trip to the trip map.
 *
 * @property tripId identifier of the trip whose locations are managed.
 * @property locationRepository the repository providing device and member locations.
 */
class LocationViewModel(
    private val tripId: String,
    private val locationRepository: LocationRepository,
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

  companion object {

    /**
     * Creates a factory that supplies the dependencies required by [LocationViewModel].
     *
     * @param tripId identifier of the trip whose locations are managed.
     * @param locationRepository the repository providing device and member locations.
     * @return a factory capable of creating [LocationViewModel] instances.
     */
    fun factory(
        tripId: String,
        locationRepository: LocationRepository,
    ): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {

          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LocationViewModel::class.java)) {
              return LocationViewModel(
                  tripId = tripId,
                  locationRepository = locationRepository,
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
