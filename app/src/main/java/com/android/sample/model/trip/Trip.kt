// Initial model and data backend structure designed with assistance from ChatGPT.
package com.android.sample.model.trip

import com.android.sample.model.TripLocation
import com.google.firebase.Timestamp

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
