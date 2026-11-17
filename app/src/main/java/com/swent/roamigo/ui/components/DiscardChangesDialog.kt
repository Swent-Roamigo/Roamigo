// AI assistance was used for implementation support.
package com.swent.roamigo.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.swent.roamigo.R

/**
 * Asks before leaving a form whose changes would be lost.
 *
 * @param title what would be discarded, as a question.
 * @param message what the user loses by discarding.
 * @param onDiscard called when the user confirms leaving.
 * @param onKeepEditing called when the user stays, including when the dialog is dismissed.
 * @param discardTestTag the test tag of the discard button.
 */
@Composable
fun DiscardChangesDialog(
    title: String,
    message: String,
    onDiscard: () -> Unit,
    onKeepEditing: () -> Unit,
    discardTestTag: String,
    modifier: Modifier = Modifier,
) {
  AlertDialog(
      onDismissRequest = onKeepEditing,
      title = { Text(title) },
      text = { Text(message) },
      confirmButton = {
        TextButton(onClick = onDiscard, modifier = Modifier.testTag(discardTestTag)) {
          Text(
              stringResource(R.string.discard_changes_confirm),
              color = MaterialTheme.colorScheme.error,
          )
        }
      },
      dismissButton = {
        TextButton(onClick = onKeepEditing) {
          Text(stringResource(R.string.discard_changes_keep_editing))
        }
      },
      modifier = modifier,
  )
}
