// Initial model and data backend structure designed with assistance from ChatGPT.
package com.android.sample.model.trip.voting

import com.google.firebase.Timestamp

// Each ballot is a separate Firestore document in a subcollection:
// trips/{tripId}/votes/{voteId}
// trips/{tripId}/votes/{voteId}/ballots/{userId}
data class VoteBallot(
    val userId: String,
    val optionId: String,
    val updatedAt: Timestamp,
)
