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
import com.swent.roamigo.model.trip.voting.Vote
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
    highlightedVoteId: String? = null,
) {
  val highlighted = highlightedVoteId?.let { id ->
    state.liveVotes.firstOrNull { it.uid == id } ?: state.closedVotes.firstOrNull { it.uid == id }
  }
  fun remaining(votes: List<Vote>): List<Vote> =
      if (highlighted == null) votes else votes.filterNot { it.uid == highlighted.uid }
  val liveVotes = remaining(state.liveVotes)
  val unresolvedVotes = remaining(state.unresolvedClosedVotes)
  val decidedVotes = remaining(state.decidedVotes)
  val renderVote: @Composable (Vote) -> Unit = { vote ->
    val finalResult = state.finalSummaries[vote.uid]
    val tied =
        finalResult
            ?.let { VoteUiState.Closed(vote, it) }
            ?.takeIf { vote.nextRoundVoteId == null && it.isUnresolvedTie }
    VoteCard(
        vote,
        onClick = { onOpenVote(vote.uid) },
        summary = state.liveSummaries[vote.uid],
        onCloseVote = onCloseVote?.let { close -> { close(vote.uid) } },
        finalResult = finalResult,
        onRevote = tied?.let { closed -> onRevote?.let { action -> { action(closed) } } },
        onOwnerDecides =
            tied?.let { closed -> onOwnerDecides?.let { action -> { action(closed) } } },
        highlighted = vote.uid == highlightedVoteId,
    )
  }
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
    if (highlighted != null) {
      item(key = highlighted.uid) { renderVote(highlighted) }
    } else if (highlightedVoteId != null && state.syncState == VotesSyncState.SYNCED) {
      item { VoteErrorContent(R.string.vote_not_found) }
    }
    items(liveVotes, key = { it.uid }) { renderVote(it) }
    if (unresolvedVotes.isNotEmpty()) {
      item {
        Text(
            stringResource(R.string.votes_unresolved),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.testTag(C.Tag.votes_unresolved_heading).semantics { heading() },
        )
      }
    }
    items(unresolvedVotes, key = { it.uid }) { renderVote(it) }
    if (decidedVotes.isNotEmpty()) {
      item {
        Text(
            stringResource(R.string.votes_decided),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.testTag(C.Tag.votes_decided_heading).semantics { heading() },
        )
      }
      items(decidedVotes, key = { it.uid }) { renderVote(it) }
    }
  }
}
