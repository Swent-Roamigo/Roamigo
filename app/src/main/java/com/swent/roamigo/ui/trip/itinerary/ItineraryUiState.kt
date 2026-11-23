// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.itinerary

import java.time.LocalDate
import java.time.LocalTime

/** What the Plan tab shows: the trip days and the accepted activities of the selected one. */
sealed interface ItineraryUiState {
  data object Loading : ItineraryUiState

  /** The activities could not be loaded and nothing is available to show. */
  data object Error : ItineraryUiState

  /**
   * @property days every day offered as a chip, in calendar order.
   * @property selectedDay the day whose activities are listed, or `null` when [days] is empty.
   * @property activities the activities of [selectedDay], ordered by start time.
   * @property hasActivities whether the trip has at least one activity on any day.
   * @property refreshFailed whether live updates stopped; the last known plan is still shown.
   */
  data class Ready(
      val days: List<LocalDate>,
      val selectedDay: LocalDate?,
      val activities: List<ItineraryActivity>,
      val hasActivities: Boolean,
      val refreshFailed: Boolean = false,
  ) : ItineraryUiState
}

/** One row of the plan. */
data class ItineraryActivity(
    val id: String,
    val name: String,
    val locationName: String,
    val startTime: LocalTime,
)
