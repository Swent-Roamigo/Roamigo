// AI assistance was used for implementation support and code review.

package com.swent.roamigo.model.trip

import com.google.firebase.Timestamp
import com.swent.roamigo.model.TripLocation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeTripRepositoryTest {

  private val location =
      TripLocation(
          latitude = 46.5197,
          longitude = 6.6323,
          name = "Lausanne",
      )

  private val trip =
      Trip(
          uid = "trip-1",
          name = "Lausanne trip",
          description = null,
          destinations = listOf(location),
          startDate = Timestamp(1000, 0),
          endDate = Timestamp(2000, 0),
          ownerId = "user-1",
          status = TripStatus.PLANNED,
      )

  private val activity =
      Activity(
          uid = "activity-1",
          tripId = trip.uid,
          activityType = ActivityType.ACCEPTED,
          name = "Visit EPFL",
          description = null,
          location = location,
          startTime = Timestamp(1200, 0),
          endTime = null,
          createdByUserId = "user-1",
          creationTime = Timestamp(900, 0),
      )

  private val member =
      TripMember(
          userId = "user-1",
          role = TripMemberRole.OWNER,
          joinTime = Timestamp(800, 0),
      )

  @Test
  fun getTripsReturnsConfiguredTrips() = runBlocking {
    val repository = FakeTripRepository(trips = listOf(trip))

    val result = repository.getTrips()

    assertEquals(listOf(trip), result)
  }

  @Test
  fun getActivitiesForTripReturnsConfiguredActivities() = runBlocking {
    val repository =
        FakeTripRepository(
            activitiesByTripId = mapOf(trip.uid to listOf(activity)),
        )

    val result = repository.getActivitiesForTrip(trip.uid)

    assertEquals(listOf(activity), result)
  }

  @Test
  fun getActivitiesForUnknownTripReturnsEmptyList() = runBlocking {
    val repository = FakeTripRepository()

    val result = repository.getActivitiesForTrip("unknown-trip")

    assertTrue(result.isEmpty())
  }

  @Test
  fun getMembersForTripReturnsConfiguredMembers() = runBlocking {
    val repository =
        FakeTripRepository(
            membersByTripId = mapOf(trip.uid to listOf(member)),
        )

    val result = repository.getMembersForTrip(trip.uid)

    assertEquals(listOf(member), result)
  }

  @Test
  fun getMembersForUnknownTripReturnsEmptyList() = runBlocking {
    val repository = FakeTripRepository()

    val result = repository.getMembersForTrip("unknown-trip")

    assertTrue(result.isEmpty())
  }

  @Test
  fun configuredFailureIsPropagated() {
    val expected = IllegalStateException("Repository failure")
    val repository = FakeTripRepository(failure = expected)

    val thrown =
        try {
          runBlocking { repository.getTrips() }
          null
        } catch (exception: IllegalStateException) {
          exception
        }

    assertEquals(expected, thrown)
  }
}
