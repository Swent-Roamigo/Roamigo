// AI assistance was used for implementation support and code review.
package com.swent.roamigo.model.country

import com.swent.roamigo.model.MAX_LATITUDE
import com.swent.roamigo.model.MAX_LONGITUDE
import com.swent.roamigo.model.MIN_LATITUDE
import com.swent.roamigo.model.MIN_LONGITUDE
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.validCountryCodeOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Uses stored codes first, a persistent cache next, and GeoNames only as a fallback. */
class CachedCountryCodeResolver(
    private val lookup: CountryCodeLookup,
    private val cache: CountryCodeCache,
) : CountryCodeResolver {

  // Per-coordinate locks avoid duplicate requests even when two callers resolve the same point.
  // Keep locks for this resolver's lifetime: removing a lock while callers wait on it is racy.
  private val locks = ConcurrentHashMap<CoordinateKey, Mutex>()

  // Also retain successful results in memory if DataStore is temporarily unavailable.
  private val resolvedInMemory = ConcurrentHashMap<CoordinateKey, String>()

  override suspend fun resolve(location: TripLocation): String? {
    // Codes in TripLocation have already been validated by its constructor.
    location.countryCode?.let {
      return it
    }

    val latitude = location.latitude
    val longitude = location.longitude

    if (latitude !in MIN_LATITUDE..MAX_LATITUDE || longitude !in MIN_LONGITUDE..MAX_LONGITUDE) {
      return null
    }

    val key = CoordinateKey(latitude.toBits(), longitude.toBits())
    val lock = locks.computeIfAbsent(key) { Mutex() }

    return lock.withLock {
      resolvedInMemory[key]?.let {
        return@withLock it
      }

      val cached =
          try {
            cache.get(latitude, longitude).validCountryCodeOrNull()
          } catch (e: CancellationException) {
            throw e
          } catch (_: Exception) {
            // An unreadable cache should not prevent an online lookup.
            null
          }

      if (cached != null) {
        resolvedInMemory[key] = cached
        return@withLock cached
      }

      val resolved =
          try {
            lookup.lookup(latitude, longitude).validCountryCodeOrNull()
          } catch (e: CancellationException) {
            throw e
          } catch (_: Exception) {
            // Offline, GeoNames error, quota exhaustion, or missing credentials.
            null
          }

      if (resolved != null) {
        resolvedInMemory[key] = resolved

        try {
          cache.put(latitude, longitude, resolved)
        } catch (e: CancellationException) {
          throw e
        } catch (_: Exception) {
          // The answer is still usable for this request when persistence fails.
        }
      }

      resolved
    }
  }

  private data class CoordinateKey(
      val latitudeBits: Long,
      val longitudeBits: Long,
  )
}
