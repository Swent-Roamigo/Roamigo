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
import java.time.Duration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowApplication
import org.robolectric.shadows.ShadowLocationManager

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DefaultLocationRepositoryTest {

  private lateinit var application: ShadowApplication
  private lateinit var locationManager: ShadowLocationManager
  private lateinit var repository: DefaultLocationRepository

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Application>()
    application = shadowOf(context)
    locationManager = shadowOf(context.getSystemService(LocationManager::class.java))
    locationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
    locationManager.setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
    repository = DefaultLocationRepository(context)
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

  private fun recentLocation(provider: String) =
      Location(provider).apply {
        latitude = 46.5191
        longitude = 6.5668
        accuracy = 12f
        time = System.currentTimeMillis()
        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
      }
}
