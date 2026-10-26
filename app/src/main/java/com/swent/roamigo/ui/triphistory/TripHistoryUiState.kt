// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.triphistory

data class TripHistoryItemUiModel(
    val id: String,
    val name: String,
    val destinations: List<String>,
    val dateRange: String,
    val travelerCount: Int,
)

data class TripHistoryUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val trips: List<TripHistoryItemUiModel> = emptyList(),
)
