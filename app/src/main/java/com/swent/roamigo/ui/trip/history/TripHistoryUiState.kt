// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.history

import java.time.LocalDate

data class TripHistoryItemUiModel(
    val id: String,
    val name: String,
    val destinations: List<String>,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val travelerCount: Int,
)

sealed interface TripHistoryUiState {
  data object Loading : TripHistoryUiState

  data class Success(val trips: List<TripHistoryItemUiModel>) : TripHistoryUiState

  data class Error(val message: String) : TripHistoryUiState
}
