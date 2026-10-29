// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.map

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swent.roamigo.model.connectivity.ConnectivityRepository
import com.swent.roamigo.model.location.DeviceLocation
import com.swent.roamigo.model.location.LocationRepository
import com.swent.roamigo.model.location.LocationSharingException
import com.swent.roamigo.model.location.MemberLocation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

/**
 * Provides the location sharing state of a trip to the trip map.
 *
 * Sharing itself is handled by [LocationRepository]: this ViewModel only asks it to start or stop,
 * mirrors whether sharing is on, and refreshes the user's own position while it is. Other members'
 * locations are shown whatever the permission state, and age into stale, then hidden. While the
 * device is offline, the last shown locations stay on the map, marked stale, and everything resumes
 * on reconnection.
 *
 * @property tripId identifier of the trip whose locations are managed.
 * @property currentUserId identifier of the signed-in user, used for their own position.
 * @property locationRepository the repository providing device and member locations.
 * @property connectivityRepository tells whether the device is online.
 * @property clock returns the current time, in milliseconds since the epoch. Used to age member
 *   locations and to date a position the device did not timestamp.
 * @property cleanupScope outlives the ViewModel, so that sharing can still be stopped once it is
 *   cleared.
 */
class LocationViewModel(
    private val tripId: String,
    private val currentUserId: String,
    private val locationRepository: LocationRepository,
    private val connectivityRepository: ConnectivityRepository,
    private val clock: () -> Long = System::currentTimeMillis,
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

  // Last start or stop request. A start waits for it, so that a start reaches the repository after
  // the requests the user made before it.
  private var lastSharingRequest: Job? = null

  // Parent of the pending start requests, so that a stop can cancel all of them at once.
  private val startRequests = SupervisorJob(viewModelScope.coroutineContext.job)

  private var refreshJob: Job? = null

  // Whether the user's last request was to share. Lets onCleared stop a sharing that the
  // repository has not reported yet.
  private var wantsSharing = false

  // Observes the connectivity, and the member locations while online. Kept so that leaving the trip
  // can stop it on its own.
  private val memberLocationsJob: Job

  // Set once the user left the trip, after which no location is shown again.
  private var hasLeftTrip = false

  init {
    viewModelScope.launch {
      locationRepository.isSharing(tripId).distinctUntilChanged().collect { sharing ->
        _uiState.update { state -> state.copy(isSharing = sharing) }
        if (sharing) startRefreshingMyLocation() else stopRefreshingMyLocation()
      }
    }
    memberLocationsJob = viewModelScope.launch {
      // Sharing is left to the repository, which keeps retrying its updates while offline.
      connectivityRepository.isOnline.collectLatest { online ->
        _uiState.update { state -> state.copy(isOffline = !online) }
        if (online) {
          // Observed again on each reconnection, so that the locations resume even if the
          // previous observation failed.
          observeMemberLocations()
        } else {
          // Nothing tells whether the members moved or stopped sharing, so the shown locations are
          // kept as they are, but marked stale.
          _uiState.update { state ->
            state.copy(staleMemberIds = state.memberLocations.mapTo(mutableSetOf()) { it.userId })
          }
        }
      }
    }
  }

  /**
   * Updates the permission state after the user answers a permission request. Call only from the
   * permission request callback: rechecks that are not the result of a request go through
   * [onPermissionChecked]. A refusal is reported as [LocationPermissionState.Denied] while the app
   * may still ask again, and as [LocationPermissionState.Revoked] once the system no longer shows
   * the request. Granting the permission does not start sharing: only [startSharing] does.
   *
   * @param granted whether the app now holds a location permission.
   * @param shouldShowRationale value of `shouldShowRequestPermissionRationale` after the request.
   */
  fun onPermissionResult(granted: Boolean, shouldShowRationale: Boolean) {
    _uiState.update { state ->
      state.copy(permissionState = nextPermissionState(granted, shouldShowRationale))
    }
  }

  /**
   * Updates the permission state after the permission is checked again without a request, for
   * example when the screen resumes. A recheck never reports [LocationPermissionState.Revoked] and
   * never starts sharing: only [startSharing] does.
   *
   * @param granted whether the app now holds a location permission.
   */
  fun onPermissionChecked(granted: Boolean) {
    _uiState.update { state ->
      state.copy(permissionState = checkedPermissionState(state.permissionState, granted))
    }
  }

  /**
   * Starts sharing the user's location with the trip. Without a granted location permission,
   * nothing is shared and [LocationUiState.sharingErrorMessage] explains why.
   */
  fun startSharing() {
    if (hasLeftTrip) return
    if (_uiState.value.permissionState != LocationPermissionState.Granted) {
      _uiState.update { state -> state.copy(sharingErrorMessage = PERMISSION_REQUIRED_MESSAGE) }
      return
    }
    wantsSharing = true
    lastSharingRequest =
        launchSharingRequest(after = lastSharingRequest, context = startRequests) {
          locationRepository.startSharing(tripId)
        }
  }

  /** Stops sharing the user's location with the trip. The own position stops refreshing at once. */
  fun stopSharing() {
    wantsSharing = false
    stopRefreshingMyLocation()
    // Cancelled instead of awaited: a start may wait long for its first update, and the repository
    // lets a stop interrupt it, so that nothing is published once the user stopped.
    startRequests.cancelChildren()
    lastSharingRequest = launchSharingRequest { locationRepository.stopSharing(tripId) }
  }

  /**
   * Stops sharing when the user leaves the trip, and clears every location shown at once, since the
   * user must no longer see its members. Member locations stop being observed and the own position
   * stops refreshing for good: later connectivity changes do not bring them back, and
   * [startSharing] does nothing. Calling it again has no effect.
   */
  fun onLeaveTrip() {
    if (hasLeftTrip) return
    hasLeftTrip = true
    stopSharing()
    memberLocationsJob.cancel()
    _uiState.update { state ->
      state.copy(
          memberLocations = emptyList(),
          staleMemberIds = emptySet(),
          myLocation = null,
          isMyLocationStale = false,
      )
    }
  }

  override fun onCleared() {
    // viewModelScope is already cancelled here, which cancels every collection and request. A
    // request that did not complete, like a stop made just before, may never reach the repository.
    if (wantsSharing || _uiState.value.isSharing || lastSharingRequest?.isCompleted == false) {
      cleanupScope.launch {
        try {
          locationRepository.stopSharing(tripId)
        } catch (exception: LocationSharingException) {
          Log.w(TAG, "Could not stop the location sharing", exception)
        }
      }
    }
  }

  private fun launchSharingRequest(
      after: Job? = null,
      context: CoroutineContext = EmptyCoroutineContext,
      request: suspend () -> Unit,
  ): Job =
      viewModelScope.launch(context) {
        after?.join()
        _uiState.update { state -> state.copy(sharingErrorMessage = null) }
        try {
          request()
        } catch (exception: LocationSharingException) {
          _uiState.update { state -> state.copy(sharingErrorMessage = exception.message) }
        } catch (exception: IllegalStateException) {
          // Thrown when the permission was withdrawn outside the app or the user signed out.
          _uiState.update { state ->
            state.copy(sharingErrorMessage = exception.message ?: SHARING_FAILED_MESSAGE)
          }
        }
      }

  private suspend fun observeMemberLocations() {
    locationRepository
        .observeMemberLocations(tripId)
        .catch { exception ->
          if (exception !is LocationSharingException) throw exception
          _uiState.update { state -> state.copy(observationErrorMessage = exception.message) }
        }
        .collectLatest { locations ->
          // A successful read shows the error of an earlier observation is over. A sharing error
          // is left as is, since only a new sharing request answers it.
          _uiState.update { state -> state.copy(observationErrorMessage = null) }
          // Locations age without new emissions, so they are re-evaluated until none is visible.
          // A new emission cancels this loop and starts over with the fresh list.
          while (true) {
            val visibleLocations = showMemberLocations(locations)
            if (visibleLocations.isEmpty()) break
            delay(STALENESS_CHECK_INTERVAL)
          }
        }
  }

  /**
   * Shows the member locations that are not too old, marking the outdated ones as stale.
   *
   * @return the locations shown.
   */
  private fun showMemberLocations(locations: List<MemberLocation>): List<MemberLocation> {
    val now = clock()
    val visibleLocations = locations.filter { location ->
      now - location.updatedAt <= HIDE_AFTER.inWholeMilliseconds
    }
    val staleMemberIds =
        visibleLocations
            .filter { location -> location.isStaleAt(now) }
            .mapTo(mutableSetOf()) { location -> location.userId }
    _uiState.update { state ->
      state.copy(memberLocations = visibleLocations, staleMemberIds = staleMemberIds)
    }
    return visibleLocations
  }

  private fun startRefreshingMyLocation() {
    // The repository may still report a sharing after the trip was left, e.g. until its stop ends.
    if (hasLeftTrip || refreshJob?.isActive == true) return
    refreshJob = viewModelScope.launch {
      while (isActive) {
        val location = locationRepository.getCurrentLocation()?.toMemberLocation()
        val now = clock()
        _uiState.update { state ->
          // Without a GPS signal there is no position, so the last known one is kept and ages.
          val myLocation = location ?: state.myLocation
          state.copy(
              myLocation = myLocation,
              isMyLocationStale = myLocation?.isStaleAt(now) == true,
          )
        }
        delay(MY_LOCATION_REFRESH_INTERVAL)
      }
    }
  }

  private fun stopRefreshingMyLocation() {
    refreshJob?.cancel()
    refreshJob = null
  }

  private fun MemberLocation.isStaleAt(now: Long): Boolean =
      now - updatedAt > STALE_AFTER.inWholeMilliseconds

  private fun DeviceLocation.toMemberLocation(): MemberLocation =
      MemberLocation(
          userId = currentUserId,
          latitude = latitude,
          longitude = longitude,
          updatedAt = timestampMillis ?: clock(),
      )

  companion object {

    /** Time between two refreshes of the user's own position while sharing. */
    val MY_LOCATION_REFRESH_INTERVAL: Duration = 5.seconds

    /**
     * Age after which a location is marked stale. Members update every 20 seconds while sharing, so
     * this means several updates were missed.
     */
    val STALE_AFTER: Duration = 1.minutes

    /** Age after which a member location is no longer shown. */
    val HIDE_AFTER: Duration = 10.minutes

    /** Time between two re-evaluations of the member locations' age. */
    val STALENESS_CHECK_INTERVAL: Duration = 10.seconds

    internal const val PERMISSION_REQUIRED_MESSAGE = "Allow location access to share your location."

    internal const val SHARING_FAILED_MESSAGE = "Unable to update location sharing."

    private const val TAG = "LocationViewModel"

    /**
     * Creates a factory that supplies the dependencies required by [LocationViewModel].
     *
     * @param tripId identifier of the trip whose locations are managed.
     * @param currentUserId identifier of the signed-in user.
     * @param locationRepository the repository providing device and member locations.
     * @param connectivityRepository tells whether the device is online.
     * @param clock returns the current time, in milliseconds since the epoch.
     * @return a factory capable of creating [LocationViewModel] instances.
     */
    fun factory(
        tripId: String,
        currentUserId: String,
        locationRepository: LocationRepository,
        connectivityRepository: ConnectivityRepository,
        clock: () -> Long = System::currentTimeMillis,
    ): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {

          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LocationViewModel::class.java)) {
              return LocationViewModel(
                  tripId = tripId,
                  currentUserId = currentUserId,
                  locationRepository = locationRepository,
                  connectivityRepository = connectivityRepository,
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
 * Android stops showing the rationale once the system no longer displays the request dialog, so a
 * refusal without rationale means the permission was denied permanently or revoked from the
 * settings, and only the settings can grant it again.
 *
 * @param granted whether the app now holds a location permission.
 * @param shouldShowRationale value of `shouldShowRequestPermissionRationale` after the request.
 * @return the permission state after the result.
 */
internal fun nextPermissionState(
    granted: Boolean,
    shouldShowRationale: Boolean,
): LocationPermissionState =
    when {
      granted -> LocationPermissionState.Granted
      shouldShowRationale -> LocationPermissionState.Denied
      else -> LocationPermissionState.Revoked
    }

/**
 * Computes the permission state after the permission is checked again without a request.
 *
 * `shouldShowRequestPermissionRationale` is also false before the app ever asked, so a recheck
 * cannot tell a permanent refusal from a first request and never reports
 * [LocationPermissionState.Revoked]. A permission lost while granted becomes
 * [LocationPermissionState.Denied] so that the app asks again; if the system no longer shows the
 * request, the result of that request reports [LocationPermissionState.Revoked]. Any other refusal
 * keeps the current state.
 *
 * @param current the permission state before the check.
 * @param granted whether the app now holds a location permission.
 * @return the permission state after the check.
 */
internal fun checkedPermissionState(
    current: LocationPermissionState,
    granted: Boolean,
): LocationPermissionState =
    when {
      granted -> LocationPermissionState.Granted
      current == LocationPermissionState.Granted -> LocationPermissionState.Denied
      else -> current
    }
