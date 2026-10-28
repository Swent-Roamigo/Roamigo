// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.map

import com.swent.roamigo.model.trip.Trip

/** State of the trip shown by the trip map. */
sealed interface TripMapUiState {
  /** The trip is being loaded. */
  data object Loading : TripMapUiState

  /**
   * The trip could not be loaded.
   *
   * @property message describes why loading failed.
   */
  data class Error(val message: String) : TripMapUiState

  /**
   * The trip was loaded.
   *
   * @property trip the trip shown on the map.
   * @property viewport camera target showing the trip's destinations.
   */
  data class Loaded(val trip: Trip, val viewport: TripMapViewport) : TripMapUiState
}
