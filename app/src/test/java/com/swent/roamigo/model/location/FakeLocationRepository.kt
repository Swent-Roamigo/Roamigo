// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.location

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [LocationRepository] for unit tests, recording the trips it was asked to share. */
class FakeLocationRepository(
    var hasPermission: Boolean = false,
    var currentLocation: DeviceLocation? = null,
) : LocationRepository {

  val memberLocations = MutableStateFlow<List<MemberLocation>>(emptyList())

  private val sharingTripIds = MutableStateFlow<Set<String>>(emptySet())

  val startSharingCalls = mutableListOf<String>()

  override fun hasLocationPermission(): Boolean = hasPermission

  override suspend fun getCurrentLocation(): DeviceLocation? = currentLocation

  override suspend fun startSharing(tripId: String) {
    check(hasPermission) { "Location permission is missing." }
    startSharingCalls += tripId
    sharingTripIds.value += tripId
  }

  override suspend fun stopSharing(tripId: String) {
    sharingTripIds.value -= tripId
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
