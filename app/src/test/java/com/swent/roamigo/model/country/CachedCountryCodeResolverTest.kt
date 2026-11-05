// AI assistance was used for implementation support and code review.
package com.swent.roamigo.model.country

import com.swent.roamigo.model.TripLocation
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class CachedCountryCodeResolverTest {
  private val lisbon = TripLocation(38.7223, -9.1393, "Lisbon")

  @Test
  fun storedCountryCodeSkipsCacheAndGeoNames() = runTest {
    var reads = 0
    var requests = 0

    val cache =
        object : CountryCodeCache {
          override suspend fun get(latitude: Double, longitude: Double): String? {
            reads++
            return null
          }

          override suspend fun put(latitude: Double, longitude: Double, countryCode: String) {
            error("Should not write cache")
          }
        }

    val resolver =
        CachedCountryCodeResolver(
            CountryCodeLookup { _, _ ->
              requests++
              "FR"
            },
            cache,
        )

    assertEquals("PT", resolver.resolve(lisbon.copy(countryCode = "PT")))
    assertEquals(0, reads)
    assertEquals(0, requests)
  }

  @Test
  fun successfulLookupIsCachedAndReused() = runTest {
    var requests = 0

    val resolver =
        CachedCountryCodeResolver(
            CountryCodeLookup { _, _ ->
              requests++
              "PT"
            },
            MemoryCache(),
        )

    assertEquals("PT", resolver.resolve(lisbon))
    assertEquals("PT", resolver.resolve(lisbon.copy(name = "Lisboa")))
    assertEquals(1, requests)
  }

  @Test
  fun cachedCountryCodeAvoidsGeoNamesEvenWhenOffline() = runTest {
    val cache = MemoryCache()
    cache.put(lisbon.latitude, lisbon.longitude, "PT")

    val resolver =
        CachedCountryCodeResolver(
            CountryCodeLookup { _, _ -> throw IOException("GeoNames unavailable") },
            cache,
        )

    assertEquals("PT", resolver.resolve(lisbon))
  }

  @Test
  fun malformedCachedValueIsIgnoredAndReplaced() = runTest {
    val cache = MemoryCache()
    cache.put(lisbon.latitude, lisbon.longitude, "pt")
    var calls = 0

    val resolver =
        CachedCountryCodeResolver(
            CountryCodeLookup { _, _ ->
              calls++
              "PT"
            },
            cache,
        )

    assertEquals("PT", resolver.resolve(lisbon))
    assertEquals("PT", resolver.resolve(lisbon))
    assertEquals(1, calls)
  }

  @Test
  fun invalidCoordinatesNeverCallGeoNames() = runTest {
    var requests = 0

    val resolver =
        CachedCountryCodeResolver(
            CountryCodeLookup { _, _ ->
              requests++
              "FR"
            },
            MemoryCache(),
        )

    listOf(
            lisbon.copy(latitude = Double.NaN),
            lisbon.copy(latitude = Double.POSITIVE_INFINITY),
            lisbon.copy(latitude = 90.1),
            lisbon.copy(longitude = -180.1),
        )
        .forEach { assertNull(resolver.resolve(it)) }

    assertEquals(0, requests)
  }

  @Test
  fun missingOrMalformedLookupIsNotCachedAndCanBeRetried() = runTest {
    var requests = 0

    val resolver =
        CachedCountryCodeResolver(
            CountryCodeLookup { _, _ ->
              requests++
              if (requests == 1) "PRT" else "PT"
            },
            MemoryCache(),
        )

    assertNull(resolver.resolve(lisbon))
    assertEquals("PT", resolver.resolve(lisbon))
    assertEquals(2, requests)
  }

  @Test
  fun networkFailureDoesNotCrashAndIsRetried() = runTest {
    var requests = 0

    val resolver =
        CachedCountryCodeResolver(
            CountryCodeLookup { _, _ ->
              requests++
              if (requests == 1) throw IOException("offline")
              "PT"
            },
            MemoryCache(),
        )

    assertNull(resolver.resolve(lisbon))
    assertEquals("PT", resolver.resolve(lisbon))
    assertEquals(2, requests)
  }

  @Test
  fun cacheFailureDoesNotLoseSuccessfulOnlineResult() = runTest {
    val cache =
        object : CountryCodeCache {
          override suspend fun get(latitude: Double, longitude: Double): String? {
            throw IOException("unreadable")
          }

          override suspend fun put(latitude: Double, longitude: Double, countryCode: String) {
            throw IOException("disk full")
          }
        }

    var requests = 0

    val resolver =
        CachedCountryCodeResolver(
            CountryCodeLookup { _, _ ->
              requests++
              "PT"
            },
            cache,
        )

    assertEquals("PT", resolver.resolve(lisbon))
    assertEquals("PT", resolver.resolve(lisbon))
    assertEquals(1, requests)
  }

  @Test
  fun concurrentRequestsForSameCoordinatesShareLookup() = runTest {
    val started = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    var requests = 0

    val resolver =
        CachedCountryCodeResolver(
            CountryCodeLookup { _, _ ->
              requests++
              started.complete(Unit)
              release.await()
              "PT"
            },
            MemoryCache(),
        )

    val first = async { resolver.resolve(lisbon) }
    val second = async { resolver.resolve(lisbon.copy(name = "Another city")) }

    started.await()
    release.complete(Unit)

    assertEquals("PT", first.await())
    assertEquals("PT", second.await())
    assertEquals(1, requests)
  }

  @Test
  fun cancellationDuringCacheReadIsPropagated() = runTest {
    val cache =
        object : CountryCodeCache {
          override suspend fun get(
              latitude: Double,
              longitude: Double,
          ): String? {
            throw CancellationException("Cache read cancelled")
          }

          override suspend fun put(
              latitude: Double,
              longitude: Double,
              countryCode: String,
          ) {
            error("Cache write should not happen")
          }
        }

    val resolver =
        CachedCountryCodeResolver(
            CountryCodeLookup { _, _ -> error("GeoNames should not be called") },
            cache,
        )

    try {
      resolver.resolve(lisbon)
      fail("Expected cancellation")
    } catch (e: CancellationException) {
      assertEquals("Cache read cancelled", e.message)
    }
  }

  @Test
  fun cancellationDuringGeoNamesLookupIsPropagated() = runTest {
    val resolver =
        CachedCountryCodeResolver(
            CountryCodeLookup { _, _ -> throw CancellationException("Lookup cancelled") },
            MemoryCache(),
        )

    try {
      resolver.resolve(lisbon)
      fail("Expected cancellation")
    } catch (e: CancellationException) {
      assertEquals("Lookup cancelled", e.message)
    }
  }

  @Test
  fun cancellationDuringCacheWriteIsPropagated() = runTest {
    val cache =
        object : CountryCodeCache {
          override suspend fun get(
              latitude: Double,
              longitude: Double,
          ): String? = null

          override suspend fun put(
              latitude: Double,
              longitude: Double,
              countryCode: String,
          ) {
            throw CancellationException("Cache write cancelled")
          }
        }

    val resolver =
        CachedCountryCodeResolver(
            CountryCodeLookup { _, _ -> "PT" },
            cache,
        )

    try {
      resolver.resolve(lisbon)
      fail("Expected cancellation")
    } catch (e: CancellationException) {
      assertEquals("Cache write cancelled", e.message)
    }
  }

  @Test
  fun concurrentFailedLookupsShareResultAndLaterCallRetries() = runTest {
    val started = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    var requests = 0

    val resolver =
        CachedCountryCodeResolver(
            CountryCodeLookup { _, _ ->
              requests++
              if (requests == 1) {
                started.complete(Unit)
                release.await()
                null
              } else {
                "PT"
              }
            },
            MemoryCache(),
        )

    val first = async { resolver.resolve(lisbon) }
    started.await()

    val second = async { resolver.resolve(lisbon.copy(name = "Lisboa")) }

    // Ensure the second caller is waiting before releasing the first.
    runCurrent()
    release.complete(Unit)

    assertNull(first.await())
    assertNull(second.await())
    assertEquals(1, requests)

    // A later independent call is allowed to retry.
    assertEquals("PT", resolver.resolve(lisbon))
    assertEquals(2, requests)
  }

  private class MemoryCache : CountryCodeCache {
    private val codes = mutableMapOf<Pair<Double, Double>, String>()

    override suspend fun get(latitude: Double, longitude: Double): String? =
        codes[latitude to longitude]

    override suspend fun put(latitude: Double, longitude: Double, countryCode: String) {
      codes[latitude to longitude] = countryCode
    }
  }
}
