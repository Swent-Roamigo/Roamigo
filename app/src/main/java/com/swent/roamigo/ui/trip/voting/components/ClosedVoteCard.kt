// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.voting.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swent.roamigo.R
import com.swent.roamigo.model.trip.voting.Vote
import com.swent.roamigo.model.trip.voting.VoteResult
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.Spacing
import com.swent.roamigo.ui.theme.VoteTieBorder
import com.swent.roamigo.ui.theme.VoteTieContent
import com.swent.roamigo.ui.theme.VoteTieSurface

/** S30 result card. Missing results mean loading, never an inferred zero-ballot outcome. */
@Composable
internal fun ClosedVoteCard(
    vote: Vote,
    result: VoteResult?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag(C.Tag.closed_vote(vote.uid)),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
      Column(
          Modifier.padding(horizontal = Spacing.screenPadding, vertical = 18.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text(
            stringResource(
                when {
                  result == null -> R.string.vote_results_loading
                  result.hasNoVotes -> R.string.vote_closed_no_votes
                  result.isTie -> R.string.vote_result_tie
                  else -> R.string.vote_closed_decided
                }
            ),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
        Text(
            vote.question,
            style = MaterialTheme.typography.headlineLarge,
        )
        vote.options.forEach { option ->
          val leading = result?.winningOptionIds?.contains(option.uid) == true
          Surface(
              modifier =
                  Modifier.fillMaxWidth()
                      .testTag(C.Tag.closed_option(vote.uid, option.uid))
                      .semantics(mergeDescendants = true) {},
              shape = MaterialTheme.shapes.small,
              color = MaterialTheme.colorScheme.onPrimary.copy(alpha = if (leading) 0.16f else 0f),
              contentColor = MaterialTheme.colorScheme.onPrimary,
          ) {
            Row(
                Modifier.padding(Spacing.small),
                horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(Modifier.weight(1f)) {
                Text(
                    if (result?.currentUserOptionId == option.uid)
                        stringResource(R.string.vote_option_yours, option.name)
                    else option.name,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (leading)
                    Text(
                        stringResource(
                            if (result.isTie) R.string.vote_result_tied_option
                            else R.string.vote_result_winner
                        ),
                        style = MaterialTheme.typography.labelSmall,
                    )
              }
              val count = result?.counts?.get(option.uid)
              Text(
                  if (count == null) stringResource(R.string.vote_result_pending_count)
                  else pluralStringResource(R.plurals.vote_count, count, count),
                  modifier = Modifier.testTag(C.Tag.vote_count(vote.uid, option.uid)),
                  style = MaterialTheme.typography.bodyMedium,
                  fontWeight = FontWeight.SemiBold,
              )
            }
          }
        }
      }
    }
    if (result?.isTie == true) {
      Surface(
          modifier =
              Modifier.fillMaxWidth().testTag(C.Tag.vote_tie(vote.uid)).semantics(
                  mergeDescendants = true
              ) {},
          shape = RoundedCornerShape(14.dp),
          color = VoteTieSurface,
          contentColor = VoteTieContent,
          border = BorderStroke(1.dp, VoteTieBorder),
      ) {
        Column(
            Modifier.padding(horizontal = Spacing.medium, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
        ) {
          Text(stringResource(R.string.vote_tie_title), style = MaterialTheme.typography.titleSmall)
          Text(
              stringResource(R.string.vote_tie_description),
              style = MaterialTheme.typography.bodySmall,
          )
        }
      }
    }
  }
}
