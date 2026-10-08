package com.swent.roamigo.model.location

interface LocationRepository {
  /**
   * Whether the app currently holds a location permission (fine or coarse). The permission itself
   * is requested by the UI; this lets the app check the result.
   */
  fun hasLocationPermission(): Boolean

  /**
   * One-shot position of this device.
   *
   * @return the current location, or null if unavailable or permission is denied.
   */
  suspend fun getCurrentLocation(): DeviceLocation?

  // TODO : Extend to access other trip members positions
}
