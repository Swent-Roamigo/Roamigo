// Implemented with assistance from OpenAI Codex.
package com.swent.roamigo.ui.trip.details

import java.time.LocalDate

/** Repository-backed details; unavailable feature summaries are deliberately omitted. */
data class TripDetailsUiState(
    val isLoading: Boolean = true,
    val error: TripDetailsError? = null,
    val details: TripDetails? = null,
)

data class TripDetails(
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val travelerCount: Int,
    val memberCount: Int,
    val suggestedPlaceCount: Int,
)

enum class TripDetailsError {
  NOT_FOUND,
  LOAD_FAILED,
}
