package com.android.sample.model.trip.voting

import java.security.Timestamp

//Each ballot is a separateFirestore document in a subcollection:
//trips/{tripId}/votes/{voteId}
//trips/{tripId}/votes/{voteId}/ballots/{userId}
data class VoteBallot(
    val userId: String,
    val optionId: String,
    val updatedAt: Timestamp,
)