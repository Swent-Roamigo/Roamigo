// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.voting

import com.google.firebase.Timestamp
import com.swent.roamigo.model.trip.voting.Vote
import com.swent.roamigo.model.trip.voting.VoteOption
import com.swent.roamigo.model.trip.voting.VoteStatus

internal val previewDinnerVote =
    Vote(
        uid = "dinner",
        tripId = "lisbon-porto",
        question = "Dinner tonight?",
        options =
            listOf(
                VoteOption("market", "Time Out Market"),
                VoteOption("taberna", "Taberna das Flores"),
                VoteOption("pizza", "Pizza by the riverside"),
            ),
        createdByUserId = "sam",
        status = VoteStatus.OPEN,
        creationTime = Timestamp(1_791_724_800, 0),
        closingTime = null,
        winningOptionId = null,
    )

internal val previewDinnerCounts = mapOf("market" to 3, "taberna" to 1, "pizza" to 0)
