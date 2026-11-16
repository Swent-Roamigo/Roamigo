// Implemented with assistance from OpenAI Codex.
package com.swent.roamigo.ui.placepicker

import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.place.PlaceCandidate

/** Search and resolution have independent feedback; failures never discard a valid selection. */
data class PlacePickerUiState(
    val query: String = "",
    val searchStatus: PlaceSearchStatus = PlaceSearchStatus.INITIAL,
    val candidates: List<PlaceCandidate> = emptyList(),
    val resolvingCandidateId: String? = null,
    val searchError: PlacePickerError? = null,
    val resolutionError: PlacePickerError? = null,
    val selectedLocation: TripLocation? = null,
    val attribution: String? = null,
    val finished: Boolean = false,
) {
  val canConfirm: Boolean
    get() =
        selectedLocation != null &&
            searchStatus != PlaceSearchStatus.LOADING &&
            resolvingCandidateId == null &&
            !finished
}

enum class PlaceSearchStatus {
  INITIAL,
  LOADING,
  EMPTY,
  RESULTS,
  ERROR,
}

enum class PlacePickerError {
  CONFIGURATION,
  INVALID_TOKEN,
  QUOTA,
  INVALID_PLACE,
  UNAVAILABLE,
}
