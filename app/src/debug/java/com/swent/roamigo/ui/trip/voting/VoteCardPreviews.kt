// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.voting

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.swent.roamigo.model.trip.voting.VoteStatus
import com.swent.roamigo.ui.theme.RoamigoTheme
import com.swent.roamigo.ui.theme.Spacing
import com.swent.roamigo.ui.trip.voting.components.VoteCard

private val dinnerPreview = previewDinnerState()

@Preview(name = "Live vote · No ballots", showBackground = true, widthDp = 390)
@Composable
private fun LiveVoteNoBallotsPreview() =
    LiveVotePreview(LiveVoteSummary(dinnerPreview.vote.options.associate { it.uid to 0 }, false))

@Preview(name = "Live vote · OPEN · Non-creator", showBackground = true, widthDp = 390)
@Composable
private fun LiveVoteCastPreview() =
    LiveVotePreview(LiveVoteSummary(dinnerPreview.voteCounts, false))

@Preview(name = "Live vote · Change vote", showBackground = true, widthDp = 390)
@Composable
private fun LiveVoteChangePreview() =
    LiveVotePreview(LiveVoteSummary(dinnerPreview.voteCounts, true))

@Preview(name = "Live vote · OPEN · Creator", showBackground = true, widthDp = 390)
@Composable
private fun LiveVoteCreatorPreview() =
    LiveVotePreview(
        LiveVoteSummary(dinnerPreview.voteCounts, hasPersistedBallot = true, isCreator = true)
    )

@Preview(
    name = "Creator · Narrow · Large font",
    showBackground = true,
    widthDp = 280,
    fontScale = 1.5f,
)
@Composable
private fun NarrowCreatorCardPreview() = LiveVoteCreatorPreview()

@Preview(name = "Vote card · CLOSED", showBackground = true, widthDp = 390)
@Composable
private fun ClosedVoteCardPreview() {
  RoamigoTheme {
    Box(Modifier.padding(Spacing.screenPadding)) {
      VoteCard(
          vote = dinnerPreview.vote.copy(status = VoteStatus.CLOSED),
          onClick = {},
      )
    }
  }
}

@Composable
private fun LiveVotePreview(summary: LiveVoteSummary) {
  RoamigoTheme {
    Box(Modifier.padding(Spacing.screenPadding)) {
      VoteCard(
          vote = dinnerPreview.vote,
          onClick = {},
          summary = summary,
          onCloseVote = {},
      )
    }
  }
}
