// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.voting

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.swent.roamigo.ui.theme.RoamigoTheme
import com.swent.roamigo.ui.theme.Spacing
import com.swent.roamigo.ui.trip.voting.components.VoteCard

private val dinnerPreview = previewDinnerState()

@Preview(name = "Live vote · No ballots", showBackground = true, widthDp = 390)
@Composable
private fun LiveVoteNoBallotsPreview() =
    LiveVotePreview(LiveVoteSummary(dinnerPreview.vote.options.associate { it.uid to 0 }, false))

@Preview(name = "Live vote · Vote", showBackground = true, widthDp = 390)
@Composable
private fun LiveVoteCastPreview() =
    LiveVotePreview(LiveVoteSummary(dinnerPreview.voteCounts, false))

@Preview(name = "Live vote · Change vote", showBackground = true, widthDp = 390)
@Composable
private fun LiveVoteChangePreview() =
    LiveVotePreview(LiveVoteSummary(dinnerPreview.voteCounts, true))

@Composable
private fun LiveVotePreview(summary: LiveVoteSummary) {
  RoamigoTheme {
    Box(Modifier.padding(Spacing.screenPadding)) {
      VoteCard(vote = dinnerPreview.vote, onClick = {}, summary = summary)
    }
  }
}
