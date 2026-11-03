// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.voting

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

/** A fresh interaction scope per opening: dismissal discards the draft and cancels observation. */
@Composable
fun VoteDialog(
    voteId: String,
    createViewModel: (String) -> VoteViewModel,
    modifier: Modifier = Modifier,
    confirmCloseOnOpen: Boolean = false,
    onDismiss: () -> Unit,
) {
  val owner =
      remember(voteId) {
        object : ViewModelStoreOwner {
          override val viewModelStore = ViewModelStore()
        }
      }
  DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
  val viewModel = viewModel(viewModelStoreOwner = owner) { createViewModel(voteId) }
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val dismiss = {
    if ((state as? VoteUiState.Ready)?.let { it.isSubmitting || it.isClosing } != true) onDismiss()
  }
  VoteDialogContent(
      state,
      viewModel::selectOption,
      viewModel::submitVote,
      viewModel::retryLoad,
      dismiss,
      modifier,
      viewModel::closeVote,
      confirmCloseOnOpen,
  )
}

/** Stateless dialog presentation; [VoteDialog] supplies the interaction state and callbacks. */
@Composable
internal fun VoteDialogContent(
    state: VoteUiState,
    onSelectOption: (String) -> Unit,
    onSubmit: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onCloseVote: () -> Unit = {},
    confirmCloseOnOpen: Boolean = false,
) {
  var confirming by remember { mutableStateOf(confirmCloseOnOpen) }
  val ready = state as? VoteUiState.Ready
  Dialog(onDismissRequest = onDismiss) {
    Surface(
        modifier.fillMaxWidth().heightIn(max = 600.dp).testTag("vote_dialog"),
        shape = MaterialTheme.shapes.large,
    ) {
      VoteScreenContent(
          state,
          onSelectOption,
          onSubmit,
          onRetry,
          onDismiss,
          onCloseVote = { confirming = true },
      )
    }
  }
  if (confirming && ready?.canClose == true) {
    AlertDialog(
        onDismissRequest = { confirming = false },
        title = { Text(stringResource(R.string.vote_close_title)) },
        text = { Text(stringResource(R.string.vote_close_confirmation)) },
        confirmButton = {
          TextButton(
              modifier = Modifier.testTag("vote_close_confirm"),
              onClick = {
                confirming = false
                onCloseVote()
              },
          ) {
            Text(stringResource(R.string.vote_close))
          }
        },
        dismissButton = {
          TextButton(onClick = { confirming = false }) {
            Text(stringResource(R.string.vote_close_cancel))
          }
        },
    )
  }
}
