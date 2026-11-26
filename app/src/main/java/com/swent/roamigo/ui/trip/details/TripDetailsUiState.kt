// Implemented with assistance from OpenAI Codex.
package com.swent.roamigo.ui.trip.details

import com.swent.roamigo.model.TripLocation
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
    /** The first destination, where the weather is looked up. */
    val destination: TripLocation? = null,
)

enum class TripDetailsError {
  NOT_FOUND,
  LOAD_FAILED,
}
