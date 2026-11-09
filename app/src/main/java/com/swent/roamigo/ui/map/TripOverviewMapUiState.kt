// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.map

/**
 * Represents the UI state for the trip overview map screen.
 *
 * @property markers List of [TripMarkerUiState] to be displayed on the map.
 * @property isLoading Whether trip destinations are currently loading from the repository.
 * @property errorMessage Optional error message if loading trip destinations failed.
 */
data class TripOverviewMapUiState(
    val markers: List<TripMarkerUiState> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)
