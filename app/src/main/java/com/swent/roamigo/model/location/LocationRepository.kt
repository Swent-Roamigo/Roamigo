// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.location

import kotlinx.coroutines.flow.Flow

interface LocationRepository {
  /**
   * Whether the app currently holds a location permission (fine or coarse). The permission itself
   * is requested by the UI; this lets the app check the result.
   */
  fun hasLocationPermission(): Boolean

  /**
   * One-shot position of this device.
   *
   * @return the current location, or null if unavailable or permission is denied.
   */
  suspend fun getCurrentLocation(): DeviceLocation?

  /**
   * Opts the current user in to location sharing for a trip and publishes their current position.
   * While sharing is on, the position is updated periodically. Sharing is off by default and only
   * starts when this function is called. It stops by itself if the user loses access to the trip.
   *
   * @param tripId identifier of the trip to share the location with.
   */
  suspend fun startSharing(tripId: String)

  /**
   * Stops publishing the current user's position for a trip. Once stopped, the user no longer
   * appears in other members' [observeMemberLocations] flow.
   *
   * @param tripId identifier of the trip to stop sharing the location with.
   */
  suspend fun stopSharing(tripId: String)

  /**
   * Streams the locations of the trip members currently sharing their position. A new list is
   * emitted whenever a member moves, starts sharing or stops sharing. The current user is excluded,
   * since their own position comes from [getCurrentLocation]. Observing does not require this
   * device to hold a location permission.
   *
   * @param tripId identifier of the trip whose member locations are observed.
   * @return a flow of the sharing members' locations, each with its last-update timestamp.
   */
  fun observeMemberLocations(tripId: String): Flow<List<MemberLocation>>

  /**
   * Streams whether the current user is sharing their location for a trip, so the UI can show a
   * persistent sharing indicator.
   *
   * @param tripId identifier of the trip.
   * @return a flow emitting true while sharing is on, false otherwise.
   */
  fun isSharing(tripId: String): Flow<Boolean>
}
