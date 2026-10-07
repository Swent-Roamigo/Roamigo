// Initial model and data backend structure designed with assistance from ChatGPT.
package com.android.sample.model.trip.inviting

import com.google.firebase.Timestamp

data class TripInvitation(
    val uid: String,
    val tripId: String,
    val inviterUserId: String,
    val inviteeUserId: String?, // For direct in-app invitations
    val token: String?, // For link invitations
    val status: InvitationStatus,
    val creationTime: Timestamp,
    val expirationTime: Timestamp?,
)

// Placeholder
enum class InvitationStatus {
  PENDING,
  ACCEPTED,
  DECLINED,
  EXPIRED,
}
