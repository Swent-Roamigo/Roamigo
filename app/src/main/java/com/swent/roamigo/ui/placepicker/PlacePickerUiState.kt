// Implemented with assistance from OpenAI Codex.
package com.swent.roamigo.ui.placepicker

import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.place.PlaceCandidate
import com.swent.roamigo.ui.map.MapCoordinate

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
    val isMapMode: Boolean = false,
    val manualDraft: PlacePickerManualDraft = PlacePickerManualDraft(),
    val mapInitialCenter: MapCoordinate? = null,
    val mapError: Boolean = false,
    val mapLoadAttempt: Int = 0,
) {
  val canConfirm: Boolean
    get() =
        !finished &&
            if (isMapMode) manualDraft.location != null
            else
                selectedLocation != null &&
                    searchStatus != PlaceSearchStatus.LOADING &&
                    resolvingCandidateId == null
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

/** An unconfirmed map draft, kept separate from the search selection. */
data class PlacePickerManualDraft(
    val point: MapCoordinate? = null,
    val name: String = "",
    val countryCode: String? = null,
) {
  val location: TripLocation?
    get() =
        point
            ?.takeIf { validCoordinates(it.latitude, it.longitude) && name.isNotBlank() }
            ?.let { TripLocation(it.latitude, it.longitude, name.trim(), countryCode) }
}
