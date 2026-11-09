// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.voting

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.google.firebase.Timestamp
import com.swent.roamigo.model.trip.voting.Vote
import com.swent.roamigo.model.trip.voting.VoteOption
import com.swent.roamigo.model.trip.voting.VoteResult
import com.swent.roamigo.model.trip.voting.VoteStatus
import com.swent.roamigo.ui.theme.RoamigoTheme

private val dinnerVote =
    Vote(
        uid = "dinner",
        tripId = "lisbon",
        question = "Where should we have dinner?",
        options =
            listOf(
                VoteOption("market", "Time Out Market"),
                VoteOption("restaurant", "Riverside restaurant"),
                VoteOption("pizza", "Pizza with a view"),
            ),
        createdByUserId = "mia",
        status = VoteStatus.CLOSED,
        creationTime = Timestamp(0, 0),
        closingTime = null,
        winningOptionId = null,
    )

private val dinnerWinnerResult =
    VoteResult(
        counts = mapOf("market" to 3, "restaurant" to 1, "pizza" to 0),
        winningOptionIds = setOf("market"),
    )

@Preview(showBackground = true, widthDp = 390, heightDp = 600)
@Composable
private fun ClosedWinnerPreview() {
  RoamigoTheme {
    VoteScreen(
        VoteUiState.Closed(dinnerVote, dinnerWinnerResult),
        {},
        {},
        {},
        {},
    )
  }
}
