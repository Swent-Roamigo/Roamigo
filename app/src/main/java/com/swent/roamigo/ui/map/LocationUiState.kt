// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.map

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

/**
 * State rendered by the trip map for location sharing.
 *
 * @property permissionState current location permission state of the user.
 * @property isSharing whether the user shares their location with the trip. Off by default.
 * @property myLocation last known position of the current user, or null if unknown.
 * @property isMyLocationStale whether [myLocation] is outdated, e.g. because the GPS signal was
 *   lost.
 * @property memberLocations locations of the other trip members currently sharing.
 * @property staleMemberIds identifiers of the members whose location is outdated.
 * @property isOffline whether member locations cannot be refreshed because the device is offline.
 * @property sharingErrorMessage message describing why the last start or stop of the sharing
 *   failed, or null if it succeeded.
 * @property observationErrorMessage message describing why the member locations could not be read,
 *   or null once they were read again.
 */
data class LocationUiState(
    val permissionState: LocationPermissionState = LocationPermissionState.NotRequested,
    val isSharing: Boolean = false,
    val myLocation: MemberLocation? = null,
    val isMyLocationStale: Boolean = false,
    val memberLocations: List<MemberLocation> = emptyList(),
    val staleMemberIds: Set<String> = emptySet(),
    val isOffline: Boolean = false,
    val sharingErrorMessage: String? = null,
    val observationErrorMessage: String? = null,
) {
  /**
   * Message describing the error to show, or null if none. A sharing error comes first, since it
   * answers an action the user just made.
   */
  val errorMessage: String?
    get() = sharingErrorMessage ?: observationErrorMessage
}
