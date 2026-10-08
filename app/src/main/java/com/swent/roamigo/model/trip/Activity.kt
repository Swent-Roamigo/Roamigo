// Initial model and data backend structure designed with assistance from ChatGPT.
package com.swent.roamigo.model.trip

import com.google.firebase.Timestamp
import com.swent.roamigo.model.TripLocation

data class Activity(
    val uid: String,
    val tripId: String,
    val activityType: ActivityType,
    val name: String,
    val description: String?,
    val location: TripLocation,
    val startTime: Timestamp,
    val endTime: Timestamp?,
    val createdByUserId: String,
    val creationTime: Timestamp,
)

enum class ActivityType {
  SUGGESTION,
  ACCEPTED, // maybe a third for when an owner directly creates/approves without going through a
  // suggestion?
}
