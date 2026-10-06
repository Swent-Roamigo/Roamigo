package com.android.sample.model.trip

import java.security.Timestamp

//Each member is a separate Firestore document in a subcollection:
//trips/{tripId}
//trips/{tripId}/members/{userId}
data class TripMember(
    val userId: String,
    val role: TripMemberPermission,
    val joinTime: Timestamp,
)

enum class TripMemberPermission {
    OWNER,
    EDIT,
    VIEW,
}