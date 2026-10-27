package com.swent.roamigo.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.google.firebase.Timestamp
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.trip.TravelMode
import com.swent.roamigo.model.trip.Trip
import com.swent.roamigo.model.trip.TripStatus
import com.swent.roamigo.ui.theme.RoamigoTheme

class TripOverviewMapScreenPreviews {
  @Preview(showBackground = true, widthDp = 390, heightDp = 844)
  @Composable
  private fun TripOverviewMapScreenPreview() {
    val sampleTrip =
        Trip(
            uid = "trip_1",
            name = "Euro trip",
            description = "Exploring Europe",
            destinations =
                listOf(
                    TripLocation(latitude = 41.3851, longitude = 2.1734, name = "Barcelona"),
                    TripLocation(latitude = 51.5074, longitude = -0.1278, name = "London"),
                ),
            startDate = Timestamp.now(),
            endDate = Timestamp.now(),
            flexibleDates = false,
            travelMode = TravelMode.GROUP,
            travelerCount = 3,
            ownerId = "user_1",
            memberIds = listOf("user_1"),
            status = TripStatus.PLANNED,
            createdAt = Timestamp.now(),
        )
    val sampleTrip2 =
        Trip(
            uid = "trip_paris_tokyo_ny",
            name = "Paris and Tokyo and New York",
            description = null,
            destinations =
                listOf(
                    TripLocation(latitude = 48.8566, longitude = 2.3522, name = "Paris"),
                    TripLocation(latitude = 35.6762, longitude = 139.6503, name = "Tokyo"),
                    TripLocation(latitude = 41.0, longitude = -71.6, name = "New York"),
                ),
            startDate = Timestamp.now(),
            endDate = Timestamp.now(),
            flexibleDates = false,
            travelMode = TravelMode.GROUP,
            travelerCount = 3,
            ownerId = "user_1",
            memberIds = listOf("user_1"),
            status = TripStatus.PLANNED,
            createdAt = Timestamp.now(),
        )
    RoamigoTheme {
      TripOverviewMapScreen(
          uiState =
              TripOverviewMapUiState(
                  markers = listOf(sampleTrip, sampleTrip2).flatMap { it.toTripMarkerUiStates() }
              ),
          onRetry = {},
      )
    }
  }
}
