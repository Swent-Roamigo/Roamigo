// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.voting

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.swent.roamigo.R
import com.swent.roamigo.ui.trip.voting.components.VoteOptionRow

/** Retains drafts through unknown results; only positive invalidation closes the dialog. */
@Composable
internal fun OwnerDecisionHost(
    votes: VotesUiState,
    state: TieResolutionState,
    onSelect: (String) -> Unit,
    onConfirm: (VoteUiState.Closed) -> Unit,
    onDismiss: () -> Unit,
    onRetryEligibility: () -> Unit,
) {
  val ready = votes as? VotesUiState.Ready
  val ownerVote = ready?.closedVotes?.firstOrNull { it.uid == state.ownerVoteId }
  val ownerClosed = ownerVote?.let { vote ->
    ready.finalSummaries[vote.uid]?.let { VoteUiState.Closed(vote, it) }
  }
  LaunchedEffect(
      ready,
      state.eligibility,
      state.ownerVoteId,
      state.submitting,
  ) {
    if (state.ownerVoteId != null) {
      val current = ready?.let {
        it.closedVotes.firstOrNull { vote -> vote.uid == state.ownerVoteId }
      }
      val noLongerTied = ready?.finalSummaries?.get(state.ownerVoteId)?.isTie == false
      val knownIneligible =
          current != null &&
              (current.nextRoundVoteId != null || current.resolvedOptionId != null || noLongerTied)
      val eligibility = state.eligibility
      val permissionRevoked =
          eligibility is ResolutionEligibility.Ready &&
              (!eligibility.canResolve || !eligibility.isOwner)
      if ((ready?.syncState == VotesSyncState.SYNCED && knownIneligible) || permissionRevoked)
          onDismiss()
    }
  }
  if (state.ownerVoteId != null && state.ownerOptions.isNotEmpty()) {
    OwnerDecisionDialog(
        state,
        onSelect,
        { ownerClosed?.let(onConfirm) },
        onDismiss,
        canConfirm =
            ready?.syncState == VotesSyncState.SYNCED &&
                ownerClosed?.isUnresolvedTie == true &&
                state.canResolve &&
                state.isOwner &&
                (state.selectedOptionId == null ||
                    state.selectedOptionId in ownerClosed.result.winningOptionIds),
        onRetryEligibility = onRetryEligibility,
    )
  }
}

@Composable
internal fun OwnerDecisionDialog(
    state: TieResolutionState,
    onSelect: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    canConfirm: Boolean,
    onRetryEligibility: () -> Unit,
) {
  AlertDialog(
      onDismissRequest = { if (!state.submitting) onDismiss() },
      title = { Text(stringResource(R.string.vote_owner_decides)) },
      text = {
        Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
          state.ownerOptions.forEach { option ->
            VoteOptionRow(
                option.name,
                state.selectedOptionId == option.uid,
                !state.submitting,
                voteCount = null,
                onClick = { onSelect(option.uid) },
            )
          }
          ResolutionEligibilityContent(state.eligibility, onRetryEligibility)
          if (!canConfirm && state.canResolve && !state.submitting)
              Text(stringResource(R.string.vote_resolution_waiting_results))
          if (state.failed) Text(stringResource(R.string.vote_resolution_error))
        }
      },
      confirmButton = {
        TextButton(
            onClick = onConfirm,
            enabled = canConfirm && state.selectedOptionId != null && !state.submitting,
        ) {
          Text(stringResource(R.string.vote_resolution_confirm))
        }
      },
      dismissButton = {
        TextButton(onClick = onDismiss, enabled = !state.submitting) {
          Text(stringResource(R.string.vote_cancel))
        }
      },
  )
}
