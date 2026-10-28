// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.voting.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.swent.roamigo.R
import com.swent.roamigo.ui.theme.Spacing
import com.swent.roamigo.ui.trip.voting.VotesSyncState
import com.swent.roamigo.ui.trip.voting.VotesUiState

@Composable
fun VotesList(
    state: VotesUiState.Ready,
    onOpenVote: (String) -> Unit,
    onRetry: () -> Unit,
) {
  LazyColumn(
      Modifier.fillMaxSize(),
      contentPadding = PaddingValues(bottom = Spacing.small),
      verticalArrangement = Arrangement.spacedBy(Spacing.large),
  ) {
    when (state.syncState) {
      VotesSyncState.SYNCED -> Unit
      VotesSyncState.RETRYING -> item { Text(stringResource(R.string.vote_syncing)) }
      VotesSyncState.FAILED -> item { VoteErrorContent(R.string.votes_sync_error, onRetry) }
    }
    if (state.liveVotes.isNotEmpty()) {
      items(state.liveVotes, key = { it.uid }) { vote ->
        VoteCard(vote, onClick = { onOpenVote(vote.uid) })
      }
    }
    if (state.closedVotes.isNotEmpty()) {
      item {
        Text(
            stringResource(R.string.votes_decided),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
      }
      items(state.closedVotes, key = { it.uid }) { vote ->
        VoteCard(vote, onClick = { onOpenVote(vote.uid) })
      }
    }
  }
}
