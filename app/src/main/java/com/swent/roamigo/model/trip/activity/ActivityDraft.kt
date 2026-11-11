// AI assistance was used for implementation support.
package com.swent.roamigo.model.trip.activity

import com.swent.roamigo.model.MAX_LATITUDE
import com.swent.roamigo.model.MAX_LONGITUDE
import com.swent.roamigo.model.MIN_LATITUDE
import com.swent.roamigo.model.MIN_LONGITUDE
import com.swent.roamigo.model.TripLocation
import java.time.Instant

// firestore.rules cannot import these values, so its activity creation rule repeats them.

/** The largest number of characters in an activity name. */
const val MAX_ACTIVITY_NAME_LENGTH = 100

/** The largest number of characters in an activity description. */
const val MAX_ACTIVITY_DESCRIPTION_LENGTH = 2000

/**
 * Details chosen by the user when adding an activity to a trip, before it is persisted.
 *
 * A draft holds only what the user decides. The activity ID, trip, type, author, and creation time
 * are assigned by the repository when the activity is created.
 *
 * @property name the name of the activity.
 * @property description an optional description of the activity.
 * @property location where the activity takes place.
 * @property startTime when the activity starts.
 * @property endTime when the activity ends, or `null` when it has no planned end.
 */
data class ActivityDraft(
    val name: String,
    val description: String?,
    val location: TripLocation,
    val startTime: Instant,
    val endTime: Instant?,
) {

  /**
   * Checks whether this draft can be turned into an activity.
   *
   * Every rule is checked, so several errors can be returned at once.
   *
   * @return the problems found in this draft, or an empty list when it is valid.
   */
  fun validate(): List<ActivityDraftError> = buildList {
    if (name.isBlank()) add(ActivityDraftError.BlankName)
    if (name.length > MAX_ACTIVITY_NAME_LENGTH) add(ActivityDraftError.NameTooLong)
    if (description != null && description.length > MAX_ACTIVITY_DESCRIPTION_LENGTH) {
      add(ActivityDraftError.DescriptionTooLong)
    }
    if (location.name.isBlank()) add(ActivityDraftError.BlankLocationName)
    if (!location.hasValidCoordinates()) add(ActivityDraftError.InvalidLocationCoordinates)
    if (endTime != null && endTime < startTime) add(ActivityDraftError.EndBeforeStart)
  }
}

// Coordinates can be NaN, which no range check accepts.
private fun TripLocation.hasValidCoordinates(): Boolean =
    latitude in MIN_LATITUDE..MAX_LATITUDE && longitude in MIN_LONGITUDE..MAX_LONGITUDE

/** A reason why an [ActivityDraft] cannot be turned into an activity. */
sealed interface ActivityDraftError {
  /** The activity name is empty or contains only whitespace. */
  data object BlankName : ActivityDraftError

  /** The activity name has more than [MAX_ACTIVITY_NAME_LENGTH] characters. */
  data object NameTooLong : ActivityDraftError

  /** The description has more than [MAX_ACTIVITY_DESCRIPTION_LENGTH] characters. */
  data object DescriptionTooLong : ActivityDraftError

  /** The location name is empty or contains only whitespace. */
  data object BlankLocationName : ActivityDraftError

  /** The location latitude or longitude is outside its valid range, or is not a number. */
  data object InvalidLocationCoordinates : ActivityDraftError

  /** The end time is before the start time. */
  data object EndBeforeStart : ActivityDraftError
}
