// Initial model and data backend structure designed with assistance from ChatGPT.
package com.android.sample.model.trip

import com.android.sample.model.Location
import com.google.firebase.Timestamp

data class Trip(
    val uid: String,
    val name: String,
    val description: String?,
    val destination: Location?,
    val startDate: Timestamp,
    val endDate: Timestamp,
    val ownerId: String,
    // val memberIds: List<String>, //not suitable for easily viewing all members at once, should
    // probably display their permission etc.
    val status: TripStatus,
)

// Placeholder
enum class TripStatus {
  PLANNED,
  ACTIVE,
  COMPLETED,
}
