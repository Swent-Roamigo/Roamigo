// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.location

import android.Manifest
import android.app.Application
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.DatabaseReference.CompletionListener
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import java.io.IOException
import java.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowApplication
import org.robolectric.shadows.ShadowLocationManager

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DefaultLocationRepositoryTest {

  private lateinit var application: ShadowApplication
  private lateinit var locationManager: ShadowLocationManager
  private lateinit var repository: DefaultLocationRepository

  private val database = mock(FirebaseDatabase::class.java)
  private val auth = mock(FirebaseAuth::class.java)
  private val user = mock(FirebaseUser::class.java)
  private val locations = mock(DatabaseReference::class.java)
  private val tripLocations = mock(DatabaseReference::class.java)
  private val ownLocation = mock(DatabaseReference::class.java)
  private val otherTripLocations = mock(DatabaseReference::class.java)
  private val ownOtherTripLocation = mock(DatabaseReference::class.java)
  private val listeners = mutableListOf<ValueEventListener>()

  // Outlives the repositories, like the files on the device outlive the app process.
  private var sharedTripsStore: DataStore<Preferences> = InMemoryDataStore()

  // Outcome reported by the database for the next writes; null means success.
  private var updateError: DatabaseError? = null
  private var removalError: DatabaseError? = null

  // Leaves writes unacknowledged, like the database does while offline.
  private var holdUpdates = false
  private var holdRemovals = false

  // Thrown by the next updates instead of writing.
  private var updateFailure: RuntimeException? = null

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Application>()
    application = shadowOf(context)
    locationManager = shadowOf(context.getSystemService(LocationManager::class.java))
    locationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
    locationManager.setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)

    `when`(auth.currentUser).thenReturn(user)
    `when`(user.uid).thenReturn(USER_ID)
    `when`(database.getReference("locations")).thenReturn(locations)
    `when`(locations.child(TRIP_ID)).thenReturn(tripLocations)
    `when`(locations.child(OTHER_TRIP_ID)).thenReturn(otherTripLocations)
    `when`(tripLocations.child(USER_ID)).thenReturn(ownLocation)
    `when`(otherTripLocations.child(USER_ID)).thenReturn(ownOtherTripLocation)
    listOf(ownLocation, ownOtherTripLocation).forEach { reference ->
      doAnswer { invocation ->
            updateFailure?.let { throw it }
            if (!holdUpdates) {
              invocation.getArgument<CompletionListener>(1).onComplete(updateError, reference)
            }
          }
          .`when`(reference)
          .setValue(any(), any(CompletionListener::class.java))
      doAnswer { invocation ->
            if (!holdRemovals) {
              invocation.getArgument<CompletionListener>(0).onComplete(removalError, reference)
            }
          }
          .`when`(reference)
          .removeValue(any(CompletionListener::class.java))
    }
    doAnswer { invocation ->
          invocation.getArgument<ValueEventListener>(0).also { listeners += it }
        }
        .`when`(tripLocations)
        .addValueEventListener(any())

    repository = createRepository(CoroutineScope(SupervisorJob()))
  }

  @Test
  fun hasLocationPermission_isFalseWithoutAnyPermission() {
    assertFalse(repository.hasLocationPermission())
  }

  @Test
  fun hasLocationPermission_isTrueWithFinePermission() {
    application.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)

    assertTrue(repository.hasLocationPermission())
  }

  @Test
  fun hasLocationPermission_isTrueWithCoarsePermissionOnly() {
    application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)

    assertTrue(repository.hasLocationPermission())
  }

  @Test
  fun getCurrentLocation_returnsNullWithoutPermission() = runTest {
    locationManager.simulateLocation(recentLocation(LocationManager.GPS_PROVIDER))

    assertNull(repository.getCurrentLocation())
  }

  @Test
  fun getCurrentLocation_returnsNullWhenNoProviderIsEnabled() = runTest {
    application.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    locationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, false)
    locationManager.setProviderEnabled(LocationManager.NETWORK_PROVIDER, false)

    assertNull(repository.getCurrentLocation())
  }

  @Test
  fun getCurrentLocation_returnsRecentGpsLocation() = runTest {
    application.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    val gpsLocation = recentLocation(LocationManager.GPS_PROVIDER)
    locationManager.simulateLocation(gpsLocation)

    val result = repository.getCurrentLocation()

    assertEquals(
        DeviceLocation(
            latitude = 46.5191,
            longitude = 6.5668,
            accuracyMeters = 12f,
            timestampMillis = gpsLocation.time,
        ),
        result,
    )
  }

  @Test
  fun getCurrentLocation_fallsBackToNetworkProviderWhenGpsIsDisabled() = runTest {
    application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
    locationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, false)
    val networkLocation =
        recentLocation(LocationManager.NETWORK_PROVIDER).apply {
          latitude = 47.3769
          longitude = 8.5417
        }
    locationManager.simulateLocation(networkLocation)

    val result = repository.getCurrentLocation()

    assertEquals(47.3769, result!!.latitude, 0.0)
    assertEquals(8.5417, result.longitude, 0.0)
  }

  @Test
  fun getCurrentLocation_usesNetworkProviderWithCoarsePermissionOnlyWhenGpsIsEnabled() = runTest {
    application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
    val networkLocation =
        recentLocation(LocationManager.NETWORK_PROVIDER).apply {
          latitude = 47.3769
          longitude = 8.5417
        }
    locationManager.simulateLocation(networkLocation)

    val result = repository.getCurrentLocation()

    assertEquals(47.3769, result!!.latitude, 0.0)
    assertEquals(8.5417, result.longitude, 0.0)
  }

  @Test
  fun getCurrentLocation_returnsNullWithCoarsePermissionOnlyWhenNetworkProviderIsDisabled() =
      runTest {
        application.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        locationManager.setProviderEnabled(LocationManager.NETWORK_PROVIDER, false)
        // GPS is enabled and has a fix, but must not be used without the fine permission.
        locationManager.simulateLocation(recentLocation(LocationManager.GPS_PROVIDER))

        assertNull(repository.getCurrentLocation())
      }

  @Test
  fun getCurrentLocation_fallsBackToNetworkProviderWhenGpsHasNoFix() = runTest {
    application.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)

    val pending = async { repository.getCurrentLocation() }
    runCurrent()
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(1))
    runCurrent()
    assertFalse(pending.isCompleted)

    locationManager.simulateLocation(
        recentLocation(LocationManager.NETWORK_PROVIDER).apply {
          latitude = 47.3769
          longitude = 8.5417
        }
    )

    assertEquals(47.3769, pending.await()!!.latitude, 0.0)
  }

  @Test
  fun getCurrentLocation_fallsBackToNetworkProviderWhenGpsIsTooSlow() = runTest {
    application.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)

    val pending = async { repository.getCurrentLocation() }
    runCurrent()
    advanceTimeBy(DefaultLocationRepository.PROVIDER_TIMEOUT)
    runCurrent()
    assertFalse(pending.isCompleted)

    locationManager.simulateLocation(
        recentLocation(LocationManager.NETWORK_PROVIDER).apply {
          latitude = 47.3769
          longitude = 8.5417
        }
    )

    assertEquals(47.3769, pending.await()!!.latitude, 0.0)
  }

  @Test
  fun getCurrentLocation_hasNullAccuracyWhenProviderReportsNone() = runTest {
    application.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    locationManager.simulateLocation(
        recentLocation(LocationManager.GPS_PROVIDER).apply { removeAccuracy() }
    )

    val result = repository.getCurrentLocation()

    assertNull(result!!.accuracyMeters)
  }

  @Test
  fun getCurrentLocation_waitsForFreshFixWhenNoRecentLocationIsKnown() = runTest {
    application.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)

    val pending = async { repository.getCurrentLocation() }
    runCurrent()
    assertFalse(pending.isCompleted)

    locationManager.simulateLocation(recentLocation(LocationManager.GPS_PROVIDER))

    assertEquals(46.5191, pending.await()!!.latitude, 0.0)
  }

  @Test
  fun getCurrentLocation_returnsNullWhenNoFixArrivesBeforeTimeout() = runTest {
    application.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)

    val pending = async { repository.getCurrentLocation() }
    // GPS and then the network provider each time out without a fix.
    repeat(2) {
      runCurrent()
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(1))
    }

    assertNull(pending.await())
  }

  @Test
  fun getCurrentLocation_stopsWaitingWhenCancelled() = runTest {
    application.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)

    val pending = async { repository.getCurrentLocation() }
    runCurrent()
    pending.cancel()
    runCurrent()

    // A fix arriving after cancellation must not be delivered to the cancelled request.
    locationManager.simulateLocation(recentLocation(LocationManager.GPS_PROVIDER))

    assertTrue(pending.isCancelled)
  }

  @Test
  fun isSharing_isFalseByDefault() = runTest { assertFalse(repository.isSharing(TRIP_ID).first()) }

  @Test
  fun startSharing_publishesCurrentPositionWithServerTimestamp() = runTest {
    application.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    locationManager.simulateLocation(recentLocation(LocationManager.GPS_PROVIDER))
    val repository = createRepository(backgroundScope)

    repository.startSharing(TRIP_ID)

    verify(ownLocation)
        .setValue(
            eq(
                mapOf(
                    "latitude" to 46.5191,
                    "longitude" to 6.5668,
                    "updatedAt" to ServerValue.TIMESTAMP,
                )
            ),
            any(CompletionListener::class.java),
        )
    assertTrue(repository.isSharing(TRIP_ID).first())
  }

  @Test
  fun startSharing_publishesPeriodicallyUntilStopped() = runTest {
    application.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    locationManager.simulateLocation(recentLocation(LocationManager.GPS_PROVIDER))
    val repository = createRepository(backgroundScope)

    repository.startSharing(TRIP_ID)
    advanceTimeBy(UPDATE_INTERVAL * 2)
    runCurrent()
    verifyUpdates(ownLocation, 3)

    repository.stopSharing(TRIP_ID)
    advanceTimeBy(UPDATE_INTERVAL * 2)
    runCurrent()
    verifyUpdates(ownLocation, 3)
  }

  @Test
  fun startSharing_keepsSharingWhenAnUpdateFailsTransiently() = runTest {
    application.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    locationManager.simulateLocation(recentLocation(LocationManager.GPS_PROVIDER))
    val repository = createRepository(backgroundScope)
    repository.startSharing(TRIP_ID)

    updateError = DatabaseError.fromCode(DatabaseError.NETWORK_ERROR)
    advanceTimeBy(UPDATE_INTERVAL)
    runCurrent()
    updateError = null
    advanceTimeBy(UPDATE_INTERVAL)
    runCurrent()

    verifyUpdates(ownLocation, 3)
    assertTrue(repository.isSharing(TRIP_ID).first())
  }

  @Test
  fun startSharing_stopsWhenAnUpdateIsDenied() = runTest {
    application.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    locationManager.simulateLocation(recentLocation(LocationManager.GPS_PROVIDER))
    val repository = createRepository(backgroundScope)
    repository.startSharing(TRIP_ID)

    // The user was removed from the trip, so the database rules now deny their updates.
    updateError = DatabaseError.fromCode(DatabaseError.PERMISSION_DENIED)
    advanceTimeBy(UPDATE_INTERVAL)
    runCurrent()
    advanceTimeBy(UPDATE_INTERVAL * 2)
    runCurrent()

    verifyUpdates(ownLocation, 2)
    assertFalse(repository.isSharing(TRIP_ID).first())
  }

  @Test
  fun startSharing_canRestartAfterAnUpdateWasDenied() = runTest {
    application.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    locationManager.simulateLocation(recentLocation(LocationManager.GPS_PROVIDER))
    val repository = createRepository(backgroundScope)
    repository.startSharing(TRIP_ID)
    updateError = DatabaseError.fromCode(DatabaseError.PERMISSION_DENIED)
    advanceTimeBy(UPDATE_INTERVAL)
    runCurrent()

    // The user was added back to the trip.
    updateError = null
    repository.startSharing(TRIP_ID)

    verifyUpdates(ownLocation, 3)
    assertTrue(repository.isSharing(TRIP_ID).first())
  }

  @Test
  fun startSharing_twiceDoesNotStartASecondUpdateLoop() = runTest {
    application.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    locationManager.simulateLocation(recentLocation(LocationManager.GPS_PROVIDER))
    val repository = createRepository(backgroundScope)

    repository.startSharing(TRIP_ID)
    repository.startSharing(TRIP_ID)
    advanceTimeBy(UPDATE_INTERVAL)
    runCurrent()

    verifyUpdates(ownLocation, 2)
  }

  @Test
  fun startSharing_onlyAffectsTheGivenTrip() = runTest {
    application.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    locationManager.simulateLocation(recentLocation(LocationManager.GPS_PROVIDER))
    val repository = createRepository(backgroundScope)

    repository.startSharing(TRIP_ID)

    assertFalse(repository.isSharing(OTHER_TRIP_ID).first())
    verifyUpdates(ownOtherTripLocation, 0)
  }

  @Test
  fun startSharing_throwsWithoutPermissionAndDoesNotShare() = runTest {
    val repository = createRepository(backgroundScope)

    val error = runCatching { repository.startSharing(TRIP_ID) }.exceptionOrNull()
    assertTrue(error is IllegalStateException)

    verifyUpdates(ownLocation, 0)
    assertFalse(repository.isSharing(TRIP_ID).first())
  }

  @Test
  fun startSharing_throwsWhenSignedOutAndDoesNotShare() = runTest {
    application.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    `when`(auth.currentUser).thenReturn(null)
    val repository = createRepository(backgroundScope)

    val error = runCatching { repository.startSharing(TRIP_ID) }.exceptionOrNull()
    assertTrue(error is IllegalStateException)

    verifyUpdates(ownLocation, 0)
    assertFalse(repository.isSharing(TRIP_ID).first())
  }

  @Test
  fun startSharing_doesNotShareWhenTheFirstUpdateFails() = runTest {
    application.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    locationManager.simulateLocation(recentLocation(LocationManager.GPS_PROVIDER))
    updateError = DatabaseError.fromCode(DatabaseError.PERMISSION_DENIED)
    val repository = createRepository(backgroundScope)

    val error = runCatching { repository.startSharing(TRIP_ID) }.exceptionOrNull()
    assertTrue(error is LocationSharingException)

    assertFalse(repository.isSharing(TRIP_ID).first())
  }

  @Test
  fun startSharing_failsWithoutALocationFixAndDoesNotShare() = runTest {
    application.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    locationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, false)
    locationManager.setProviderEnabled(LocationManager.NETWORK_PROVIDER, false)
    val repository = createRepository(backgroundScope)

    val error = runCatching { repository.startSharing(TRIP_ID) }.exceptionOrNull()

    assertTrue(error is LocationSharingException)
    verifyUpdates(ownLocation, 0)
    assertFalse(repository.isSharing(TRIP_ID).first())
  }

  @Test
  fun startSharing_failsWhenTheFirstUpdateIsNotAcknowledgedInTime() = runTest {
    application.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    locationManager.simulateLocation(recentLocation(LocationManager.GPS_PROVIDER))
    holdUpdates = true
    val repository = createRepository(backgroundScope)

    val start = async { runCatching { repository.startSharing(TRIP_ID) } }
    runCurrent()
    advanceTimeBy(DefaultLocationRepository.WRITE_TIMEOUT)
    runCurrent()

    assertTrue(start.await().exceptionOrNull() is LocationSharingException)
    assertFalse(repository.isSharing(TRIP_ID).first())
    // The queued update would otherwise be published once back online.
    verifyRemovals(ownLocation, 1)
  }

  @Test
  fun startSharing_failsWhenTheFirstUpdateThrows() = runTest {
    application.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    locationManager.simulateLocation(recentLocation(LocationManager.GPS_PROVIDER))
    updateFailure = IllegalStateException("Unexpected failure")
    val repository = createRepository(backgroundScope)

    val error = runCatching { repository.startSharing(TRIP_ID) }.exceptionOrNull()

    assertTrue(error is LocationSharingException)
    assertFalse(repository.isSharing(TRIP_ID).first())
  }

  @Test
  fun startSharing_stopsWhenAnUpdateThrows() = runTest {
    application.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    locationManager.simulateLocation(recentLocation(LocationManager.GPS_PROVIDER))
    val repository = createRepository(backgroundScope)
    repository.startSharing(TRIP_ID)

    updateFailure = IllegalStateException("Unexpected failure")
    advanceTimeBy(UPDATE_INTERVAL)
    runCurrent()
    updateFailure = null
    advanceTimeBy(UPDATE_INTERVAL * 2)
    runCurrent()

    verifyUpdates(ownLocation, 2)
    assertFalse(repository.isSharing(TRIP_ID).first())
  }

  @Test
  fun startSharing_replacesSharingLeftByAnotherAccount() = runTest {
    application.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    locationManager.simulateLocation(recentLocation(LocationManager.GPS_PROVIDER))
    val repository = createRepository(backgroundScope)
    repository.startSharing(TRIP_ID)
    val otherUserLocation = signInAsOtherUser()

    repository.startSharing(TRIP_ID)
    advanceTimeBy(UPDATE_INTERVAL)
    runCurrent()

    verifyUpdates(ownLocation, 1)
    verifyUpdates(otherUserLocation, 2)
    assertTrue(repository.isSharing(TRIP_ID).first())
  }

  @Test
  fun stopSharing_removesEntryAndStopsSharing() = runTest {
    application.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    locationManager.simulateLocation(recentLocation(LocationManager.GPS_PROVIDER))
    val repository = createRepository(backgroundScope)
    val sharingStates = mutableListOf<Boolean>()
    backgroundScope.launch { repository.isSharing(TRIP_ID).toList(sharingStates) }
    runCurrent()

    repository.startSharing(TRIP_ID)
    runCurrent()
    repository.stopSharing(TRIP_ID)
    runCurrent()

    verifyRemovals(ownLocation, 1)
    assertEquals(listOf(false, true, false), sharingStates)
  }

  @Test
  fun stopSharing_removesEntryLeftByAPreviousSession() = runTest {
    repository.stopSharing(TRIP_ID)

    verifyRemovals(ownLocation, 1)
  }

  @Test
  fun stopSharing_stopsUpdatesAfterSignOut() = runTest {
    application.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    locationManager.simulateLocation(recentLocation(LocationManager.GPS_PROVIDER))
    val repository = createRepository(backgroundScope)
    repository.startSharing(TRIP_ID)

    `when`(auth.currentUser).thenReturn(null)
    repository.stopSharing(TRIP_ID)
    advanceTimeBy(UPDATE_INTERVAL * 2)
    runCurrent()

    verifyUpdates(ownLocation, 1)
    verifyRemovals(ownLocation, 0)
    assertFalse(repository.isSharing(TRIP_ID).first())
  }

  @Test
  fun stopSharing_doesNotRemoveEntryOfAnotherAccount() = runTest {
    application.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    locationManager.simulateLocation(recentLocation(LocationManager.GPS_PROVIDER))
    val repository = createRepository(backgroundScope)
    repository.startSharing(TRIP_ID)
    val otherUserLocation = signInAsOtherUser()

    repository.stopSharing(TRIP_ID)

    verifyRemovals(ownLocation, 0)
    verifyRemovals(otherUserLocation, 0)
    assertFalse(repository.isSharing(TRIP_ID).first())
  }

  @Test
  fun stopSharing_succeedsWhenRemovalIsDeniedAfterLeavingTheTrip() = runTest {
    removalError = DatabaseError.fromCode(DatabaseError.PERMISSION_DENIED)

    repository.stopSharing(TRIP_ID)

    verifyRemovals(ownLocation, 1)
  }

  @Test
  fun stopSharing_throwsWhenRemovalFails() = runTest {
    removalError = DatabaseError.fromCode(DatabaseError.NETWORK_ERROR)

    val error = runCatching { repository.stopSharing(TRIP_ID) }.exceptionOrNull()

    assertTrue(error is LocationSharingException)
  }

  @Test
  fun stopSharing_throwsWhenRemovalIsNotAcknowledgedInTime() = runTest {
    application.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    locationManager.simulateLocation(recentLocation(LocationManager.GPS_PROVIDER))
    val repository = createRepository(backgroundScope)
    repository.startSharing(TRIP_ID)
    holdRemovals = true

    val stop = async { runCatching { repository.stopSharing(TRIP_ID) } }
    runCurrent()
    advanceTimeBy(DefaultLocationRepository.WRITE_TIMEOUT)
    runCurrent()

    assertTrue(stop.await().exceptionOrNull() is LocationSharingException)
    assertFalse(repository.isSharing(TRIP_ID).first())
  }

  @Test
  fun stopSharing_issuesRemovalBeforeARestartCanUpdate() = runTest {
    application.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    locationManager.simulateLocation(recentLocation(LocationManager.GPS_PROVIDER))
    val repository = createRepository(backgroundScope)
    repository.startSharing(TRIP_ID)
    holdRemovals = true

    val stop = async { runCatching { repository.stopSharing(TRIP_ID) } }
    runCurrent()
    repository.startSharing(TRIP_ID)

    val order = inOrder(ownLocation)
    order.verify(ownLocation).setValue(any(), any(CompletionListener::class.java))
    order.verify(ownLocation).removeValue(any(CompletionListener::class.java))
    order.verify(ownLocation).setValue(any(), any(CompletionListener::class.java))
    assertTrue(repository.isSharing(TRIP_ID).first())
    stop.cancel()
  }

  @Test
  fun stopSharing_doesNotWaitForAPendingFirstUpdate() = runTest {
    application.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    locationManager.simulateLocation(recentLocation(LocationManager.GPS_PROVIDER))
    val repository = createRepository(backgroundScope)
    holdUpdates = true
    val start = async { runCatching { repository.startSharing(TRIP_ID) } }
    runCurrent()
    assertFalse(start.isCompleted)

    repository.stopSharing(TRIP_ID)

    verifyRemovals(ownLocation, 1)
    assertFalse(repository.isSharing(TRIP_ID).first())
    // The interrupted start ends instead of waiting for an update that no longer matters.
    assertTrue(start.await().isFailure)
  }

  @Test
  fun restoreSharing_resumesSharingAfterTheProcessWasKilled() = runTest {
    application.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    locationManager.simulateLocation(recentLocation(LocationManager.GPS_PROVIDER))
    val killedProcess = killableScope()
    createRepository(killedProcess).startSharing(TRIP_ID)
    killedProcess.cancel()
    val repository = createRepository(backgroundScope)

    repository.restoreSharing()
    advanceTimeBy(UPDATE_INTERVAL)
    runCurrent()

    verifyUpdates(ownLocation, 3)
    assertTrue(repository.isSharing(TRIP_ID).first())
    assertFalse(repository.isSharing(OTHER_TRIP_ID).first())
  }

  @Test
  fun restoreSharing_doesNotResumeAStoppedSharing() = runTest {
    application.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    locationManager.simulateLocation(recentLocation(LocationManager.GPS_PROVIDER))
    val previousProcess = killableScope()
    val previousRepository = createRepository(previousProcess)
    previousRepository.startSharing(TRIP_ID)
    previousRepository.stopSharing(TRIP_ID)
    previousProcess.cancel()
    val repository = createRepository(backgroundScope)

    repository.restoreSharing()

    verifyUpdates(ownLocation, 1)
    assertFalse(repository.isSharing(TRIP_ID).first())
  }

  @Test
  fun restoreSharing_doesNotResumeASharingThatStoppedByItself() = runTest {
    application.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    locationManager.simulateLocation(recentLocation(LocationManager.GPS_PROVIDER))
    val previousProcess = killableScope()
    createRepository(previousProcess).startSharing(TRIP_ID)
    updateError = DatabaseError.fromCode(DatabaseError.PERMISSION_DENIED)
    advanceTimeBy(UPDATE_INTERVAL)
    runCurrent()
    previousProcess.cancel()
    val repository = createRepository(backgroundScope)

    repository.restoreSharing()

    verifyUpdates(ownLocation, 2)
    assertFalse(repository.isSharing(TRIP_ID).first())
  }

  @Test
  fun restoreSharing_doesNotResumeAFailedStart() = runTest {
    application.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    locationManager.simulateLocation(recentLocation(LocationManager.GPS_PROVIDER))
    updateError = DatabaseError.fromCode(DatabaseError.PERMISSION_DENIED)
    runCatching { createRepository(backgroundScope).startSharing(TRIP_ID) }
    updateError = null
    val repository = createRepository(backgroundScope)

    repository.restoreSharing()

    verifyUpdates(ownLocation, 1)
    assertFalse(repository.isSharing(TRIP_ID).first())
  }

  @Test
  fun restoreSharing_removesThePositionWhenSharingCannotResume() = runTest {
    application.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    locationManager.simulateLocation(recentLocation(LocationManager.GPS_PROVIDER))
    val killedProcess = killableScope()
    createRepository(killedProcess).startSharing(TRIP_ID)
    killedProcess.cancel()
    application.denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    val repository = createRepository(backgroundScope)

    repository.restoreSharing()
    application.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    repository.restoreSharing()

    // Removed once, and forgotten so that the second restore does not share again.
    verifyRemovals(ownLocation, 1)
    verifyUpdates(ownLocation, 1)
    assertFalse(repository.isSharing(TRIP_ID).first())
  }

  @Test
  fun restoreSharing_doesNotResumeTheSharingOfAnotherAccount() = runTest {
    application.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    locationManager.simulateLocation(recentLocation(LocationManager.GPS_PROVIDER))
    val killedProcess = killableScope()
    createRepository(killedProcess).startSharing(TRIP_ID)
    killedProcess.cancel()
    val otherUserLocation = signInAsOtherUser()
    val repository = createRepository(backgroundScope)

    repository.restoreSharing()

    verifyUpdates(otherUserLocation, 0)
    assertFalse(repository.isSharing(TRIP_ID).first())
  }

  @Test
  fun restoreSharing_doesNothingWhenSignedOut() = runTest {
    application.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    locationManager.simulateLocation(recentLocation(LocationManager.GPS_PROVIDER))
    val killedProcess = killableScope()
    createRepository(killedProcess).startSharing(TRIP_ID)
    killedProcess.cancel()
    `when`(auth.currentUser).thenReturn(null)
    val repository = createRepository(backgroundScope)

    repository.restoreSharing()

    verifyUpdates(ownLocation, 1)
    verifyRemovals(ownLocation, 0)
    assertFalse(repository.isSharing(TRIP_ID).first())
  }

  @Test
  fun restoreSharing_throwsWhenTheSavedTripsCannotBeRead() = runTest {
    sharedTripsStore = FailingDataStore()
    val repository = createRepository(backgroundScope)

    val error = runCatching { repository.restoreSharing() }.exceptionOrNull()

    assertTrue(error is LocationSharingException)
  }

  @Test
  fun startSharing_failsAndRemovesThePositionWhenTheTripCannotBeSaved() = runTest {
    application.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    locationManager.simulateLocation(recentLocation(LocationManager.GPS_PROVIDER))
    sharedTripsStore = FailingDataStore()
    val repository = createRepository(backgroundScope)

    val error = runCatching { repository.startSharing(TRIP_ID) }.exceptionOrNull()

    assertTrue(error is LocationSharingException)
    verifyRemovals(ownLocation, 1)
    assertFalse(repository.isSharing(TRIP_ID).first())
  }

  @Test
  fun observeMemberLocations_emitsEmptyListWithoutSharingMembers() = runTest {
    val emissions = collectMemberLocations()

    sendMembers()

    assertEquals(listOf(emptyList<MemberLocation>()), emissions)
  }

  @Test
  fun observeMemberLocations_emitsSingleSharingMember() = runTest {
    val emissions = collectMemberLocations()

    sendMembers(memberSnapshot("user-2", 47.3769, 8.5417, 1_000L))

    assertEquals(listOf(listOf(MemberLocation("user-2", 47.3769, 8.5417, 1_000L))), emissions)
  }

  @Test
  fun observeMemberLocations_emitsSeveralMembersExcludingCurrentUserAndInvalidEntries() = runTest {
    val emissions = collectMemberLocations()

    sendMembers(
        memberSnapshot("user-2", 47.3769, 8.5417, 1_000L),
        memberSnapshot(USER_ID, 46.5191, 6.5668, 2_000L),
        memberSnapshot("user-3", 46.2044, 6.1432, 3_000L),
        memberSnapshot("user-4", value = mapOf("latitude" to 200.0)),
        memberSnapshot("user-5", value = "invalid"),
    )

    assertEquals(
        listOf(
            listOf(
                MemberLocation("user-2", 47.3769, 8.5417, 1_000L),
                MemberLocation("user-3", 46.2044, 6.1432, 3_000L),
            )
        ),
        emissions,
    )
  }

  @Test
  fun observeMemberLocations_emitsWhenMembersMoveStartOrStopSharing() = runTest {
    val emissions = collectMemberLocations()

    sendMembers(memberSnapshot("user-2", 47.3769, 8.5417, 1_000L))
    sendMembers(
        memberSnapshot("user-2", 47.3770, 8.5418, 2_000L),
        memberSnapshot("user-3", 46.2044, 6.1432, 2_000L),
    )
    sendMembers(memberSnapshot("user-3", 46.2044, 6.1432, 2_000L))

    assertEquals(
        listOf(
            listOf(MemberLocation("user-2", 47.3769, 8.5417, 1_000L)),
            listOf(
                MemberLocation("user-2", 47.3770, 8.5418, 2_000L),
                MemberLocation("user-3", 46.2044, 6.1432, 2_000L),
            ),
            listOf(MemberLocation("user-3", 46.2044, 6.1432, 2_000L)),
        ),
        emissions,
    )
  }

  @Test
  fun observeMemberLocations_worksWithoutLocationPermission() = runTest {
    assertFalse(repository.hasLocationPermission())
    val emissions = collectMemberLocations()

    sendMembers(memberSnapshot("user-2", 47.3769, 8.5417, 1_000L))

    assertEquals(listOf(listOf(MemberLocation("user-2", 47.3769, 8.5417, 1_000L))), emissions)
  }

  @Test
  fun observeMemberLocations_removesListenerWhenCollectionStops() = runTest {
    val collection = launch { repository.observeMemberLocations(TRIP_ID).collect {} }
    runCurrent()

    collection.cancel()
    runCurrent()

    verify(tripLocations).removeEventListener(listeners.single())
  }

  @Test
  fun observeMemberLocations_failsWhenDatabaseCancelsListener() = runTest {
    val result = async { runCatching { repository.observeMemberLocations(TRIP_ID).first() } }
    runCurrent()

    listeners.single().onCancelled(DatabaseError.fromCode(DatabaseError.PERMISSION_DENIED))

    assertTrue(result.await().exceptionOrNull() is LocationSharingException)
  }

  @Test
  fun isSharing_isFalseWhileTheFirstUpdateIsPending() = runTest {
    application.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    locationManager.simulateLocation(recentLocation(LocationManager.GPS_PROVIDER))
    val repository = createRepository(backgroundScope)
    holdUpdates = true

    val start = async { runCatching { repository.startSharing(TRIP_ID) } }
    runCurrent()

    assertFalse(start.isCompleted)
    assertFalse(repository.isSharing(TRIP_ID).first())
    start.cancel()
  }

  @Test
  fun startSharing_secondCallWaitsForThePendingFirstUpdate() = runTest {
    application.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    locationManager.simulateLocation(recentLocation(LocationManager.GPS_PROVIDER))
    val repository = createRepository(backgroundScope)
    holdUpdates = true
    val first = async { runCatching { repository.startSharing(TRIP_ID) } }
    runCurrent()

    val second = async { runCatching { repository.startSharing(TRIP_ID) } }
    runCurrent()

    assertFalse(second.isCompleted)
    advanceTimeBy(DefaultLocationRepository.WRITE_TIMEOUT)
    runCurrent()
    // Both calls report the failed first update, and only one update was attempted.
    assertTrue(first.await().exceptionOrNull() is LocationSharingException)
    assertTrue(second.await().exceptionOrNull() is LocationSharingException)
    verifyUpdates(ownLocation, 1)
    assertFalse(repository.isSharing(TRIP_ID).first())
  }

  @Test
  fun restoreSharing_restoresTheTripsConcurrently() = runTest {
    application.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    locationManager.simulateLocation(recentLocation(LocationManager.GPS_PROVIDER))
    val killedProcess = killableScope()
    val previousRepository = createRepository(killedProcess)
    previousRepository.startSharing(TRIP_ID)
    previousRepository.startSharing(OTHER_TRIP_ID)
    killedProcess.cancel()
    val repository = createRepository(backgroundScope)
    holdUpdates = true

    val restore = async { repository.restoreSharing() }
    runCurrent()

    // Both first updates are issued before either is acknowledged.
    verifyUpdates(ownLocation, 2)
    verifyUpdates(ownOtherTripLocation, 2)
    restore.cancel()
  }

  @Test
  fun stopAllSharing_stopsEveryTripAndRemovesItsEntry() = runTest {
    application.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    locationManager.simulateLocation(recentLocation(LocationManager.GPS_PROVIDER))
    val repository = createRepository(backgroundScope)
    repository.startSharing(TRIP_ID)
    repository.startSharing(OTHER_TRIP_ID)

    repository.stopAllSharing()
    advanceTimeBy(UPDATE_INTERVAL)
    runCurrent()

    verifyRemovals(ownLocation, 1)
    verifyRemovals(ownOtherTripLocation, 1)
    verifyUpdates(ownLocation, 1)
    assertFalse(repository.isSharing(TRIP_ID).first())
    assertFalse(repository.isSharing(OTHER_TRIP_ID).first())
  }

  @Test
  fun stopAllSharing_removesEntriesLeftByAPreviousProcess() = runTest {
    application.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    locationManager.simulateLocation(recentLocation(LocationManager.GPS_PROVIDER))
    val killedProcess = killableScope()
    createRepository(killedProcess).startSharing(TRIP_ID)
    killedProcess.cancel()
    val repository = createRepository(backgroundScope)

    repository.stopAllSharing()
    repository.restoreSharing()

    verifyRemovals(ownLocation, 1)
    verifyUpdates(ownLocation, 1)
  }

  @Test
  fun stopAllSharing_stopsEveryTripEvenWhenARemovalFails() = runTest {
    application.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    locationManager.simulateLocation(recentLocation(LocationManager.GPS_PROVIDER))
    val repository = createRepository(backgroundScope)
    repository.startSharing(TRIP_ID)
    repository.startSharing(OTHER_TRIP_ID)
    removalError = DatabaseError.fromCode(DatabaseError.NETWORK_ERROR)

    val error = runCatching { repository.stopAllSharing() }.exceptionOrNull()

    assertTrue(error is LocationSharingException)
    verifyRemovals(ownLocation, 1)
    verifyRemovals(ownOtherTripLocation, 1)
    assertFalse(repository.isSharing(TRIP_ID).first())
    assertFalse(repository.isSharing(OTHER_TRIP_ID).first())
  }

  @Test
  fun stopAllSharing_doesNothingWhenSignedOut() = runTest {
    `when`(auth.currentUser).thenReturn(null)

    repository.stopAllSharing()

    verifyRemovals(ownLocation, 0)
  }

  private fun createRepository(scope: CoroutineScope) =
      DefaultLocationRepository(
          context = ApplicationProvider.getApplicationContext<Application>(),
          database = database,
          auth = auth,
          sharedTripsStore = sharedTripsStore,
          scope = scope,
          updateInterval = UPDATE_INTERVAL,
      )

  // Can be cancelled to simulate the system killing the app process.
  private fun TestScope.killableScope() =
      CoroutineScope(backgroundScope.coroutineContext + Job(backgroundScope.coroutineContext.job))

  private fun signInAsOtherUser(): DatabaseReference {
    val otherUser = mock(FirebaseUser::class.java)
    `when`(otherUser.uid).thenReturn(OTHER_USER_ID)
    val otherUserLocation = mock(DatabaseReference::class.java)
    doAnswer { invocation ->
          invocation.getArgument<CompletionListener>(1).onComplete(null, otherUserLocation)
        }
        .`when`(otherUserLocation)
        .setValue(any(), any(CompletionListener::class.java))
    `when`(tripLocations.child(OTHER_USER_ID)).thenReturn(otherUserLocation)
    `when`(auth.currentUser).thenReturn(otherUser)
    return otherUserLocation
  }

  private fun verifyUpdates(reference: DatabaseReference, count: Int) {
    verify(reference, times(count)).setValue(any(), any(CompletionListener::class.java))
  }

  private fun verifyRemovals(reference: DatabaseReference, count: Int) {
    verify(reference, times(count)).removeValue(any(CompletionListener::class.java))
  }

  private fun TestScope.collectMemberLocations(): List<List<MemberLocation>> {
    val emissions = mutableListOf<List<MemberLocation>>()
    backgroundScope.launch { repository.observeMemberLocations(TRIP_ID).toList(emissions) }
    runCurrent()
    return emissions
  }

  private fun TestScope.sendMembers(vararg members: DataSnapshot) {
    listeners.single().onDataChange(tripSnapshot(*members))
    runCurrent()
  }

  private fun tripSnapshot(vararg members: DataSnapshot): DataSnapshot =
      mock(DataSnapshot::class.java).also { `when`(it.children).thenReturn(members.toList()) }

  private fun memberSnapshot(
      userId: String,
      latitude: Double = 0.0,
      longitude: Double = 0.0,
      updatedAt: Long = 0L,
      value: Any? =
          mapOf("latitude" to latitude, "longitude" to longitude, "updatedAt" to updatedAt),
  ): DataSnapshot =
      mock(DataSnapshot::class.java).also {
        `when`(it.key).thenReturn(userId)
        `when`(it.value).thenReturn(value)
      }

  private fun recentLocation(provider: String) =
      Location(provider).apply {
        latitude = 46.5191
        longitude = 6.5668
        accuracy = 12f
        time = System.currentTimeMillis()
        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
      }

  private class InMemoryDataStore : DataStore<Preferences> {
    override val data = MutableStateFlow(emptyPreferences())

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences
    ): Preferences = transform(data.value).also { data.value = it }
  }

  private class FailingDataStore : DataStore<Preferences> {
    override val data = flow<Preferences> { throw IOException("Disk error") }

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences
    ): Preferences = throw IOException("Disk error")
  }

  private companion object {
    const val USER_ID = "user-1"
    const val OTHER_USER_ID = "user-2"
    const val TRIP_ID = "trip-1"
    const val OTHER_TRIP_ID = "trip-2"
    val UPDATE_INTERVAL = 20.seconds
  }
}
