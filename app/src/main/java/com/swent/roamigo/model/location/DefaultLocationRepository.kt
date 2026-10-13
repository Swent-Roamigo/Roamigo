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
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

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

  // Periodic update job of each trip this user is sharing with.
  private val sharingJobs = MutableStateFlow<Map<String, Job>>(emptyMap())

  // Keeps concurrent start/stop calls from leaving an orphaned update job behind.
  private val sharingMutex = Mutex()

  override fun hasLocationPermission(): Boolean = LOCATION_PERMISSIONS.any {
    ContextCompat.checkSelfPermission(appContext, it) == PackageManager.PERMISSION_GRANTED
  }

  override suspend fun getCurrentLocation(): DeviceLocation? {
    if (!hasLocationPermission()) return null
    // An enabled provider may still fail to get a fix (e.g. GPS indoors), so fall back to the next.
    return usableProviders().firstNotNullOfOrNull { requestSingleLocation(it) }?.toDeviceLocation()
  }

  /** @throws IllegalStateException if location permission is missing or no user is signed in. */
  override suspend fun startSharing(tripId: String) {
    check(hasLocationPermission()) { "Location permission is required to share the location" }
    val userId = requireCurrentUserId()
    sharingMutex.withLock {
      if (tripId in sharingJobs.value) return
      // The first update runs in the caller so that a failure is reported instead of swallowed.
      publishCurrentLocation(tripId, userId)
      val job = scope.launch {
        while (true) {
          delay(updateInterval)
          try {
            publishCurrentLocation(tripId, userId)
          } catch (e: CancellationException) {
            throw e
          } catch (_: Exception) {
            // A failed update only makes the position older; the next one retries.
          }
        }
      }
      sharingJobs.update { it + (tripId to job) }
    }
  }

  /** @throws IllegalStateException if no user is signed in. */
  override suspend fun stopSharing(tripId: String) {
    val userId = requireCurrentUserId()
    sharingMutex.withLock {
      // Waiting for the job ensures no update can be written after the entry is removed.
      sharingJobs.value[tripId]?.cancelAndJoin()
      sharingJobs.update { it - tripId }
      // Also removes an entry left over by a previous app session.
      memberLocationReference(tripId, userId).removeValue().await()
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
            close(error.toException())
          }
        }
    val tripLocations = tripLocationsReference(tripId)
    tripLocations.addValueEventListener(listener)
    awaitClose { tripLocations.removeEventListener(listener) }
  }

  override fun isSharing(tripId: String): Flow<Boolean> =
      sharingJobs.map { tripId in it }.distinctUntilChanged()

  private suspend fun publishCurrentLocation(tripId: String, userId: String) {
    // Without a fix there is nothing new to share; the previous position simply ages.
    val location = getCurrentLocation() ?: return
    memberLocationReference(tripId, userId)
        .setValue(
            mapOf(
                MemberLocation.LATITUDE to location.latitude,
                MemberLocation.LONGITUDE to location.longitude,
                // The security rules only accept the server time, which also keeps members'
                // timestamps comparable regardless of their device clocks.
                MemberLocation.UPDATED_AT to ServerValue.TIMESTAMP,
            )
        )
        .await()
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

  private companion object {
    val LOCATION_PERMISSIONS =
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    // GPS is more precise, the network provider is the fallback when GPS is unusable.
    val PREFERRED_PROVIDERS = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)

    // Resuming a continuation is thread-safe and cheap, so no need to hop threads first.
    val DIRECT_EXECUTOR = Executor(Runnable::run)

    const val LOCATIONS_PATH = "locations"

    // Fresh enough to follow members walking around while keeping GPS usage battery-friendly.
    val DEFAULT_UPDATE_INTERVAL = 20.seconds
  }
}
