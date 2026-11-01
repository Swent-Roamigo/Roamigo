// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.voting

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.google.firebase.Timestamp
import com.swent.roamigo.model.trip.voting.Vote
import com.swent.roamigo.model.trip.voting.VoteOption
import com.swent.roamigo.model.trip.voting.VoteStatus
import com.swent.roamigo.ui.theme.RoamigoTheme

@Preview(name = "Vote dialog · No selection", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun VoteDialogNoSelectionPreview() = VoteDialogPreview(previewDinnerState())

@Preview(
    name = "Vote dialog · Restored ballot",
    showBackground = true,
    widthDp = 390,
    heightDp = 844,
)
@Composable
private fun VoteDialogRestoredPreview() =
    VoteDialogPreview(
        previewDinnerState().copy(persistedOptionId = "market", selectedOptionId = "market")
    )

@Preview(name = "Vote dialog · Changed draft", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun VoteDialogDraftPreview() =
    VoteDialogPreview(
        previewDinnerState().copy(persistedOptionId = "market", selectedOptionId = "taberna")
    )

@Preview(name = "Vote dialog · Submitting", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun VoteDialogSubmittingPreview() =
    VoteDialogPreview(
        previewDinnerState()
            .copy(
                selectedOptionId = "taberna",
                submissionState = VoteSubmissionState.Submitting,
            )
    )

@Preview(
    name = "Vote dialog · Submission error",
    showBackground = true,
    widthDp = 390,
    heightDp = 844,
)
@Composable
private fun VoteDialogErrorPreview() =
    VoteDialogPreview(
        previewDinnerState()
            .copy(
                selectedOptionId = "taberna",
                submissionState = VoteSubmissionState.Failed,
            )
    )

@Preview(name = "Vote dialog · Loading", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun VoteDialogLoadingPreview() = VoteDialogPreview(VoteUiState.Loading)

/** Shared sample for the dialog and live-card previews in this debug source set. */
internal fun previewDinnerState() =
    VoteUiState.Ready(
        vote =
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
            ),
        voteCounts = mapOf("market" to 2, "taberna" to 1, "pizza" to 0),
    )

@Composable
private fun VoteDialogPreview(state: VoteUiState) {
  RoamigoTheme {
    VoteDialogContent(state, onSelectOption = {}, onSubmit = {}, onRetry = {}, onDismiss = {})
  }
}
