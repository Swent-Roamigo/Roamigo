// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.voting

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.swent.roamigo.ui.theme.RoamigoTheme

@Preview(name = "Vote dialog · No selection", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun VoteDialogNoSelectionPreview() = VoteDialogPreview(dialogReadyState())

@Preview(
    name = "Vote dialog · Restored ballot",
    showBackground = true,
    widthDp = 390,
    heightDp = 844,
)
@Composable
private fun VoteDialogRestoredPreview() =
    VoteDialogPreview(
        dialogReadyState().copy(persistedOptionId = "market", selectedOptionId = "market")
    )

@Preview(name = "Vote dialog · Changed draft", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun VoteDialogDraftPreview() =
    VoteDialogPreview(
        dialogReadyState().copy(persistedOptionId = "market", selectedOptionId = "taberna")
    )

@Preview(name = "Vote dialog · Submitting", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun VoteDialogSubmittingPreview() =
    VoteDialogPreview(
        dialogReadyState()
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
        dialogReadyState()
            .copy(
                selectedOptionId = "taberna",
                submissionState = VoteSubmissionState.Failed,
            )
    )

@Preview(name = "Vote dialog · Loading", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun VoteDialogLoadingPreview() = VoteDialogPreview(VoteUiState.Loading)

private fun dialogReadyState() =
    VoteUiState.Ready(previewDinnerVote, voteCounts = previewDinnerCounts)

@Composable
private fun VoteDialogPreview(state: VoteUiState) {
  RoamigoTheme {
    VoteDialogContent(state, onSelectOption = {}, onSubmit = {}, onRetry = {}, onDismiss = {})
  }
}
