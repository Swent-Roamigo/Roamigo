// Implemented with assistance from OpenAI Codex.
package com.swent.roamigo.ui.trip.details

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.swent.roamigo.ui.theme.RoamigoTheme
import java.time.LocalDate

@Preview(name = "Trip details - Ready", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun TripDetailsReadyPreview() {
  TripDetailsPreview(
      TripDetailsUiState(
          isLoading = false,
          details =
              TripDetails(
                  name = "Lisbon & Porto",
                  startDate = LocalDate.of(2026, 10, 12),
                  endDate = LocalDate.of(2026, 10, 19),
                  travelerCount = 3,
                  memberCount = 3,
                  suggestedPlaceCount = 14,
              ),
      )
  )
}

@Preview(name = "Trip details - Loading", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun TripDetailsLoadingPreview() = TripDetailsPreview(TripDetailsUiState())

@Preview(name = "Trip details - Error", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun TripDetailsErrorPreview() =
    TripDetailsPreview(TripDetailsUiState(isLoading = false, error = TripDetailsError.LOAD_FAILED))

@Preview(name = "Trip details - Unavailable", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun TripDetailsUnavailablePreview() =
    TripDetailsPreview(TripDetailsUiState(isLoading = false, error = TripDetailsError.NOT_FOUND))

@Composable
private fun TripDetailsPreview(state: TripDetailsUiState) {
  RoamigoTheme { TripDetailsScreen(state = state, onRetry = {}, onBack = {}, onTripsClick = {}) }
}
