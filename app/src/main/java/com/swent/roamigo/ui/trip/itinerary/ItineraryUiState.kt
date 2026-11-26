// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.itinerary

import java.time.LocalDate
import java.time.LocalTime

/**
 * What the Plan tab shows: the trip days, the accepted activities of the selected one, and how many
 * suggestions are waiting for votes.
 */
sealed interface ItineraryUiState {
  /** The activities are loading for the first time. */
  data object Loading : ItineraryUiState

  /** The activities could not be loaded and nothing is available to show. */
  data object Error : ItineraryUiState

  /** The trip doesn't exist or isn't visible to the user, so there is no plan to retry. */
  data object TripNotFound : ItineraryUiState

  /**
   * The plan is available, possibly with an outdated or empty day.
   *
   * @property days every day offered as a chip, in calendar order.
   * @property selectedDay the day whose activities are listed, or `null` when [days] is empty.
   * @property activities the activities of [selectedDay], ordered by start time.
   * @property hasActivities whether the trip has at least one activity on any day.
   * @property refreshFailed whether live updates of the activities stopped; the last known plan is
   *   still shown.
   * @property waitingSuggestions how many suggestions still have an open vote, or 0 while the votes
   *   are unknown: before they first load, or after their updates stopped.
   */
  data class Ready(
      val days: List<LocalDate>,
      val selectedDay: LocalDate?,
      val activities: List<ItineraryActivity>,
      val hasActivities: Boolean,
      val refreshFailed: Boolean = false,
      val waitingSuggestions: Int = 0,
  ) : ItineraryUiState
}

/**
 * One row of the plan.
 *
 * @property id the activity ID, used to identify the row.
 * @property locationName the name of the activity's place, shown under its name.
 * @property startTime the local start time in the plan's zone.
 */
data class ItineraryActivity(
    val id: String,
    val name: String,
    val locationName: String,
    val startTime: LocalTime,
)
