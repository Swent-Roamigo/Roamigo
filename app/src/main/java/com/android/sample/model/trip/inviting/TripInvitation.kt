package com.android.sample.model.trip.inviting

import java.security.Timestamp

data class TripInvitation(
    val uid: String,
    val tripId: String,
    val inviterUserId: String,
    val inviteeUserId: String?, //For direct in-app invitations
    val token: String?, //For link invitations
    val status: InvitationStatus,
    val creationTime: Timestamp,
    val expirationTime: Timestamp?,
)

//Placeholder
enum class InvitationStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    EXPIRED,
}