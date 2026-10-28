// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.voting

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.google.firebase.Timestamp
import com.swent.roamigo.model.trip.voting.Vote
import com.swent.roamigo.model.trip.voting.VoteOption
import com.swent.roamigo.model.trip.voting.VoteStatus
import com.swent.roamigo.ui.theme.RoamigoTheme

@Preview(name = "Votes · Ready", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun VotesReadyPreview() {
  val dinner =
      Vote(
          uid = "dinner",
          tripId = "lisbon-porto",
          question = "Dinner tonight?",
          options =
              listOf(
                  VoteOption("market", "Time Out Market"),
                  VoteOption("taberna", "Taberna das Flores"),
              ),
          createdByUserId = "sam",
          status = VoteStatus.OPEN,
          creationTime = Timestamp(1_791_724_800, 0),
          closingTime = null,
          winningOptionId = null,
      )
  VotesPreview(
      VotesUiState.Ready(
          liveVotes =
              listOf(
                  dinner,
                  dinner.copy(
                      uid = "explore-porto",
                      question = "How should we explore Porto?",
                      options =
                          listOf(
                              VoteOption("walk", "Walk along the riverside"),
                              VoteOption("tram", "Take the historic tram"),
                          ),
                      creationTime = Timestamp(1_791_721_200, 0),
                  ),
              ),
          closedVotes =
              listOf(
                  dinner.copy(
                      uid = "lx-factory",
                      question = "When should we visit LX Factory?",
                      options =
                          listOf(
                              VoteOption("afternoon", "Early afternoon"),
                              VoteOption("evening", "Before dinner"),
                          ),
                      status = VoteStatus.CLOSED,
                      creationTime = Timestamp(1_791_637_200, 0),
                  )
              ),
      )
  )
}

@Preview(name = "Votes · Empty", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun VotesEmptyPreview() = VotesPreview(VotesUiState.Empty)

@Composable
private fun VotesPreview(state: VotesUiState) {
  RoamigoTheme {
    VotesScreenContent(
        state = state,
        tripName = "Lisbon & Porto",
        onOpenVote = {},
        onCreateVote = {},
        onRetry = {},
        onTripsClick = {},
    )
  }
}
