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
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * [LocationRepository] backed by the Android framework [LocationManager].
 *
 * @param context any context; only its application context is retained.
 * @param locationManager the system location service, injectable for tests.
 */
class DefaultLocationRepository(
    context: Context,
    private val locationManager: LocationManager =
        context.getSystemService(LocationManager::class.java),
) : LocationRepository {

  private val appContext = context.applicationContext

  override fun hasLocationPermission(): Boolean = LOCATION_PERMISSIONS.any {
    ContextCompat.checkSelfPermission(appContext, it) == PackageManager.PERMISSION_GRANTED
  }

  override suspend fun getCurrentLocation(): DeviceLocation? {
    if (!hasLocationPermission()) return null
    // An enabled provider may still fail to get a fix (e.g. GPS indoors), so fall back to the next.
    return usableProviders().firstNotNullOfOrNull { requestSingleLocation(it) }?.toDeviceLocation()
  }

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
  }
}
