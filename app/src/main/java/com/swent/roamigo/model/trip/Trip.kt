// Initial model and data backend structure designed with assistance from ChatGPT.
package com.swent.roamigo.model.trip

import com.google.firebase.Timestamp
import com.swent.roamigo.model.TripLocation

data class Trip(
    val uid: String,
    val name: String,
    val description: String?,
    val destinations: List<TripLocation>,
    val startDate: Timestamp,
    val endDate: Timestamp,
    val ownerId: String, // this is authoritative over TripMemberRole of TripMember
    val status: TripStatus,
)

// Placeholder
enum class TripStatus {
  PLANNED,
  ACTIVE,
  COMPLETED,
}
