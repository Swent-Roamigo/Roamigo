// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.google.firebase.Timestamp
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.trip.TravelMode
import com.swent.roamigo.model.trip.Trip
import com.swent.roamigo.model.trip.TripStatus
import com.swent.roamigo.ui.theme.RoamigoTheme

class TripMapScreenPreviews {
  @Preview(showBackground = true, widthDp = 390, heightDp = 844)
  @Composable
  private fun TripMapLoadingPreview() {
    TripMapPreview(uiState = TripMapUiState.Loading, isSharing = false)
  }

  @Preview(showBackground = true, widthDp = 390, heightDp = 844)
  @Composable
  private fun TripMapErrorPreview() {
    TripMapPreview(
        uiState = TripMapUiState.Error(TripMapViewModel.LOAD_FAILED_MESSAGE),
        isSharing = false,
    )
  }

  @Preview(showBackground = true, widthDp = 390, heightDp = 844)
  @Composable
  private fun TripMapSharingOffPreview() {
    TripMapPreview(uiState = loadedState, isSharing = false)
  }

  @Preview(showBackground = true, widthDp = 390, heightDp = 844)
  @Composable
  private fun TripMapSharingOnPreview() {
    TripMapPreview(uiState = loadedState, isSharing = true)
  }

  @Composable
  private fun TripMapPreview(uiState: TripMapUiState, isSharing: Boolean) {
    RoamigoTheme {
      TripMapContent(
          uiState = uiState,
          isSharing = isSharing,
          onRetry = {},
          onTurnOnSharing = {},
          onPauseSharing = {},
      )
    }
  }

  private val previewTrip =
      Trip(
          uid = "trip_lisbon",
          name = "Lisbon & Porto",
          description = null,
          destinations =
              listOf(TripLocation(latitude = 38.7223, longitude = -9.1393, name = "Lisbon")),
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

  private val loadedState =
      TripMapUiState.Loaded(
          trip = previewTrip,
          viewport = TripMapViewport.Centered(latitude = 38.7223, longitude = -9.1393),
          members = mapOf("user_1" to memberDisplayInfo("user_1", null)),
      )
}
