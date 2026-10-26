// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.location

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [LocationRepository] for unit tests. It records the calls it receives and can be made
 * to fail.
 */
class FakeLocationRepository(
    var hasPermission: Boolean = false,
    var currentLocation: DeviceLocation? = null,
) : LocationRepository {

  val memberLocations = MutableStateFlow<List<MemberLocation>>(emptyList())

  private val sharingTripIds = MutableStateFlow<Set<String>>(emptySet())

  val startSharingCalls = mutableListOf<String>()

  val stopSharingCalls = mutableListOf<String>()

  var getCurrentLocationCalls = 0
    private set

  /** Thrown by [startSharing] instead of starting, when set. */
  var startSharingError: Exception? = null

  /** Thrown by [stopSharing] after stopping, when set, like a failed removal from the database. */
  var stopSharingError: Exception? = null

  override fun hasLocationPermission(): Boolean = hasPermission

  override suspend fun getCurrentLocation(): DeviceLocation? {
    getCurrentLocationCalls++
    return currentLocation
  }

  override suspend fun startSharing(tripId: String) {
    startSharingCalls += tripId
    startSharingError?.let { throw it }
    check(hasPermission) { "Location permission is missing." }
    sharingTripIds.value += tripId
  }

  override suspend fun stopSharing(tripId: String) {
    stopSharingCalls += tripId
    sharingTripIds.value -= tripId
    stopSharingError?.let { throw it }
  }

  override suspend fun stopAllSharing() {
    sharingTripIds.value = emptySet()
  }

  override suspend fun restoreSharing() = Unit

  override fun observeMemberLocations(tripId: String): Flow<List<MemberLocation>> = memberLocations

  override fun isSharing(tripId: String): Flow<Boolean> = sharingTripIds.map { tripIds ->
    tripId in tripIds
  }
}
