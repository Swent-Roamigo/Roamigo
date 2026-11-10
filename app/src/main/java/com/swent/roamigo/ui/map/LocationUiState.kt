// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.map

import androidx.annotation.StringRes
import com.swent.roamigo.R
import com.swent.roamigo.model.location.MemberLocation

/** Location permission state of the current user, as seen by the trip map. */
enum class LocationPermissionState {
  /** The user has not answered a permission request yet. */
  NotRequested,
  Granted,
  /** The user refused the permission, and the app may still ask for it again. */
  Denied,
  /**
   * The user refused the permission and the system no longer shows the request, because it was
   * denied permanently or revoked from the settings. Only the settings can grant it again.
   */
  Revoked,
}

/** The sheet explaining the location permission, if one is open. */
enum class LocationPermissionSheet {
  None,

  /** Why the location is needed, shown before the system asks. */
  Rationale,

  /** The user refused. Sharing stays off, and the system asks again next time. */
  Denied,

  /** The system no longer asks, so only the app's settings can allow the location. */
  Blocked,
}

/** One-off requests to the trip map, which owns the Android parts: permission and settings. */
sealed interface LocationEffect {
  /** Show the system dialog asking for the location permission. */
  data object RequestPermission : LocationEffect

  /** Open the app's page in the system settings. */
  data object OpenSettings : LocationEffect
}

/** Why the last start or stop of the location sharing failed. */
enum class LocationSharingError {
  /** Sharing was asked for without a granted location permission. */
  PermissionRequired,

  /** The repository could not start or stop the sharing. */
  RequestFailed,
}

/**
 * State rendered by the trip map for location sharing.
 *
 * @property permissionState current location permission state of the user.
 * @property permissionSheet the sheet explaining the location permission, if one is open.
 * @property isSharing whether the user shares their location with the trip. Off by default.
 * @property myLocation last known position of the current user, or null if unknown.
 * @property isMyLocationStale whether [myLocation] is outdated, e.g. because the GPS signal was
 *   lost.
 * @property memberLocations locations of the other trip members currently sharing.
 * @property staleMemberIds identifiers of the members whose location is outdated.
 * @property isOffline whether member locations cannot be refreshed because the device is offline.
 * @property sharingError why the last start or stop of the sharing failed, or null if it succeeded
 *   or is still running.
 * @property observationErrorMessage message describing why the member locations could not be read,
 *   or null once they were read again.
 */
data class LocationUiState(
    val permissionState: LocationPermissionState = LocationPermissionState.NotRequested,
    val permissionSheet: LocationPermissionSheet = LocationPermissionSheet.None,
    val isSharing: Boolean = false,
    val myLocation: MemberLocation? = null,
    val isMyLocationStale: Boolean = false,
    val memberLocations: List<MemberLocation> = emptyList(),
    val staleMemberIds: Set<String> = emptySet(),
    val isOffline: Boolean = false,
    val sharingError: LocationSharingError? = null,
    val observationErrorMessage: String? = null,
) {
  /**
   * Localized message of the error to show, or null if there is none. A [sharingError] comes first,
   * since it answers an action the user just made. Only the kind of error picks the text:
   * [observationErrorMessage] is not localized and may come from the repository, so it is never
   * shown.
   */
  @get:StringRes
  val errorMessageRes: Int?
    get() =
        when {
          sharingError == LocationSharingError.PermissionRequired ->
              R.string.trip_map_error_permission_required
          sharingError == LocationSharingError.RequestFailed ->
              R.string.trip_map_error_sharing_failed
          observationErrorMessage != null -> R.string.trip_map_error_locations_unavailable
          else -> null
        }

  /**
   * Own position to show on the map, or null if none. Only shown while the user shares with the
   * permission granted: [myLocation] keeps its last value after sharing stops.
   */
  val shownMyLocation: MemberLocation?
    get() = myLocation.takeIf { isSharing && permissionState == LocationPermissionState.Granted }
}
