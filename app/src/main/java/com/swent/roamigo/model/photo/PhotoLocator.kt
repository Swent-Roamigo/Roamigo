// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.photo

import com.swent.roamigo.model.location.DeviceLocation
import com.swent.roamigo.model.location.LocationRepository
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A valid position where a photo was taken.
 *
 * @throws IllegalArgumentException if a value is not finite or is out of range. Use [orNull] for
 *   values that are not known to be valid.
 */
data class PhotoCoordinates(val latitude: Double, val longitude: Double) {
  init {
    require(isValid(latitude, longitude)) { "Invalid photo coordinates: $latitude, $longitude" }
  }

  companion object {
    // Same bounds as isValidNewPhoto in firestore.rules, which rejects the whole photo otherwise.
    private const val MAX_LATITUDE = 90.0
    private const val MAX_LONGITUDE = 180.0

    /** The coordinates for this pair, or null if it is not a valid position. */
    fun orNull(latitude: Double, longitude: Double): PhotoCoordinates? =
        if (isValid(latitude, longitude)) PhotoCoordinates(latitude, longitude) else null

    private fun isValid(latitude: Double, longitude: Double): Boolean =
        latitude.isFinite() &&
            longitude.isFinite() &&
            latitude in -MAX_LATITUDE..MAX_LATITUDE &&
            longitude in -MAX_LONGITUDE..MAX_LONGITUDE
  }
}

/**
 * How long reading the position of a photo may take before the photo is saved without one. A slow
 * fix must not make the user wait, and a photo is worth more than its position.
 */
val PHOTO_LOCATION_TIMEOUT: Duration = 5.seconds

/** Outcome of [PhotoLocator.locate]. */
sealed interface PhotoLocationResult {
  data class Found(val coordinates: PhotoCoordinates) : PhotoLocationResult

  /** The location permission is not granted. */
  data object PermissionOff : PhotoLocationResult

  /** The permission is granted, but no usable position could be read. */
  data object Unavailable : PhotoLocationResult

  /** No position arrived within the allowed time. */
  data object TimedOut : PhotoLocationResult
}

/** Reads where a photo is taken. */
interface PhotoLocator {
  /**
   * Reads the current position of this device. It never requests the location permission: it only
   * uses one that is already granted, e.g. on the trip map. The call is cancellable, and a
   * cancelled read does not return a result.
   *
   * It never throws, except [CancellationException]: a failure is reported as
   * [PhotoLocationResult.PermissionOff], [PhotoLocationResult.Unavailable] or
   * [PhotoLocationResult.TimedOut].
   *
   * @param timeout how long to wait for a position. A zero or negative value times out at once.
   */
  suspend fun locate(timeout: Duration = PHOTO_LOCATION_TIMEOUT): PhotoLocationResult
}

/** [PhotoLocator] backed by a [LocationRepository]. */
class DefaultPhotoLocator(private val locationRepository: LocationRepository) : PhotoLocator {

  override suspend fun locate(timeout: Duration): PhotoLocationResult {
    if (!locationRepository.hasLocationPermission()) return PhotoLocationResult.PermissionOff
    if (timeout <= Duration.ZERO) return PhotoLocationResult.TimedOut
    val read =
        try {
          // Wrapped, since a null fix and a timeout would both look like null otherwise.
          withTimeoutOrNull(timeout) { Read(locationRepository.getCurrentLocation()) }
        } catch (e: SecurityException) {
          // The permission was revoked between the check and the read.
          return PhotoLocationResult.PermissionOff
        }
    if (read == null) return PhotoLocationResult.TimedOut
    val coordinates = read.location?.toValidCoordinates()
    return if (coordinates == null) PhotoLocationResult.Unavailable
    else PhotoLocationResult.Found(coordinates)
  }

  private class Read(val location: DeviceLocation?)

  private fun DeviceLocation.toValidCoordinates(): PhotoCoordinates? =
      PhotoCoordinates.orNull(latitude, longitude)
}
