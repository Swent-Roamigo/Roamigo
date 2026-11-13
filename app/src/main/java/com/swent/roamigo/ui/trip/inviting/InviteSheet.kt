// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.inviting

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swent.roamigo.R
import com.swent.roamigo.model.trip.inviting.TripInvitationService
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.components.TripActionButton
import com.swent.roamigo.ui.theme.Spacing

/** Creates the invitation link of [tripId] and shows it in a bottom sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteRoute(
    tripId: String,
    service: TripInvitationService,
    onDismiss: () -> Unit,
) {
  val viewModel: InviteViewModel =
      viewModel(
          key = "invite_$tripId",
          factory = InviteViewModel.factory(tripId, service),
      )
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current
  ModalBottomSheet(
      onDismissRequest = onDismiss,
      sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
      containerColor = MaterialTheme.colorScheme.background,
  ) {
    InviteSheetContent(
        state = state,
        onShare = { ready -> shareInvitation(context, ready) },
        onCopyCode = { ready -> copyCode(context, ready.token) },
        onStopSharing = viewModel::stopSharing,
        onCreateLink = viewModel::load,
    )
  }
}

/** The sheet's content, separate from the bottom sheet so it can be tested and previewed. */
@Composable
fun InviteSheetContent(
    state: InviteUiState,
    onShare: (InviteUiState.Ready) -> Unit,
    onCopyCode: (InviteUiState.Ready) -> Unit,
    onStopSharing: () -> Unit,
    onCreateLink: () -> Unit,
    modifier: Modifier = Modifier,
) {
  var confirmingStop by rememberSaveable { mutableStateOf(false) }
  Column(
      modifier
          .fillMaxWidth()
          .testTag(C.Tag.invite_sheet)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = Spacing.screenPadding)
          .padding(bottom = Spacing.extraLarge),
      verticalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    Text(stringResource(R.string.invite_title), style = MaterialTheme.typography.titleLarge)
    when (state) {
      InviteUiState.Loading ->
          Box(Modifier.fillMaxWidth().padding(Spacing.large), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
          }
      is InviteUiState.Ready -> {
        Text(
            stringResource(R.string.invite_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
              Modifier.padding(Spacing.large),
              verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text(
                stringResource(R.string.invite_code_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                state.token,
                modifier = Modifier.testTag(C.Tag.invite_code),
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
            )
          }
        }
        TripActionButton(
            onClick = { onShare(state) },
            modifier = Modifier.testTag(C.Tag.invite_share),
        ) {
          Text(stringResource(R.string.invite_share))
        }
        TripActionButton(
            onClick = { onCopyCode(state) },
            modifier = Modifier.testTag(C.Tag.invite_copy),
            outlined = true,
        ) {
          Text(stringResource(R.string.invite_copy_code))
        }
        TextButton(
            onClick = { confirmingStop = true },
            modifier = Modifier.fillMaxWidth().testTag(C.Tag.invite_stop),
        ) {
          Text(
              stringResource(R.string.invite_stop),
              color = MaterialTheme.colorScheme.error,
          )
        }
      }
      InviteUiState.Stopped -> {
        Text(
            stringResource(R.string.invite_stopped),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TripActionButton(
            onClick = onCreateLink,
            modifier = Modifier.testTag(C.Tag.invite_new_link),
        ) {
          Text(stringResource(R.string.invite_new_link))
        }
      }
      is InviteUiState.Failed -> {
        Text(
            stringResource(
                if (state.notAllowed) R.string.invite_not_allowed else R.string.invite_failed
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!state.notAllowed) {
          TripActionButton(
              onClick = onCreateLink,
              modifier = Modifier.testTag(C.Tag.invite_retry),
          ) {
            Text(stringResource(R.string.invite_retry))
          }
        }
      }
    }
  }
  if (confirmingStop) {
    // Cancel is always available, and the destructive action says what it does.
    AlertDialog(
        onDismissRequest = { confirmingStop = false },
        title = { Text(stringResource(R.string.invite_stop_title)) },
        text = { Text(stringResource(R.string.invite_stop_message)) },
        confirmButton = {
          TextButton(
              onClick = {
                confirmingStop = false
                onStopSharing()
              },
              modifier = Modifier.testTag(C.Tag.invite_stop_confirm),
          ) {
            Text(
                stringResource(R.string.invite_stop_confirm),
                color = MaterialTheme.colorScheme.error,
            )
          }
        },
        dismissButton = {
          TextButton(onClick = { confirmingStop = false }) {
            Text(stringResource(R.string.invite_cancel))
          }
        },
    )
  }
}

private fun shareInvitation(context: Context, ready: InviteUiState.Ready) {
  val message = context.getString(R.string.invite_share_message, ready.link, ready.token)
  val send =
      Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, message)
      }
  context.startActivity(Intent.createChooser(send, context.getString(R.string.invite_share)))
}

private fun copyCode(context: Context, token: String) {
  val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
  clipboard.setPrimaryClip(
      ClipData.newPlainText(context.getString(R.string.invite_code_label), token)
  )
  Toast.makeText(context, R.string.invite_copied, Toast.LENGTH_SHORT).show()
}
