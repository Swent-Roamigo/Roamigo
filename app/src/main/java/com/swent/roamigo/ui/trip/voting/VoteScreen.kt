// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.voting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swent.roamigo.R
import com.swent.roamigo.model.trip.voting.VoteRepository
import com.swent.roamigo.ui.theme.Spacing
import com.swent.roamigo.ui.trip.voting.components.VoteActions
import com.swent.roamigo.ui.trip.voting.components.VoteErrorContent
import com.swent.roamigo.ui.trip.voting.components.VoteOptionRow

/** Connects [voteRepository] to [VoteScreen] for the selected trip, vote, and member. */
@Composable
fun VoteRoute(
    voteRepository: VoteRepository,
    tripId: String,
    voteId: String,
    currentUserId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
) {
  val viewModel = viewModel { VoteViewModel(voteRepository, tripId, voteId, currentUserId) }
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  VoteScreen(
      state,
      viewModel::selectOption,
      viewModel::submitVote,
      viewModel::retryLoad,
      onBack,
      modifier,
      readOnly,
  )
}

/** Renders [state] and forwards selection, submission, retry, and back actions. */
@Composable
fun VoteScreen(
    state: VoteUiState,
    onSelectOption: (String) -> Unit,
    onSubmit: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
) {
  Column(
      modifier
          .fillMaxSize()
          .background(MaterialTheme.colorScheme.background)
          .safeDrawingPadding()
          .padding(horizontal = Spacing.screenPadding, vertical = Spacing.medium),
      verticalArrangement = Arrangement.spacedBy(Spacing.large),
  ) {
    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
      when (state) {
        VoteUiState.Loading -> VoteLoadingContent()
        VoteUiState.Closed ->
            Text(stringResource(R.string.vote_closed), textAlign = TextAlign.Center)
        is VoteUiState.LoadError ->
            VoteErrorContent(
                messageResource =
                    if (state.reason == VoteLoadError.NOT_FOUND) R.string.vote_not_found
                    else R.string.vote_load_error,
                onRetry = if (state.canRetry) onRetry else null,
            )
        is VoteUiState.Ready ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.large),
            ) {
              Text(state.vote.question, style = MaterialTheme.typography.headlineLarge)
              Column(
                  Modifier.selectableGroup(),
                  verticalArrangement = Arrangement.spacedBy(Spacing.small),
              ) {
                state.vote.options.forEach { option ->
                  VoteOptionRow(
                      option.name,
                      state.selectedOptionId == option.uid,
                      state.canSelect && !readOnly,
                      voteCount = state.voteCounts[option.uid] ?: 0,
                      onClick = { onSelectOption(option.uid) },
                  )
                }
              }
            }
      }
    }
    if (state is VoteUiState.Ready) {
      when (state.syncState) {
        VoteSyncState.Synced -> Unit
        VoteSyncState.Retrying ->
            Text(
                stringResource(R.string.vote_syncing),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        VoteSyncState.Failed ->
            VoteErrorContent(
                R.string.vote_ballots_error,
                onRetry,
                retryEnabled = !state.isSubmitting,
            )
      }
      if (state.submissionState == VoteSubmissionState.Failed) {
        VoteErrorContent(R.string.vote_ballot_submission_error)
      }
    }
    val ready = state as? VoteUiState.Ready
    VoteActions(
        canSubmit = ready?.canSubmit == true,
        isSubmitting = ready?.isSubmitting == true,
        hasPersistedBallot = ready?.hasPersistedBallot == true,
        onSubmit = onSubmit,
        onBack = onBack,
        showSubmit = ready != null && !readOnly,
    )
  }
}

@Composable
private fun VoteLoadingContent() {
  Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    CircularProgressIndicator()
    Text(stringResource(R.string.vote_loading))
  }
}
