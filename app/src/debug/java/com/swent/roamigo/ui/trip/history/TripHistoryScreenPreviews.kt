// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.history

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.swent.roamigo.ui.theme.RoamigoTheme
import java.time.LocalDate

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun TripHistoryPreview() {
  RoamigoTheme {
    TripHistoryScreen(
        uiState =
            TripHistoryUiState.Success(
                trips =
                    listOf(
                        TripHistoryItemUiModel(
                            id = "rome",
                            name = "Summer in Italy",
                            destinations = listOf("Rome", "Florence"),
                            startDate = LocalDate.of(2016, 6, 5),
                            endDate = LocalDate.of(2016, 6, 12),
                            travelerCount = 3,
                        ),
                    ),
            ),
        onOpenTrip = {},
    )
  }
}
