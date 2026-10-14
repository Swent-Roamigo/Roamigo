// AI assistance was used for implementation support and code review.

package com.swent.roamigo.model.trip

import com.google.firebase.Timestamp
import com.swent.roamigo.model.TripLocation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
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
          flexibleDates = false,
          travelMode = TravelMode.SOLO,
          travelerCount = 1,
          ownerId = "user-1",
          memberIds = listOf("user-1"),
          status = TripStatus.PLANNED,
          createdAt = Timestamp(500, 0),
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

  private val draft =
      TripDraft(
          name = "Iberian tour",
          destinations =
              listOf(
                  TripLocation(38.7223, -9.1393, "Lisbon"),
                  TripLocation(41.1579, -8.6291, "Porto"),
                  TripLocation(41.3874, 2.1686, "Barcelona"),
              ),
          startDate = Timestamp(1000, 0),
          endDate = Timestamp(2000, 0),
          flexibleDates = true,
          travelMode = TravelMode.GROUP,
          travelerCount = 3,
      )

  @Test
  fun createTripStoresTripOwnedByCurrentUser() = runBlocking {
    val repository = FakeTripRepository(trips = listOf(trip), currentUserId = "owner")

    val created = repository.createTrip("new-trip", draft)

    assertEquals("new-trip", created.uid)
    assertEquals(draft.name, created.name)
    assertEquals(draft.destinations, created.destinations)
    assertEquals(draft.startDate, created.startDate)
    assertEquals(draft.endDate, created.endDate)
    assertEquals(draft.flexibleDates, created.flexibleDates)
    assertEquals(draft.travelMode, created.travelMode)
    assertEquals(draft.travelerCount, created.travelerCount)
    assertEquals("owner", created.ownerId)
    assertEquals(listOf("owner"), created.memberIds)
    assertEquals(TripStatus.PLANNED, created.status)
    assertEquals(listOf(trip, created), repository.getTrips())
  }

  @Test
  fun createTripAddsSingleOwnerMember() = runBlocking {
    val repository = FakeTripRepository(currentUserId = "owner")

    val created = repository.createTrip("new-trip", draft)

    val members = repository.getMembersForTrip("new-trip")
    assertEquals(1, members.size)
    assertEquals("owner", members.single().userId)
    assertEquals(TripMemberRole.OWNER, members.single().role)
    assertEquals(created.createdAt, members.single().joinTime)
  }

  @Test
  fun createTripTwiceWithSameIdReturnsExistingTrip() = runBlocking {
    val repository = FakeTripRepository(currentUserId = "owner")

    val first = repository.createTrip("new-trip", draft)
    val second = repository.createTrip("new-trip", draft)

    assertSame(first, second)
    assertEquals(listOf(first), repository.getTrips())
    assertEquals(1, repository.getMembersForTrip("new-trip").size)
  }

  @Test
  fun createTripWithIdOwnedByAnotherUserIsDenied() = runBlocking {
    val repository = FakeTripRepository(trips = listOf(trip), currentUserId = "intruder")

    assertThrows(TripCreationException.PermissionDenied::class.java) {
      runBlocking { repository.createTrip(trip.uid, draft) }
    }
    assertEquals(listOf(trip), repository.getTrips())
    assertTrue(repository.getMembersForTrip(trip.uid).isEmpty())
  }

  @Test
  fun createTripWithInvalidDraftStoresNothing() = runBlocking {
    val repository = FakeTripRepository(currentUserId = "owner")
    val invalid = draft.copy(name = " ", destinations = emptyList())

    val thrown =
        assertThrows(TripCreationException.Validation::class.java) {
          runBlocking { repository.createTrip("new-trip", invalid) }
        }

    assertEquals(
        listOf(TripDraftError.NoDestinations, TripDraftError.BlankName),
        thrown.errors,
    )
    assertTrue(repository.getTrips().isEmpty())
    assertTrue(repository.getMembersForTrip("new-trip").isEmpty())
  }

  @Test
  fun createTripWithoutUserIsNotAuthenticated() = runBlocking {
    val repository = FakeTripRepository(currentUserId = null)

    assertThrows(TripCreationException.NotAuthenticated::class.java) {
      runBlocking { repository.createTrip("new-trip", draft) }
    }
    assertTrue(repository.getTrips().isEmpty())
  }

  @Test
  fun createTripPropagatesConfiguredFailure() {
    val expected = IllegalStateException("Repository failure")
    val repository = FakeTripRepository(failure = expected, currentUserId = "owner")

    val thrown =
        assertThrows(IllegalStateException::class.java) {
          runBlocking { repository.createTrip("new-trip", draft) }
        }

    assertSame(expected, thrown)
  }
}
