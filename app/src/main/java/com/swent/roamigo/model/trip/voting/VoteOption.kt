// Initial model and data backend structure designed with assistance from ChatGPT.
package com.swent.roamigo.model.trip.voting

/** Product limits for new votes; mirrored by hasValidVoteOptions in firestore.rules. */
const val MIN_VOTE_OPTIONS = 2
const val MAX_VOTE_OPTIONS = 10

data class VoteOption(
    val uid: String,
    val name: String,
)
