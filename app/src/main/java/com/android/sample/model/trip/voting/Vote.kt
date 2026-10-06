package com.android.sample.model.trip.voting

import java.security.Timestamp

data class Vote(
    val uid: String,
    val tripId: String,
    val question: String?,
    val options: List<VoteOption>,
    val createdByUserId: String,
    val status: VoteStatus,
    val creationTime: Timestamp,
    val closingTime: Timestamp?,
    val winningOptionId: String?,
)

enum class VoteStatus {
    OPEN,
    CLOSED,
}