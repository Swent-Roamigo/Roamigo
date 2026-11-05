// AI assistance was used for implementation support and code review.
package com.swent.roamigo.model.country

import com.swent.roamigo.model.MAX_LATITUDE
import com.swent.roamigo.model.MAX_LONGITUDE
import com.swent.roamigo.model.MIN_LATITUDE
import com.swent.roamigo.model.MIN_LONGITUDE
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.validCountryCodeOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
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
  private class ResolutionState {
    val mutex = Mutex()
    val completedAttempts = AtomicLong(0)
    var lastResult: String? = null
  }

  private val states = ConcurrentHashMap<CoordinateKey, ResolutionState>()

  // Also retain successful results in memory if DataStore is temporarily unavailable.
  private val resolvedInMemory = ConcurrentHashMap<CoordinateKey, String>()

  override suspend fun resolve(location: TripLocation): String? {
    location.countryCode?.let {
      return it
    }

    val latitude = location.latitude
    val longitude = location.longitude

    if (latitude !in MIN_LATITUDE..MAX_LATITUDE || longitude !in MIN_LONGITUDE..MAX_LONGITUDE) {
      return null
    }

    val key = CoordinateKey(latitude.toBits(), longitude.toBits())
    val state = states.computeIfAbsent(key) { ResolutionState() }

    val observedAttempt = state.completedAttempts.get()

    return state.mutex.withLock {
      resolvedInMemory[key]?.let {
        return@withLock it
      }

      // Another caller completed a lookup while this one was waiting.
      if (state.completedAttempts.get() != observedAttempt) {
        return@withLock state.lastResult
      }

      val result = resolveFromSources(latitude, longitude, key)
      state.lastResult = result
      state.completedAttempts.incrementAndGet()
      result
    }
  }

  private data class CoordinateKey(
      val latitudeBits: Long,
      val longitudeBits: Long,
  )

  private suspend fun resolveFromSources(
      latitude: Double,
      longitude: Double,
      key: CoordinateKey,
  ): String? {
    val cached = readCache(latitude, longitude)

    if (cached != null) {
      resolvedInMemory[key] = cached
      return cached
    }

    val resolved = lookupCountry(latitude, longitude) ?: return null
    resolvedInMemory[key] = resolved
    persistCountry(latitude, longitude, resolved)
    return resolved
  }

  private suspend fun readCache(latitude: Double, longitude: Double): String? =
      try {
        cache.get(latitude, longitude).validCountryCodeOrNull()
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        null
      }

  private suspend fun lookupCountry(latitude: Double, longitude: Double): String? =
      try {
        lookup.lookup(latitude, longitude).validCountryCodeOrNull()
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        null
      }

  private suspend fun persistCountry(
      latitude: Double,
      longitude: Double,
      countryCode: String,
  ) {
    try {
      cache.put(latitude, longitude, countryCode)
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      // The in-memory result remains available if persistence fails.
    }
  }
}
