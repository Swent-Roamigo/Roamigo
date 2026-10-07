// Initial model and data backend structure designed with assistance from ChatGPT.
package com.android.sample.model.trip

import com.google.firebase.Timestamp

// Each member is a separate Firestore document in a subcollection:
// trips/{tripId}
// trips/{tripId}/members/{userId}
data class TripMember(
    val userId: String,
    val role: TripMemberRole,
    val joinTime: Timestamp,
)

enum class TripMemberRole {
  OWNER, // Trip ownerId is authoritative over this tag
  EDITOR,
  VIEWER,
}
