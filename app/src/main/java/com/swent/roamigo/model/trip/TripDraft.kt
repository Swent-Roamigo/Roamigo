// AI assistance was used for implementation support and code review.
package com.swent.roamigo.model.trip

import com.swent.roamigo.model.MAX_LATITUDE
import com.swent.roamigo.model.MAX_LONGITUDE
import com.swent.roamigo.model.MIN_LATITUDE
import com.swent.roamigo.model.MIN_LONGITUDE
import com.swent.roamigo.model.TripLocation
import java.time.Instant

/**
 * Details chosen by the user when creating a trip, before it is persisted.
 *
 * A draft holds only what the user decides. The trip ID, owner, members, status, and creation time
 * are assigned by the repository when the trip is created. Dates are represented as
 * platform-neutral instants; repositories handle conversion to their storage format.
 *
 * @property name the name of the trip.
 * @property destinations the destinations of the trip, in visiting order.
 * @property startDate the start of the first day in the timezone chosen by the caller.
 * @property endDate the start of the last day in the timezone chosen by the caller.
 * @property flexibleDates whether the group is open to shifting the dates.
 * @property travelMode whether the trip is solo or for a group.
 * @property travelerCount the number of people expected on the trip.
 */
data class TripDraft(
    val name: String,
    val destinations: List<TripLocation>,
    val startDate: Instant,
    val endDate: Instant,
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
    if (destinations.size > MAX_DESTINATIONS) add(TripDraftError.TooManyDestinations)
    // Each problem is reported once, however many destinations share it.
    if (destinations.any { it.name.isBlank() }) add(TripDraftError.BlankDestinationName)
    if (destinations.any { !it.hasValidCoordinates() }) {
      add(TripDraftError.InvalidDestinationCoordinates)
    }
    if (name.isBlank()) add(TripDraftError.BlankName)
    if (endDate < startDate) add(TripDraftError.EndBeforeStart)
    if (travelerCount !in travelMode.allowedTravelerCounts) {
      add(TripDraftError.InvalidTravelerCount)
    }
  }
}

private fun TripLocation.hasValidCoordinates(): Boolean =
    latitude in MIN_LATITUDE..MAX_LATITUDE && longitude in MIN_LONGITUDE..MAX_LONGITUDE

/** A reason why a [TripDraft] cannot be turned into a trip. */
sealed interface TripDraftError {
  /** The draft has no destination. */
  data object NoDestinations : TripDraftError

  /** The draft has more than [MAX_DESTINATIONS] destinations. */
  data object TooManyDestinations : TripDraftError

  /** A destination name is empty or contains only whitespace. */
  data object BlankDestinationName : TripDraftError

  /** A destination latitude or longitude is outside its valid range, or is not a number. */
  data object InvalidDestinationCoordinates : TripDraftError

  /** The trip name is empty or contains only whitespace. */
  data object BlankName : TripDraftError

  /** The end date is before the start date. */
  data object EndBeforeStart : TripDraftError

  /** The traveler count is outside [TravelMode.allowedTravelerCounts] for the trip's mode. */
  data object InvalidTravelerCount : TripDraftError
}
