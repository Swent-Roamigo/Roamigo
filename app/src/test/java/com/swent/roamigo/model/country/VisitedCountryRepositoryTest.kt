// AI assistance was used for implementation support and code review.
package com.swent.roamigo.model.country

import com.google.firebase.Timestamp
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.trip.FakeTripRepository
import com.swent.roamigo.model.trip.TravelMode
import com.swent.roamigo.model.trip.Trip
import com.swent.roamigo.model.trip.TripStatus
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class VisitedCountryRepositoryTest {
  private val clock =
      Clock.fixed(
          Instant.parse("2026-10-26T12:00:00Z"),
          ZoneOffset.UTC,
      )

  @Test
  fun countsCountryOncePerCompletedTripAndAcrossSeparateTrips() = runTest {
    val trips =
        listOf(
            trip("1", "2026-10-24T12:00:00Z", "PT", "PT", "FR"),
            trip("2", "2026-10-25T12:00:00Z", "PT"),
            trip("3", "2026-10-23T12:00:00Z", "FR"),
        )

    val repository =
        VisitedCountryRepository(
            FakeTripRepository(trips = trips),
            storedResolver(),
            clock,
        )

    assertEquals(
        mapOf("PT" to 2, "FR" to 2),
        repository.visitCounts(),
    )
  }

  @Test
  fun ignoresEndingTodayAndFutureTripsEvenWhenStatusSaysCompleted() = runTest {
    val trips =
        listOf(
            trip(
                "past",
                "2026-10-25T12:00:00Z",
                "PT",
                status = TripStatus.PLANNED,
            ),
            trip("today", "2026-10-26T08:00:00Z", "FR"),
            trip(
                "future",
                "2026-10-27T08:00:00Z",
                "ES",
                status = TripStatus.COMPLETED,
            ),
        )

    val repository =
        VisitedCountryRepository(
            FakeTripRepository(trips = trips),
            storedResolver(),
            clock,
        )

    assertEquals(mapOf("PT" to 1), repository.visitCounts())
  }

  @Test
  fun respectsUserTimezoneForDayBoundaries() = runTest {
    val trips = listOf(trip("boundary", "2026-10-25T23:30:00Z", "PT"))

    val repository = FakeTripRepository(trips = trips)

    val utc =
        VisitedCountryRepository(
            repository,
            storedResolver(),
            clock,
        )

    val tokyo =
        VisitedCountryRepository(
            repository,
            storedResolver(),
            clock.withZone(ZoneId.of("Asia/Tokyo")),
        )

    assertEquals(mapOf("PT" to 1), utc.visitCounts())
    assertEquals(emptyMap<String, Int>(), tokyo.visitCounts())
  }

  @Test
  fun missingCountryAndLookupFailureDoNotHideKnownCountries() = runTest {
    val destinations =
        listOf(
            TripLocation(38.7, -9.1, "Lisbon", "PT"),
            TripLocation(48.8, 2.3, "Paris"),
            TripLocation(0.0, -50.0, "Unavailable"),
        )

    val trips =
        listOf(
            trip(
                "partial",
                "2026-10-25T12:00:00Z",
                destinations = destinations,
            )
        )

    val resolver = CountryCodeResolver { location ->
      when (location.name) {
        "Paris" -> "FR"
        "Unavailable" -> throw IOException("offline")
        else -> location.countryCode
      }
    }

    val repository =
        VisitedCountryRepository(
            FakeTripRepository(trips = trips),
            resolver,
            clock,
        )

    assertEquals(
        mapOf("PT" to 1, "FR" to 1),
        repository.visitCounts(),
    )
  }

  @Test
  fun repeatedTripIdIsNotCountedTwice() = runTest {
    val sameTrip = trip("duplicate", "2026-10-25T12:00:00Z", "PT")

    val repository =
        VisitedCountryRepository(
            FakeTripRepository(trips = listOf(sameTrip, sameTrip)),
            storedResolver(),
            clock,
        )

    assertEquals(mapOf("PT" to 1), repository.visitCounts())
  }

  @Test
  fun noTripsReturnEmptyMapWithoutResolving() = runTest {
    val resolver = CountryCodeResolver { _ -> error("No trip should be resolved") }

    val repository =
        VisitedCountryRepository(
            FakeTripRepository(),
            resolver,
            clock,
        )

    assertEquals(emptyMap<String, Int>(), repository.visitCounts())
  }

  @Test
  fun cancellationIsPropagated() = runTest {
    val trips = listOf(trip("past", "2026-10-25T12:00:00Z", "PT"))

    val resolver = CountryCodeResolver { throw CancellationException("cancelled") }

    val repository =
        VisitedCountryRepository(
            FakeTripRepository(trips = trips),
            resolver,
            clock,
        )

    try {
      repository.visitCounts()
      fail("Expected cancellation")
    } catch (_: CancellationException) {
      // A canceled screen must not continue resolving countries.
    }
  }

  @Test
  fun tripRepositoryFailureIsPropagatedInsteadOfReturningMisleadingEmptyMap() = runTest {
    val trips = FakeTripRepository(failure = IOException("Firestore unavailable"))

    val repository =
        VisitedCountryRepository(
            trips,
            storedResolver(),
            clock,
        )

    try {
      repository.visitCounts()
      fail("Expected repository failure")
    } catch (_: IOException) {
      // The future ViewModel can display a retryable error state.
    }
  }

  @Test
  fun duplicateCoordinatesBeyondSemaphoreLimitShareFailedLookup() = runTest {
    val destinations =
        List(7) { index -> TripLocation(38.7223, -9.1393, "Lisbon $index") } +
            TripLocation(48.8566, 2.3522, "Paris", "FR")

    var lookupCalls = 0

    val resolver = CountryCodeResolver { location ->
      if (location.countryCode != null) {
        location.countryCode
      } else {
        lookupCalls++
        if (lookupCalls == 1) throw IOException("offline")
        "PT"
      }
    }

    val repository =
        VisitedCountryRepository(
            FakeTripRepository(
                trips =
                    listOf(
                        trip(
                            "multi-city",
                            "2026-10-25T12:00:00Z",
                            destinations = destinations,
                        )
                    )
            ),
            resolver,
            clock,
        )

    // The failed lookup is shared across all seven destinations.
    // The known country remains visible.
    assertEquals(mapOf("FR" to 1), repository.visitCounts())
    assertEquals(1, lookupCalls)

    // A new refresh can retry the failed resolution.
    assertEquals(mapOf("FR" to 1, "PT" to 1), repository.visitCounts())
    assertEquals(2, lookupCalls)
  }

  private fun storedResolver() = CountryCodeResolver { it.countryCode }

  private fun trip(
      id: String,
      end: String,
      vararg countryCodes: String,
      status: TripStatus = TripStatus.PLANNED,
      destinations: List<TripLocation> = countryCodes.mapIndexed { index, code ->
        TripLocation(
            38.0 + index,
            -9.0,
            "City $index",
            code,
        )
      },
  ): Trip {
    val instant = Instant.parse(end)

    return Trip(
        uid = id,
        name = id,
        description = null,
        destinations = destinations,
        startDate = timestamp(instant.minusSeconds(86_400)),
        endDate = timestamp(instant),
        flexibleDates = false,
        travelMode = TravelMode.GROUP,
        travelerCount = 2,
        ownerId = "owner",
        memberIds = listOf("owner"),
        status = status,
        createdAt = timestamp(instant.minusSeconds(172_800)),
    )
  }

  private fun timestamp(instant: Instant) = Timestamp(instant.epochSecond, instant.nano)
}
