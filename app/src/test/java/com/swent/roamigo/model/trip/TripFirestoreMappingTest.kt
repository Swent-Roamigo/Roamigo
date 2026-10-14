// AI assistance was used for implementation support and code review.
package com.swent.roamigo.model.trip

import com.google.firebase.Timestamp
import com.swent.roamigo.model.TripLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TripFirestoreMappingTest {

  private val destinations =
      listOf(
          TripLocation(38.7223, -9.1393, "Lisbon"),
          TripLocation(41.1579, -8.6291, "Porto"),
          TripLocation(41.3874, 2.1686, "Barcelona"),
      )

  private val trip =
      Trip(
          uid = "trip-1",
          name = "Iberian tour",
          description = "Three cities",
          destinations = destinations,
          startDate = Timestamp(1000, 0),
          endDate = Timestamp(2000, 0),
          flexibleDates = true,
          travelMode = TravelMode.GROUP,
          travelerCount = 3,
          ownerId = "owner",
          memberIds = listOf("owner", "friend"),
          status = TripStatus.PLANNED,
          createdAt = Timestamp(500, 0),
      )

  private val member = TripMember("owner", TripMemberRole.OWNER, Timestamp(500, 0))

  @Test
  fun tripSurvivesRoundTrip() {
    assertEquals(trip, tripFromFirestore(trip.uid, trip.toFirestoreData()))
  }

  @Test
  fun tripWithoutDescriptionSurvivesRoundTrip() {
    val withoutDescription = trip.copy(description = null)

    assertEquals(
        withoutDescription,
        tripFromFirestore(trip.uid, withoutDescription.toFirestoreData()),
    )
  }

  @Test
  fun tripIdComesFromDocumentAndIsNotStored() {
    assertEquals(false, trip.toFirestoreData().containsKey("uid"))
    assertEquals("other-id", tripFromFirestore("other-id", trip.toFirestoreData()).uid)
  }

  @Test
  fun tripStoresEnumsByName() {
    val data = trip.toFirestoreData()

    assertEquals("GROUP", data["travelMode"])
    assertEquals("PLANNED", data["status"])
  }

  @Test
  fun destinationsKeepTheirOrder() {
    val data = trip.toFirestoreData()

    assertEquals(
        listOf("Lisbon", "Porto", "Barcelona"),
        (data["destinations"] as List<*>).map { (it as Map<*, *>)["name"] },
    )
    assertEquals(destinations, tripFromFirestore(trip.uid, data).destinations)
  }

  @Test
  fun numbersAsReturnedByFirestoreAreAccepted() {
    val data =
        trip.toFirestoreData() +
            mapOf(
                "travelerCount" to 3L,
                "destinations" to
                    listOf(mapOf("name" to "Null Island", "latitude" to 0L, "longitude" to 0L)),
            )

    val result = tripFromFirestore(trip.uid, data)

    assertEquals(3, result.travelerCount)
    assertEquals(listOf(TripLocation(0.0, 0.0, "Null Island")), result.destinations)
  }

  @Test
  fun tripMemberSurvivesRoundTrip() {
    val data = member.toFirestoreData()

    assertEquals("OWNER", data["role"])
    assertEquals(member, tripMemberFromFirestore(data))
  }

  @Test
  fun tripWithMissingFieldIsRejected() {
    listOf(
            "name",
            "destinations",
            "startDate",
            "travelMode",
            "travelerCount",
            "ownerId",
            "createdAt",
        )
        .forEach { field ->
          assertThrows(field, IllegalArgumentException::class.java) {
            tripFromFirestore(trip.uid, trip.toFirestoreData() - field)
          }
        }
  }

  @Test
  fun tripWithIllTypedFieldIsRejected() {
    mapOf(
            "name" to 42,
            "description" to 42,
            "startDate" to "yesterday",
            "flexibleDates" to "yes",
            "travelMode" to "CAR",
            "travelerCount" to 2.5,
            "memberIds" to listOf("owner", 42),
            "status" to "planned",
            "destinations" to listOf("Lisbon"),
        )
        .forEach { (field, value) ->
          assertThrows(field, IllegalArgumentException::class.java) {
            tripFromFirestore(trip.uid, trip.toFirestoreData() + (field to value))
          }
        }
  }

  @Test
  fun tripMemberWithMissingOrIllTypedFieldIsRejected() {
    val data = member.toFirestoreData()

    assertThrows(IllegalArgumentException::class.java) { tripMemberFromFirestore(data - "userId") }
    assertThrows(IllegalArgumentException::class.java) {
      tripMemberFromFirestore(data + ("role" to "ADMIN"))
    }
    assertThrows(IllegalArgumentException::class.java) {
      tripMemberFromFirestore(data + ("joinTime" to 500L))
    }
  }

  @Test
  fun activityIsReadFromFirestoreData() {
    val activity =
        Activity(
            uid = "activity-1",
            tripId = trip.uid,
            activityType = ActivityType.ACCEPTED,
            name = "Tram 28",
            description = null,
            location = destinations.first(),
            startTime = Timestamp(1200, 0),
            endTime = null,
            createdByUserId = "owner",
            creationTime = Timestamp(900, 0),
        )

    val data =
        mapOf(
            "activityType" to "ACCEPTED",
            "name" to "Tram 28",
            "description" to null,
            "location" to mapOf("name" to "Lisbon", "latitude" to 38.7223, "longitude" to -9.1393),
            "startTime" to Timestamp(1200, 0),
            "endTime" to null,
            "createdByUserId" to "owner",
            "creationTime" to Timestamp(900, 0),
        )

    assertEquals(activity, activityFromFirestore(activity.uid, activity.tripId, data))
  }
}
