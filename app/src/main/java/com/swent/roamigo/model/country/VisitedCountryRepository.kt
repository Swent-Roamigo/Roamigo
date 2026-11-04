// AI assistance was used for implementation support and code review.
package com.swent.roamigo.model.country

import com.swent.roamigo.model.trip.TripRepository
import com.swent.roamigo.model.trip.isCompletedOn
import com.swent.roamigo.model.validCountryCodeOrNull
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Calculates visits by ISO alpha-2 country code from the current user's completed trips. */
class VisitedCountryRepository(
    private val tripRepository: TripRepository,
    private val resolver: CountryCodeResolver,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

  /**
   * A country counts once per completed trip, even if multiple cities were visited. The trip list
   * is fetched on every call, so the future UI can explicitly refresh it. Trip loading errors
   * propagate; individual failed lookups do not hide other countries.
   */
  suspend fun visitCounts(): Map<String, Int> {
    val today = LocalDate.now(clock)

    val completed =
        tripRepository
            .getTrips()
            .distinctBy { it.uid }
            .filter { it.isCompletedOn(today, clock.zone) }

    // Limit simultaneous requests to respect GeoNames and avoid swamping DataStore.
    val limiter = Semaphore(MAX_CONCURRENT_RESOLUTIONS)

    val resolved = coroutineScope {
      completed
          .flatMap { trip ->
            trip.destinations.map { destination ->
              async {
                val code =
                    try {
                      limiter.withPermit { resolver.resolve(destination).validCountryCodeOrNull() }
                    } catch (e: CancellationException) {
                      throw e
                    } catch (_: Exception) {
                      null
                    }

                trip.uid to code
              }
            }
          }
          .awaitAll()
    }

    return resolved
        .groupBy { it.first }
        .values
        .flatMap { tripLocations -> tripLocations.mapNotNull { it.second }.toSet() }
        .groupingBy { it }
        .eachCount()
  }

  private companion object {
    const val MAX_CONCURRENT_RESOLUTIONS = 4
  }
}
