// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.map

import com.google.firebase.Timestamp
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.trip.TravelMode
import com.swent.roamigo.model.trip.Trip
import com.swent.roamigo.model.trip.TripStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class TripMarkerUiStateTest {

  @Test
  fun toTripMarkerUiState_convertsLocationCorrectly() {
    val location = TripLocation(latitude = 38.7223, longitude = -9.1393, name = "Lisbon")
    val trip =
        Trip(
            uid = "trip_789",
            name = "Portugal Tour",
            description = "Exploring Portugal",
            destinations = listOf(location),
            startDate = Timestamp.now(),
            endDate = Timestamp.now(),
            flexibleDates = false,
            travelMode = TravelMode.SOLO,
            travelerCount = 1,
            ownerId = "user_1",
            memberIds = listOf("user_1"),
            status = TripStatus.PLANNED,
            createdAt = Timestamp.now(),
        )

    val marker = location.toTripMarkerUiState(trip)

    assertEquals("trip_789_Lisbon_38.7223_-9.1393", marker.id)
    assertEquals("trip_789", marker.tripId)
    assertEquals("Portugal Tour", marker.tripTitle)
    assertEquals("Lisbon", marker.locationName)
    assertEquals(38.7223, marker.latitude, 0.0001)
    assertEquals(-9.1393, marker.longitude, 0.0001)
    assertEquals("Lisbon", marker.snippet)
  }

  @Test
  fun toTripMarkerUiStates_convertsAllTripDestinations() {
    val loc1 = TripLocation(latitude = 41.3851, longitude = 2.1734, name = "Barcelona")
    val loc2 = TripLocation(latitude = 40.4168, longitude = -3.7038, name = "Madrid")
    val trip =
        Trip(
            uid = "trip_spain",
            name = "Spain Vacation",
            description = null,
            destinations = listOf(loc1, loc2),
            startDate = Timestamp.now(),
            endDate = Timestamp.now(),
            flexibleDates = false,
            travelMode = TravelMode.GROUP,
            travelerCount = 2,
            ownerId = "user_1",
            memberIds = listOf("user_1"),
            status = TripStatus.ACTIVE,
            createdAt = Timestamp.now(),
        )

    val markers: List<TripMarkerUiState> = trip.toTripMarkerUiStates()

    assertEquals(2, markers.size)
    assertEquals("Barcelona", markers[0].locationName)
    assertEquals("Madrid", markers[1].locationName)
  }
}
