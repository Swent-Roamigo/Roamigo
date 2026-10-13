// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.location

import android.Manifest
import android.app.Application
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseException
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import java.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
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
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
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
    listOf(ownLocation, ownOtherTripLocation).forEach {
      `when`(it.setValue(any())).thenReturn(Tasks.forResult(null))
      `when`(it.removeValue()).thenReturn(Tasks.forResult(null))
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
            mapOf(
                "latitude" to 46.5191,
                "longitude" to 6.5668,
                "updatedAt" to ServerValue.TIMESTAMP,
            )
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
    verify(ownLocation, times(3)).setValue(any())

    repository.stopSharing(TRIP_ID)
    advanceTimeBy(UPDATE_INTERVAL * 2)
    runCurrent()
    verify(ownLocation, times(3)).setValue(any())
  }

  @Test
  fun startSharing_keepsSharingWhenAnUpdateFails() = runTest {
    application.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    locationManager.simulateLocation(recentLocation(LocationManager.GPS_PROVIDER))
    val repository = createRepository(backgroundScope)
    repository.startSharing(TRIP_ID)

    `when`(ownLocation.setValue(any()))
        .thenReturn(Tasks.forException(DatabaseException("Permission denied")))
    advanceTimeBy(UPDATE_INTERVAL)
    runCurrent()

    verify(ownLocation, times(2)).setValue(any())
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

    verify(ownLocation, times(2)).setValue(any())
  }

  @Test
  fun startSharing_onlyAffectsTheGivenTrip() = runTest {
    application.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    locationManager.simulateLocation(recentLocation(LocationManager.GPS_PROVIDER))
    val repository = createRepository(backgroundScope)

    repository.startSharing(TRIP_ID)

    assertFalse(repository.isSharing(OTHER_TRIP_ID).first())
    verify(ownOtherTripLocation, never()).setValue(any())
  }

  @Test
  fun startSharing_throwsWithoutPermissionAndDoesNotShare() = runTest {
    val repository = createRepository(backgroundScope)

    val error = runCatching { repository.startSharing(TRIP_ID) }.exceptionOrNull()
    assertTrue(error is IllegalStateException)

    verify(ownLocation, never()).setValue(any())
    assertFalse(repository.isSharing(TRIP_ID).first())
  }

  @Test
  fun startSharing_throwsWhenSignedOutAndDoesNotShare() = runTest {
    application.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    `when`(auth.currentUser).thenReturn(null)
    val repository = createRepository(backgroundScope)

    val error = runCatching { repository.startSharing(TRIP_ID) }.exceptionOrNull()
    assertTrue(error is IllegalStateException)

    verify(ownLocation, never()).setValue(any())
    assertFalse(repository.isSharing(TRIP_ID).first())
  }

  @Test
  fun startSharing_doesNotShareWhenTheFirstUpdateFails() = runTest {
    application.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    locationManager.simulateLocation(recentLocation(LocationManager.GPS_PROVIDER))
    `when`(ownLocation.setValue(any()))
        .thenReturn(Tasks.forException(DatabaseException("Permission denied")))
    val repository = createRepository(backgroundScope)

    val error = runCatching { repository.startSharing(TRIP_ID) }.exceptionOrNull()
    assertTrue(error is DatabaseException)

    assertFalse(repository.isSharing(TRIP_ID).first())
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

    verify(ownLocation).removeValue()
    assertEquals(listOf(false, true, false), sharingStates)
  }

  @Test
  fun stopSharing_removesEntryLeftByAPreviousSession() = runTest {
    repository.stopSharing(TRIP_ID)

    verify(ownLocation).removeValue()
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

    assertTrue(result.await().exceptionOrNull() is DatabaseException)
  }

  private fun createRepository(scope: CoroutineScope) =
      DefaultLocationRepository(
          context = ApplicationProvider.getApplicationContext<Application>(),
          database = database,
          auth = auth,
          scope = scope,
          updateInterval = UPDATE_INTERVAL,
      )

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

  private companion object {
    const val USER_ID = "user-1"
    const val TRIP_ID = "trip-1"
    const val OTHER_TRIP_ID = "trip-2"
    val UPDATE_INTERVAL = 20.seconds
  }
}
