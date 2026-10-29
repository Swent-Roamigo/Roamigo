// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.creation

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.google.firebase.Timestamp
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.trip.TravelMode
import com.swent.roamigo.model.trip.Trip
import com.swent.roamigo.model.trip.TripStatus
import com.swent.roamigo.ui.theme.RoamigoTheme
import java.time.Instant
import java.time.ZoneId

@Preview(name = "Trip ready", showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Trip ready landscape", showBackground = true, widthDp = 800, heightDp = 360)
@Composable
private fun TripReadyPreview() {
  val trip =
      Trip(
          uid = "preview",
          name = "Lisbon → Porto",
          description = null,
          destinations =
              listOf(TripLocation(38.72, -9.14, "Lisbon"), TripLocation(41.16, -8.63, "Porto")),
          startDate = Timestamp(1791763200, 0),
          endDate = Timestamp(1791763200, 0),
          flexibleDates = false,
          travelMode = TravelMode.GROUP,
          travelerCount = 3,
          ownerId = "preview",
          memberIds = listOf("preview"),
          status = TripStatus.PLANNED,
          createdAt = Timestamp(1791763200, 0),
      )
  RoamigoTheme {
    TripReadyContent(
        state =
            TripReadyState.Ready(
                trip,
                Instant.ofEpochSecond(1791763200).atZone(ZoneId.systemDefault()).toLocalDate(),
                Instant.ofEpochSecond(1791763200).atZone(ZoneId.systemDefault()).toLocalDate(),
                1,
            ),
        photo = null,
        onRetry = {},
        onInviteClick = {},
        onSuggestClick = {},
        onShareLocationClick = {},
        onOpenItineraryClick = {},
        onShareTripClick = {},
    )
  }
}
