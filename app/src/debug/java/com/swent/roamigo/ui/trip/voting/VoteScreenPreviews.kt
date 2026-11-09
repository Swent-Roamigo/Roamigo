// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.voting

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.google.firebase.Timestamp
import com.swent.roamigo.model.trip.voting.Vote
import com.swent.roamigo.model.trip.voting.VoteOption
import com.swent.roamigo.model.trip.voting.VoteStatus
import com.swent.roamigo.ui.theme.RoamigoTheme

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun NoVotePreview() =
    VoteInteractionPreview(
        persistedOptionId = null,
        selectedOptionId = null,
    )

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ExistingVotePreview() =
    VoteInteractionPreview(
        persistedOptionId = "restaurant",
        selectedOptionId = "restaurant",
    )

@Composable
private fun VoteInteractionPreview(
    persistedOptionId: String?,
    selectedOptionId: String?,
) {
  val vote =
      Vote(
          uid = "vote",
          tripId = "trip",
          question = "Where should we have dinner?",
          options =
              listOf(
                  VoteOption(
                      uid = "market",
                      name = "Local market",
                  ),
                  VoteOption(
                      uid = "restaurant",
                      name = "Restaurant",
                  ),
                  VoteOption(
                      uid = "pizza",
                      name = "Pizza place",
                  ),
              ),
          createdByUserId = "member",
          status = VoteStatus.OPEN,
          creationTime =
              Timestamp(
                  seconds = 0,
                  nanoseconds = 0,
              ),
          closingTime = null,
          winningOptionId = null,
      )

  RoamigoTheme {
    VoteScreen(
        state =
            VoteUiState.Ready(
                vote = vote,
                persistedOptionId = persistedOptionId,
                selectedOptionId = selectedOptionId,
                voteCounts =
                    mapOf(
                        "market" to 0,
                        "restaurant" to 1,
                        "pizza" to 5,
                    ),
            ),
        onSelectOption = {},
        onSubmit = {},
        onRetry = {},
        onBack = {},
    )
  }
}
