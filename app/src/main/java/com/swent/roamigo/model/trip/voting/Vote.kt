// Initial model and data backend structure designed with assistance from ChatGPT.
package com.swent.roamigo.model.trip.voting

import com.google.firebase.Timestamp

data class Vote(
    val uid: String,
    val tripId: String,
    val question: String,
    val options: List<VoteOption>,
    val createdByUserId: String,
    val status: VoteStatus,
    val creationTime: Timestamp,
    val closingTime: Timestamp?,
    val winningOptionId: String?,
    val parentVoteId: String? = null,
    val nextRoundVoteId: String? = null,
    val round: Int = 1,
    // Owner choice is independent of the original ballot outcome.
    val resolvedOptionId: String? = null,
    val resolvedByUserId: String? = null,
)

enum class VoteStatus {
  OPEN,
  CLOSED,
}
