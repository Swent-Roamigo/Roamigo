// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.voting.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swent.roamigo.R
import com.swent.roamigo.model.trip.voting.Vote
import com.swent.roamigo.model.trip.voting.VoteStatus
import com.swent.roamigo.ui.theme.Spacing

/** Opens a vote without observing ballots or inferring a final result in the trip list. */
@Composable
fun VoteCard(vote: Vote, onClick: () -> Unit) {
  when (vote.status) {
    VoteStatus.OPEN -> LiveVoteCard(vote, onClick)
    VoteStatus.CLOSED -> ClosedVoteRow(vote, onClick)
  }
}

@Composable
private fun LiveVoteCard(vote: Vote, onClick: () -> Unit) {
  Surface(
      onClick = onClick,
      modifier = Modifier.fillMaxWidth(),
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
      vote.options.forEach {
        Text(
            it.name,
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
      }
    }
  }
}

@Composable
private fun ClosedVoteRow(vote: Vote, onClick: () -> Unit) {
  Surface(
      onClick = onClick,
      modifier = Modifier.fillMaxWidth(),
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
