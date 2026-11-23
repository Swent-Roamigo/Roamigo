// Initial model and data backend structure designed with assistance from ChatGPT.
package com.swent.roamigo.model.trip

import com.google.firebase.Timestamp
import com.swent.roamigo.model.TripLocation

/**
 * An activity of a trip: either a [ActivityType.SUGGESTION] or an [ActivityType.ACCEPTED] entry of
 * the itinerary.
 *
 * @property startTime when the activity starts. Only a suggestion may have none, meaning that no
 *   time is proposed yet; an accepted activity always has one.
 * @property endTime when the activity ends, or `null` when it has no planned end. It is set only
 *   when [startTime] is, and never before it.
 */
data class Activity(
    val uid: String,
    val tripId: String,
    val activityType: ActivityType,
    val name: String,
    val description: String?,
    val location: TripLocation,
    val startTime: Timestamp?,
    val endTime: Timestamp?,
    val createdByUserId: String,
    val creationTime: Timestamp,
)

enum class ActivityType {
  SUGGESTION,
  ACCEPTED, // maybe a third for when an owner directly creates/approves without going through a
  // suggestion?
}
