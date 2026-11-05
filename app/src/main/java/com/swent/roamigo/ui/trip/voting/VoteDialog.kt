// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.voting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swent.roamigo.R
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.Spacing
import com.swent.roamigo.ui.trip.voting.components.VoteErrorContent

/** A fresh ballot interaction scope per opening. Dismissal discards the draft. */
@Composable
fun VoteDialog(
    voteId: String,
    createViewModel: (String) -> VoteViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val viewModel = rememberVoteViewModel(voteId, createViewModel)
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  VoteDialogContent(
      state,
      viewModel::selectOption,
      viewModel::submitVote,
      viewModel::retryLoad,
      onDismiss,
      modifier,
  )
}

/** The card opens only this confirmation over the Votes list. */
@Composable
fun VoteCloseDialog(
    voteId: String,
    createViewModel: (String) -> VoteViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val viewModel = rememberVoteViewModel(voteId, createViewModel)
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  VoteCloseDialogContent(state, viewModel::closeVote, viewModel::retryLoad, onDismiss, modifier)
}

@Composable
private fun rememberVoteViewModel(
    voteId: String,
    createViewModel: (String) -> VoteViewModel,
): VoteViewModel {
  val owner =
      remember(voteId) {
        object : ViewModelStoreOwner {
          override val viewModelStore = ViewModelStore()
        }
      }
  DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
  return viewModel(viewModelStoreOwner = owner) { createViewModel(voteId) }
}

@Composable
internal fun VoteDialogContent(
    state: VoteUiState,
    onSelectOption: (String) -> Unit,
    onSubmit: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val locked = (state as? VoteUiState.Ready)?.isSubmitting == true
  val dismiss = { if (!locked) onDismiss() }
  Dialog(onDismissRequest = dismiss) {
    Surface(
        modifier.fillMaxWidth().heightIn(max = 600.dp).testTag(C.Tag.vote_dialog),
        shape = MaterialTheme.shapes.large,
    ) {
      VoteScreen(state, onSelectOption, onSubmit, onRetry, dismiss)
    }
  }
}

@Composable
internal fun VoteCloseDialogContent(
    state: VoteUiState,
    onConfirm: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
  LaunchedEffect(state) { if (state is VoteUiState.Closed) onDismiss() }
  if (state is VoteUiState.Closed) return
  VoteCloseConfirmation(state, onConfirm, onRetry, onDismiss, modifier)
}

/** Card-only confirmation presentation, including transaction recovery. */
@Composable
internal fun VoteCloseConfirmation(
    state: VoteUiState,
    onConfirm: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val ready = state as? VoteUiState.Ready
  val locked = ready?.let { it.isSubmitting || it.isClosing } == true
  val cancel = { if (!locked) onCancel() }
  AlertDialog(
      modifier = modifier.testTag(C.Tag.vote_close_dialog),
      onDismissRequest = cancel,
      title = {
        Text(
            if (ready != null) stringResource(R.string.vote_close_title_named, ready.vote.question)
            else stringResource(R.string.vote_close_title)
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
          when (state) {
            VoteUiState.Loading -> {
              CircularProgressIndicator()
              Text(stringResource(R.string.vote_loading))
            }
            is VoteUiState.Closed -> Text(stringResource(R.string.vote_closed))
            is VoteUiState.LoadError ->
                VoteErrorContent(
                    if (state.reason == VoteLoadError.NOT_FOUND) R.string.vote_not_found
                    else R.string.vote_load_error,
                    onRetry = if (state.canRetry) onRetry else null,
                )
            is VoteUiState.Ready -> {
              when {
                state.isClosing -> Text(stringResource(R.string.vote_closing))
                !state.isCreator -> Text(stringResource(R.string.vote_close_unavailable))
                else -> {
                  Text(stringResource(R.string.vote_close_confirmation))
                  if (state.closingState == VoteSubmissionState.Failed) {
                    VoteErrorContent(R.string.vote_close_error)
                  }
                  when (state.syncState) {
                    VoteSyncState.Synced -> Unit
                    VoteSyncState.Retrying -> Text(stringResource(R.string.vote_syncing))
                    VoteSyncState.Failed ->
                        VoteErrorContent(
                            R.string.vote_ballots_error,
                            onRetry,
                            retryEnabled = !locked,
                        )
                  }
                }
              }
            }
          }
        }
      },
      confirmButton = {
        if (ready?.isCreator == true && !ready.isClosing) {
          TextButton(
              modifier = Modifier.testTag(C.Tag.vote_close_confirm),
              enabled = ready.canClose,
              onClick = onConfirm,
              colors =
                  ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
          ) {
            Text(stringResource(R.string.vote_close))
          }
        }
      },
      dismissButton = {
        TextButton(enabled = !locked, onClick = cancel) {
          Text(stringResource(R.string.vote_close_cancel))
        }
      },
  )
}
