// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.location

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/**
 * In-memory [LocationRepository] for unit tests. It records the calls it receives and can be made
 * to fail.
 */
class FakeLocationRepository(
    var hasPermission: Boolean = false,
    var currentLocation: DeviceLocation? = null,
) : LocationRepository {

  val memberLocations = MutableStateFlow<List<MemberLocation>>(emptyList())

  private val observationFailures = MutableSharedFlow<Exception>(extraBufferCapacity = 1)

  private val sharingTripIds = MutableStateFlow<Set<String>>(emptySet())

  val startSharingCalls = mutableListOf<String>()

  val stopSharingCalls = mutableListOf<String>()

  var getCurrentLocationCalls = 0
    private set

  /** Thrown by [startSharing] instead of starting, when set. */
  var startSharingError: Exception? = null

  /** Thrown by the [observeMemberLocations] flow instead of emitting, when set. */
  var observeMemberLocationsError: Exception? = null

  /** Thrown by [stopSharing] after stopping, when set, like a failed removal from the database. */
  var stopSharingError: Exception? = null

  /**
   * Makes the running [observeMemberLocations] flows throw [exception], like a database listener
   * cancelled after it already emitted. Later observations are not affected.
   */
  fun failMemberLocations(exception: Exception) {
    observationFailures.tryEmit(exception)
  }

  override fun hasLocationPermission(): Boolean = hasPermission

  override suspend fun getCurrentLocation(): DeviceLocation? {
    getCurrentLocationCalls++
    return currentLocation
  }

  /**
   * Awaited by [startSharing] before publishing, when set, like a start waiting for its first
   * update.
   */
  var startSharingGate: CompletableDeferred<Unit>? = null

  /** Number of [startSharing] calls cancelled while awaiting [startSharingGate]. */
  var cancelledStartSharingCalls = 0
    private set

  /** Trips whose location is currently published. */
  val sharedTripIds: Set<String>
    get() = sharingTripIds.value

  override suspend fun startSharing(tripId: String) {
    startSharingCalls += tripId
    startSharingError?.let { throw it }
    check(hasPermission) { "Location permission is missing." }
    try {
      startSharingGate?.await()
    } catch (exception: CancellationException) {
      cancelledStartSharingCalls++
      throw exception
    }
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

  override fun observeMemberLocations(tripId: String): Flow<List<MemberLocation>> = flow {
    observeMemberLocationsError?.let { throw it }
    emitAll(merge(memberLocations, observationFailures.map { throw it }))
  }

  override fun isSharing(tripId: String): Flow<Boolean> = sharingTripIds.map { tripIds ->
    tripId in tripIds
  }
}
