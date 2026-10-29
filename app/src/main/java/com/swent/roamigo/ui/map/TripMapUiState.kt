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
   * @property members display information of every trip member, keyed by user ID. Until the
   *   members' profiles are loaded, or if they cannot be, each entry holds fallback information.
   */
  data class Loaded(
      val trip: Trip,
      val viewport: TripMapViewport,
      val members: Map<String, MemberDisplayInfo>,
  ) : TripMapUiState
}

/**
 * How a trip member is shown on the map.
 *
 * @property displayName name of the member, or the first character of their user ID, uppercased,
 *   when their profile is missing or has no name.
 * @property initial uppercased first character of [displayName].
 * @property avatarUrl URL of the member's profile picture, or null when they have none, in which
 *   case the UI shows the default avatar.
 */
data class MemberDisplayInfo(
    val displayName: String,
    val initial: String,
    val avatarUrl: String?,
)
