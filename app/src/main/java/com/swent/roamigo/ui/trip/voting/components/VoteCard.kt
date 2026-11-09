// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.voting.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swent.roamigo.R
import com.swent.roamigo.model.trip.voting.Vote
import com.swent.roamigo.model.trip.voting.VoteStatus
import com.swent.roamigo.ui.theme.Ink
import com.swent.roamigo.ui.theme.Spacing
import com.swent.roamigo.ui.trip.voting.LiveVoteSummary

/** Renders the trip summary; ballot observation and interaction stay outside the card. */
@Composable
fun VoteCard(
    vote: Vote,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: LiveVoteSummary? = null,
    onCloseVote: (() -> Unit)? = null,
) {
  when (vote.status) {
    VoteStatus.OPEN -> LiveVoteCard(vote, onClick, summary, modifier, onCloseVote)
    VoteStatus.CLOSED -> ClosedVoteRow(vote, onClick, modifier)
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LiveVoteCard(
    vote: Vote,
    onClick: () -> Unit,
    summary: LiveVoteSummary?,
    modifier: Modifier,
    onCloseVote: (() -> Unit)?,
) {
  Surface(
      modifier = modifier.fillMaxWidth(),
      shape = RoundedCornerShape(22.dp),
      color = MaterialTheme.colorScheme.primary,
      contentColor = MaterialTheme.colorScheme.onPrimary,
  ) {
    Column(
        Modifier.padding(horizontal = Spacing.screenPadding, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Text(
          stringResource(R.string.votes_live_vote),
          style = MaterialTheme.typography.bodySmall,
          fontSize = 13.sp,
          lineHeight = 18.sp,
          fontWeight = FontWeight.Medium,
      )
      Text(
          vote.question,
          style = MaterialTheme.typography.headlineLarge,
          fontSize = 30.sp,
          lineHeight = 36.sp,
      )
      vote.options.forEach { option ->
        VoteCountRow(
            option.name,
            summary?.counts?.get(option.uid) ?: 0,
            "vote_count_${vote.uid}_${option.uid}",
        )
      }
      FlowRow(
          horizontalArrangement = Arrangement.spacedBy(Spacing.small),
          verticalArrangement = Arrangement.spacedBy(Spacing.small),
      ) {
        Button(
            onClick = onClick,
            modifier = Modifier.heightIn(min = 48.dp).testTag("vote_action_${vote.uid}"),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Ink),
        ) {
          Text(
              stringResource(
                  if (summary?.hasPersistedBallot == true) R.string.votes_change_vote
                  else R.string.votes_cast_vote
              ),
          )
        }
        if (summary?.isCreator == true && onCloseVote != null) {
          OutlinedButton(
              onClick = onCloseVote,
              modifier = Modifier.heightIn(min = 48.dp).testTag("close_vote_${vote.uid}"),
              shape = CircleShape,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimary),
              colors =
                  ButtonDefaults.outlinedButtonColors(
                      contentColor = MaterialTheme.colorScheme.onPrimary
                  ),
          ) {
            Text(stringResource(R.string.vote_close))
          }
        }
      }
    }
  }
}

@Composable
private fun VoteCountRow(name: String, count: Int, tag: String) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(
        name,
        modifier = Modifier.weight(1f).padding(end = Spacing.small),
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        pluralStringResource(R.plurals.vote_count, count, count),
        modifier = Modifier.testTag(tag),
        style = MaterialTheme.typography.bodyMedium,
    )
  }
}

@Composable
private fun ClosedVoteRow(vote: Vote, onClick: () -> Unit, modifier: Modifier) {
  Surface(
      onClick = onClick,
      modifier = modifier.fillMaxWidth(),
      color = MaterialTheme.colorScheme.surface,
  ) {
    Row(
        Modifier.heightIn(min = 48.dp).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Surface(
          modifier = Modifier.size(40.dp),
          shape = CircleShape,
          color = MaterialTheme.colorScheme.primaryContainer,
      ) {
        Box(contentAlignment = Alignment.Center) {
          Image(
              painter = painterResource(R.drawable.vote_closed_check),
              contentDescription = null,
              modifier = Modifier.size(20.dp),
          )
        }
      }
      Column(
          Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
      ) {
        Text(vote.question, style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.votes_closed_vote),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
