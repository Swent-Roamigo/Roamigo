// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.voting.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import com.swent.roamigo.R
import com.swent.roamigo.model.trip.voting.Vote
import com.swent.roamigo.model.trip.voting.VoteResult
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.Spacing

/** Read-only results, with text labels as well as color to identify leading options. */
@Composable
fun VoteResultContent(vote: Vote, result: VoteResult, modifier: Modifier = Modifier) {
  Column(
      modifier.fillMaxSize().verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(Spacing.large),
  ) {
    Text(vote.question, style = MaterialTheme.typography.headlineLarge)
    Text(
        stringResource(
            when {
              result.hasNoVotes -> R.string.vote_result_no_votes
              result.isTie ->
                  if (vote.resolvedOptionId == null) R.string.vote_tie_unresolved
                  else R.string.vote_final_results
              else -> R.string.vote_final_results
            }
        ),
        style = MaterialTheme.typography.titleMedium,
    )
    voteDecisionLabel(vote, result)?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
    vote.resolvedOptionId?.let { selected ->
      vote.options
          .firstOrNull { it.uid == selected }
          ?.let { option ->
            Text(
                stringResource(R.string.vote_owner_outcome, option.name),
                style = MaterialTheme.typography.titleMedium,
            )
          }
    }
    if (vote.parentVoteId != null) Text(stringResource(R.string.vote_revote_round, vote.round))
    vote.options.forEach { option ->
      val leading = option.uid in result.winningOptionIds
      Surface(
          modifier =
              Modifier.fillMaxWidth().testTag(C.Tag.voteResult(option.uid)).semantics(
                  mergeDescendants = true
              ) {},
          shape = MaterialTheme.shapes.small,
          color =
              if (leading) MaterialTheme.colorScheme.secondaryContainer
              else MaterialTheme.colorScheme.surfaceVariant,
      ) {
        Row(
            Modifier.padding(Spacing.medium),
            horizontalArrangement = Arrangement.spacedBy(Spacing.small),
        ) {
          Column(Modifier.weight(1f)) {
            Text(
                if (result.currentUserOptionId == option.uid)
                    stringResource(R.string.vote_option_yours, option.name)
                else option.name,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (leading)
                Text(
                    stringResource(
                        if (result.isTie) R.string.vote_result_tied_option
                        else R.string.vote_result_winner
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
          }
          val count = result.counts[option.uid] ?: 0
          Text(
              pluralStringResource(R.plurals.vote_count, count, count),
              style = MaterialTheme.typography.bodySmall,
          )
        }
      }
    }
  }
}
