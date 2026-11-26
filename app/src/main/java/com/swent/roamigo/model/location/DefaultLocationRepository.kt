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
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
import java.io.IOException
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
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
 * Running updates live in this instance, so a single instance should be shared app-wide. The trips
 * being shared are also saved on the device, so that [restoreSharing] can resume them after the app
 * process was killed.
 *
 * @param context any context; only its application context is retained.
 * @param locationManager the system location service, injectable for tests.
 * @param database the Realtime Database holding shared member locations.
 * @param auth provides the signed-in user whose position is shared.
 * @param sharedTripsStore saves the trips each user is sharing with, across app processes.
 * @param scope runs the periodic position updates while sharing is on, and the restored trips.
 * @param updateInterval delay between two published positions while sharing is on.
 * @param clock current device time in milliseconds since the epoch, on the same clock as the fix
 *   timestamps; injectable for tests.
 */
class DefaultLocationRepository(
    context: Context,
    private val locationManager: LocationManager =
        context.getSystemService(LocationManager::class.java),
    private val database: FirebaseDatabase = Firebase.database,
    private val auth: FirebaseAuth = Firebase.auth,
    private val sharedTripsStore: DataStore<Preferences> = context.sharedTripsDataStore,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val updateInterval: Duration = DEFAULT_UPDATE_INTERVAL,
    private val clock: () -> Long = System::currentTimeMillis,
) : LocationRepository {

  private val appContext = context.applicationContext

  // Update job of each trip this device is sharing with, and the user it shares as.
  private val sharings = MutableStateFlow<Map<String, TripSharing>>(emptyMap())

  // Keeps concurrent start/stop calls from leaving an orphaned update job behind. Never held
  // while waiting for a location fix or the database, so a stop is not delayed by a pending start.
  private val sharingMutex = Mutex()

  // Newest fix produced by getCurrentLocation(), including the ones taken by the trip map and by
  // sharing updates, so that callers that cannot wait for a new fix can still use it. Kept when the
  // fine permission is revoked so it is usable again once the permission is granted back.
  private val latestFix = AtomicReference<Location?>(null)

  override fun hasLocationPermission(): Boolean = LOCATION_PERMISSIONS.any {
    ContextCompat.checkSelfPermission(appContext, it) == PackageManager.PERMISSION_GRANTED
  }

  override suspend fun getCurrentLocation(): DeviceLocation? {
    if (!hasLocationPermission()) return null
    val fix = firstFix(usableProviders()) ?: return null
    // Calls may finish out of order, so a slower call must not replace a newer fix.
    latestFix.updateAndGet { latest ->
      if (latest != null && latest.time > fix.time) latest else fix
    }
    return fix.toDeviceLocation()
  }

  // The permission is checked first, like in getCurrentLocation().
  @SuppressLint("MissingPermission")
  override fun lastKnownLocation(maxAge: Duration): DeviceLocation? {
    if (!hasLocationPermission()) return null
    val oldestAllowed = clock() - maxAge.inWholeMilliseconds
    val systemFixes = usableProviders().mapNotNull { locationManager.getLastKnownLocation(it) }
    // A GPS fix remembered under the fine permission must not outlive a switch to approximate.
    val rememberedFix =
        latestFix.get()?.takeIf {
          it.provider != LocationManager.GPS_PROVIDER || hasFinePermission()
        }
    return (systemFixes + listOfNotNull(rememberedFix))
        .filter { it.time >= oldestAllowed }
        .maxByOrNull { it.time }
        ?.toDeviceLocation()
  }

  // Returns the first fix, or null once each provider answered without one or timed out. GPS wins
  // when it is ready at the same time as the network provider. The providers are asked at once
  // rather than one after the other: GPS may take up to PROVIDER_TIMEOUT without a fix (e.g.
  // indoors), so callers with a shorter deadline, such as the photo locator, would give up before
  // the network provider was even asked. The first fix is taken rather than waiting for GPS, since
  // a quick approximate position is more useful to those callers than none.
  private suspend fun firstFix(providers: List<String>): Location? = coroutineScope {
    val pending =
        providers
            .map { async { withTimeoutOrNull(PROVIDER_TIMEOUT) { requestSingleLocation(it) } } }
            .toMutableList()
    var fix: Location? = null
    while (fix == null && pending.isNotEmpty()) {
      // select picks the first clause in this list when several are ready, hence GPS first.
      val (request, location) =
          select<Pair<Deferred<Location?>, Location?>> {
            pending.forEach { request -> request.onAwait { request to it } }
          }
      pending -= request
      fix = location
    }
    // Cancelled, or coroutineScope would wait for the providers still searching and keep GPS on.
    pending.forEach { it.cancel() }
    fix
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
        // A start still in progress is awaited, so that this call cannot report a sharing whose
        // first update later fails.
        if (current.userId == userId) return@withLock current.firstUpdate
        // Left running by an account that signed out without stopping; its updates are denied.
        current.job.cancelAndJoin()
      }
      val firstUpdate = CompletableDeferred<Unit>()
      // Started only once registered, so that it can unregister itself when sharing ends.
      val job =
          scope.launch(start = CoroutineStart.LAZY) {
            val failure =
                try {
                  shareLocation(tripId, userId, coroutineContext.job, firstUpdate)
                  null
                } catch (e: CancellationException) {
                  throw e
                } catch (e: Exception) {
                  // Caught so that a failure stops sharing instead of crashing the app.
                  e as? LocationSharingException ?: LocationSharingException("Sharing failed", e)
                }
            // Sharing ended by itself. Unregistered before reporting so that the caller never sees
            // the failed trip as shared.
            unregister(tripId, userId, coroutineContext.job)
            failure?.let { firstUpdate.completeExceptionally(it) }
          }
      job.invokeOnCompletion {
        // A cancelled job does not unregister itself; its saved trip is handled by stopSharing.
        removeSharing(tripId, job)
        // A stop during the first update must not leave the caller waiting forever.
        firstUpdate.cancel()
      }
      sharings.update { it + (tripId to TripSharing(userId, job, firstUpdate)) }
      job.start()
      firstUpdate
    }
    firstUpdate.await()
  }

  /**
   * The database only lets users remove their own entry, so once the user who started the sharing
   * signed out, the sharing is stopped on the device but its entry stays until the server removes
   * it as stale or because the user left the trip. Call [stopAllSharing] before signing out to
   * remove it right away.
   *
   * @throws LocationSharingException if the trip could not be forgotten on the device, or if the
   *   entry could not be removed or the removal is not acknowledged in time, e.g. while offline.
   *   Sharing is stopped anyway, and an unacknowledged removal is still applied once the device is
   *   back online.
   */
  override suspend fun stopSharing(tripId: String) {
    val removal = sharingMutex.withLock {
      val sharing = sharings.value[tripId]
      if (sharing != null) {
        // Waiting for the job ensures no update can be written after the entry is removed.
        sharing.job.cancelAndJoin()
        sharings.update { it - tripId }
      }
      val userId = sharing?.userId ?: auth.currentUser?.uid ?: return
      // Forgotten before the removal, so that a failure cannot let restoreSharing resume a sharing
      // the user stopped.
      editSharedTrips(userId) { it - tripId }
      if (userId != auth.currentUser?.uid) return
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

  /**
   * Called through [LocationSharingRestorer] by `LocationSharingRestoreViewModel`, at the root of
   * `RoamigoApp`, so only while the app is in the foreground.
   *
   * @throws LocationSharingException if the saved trips could not be read. Failing to resume or
   *   stop the sharing of a single trip is not reported, so that the other trips are still
   *   restored.
   */
  override suspend fun restoreSharing() {
    val userId = auth.currentUser?.uid ?: return
    val tripIds = readSharedTrips(userId)
    // Concurrent, since each trip may wait several seconds for a fix and for the database. Run on
    // the repository's scope so that a caller cancelled meanwhile, e.g. when the app UI closes,
    // cannot skip the removal of a trip that then fails to resume; it was already forgotten.
    tripIds.map { scope.launch { restoreTrip(it, userId) } }.joinAll()
  }

  /**
   * @throws LocationSharingException if the saved trips could not be read, or if the entry of at
   *   least one trip could not be removed. Every trip is stopped anyway.
   */
  override suspend fun stopAllSharing() {
    val userId = auth.currentUser?.uid ?: return
    val running = sharings.value.filterValues { it.userId == userId }.keys
    // Saved trips also cover the entries left by a previous process that were not restored.
    val tripIds = running + readSharedTrips(userId)
    val failures = coroutineScope {
      tripIds.map { tripId -> async { stopSharingOrFailure(tripId) } }.awaitAll()
    }
    failures.firstOrNull { it != null }?.let { throw it }
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
      sharings.map { it[tripId]?.isPublished == true }.distinctUntilChanged()

  private suspend fun restoreTrip(tripId: String, userId: String) {
    // The trips were read for userId, and startSharing and stopSharing act as the current user:
    // after an account switch they would share without that user's opt-in or edit their trips.
    // The trip stays saved, so it is restored at userId's next startup.
    if (auth.currentUser?.uid != userId) return
    try {
      startSharing(tripId)
    } catch (e: CancellationException) {
      // Also thrown when the user stops this trip's sharing meanwhile, which needs no cleanup.
      currentCoroutineContext().ensureActive()
    } catch (e: Exception) {
      if (auth.currentUser?.uid != userId) return
      // E.g. the permission was revoked or there is no fix: the position left by the previous
      // process must not stay published while the trip is not shown as shared.
      try {
        stopSharing(tripId)
      } catch (e: LocationSharingException) {
        // A removal that is not acknowledged yet is still applied once back online.
      }
    }
  }

  private suspend fun stopSharingOrFailure(tripId: String): LocationSharingException? =
      try {
        stopSharing(tripId)
        null
      } catch (e: LocationSharingException) {
        e
      }

  private suspend fun shareLocation(
      tripId: String,
      userId: String,
      job: Job,
      firstUpdate: CompletableDeferred<Unit>,
  ) {
    publishFirstLocation(tripId, userId)
    saveSharedTrip(tripId, userId)
    // Reported as shared only now, so that a start whose first update fails never shows as shared.
    sharings.update { current ->
      val sharing = current[tripId]?.takeIf { it.job == job } ?: return@update current
      current + (tripId to sharing.copy(isPublished = true))
    }
    firstUpdate.complete(Unit)
    while (true) {
      delay(updateInterval)
      // Other failures only make the position older and the next update retries, but a denied
      // update means the user lost access to the trip, so every following one would be denied too.
      if (publishCurrentLocation(tripId, userId)?.code == DatabaseError.PERMISSION_DENIED) return
    }
  }

  private suspend fun unregister(tripId: String, userId: String, job: Job) {
    // Forgotten while still registered, so that a new start for the trip, which saves it again,
    // cannot run before this removal.
    try {
      editSharedTrips(userId) { it - tripId }
    } catch (e: LocationSharingException) {
      // Left saved, so restoreSharing retries the trip at the next startup and stops it if needed.
    }
    removeSharing(tripId, job)
  }

  private fun removeSharing(tripId: String, job: Job) {
    // A newer start may already have replaced this job, which must stay registered.
    sharings.update { if (it[tripId]?.job == job) it - tripId else it }
  }

  /** @throws LocationSharingException if the trip could not be saved; it is then not shared. */
  private suspend fun saveSharedTrip(tripId: String, userId: String) {
    try {
      editSharedTrips(userId) { it + tripId }
    } catch (e: LocationSharingException) {
      // An unsaved trip could not be restored after the process is killed, so its position would
      // stay published.
      queueWrite { memberLocationReference(tripId, userId).removeValue(it) }
      throw e
    }
  }

  /** @throws LocationSharingException if the saved trips could not be read. */
  private suspend fun readSharedTrips(userId: String): Set<String> =
      try {
        sharedTripsStore.data.first()[sharedTripsKey(userId)].orEmpty()
      } catch (e: IOException) {
        throw LocationSharingException("Could not read the shared trips", e)
      }

  /** @throws LocationSharingException if the saved trips could not be written. */
  private suspend fun editSharedTrips(userId: String, transform: (Set<String>) -> Set<String>) {
    try {
      sharedTripsStore.edit {
        it[sharedTripsKey(userId)] = transform(it[sharedTripsKey(userId)].orEmpty())
      }
    } catch (e: IOException) {
      throw LocationSharingException("Could not save the shared trips", e)
    }
  }

  // Kept per user, so that a trip shared by an account that signed out is not resumed for another.
  private fun sharedTripsKey(userId: String) = stringSetPreferencesKey("shared_trips_$userId")

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
          queueWrite { reference.removeValue(it) }
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

  // For cleanups nobody waits for: the database applies the write once online, and a failure could
  // not be reported to anyone since the operation that queued it already failed.
  private fun queueWrite(write: (DatabaseReference.CompletionListener) -> Unit) {
    write { _, _ -> }
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
    val hasFinePermission = hasFinePermission()
    return PREFERRED_PROVIDERS.filter {
      (it != LocationManager.GPS_PROVIDER || hasFinePermission) &&
          locationManager.isProviderEnabled(it)
    }
  }

  private fun hasFinePermission(): Boolean =
      ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
          PackageManager.PERMISSION_GRANTED

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

  // Registered as soon as the start begins, so that a stop can interrupt it, but only published
  // once its first update succeeded.
  private data class TripSharing(
      val userId: String,
      val job: Job,
      val firstUpdate: Deferred<Unit>,
      val isPublished: Boolean = false,
  )

  companion object {
    // Long enough for a usual fix, short enough to give up on a provider that cannot get one, e.g.
    // GPS indoors.
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

    // A single instance per file is allowed in a process, hence the extension property.
    private val Context.sharedTripsDataStore by preferencesDataStore(name = "location_sharing")

    // Fresh enough to follow members walking around while keeping GPS usage battery-friendly.
    private val DEFAULT_UPDATE_INTERVAL = 20.seconds
  }
}
