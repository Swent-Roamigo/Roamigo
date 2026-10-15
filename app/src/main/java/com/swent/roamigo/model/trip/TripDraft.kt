// AI assistance was used for implementation support and code review.
package com.swent.roamigo.model.trip

import com.google.firebase.Timestamp
import com.swent.roamigo.model.TripLocation

/**
 * Details chosen by the user when creating a trip, before it is persisted.
 *
 * A draft holds only what the user decides. The trip ID, owner, members, status, and creation time
 * are assigned by the repository when the trip is created.
 *
 * @property name the name of the trip.
 * @property destinations the destinations of the trip, in visiting order.
 * @property startDate the first day of the trip.
 * @property endDate the last day of the trip.
 * @property flexibleDates whether the group is open to shifting the dates.
 * @property travelMode whether the trip is solo or for a group.
 * @property travelerCount the number of people expected on the trip.
 */
data class TripDraft(
    val name: String,
    val destinations: List<TripLocation>,
    val startDate: Timestamp,
    val endDate: Timestamp,
    val flexibleDates: Boolean,
    val travelMode: TravelMode,
    val travelerCount: Int,
) {

  /**
   * Checks whether this draft can be turned into a trip.
   *
   * Every rule is checked, so several errors can be returned at once.
   *
   * @return the problems found in this draft, or an empty list when it is valid.
   */
  fun validate(): List<TripDraftError> = buildList {
    if (destinations.isEmpty()) add(TripDraftError.NoDestinations)
    if (name.isBlank()) add(TripDraftError.BlankName)
    if (endDate < startDate) add(TripDraftError.EndBeforeStart)
    if (travelerCount !in travelMode.allowedTravelerCounts) {
      add(TripDraftError.InvalidTravelerCount)
    }
  }
}

/** A reason why a [TripDraft] cannot be turned into a trip. */
sealed interface TripDraftError {
  /** The draft has no destination. */
  data object NoDestinations : TripDraftError

  /** The trip name is empty or contains only whitespace. */
  data object BlankName : TripDraftError

  /** The end date is before the start date. */
  data object EndBeforeStart : TripDraftError

  /** The traveler count is outside [TravelMode.allowedTravelerCounts] for the trip's mode. */
  data object InvalidTravelerCount : TripDraftError
}
