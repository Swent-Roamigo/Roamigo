// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.voting.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.swent.roamigo.R
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.Spacing
import com.swent.roamigo.ui.trip.voting.VoteUiState
import com.swent.roamigo.ui.trip.voting.VotesSyncState
import com.swent.roamigo.ui.trip.voting.VotesUiState

@Composable
fun VotesList(
    state: VotesUiState.Ready,
    onOpenVote: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onCloseVote: ((String) -> Unit)? = null,
    onRevote: ((VoteUiState.Closed) -> Unit)? = null,
    onOwnerDecides: ((VoteUiState.Closed) -> Unit)? = null,
    listState: LazyListState = rememberLazyListState(),
) {
  LazyColumn(
      modifier.fillMaxSize(),
      state = listState,
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
        VoteCard(
            vote,
            onClick = { onOpenVote(vote.uid) },
            summary = state.liveSummaries[vote.uid],
            onCloseVote = onCloseVote?.let { close -> { close(vote.uid) } },
        )
      }
    }
    if (state.unresolvedClosedVotes.isNotEmpty()) {
      item {
        Text(
            stringResource(R.string.votes_unresolved),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.testTag(C.Tag.votes_unresolved_heading).semantics { heading() },
        )
      }
    }
    items(state.unresolvedClosedVotes, key = { it.uid }) { vote ->
      val tied =
          state.finalSummaries[vote.uid]
              ?.let { VoteUiState.Closed(vote, it) }
              ?.takeIf { it.isUnresolvedTie }
      VoteCard(
          vote,
          onClick = { onOpenVote(vote.uid) },
          finalResult = state.finalSummaries[vote.uid],
          onRevote = tied?.let { closed -> onRevote?.let { action -> { action(closed) } } },
          onOwnerDecides =
              tied?.let { closed -> onOwnerDecides?.let { action -> { action(closed) } } },
      )
    }
    if (state.decidedVotes.isNotEmpty()) {
      item {
        Text(
            stringResource(R.string.votes_decided),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.testTag(C.Tag.votes_decided_heading).semantics { heading() },
        )
      }
      items(state.decidedVotes, key = { it.uid }) { vote ->
        VoteCard(
            vote,
            onClick = { onOpenVote(vote.uid) },
            finalResult = state.finalSummaries[vote.uid],
        )
      }
    }
  }
}
