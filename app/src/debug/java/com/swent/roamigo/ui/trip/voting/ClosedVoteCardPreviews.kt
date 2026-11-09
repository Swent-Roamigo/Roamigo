// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.voting

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.google.firebase.Timestamp
import com.swent.roamigo.model.trip.voting.Vote
import com.swent.roamigo.model.trip.voting.VoteOption
import com.swent.roamigo.model.trip.voting.VoteResult
import com.swent.roamigo.model.trip.voting.VoteStatus
import com.swent.roamigo.ui.theme.RoamigoTheme
import com.swent.roamigo.ui.theme.Spacing
import com.swent.roamigo.ui.trip.voting.components.VoteCard

internal val closedDinnerPreview =
    Vote(
        "closed-dinner",
        "lisbon",
        "Dinner tonight?",
        listOf(
            VoteOption("market", "Time Out Market"),
            VoteOption("taberna", "Taberna das Flores"),
            VoteOption("riverside", "Riverside restaurant with a terrace overlooking the old town"),
        ),
        "sam",
        VoteStatus.CLOSED,
        Timestamp(0, 0),
        null,
        null,
    )

// Keep previews independent of repository and result-calculator implementation changes.
internal val closedDinnerTieResult =
    VoteResult(
        counts = mapOf("market" to 1, "taberna" to 1, "riverside" to 0),
        winningOptionIds = setOf("market", "taberna"),
        currentUserOptionId = "market",
    )

@Preview(name = "Closed · Tie", showBackground = true, widthDp = 390)
@Composable
private fun ClosedTieCardPreview() = ClosedResultCardPreview(closedDinnerTieResult)

@Preview(
    name = "Closed · Three-way tie · Narrow · Large font",
    showBackground = true,
    widthDp = 280,
    fontScale = 1.5f,
)
@Composable
private fun ClosedNarrowCardPreview() =
    ClosedResultCardPreview(
        VoteResult(
            counts = mapOf("market" to 1, "taberna" to 1, "riverside" to 1),
            winningOptionIds = setOf("market", "taberna", "riverside"),
            currentUserOptionId = "market",
        )
    )

@Composable
private fun ClosedResultCardPreview(result: VoteResult) {

  RoamigoTheme {
    Box(Modifier.padding(Spacing.screenPadding)) {
      VoteCard(closedDinnerPreview, {}, finalResult = result)
    }
  }
}

@Preview(
    name = "Votes tab · Tie above compact Decided row",
    showBackground = true,
    widthDp = 390,
    heightDp = 844,
)
@Composable
private fun TiedAndClosedResultsTabPreview() {
  val decided =
      closedDinnerPreview.copy(
          uid = "lx-factory",
          question = "When should we visit LX Factory?",
          options =
              listOf(
                  VoteOption("afternoon", "Early afternoon"),
                  VoteOption("evening", "Before dinner"),
              ),
      )
  VotesPreview(
      VotesUiState.Ready(
          liveVotes = emptyList(),
          closedVotes = listOf(closedDinnerPreview, decided),
          finalSummaries =
              mapOf(
                  closedDinnerPreview.uid to closedDinnerTieResult,
                  decided.uid to
                      VoteResult(
                          counts = mapOf("afternoon" to 2, "evening" to 1),
                          winningOptionIds = setOf("afternoon"),
                      ),
              ),
      )
  )
}
