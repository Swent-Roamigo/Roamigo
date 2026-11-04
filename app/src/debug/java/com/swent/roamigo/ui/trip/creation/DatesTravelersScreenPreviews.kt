// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.creation

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.swent.roamigo.ui.theme.RoamigoTheme
import java.time.LocalDate

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun DatesTravelersScreenPreview() {
  RoamigoTheme {
    DatesTravelersScreen(
        uiState =
            CreateTripUiState(
                startDate = LocalDate.of(2026, 10, 12),
                endDate = LocalDate.of(2026, 10, 19),
            ),
        onBack = {},
        onClose = {},
        onCreateTrip = {},
        onPreviousMonth = {},
        onNextMonth = {},
        onSelectDate = {},
        onFlexibleDatesChange = {},
        onTravelModeChange = {},
        onDecrementTravelers = {},
        onIncrementTravelers = {},
    )
  }
}
