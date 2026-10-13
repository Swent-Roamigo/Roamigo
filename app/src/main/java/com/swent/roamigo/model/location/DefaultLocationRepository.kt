// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [LocationRepository] backed by the Android framework [LocationManager] for this device and by
 * Firebase Realtime Database for sharing positions with trip members at
 * `locations/{tripId}/{userId}`.
 *
 * Sharing state lives in this instance, so a single instance should be shared app-wide.
 *
 * @param context any context; only its application context is retained.
 * @param locationManager the system location service, injectable for tests.
 * @param database the Realtime Database holding shared member locations.
 * @param auth provides the signed-in user whose position is shared.
 * @param scope runs the periodic position updates while sharing is on.
 * @param updateInterval delay between two published positions while sharing is on.
 */
class DefaultLocationRepository(
    context: Context,
    private val locationManager: LocationManager =
        context.getSystemService(LocationManager::class.java),
    private val database: FirebaseDatabase = Firebase.database,
    private val auth: FirebaseAuth = Firebase.auth,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val updateInterval: Duration = DEFAULT_UPDATE_INTERVAL,
) : LocationRepository {

  private val appContext = context.applicationContext

  // Update job of each trip this device is sharing with, and the user it shares as.
  private val sharings = MutableStateFlow<Map<String, TripSharing>>(emptyMap())

  // Keeps concurrent start/stop calls from leaving an orphaned update job behind. Never held
  // while waiting for a location fix or the database, so a stop is not delayed by a pending start.
  private val sharingMutex = Mutex()

  override fun hasLocationPermission(): Boolean = LOCATION_PERMISSIONS.any {
    ContextCompat.checkSelfPermission(appContext, it) == PackageManager.PERMISSION_GRANTED
  }

  override suspend fun getCurrentLocation(): DeviceLocation? {
    if (!hasLocationPermission()) return null
    // An enabled provider may still fail to get a fix (e.g. GPS indoors), so fall back to the next.
    return usableProviders()
        .firstNotNullOfOrNull { withTimeoutOrNull(PROVIDER_TIMEOUT) { requestSingleLocation(it) } }
        ?.toDeviceLocation()
  }

  /**
   * Sharing stops by itself, and [isSharing] turns false, once the database denies an update, e.g.
   * after the user left the trip or signed out, or once an update fails unexpectedly.
   *
   * @throws kotlinx.coroutines.CancellationException if [stopSharing] is called for the trip before
   *   the first update completes.
   * @throws IllegalStateException if location permission is missing or no user is signed in.
   * @throws LocationSharingException if no position is available, or if the first update is denied
   *   or not acknowledged in time, e.g. while offline.
   */
  override suspend fun startSharing(tripId: String) {
    check(hasLocationPermission()) { "Location permission is required to share the location" }
    val userId = requireCurrentUserId()
    val firstUpdate = sharingMutex.withLock {
      val current = sharings.value[tripId]
      if (current != null) {
        if (current.userId == userId) return
        // Left running by an account that signed out without stopping; its updates are denied.
        current.job.cancelAndJoin()
      }
      val firstUpdate = CompletableDeferred<Unit>()
      // Started only once registered, so that it can unregister itself when sharing ends.
      val job =
          scope.launch(start = CoroutineStart.LAZY) {
            try {
              shareLocation(tripId, userId, firstUpdate)
            } catch (e: CancellationException) {
              throw e
            } catch (e: Exception) {
              // Caught so that a failure stops sharing instead of crashing the app. Unregistered
              // before reporting so that the caller never sees the failed trip as shared.
              unregister(tripId, coroutineContext.job)
              firstUpdate.completeExceptionally(
                  e as? LocationSharingException ?: LocationSharingException("Sharing failed", e)
              )
            }
          }
      job.invokeOnCompletion {
        unregister(tripId, job)
        // A stop during the first update must not leave the caller waiting forever.
        firstUpdate.cancel()
      }
      sharings.update { it + (tripId to TripSharing(userId, job)) }
      job.start()
      firstUpdate
    }
    firstUpdate.await()
  }

  /**
   * The database only lets users remove their own entry, so call this before signing out:
   * afterwards the entry stays until the user leaves the trip.
   *
   * @throws LocationSharingException if the entry could not be removed or the removal is not
   *   acknowledged in time, e.g. while offline. Sharing is stopped anyway, and an unacknowledged
   *   removal is still applied once the device is back online.
   */
  override suspend fun stopSharing(tripId: String) {
    val removal = sharingMutex.withLock {
      val sharing = sharings.value[tripId]
      if (sharing != null) {
        // Waiting for the job ensures no update can be written after the entry is removed.
        sharing.job.cancelAndJoin()
        sharings.update { it - tripId }
      }
      val userId = auth.currentUser?.uid ?: return
      if (sharing != null && sharing.userId != userId) return
      // Issued under the lock so that a restarted sharing can only write after this removal. Also
      // removes an entry left over by a previous app session.
      startWrite { memberLocationReference(tripId, userId).removeValue(it) }
    }
    val error = removal.awaitAcknowledged()
    // Denied once the user left the trip, which already removed the entry.
    if (error != null && error.code != DatabaseError.PERMISSION_DENIED) {
      throw LocationSharingException("Could not remove the shared location", error.toException())
    }
  }

  override fun observeMemberLocations(tripId: String): Flow<List<MemberLocation>> = callbackFlow {
    val currentUserId = auth.currentUser?.uid
    val listener =
        object : ValueEventListener {
          override fun onDataChange(snapshot: DataSnapshot) {
            trySend(
                snapshot.children.mapNotNull { member ->
                  val userId = member.key
                  val data = member.value as? Map<*, *>
                  if (userId == null || userId == currentUserId || data == null) null
                  else MemberLocation.fromMap(userId, data)
                }
            )
          }

          override fun onCancelled(error: DatabaseError) {
            close(
                LocationSharingException("Could not read the trip locations", error.toException())
            )
          }
        }
    val tripLocations = tripLocationsReference(tripId)
    tripLocations.addValueEventListener(listener)
    awaitClose { tripLocations.removeEventListener(listener) }
  }

  override fun isSharing(tripId: String): Flow<Boolean> =
      sharings.map { tripId in it }.distinctUntilChanged()

  private suspend fun shareLocation(
      tripId: String,
      userId: String,
      firstUpdate: CompletableDeferred<Unit>,
  ) {
    publishFirstLocation(tripId, userId)
    firstUpdate.complete(Unit)
    while (true) {
      delay(updateInterval)
      // Other failures only make the position older and the next update retries, but a denied
      // update means the user lost access to the trip, so every following one would be denied too.
      if (publishCurrentLocation(tripId, userId)?.code == DatabaseError.PERMISSION_DENIED) return
    }
  }

  private fun unregister(tripId: String, job: Job) {
    // A newer start may already have replaced this job, which must stay registered.
    sharings.update { if (it[tripId]?.job == job) it - tripId else it }
  }

  /** @throws LocationSharingException if there is no fix or the update fails or times out. */
  private suspend fun publishFirstLocation(tripId: String, userId: String) {
    val location =
        getCurrentLocation() ?: throw LocationSharingException("No location is available to share")
    val reference = memberLocationReference(tripId, userId)
    val update = startWrite { reference.setValue(location.toDatabaseValue(), it) }
    val error =
        try {
          update.awaitAcknowledged()
        } catch (e: LocationSharingException) {
          // The update stays queued until the device is back online, so queue its removal after
          // it, or the position would be published while sharing shows as off.
          startWrite { reference.removeValue(it) }
          throw e
        }
    if (error != null) {
      throw LocationSharingException("Could not publish the location", error.toException())
    }
  }

  /** @return the database error, or null if the update succeeded or there was no fix to share. */
  private suspend fun publishCurrentLocation(tripId: String, userId: String): DatabaseError? {
    // Without a fix there is nothing new to share; the previous position simply ages.
    val location = getCurrentLocation() ?: return null
    // Not bounded by a timeout: while offline, updates simply pause until the connection is back.
    return startWrite {
      memberLocationReference(tripId, userId).setValue(location.toDatabaseValue(), it)
    }
        .await()
  }

  private fun DeviceLocation.toDatabaseValue() =
      mapOf(
          MemberLocation.LATITUDE to latitude,
          MemberLocation.LONGITUDE to longitude,
          // The security rules only accept the server time, which also keeps members'
          // timestamps comparable regardless of their device clocks.
          MemberLocation.UPDATED_AT to ServerValue.TIMESTAMP,
      )

  // Unlike the Task API, the completion listener exposes the error code, which tells a lost
  // access apart from a transient failure.
  private fun startWrite(
      write: (DatabaseReference.CompletionListener) -> Unit
  ): Deferred<DatabaseError?> =
      CompletableDeferred<DatabaseError?>().also { result ->
        write { error, _ -> result.complete(error) }
      }

  // The database only acknowledges a write once online, so without a bound the caller would wait
  // indefinitely while offline.
  private suspend fun Deferred<DatabaseError?>.awaitAcknowledged(): DatabaseError? =
      try {
        withTimeout(WRITE_TIMEOUT) { await() }
      } catch (e: TimeoutCancellationException) {
        throw LocationSharingException("The database did not acknowledge the write in time", e)
      }

  private fun requireCurrentUserId(): String =
      checkNotNull(auth.currentUser?.uid) { "A signed-in user is required to share the location" }

  private fun tripLocationsReference(tripId: String): DatabaseReference =
      database.getReference(LOCATIONS_PATH).child(tripId)

  private fun memberLocationReference(tripId: String, userId: String): DatabaseReference =
      tripLocationsReference(tripId).child(userId)

  // GPS requires the fine permission; a coarse-only caller would get a SecurityException.
  private fun usableProviders(): List<String> {
    val hasFinePermission =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    return PREFERRED_PROVIDERS.filter {
      (it != LocationManager.GPS_PROVIDER || hasFinePermission) &&
          locationManager.isProviderEnabled(it)
    }
  }

  // The permission is checked in getCurrentLocation(); Android kills the process when it is
  // revoked, so it cannot disappear between the check and this call.
  @SuppressLint("MissingPermission")
  private suspend fun requestSingleLocation(provider: String): Location? =
      suspendCancellableCoroutine { continuation ->
        val cancellationSignal = CancellationSignal()
        continuation.invokeOnCancellation { cancellationSignal.cancel() }
        LocationManagerCompat.getCurrentLocation(
            locationManager,
            provider,
            cancellationSignal,
            DIRECT_EXECUTOR,
        ) { location ->
          continuation.resume(location)
        }
      }

  private fun Location.toDeviceLocation() =
      DeviceLocation(
          latitude = latitude,
          longitude = longitude,
          accuracyMeters = if (hasAccuracy()) accuracy else null,
          timestampMillis = time,
      )

  private class TripSharing(val userId: String, val job: Job)

  companion object {
    // Long enough for a usual fix, short enough not to delay the fallback to the next provider
    // when GPS cannot get one, e.g. indoors.
    internal val PROVIDER_TIMEOUT = 10.seconds

    // Leaves room for a slow network while keeping callers from waiting on an offline database.
    internal val WRITE_TIMEOUT = 10.seconds

    private val LOCATION_PERMISSIONS =
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    // GPS is more precise, the network provider is the fallback when GPS is unusable.
    private val PREFERRED_PROVIDERS =
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)

    // Resuming a continuation is thread-safe and cheap, so no need to hop threads first.
    private val DIRECT_EXECUTOR = Executor(Runnable::run)

    private const val LOCATIONS_PATH = "locations"

    // Fresh enough to follow members walking around while keeping GPS usage battery-friendly.
    private val DEFAULT_UPDATE_INTERVAL = 20.seconds
  }
}
