// AI assistance was used for implementation support.
package com.swent.roamigo.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * Asks the user to confirm an action that cannot be undone, such as discarding a draft.
 *
 * @param title the action, as a question.
 * @param message what the user loses by confirming.
 * @param confirmLabel the label of the destructive button, shown in the error color.
 * @param dismissLabel the label of the button that cancels the action.
 * @param onConfirm called when the user confirms the action.
 * @param onDismiss called when the user cancels, including when the dialog is dismissed.
 * @param confirmTestTag the test tag of the destructive button.
 */
@Composable
fun DestructiveConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmTestTag: String,
    modifier: Modifier = Modifier,
) {
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(title) },
      text = { Text(message) },
      confirmButton = {
        TextButton(onClick = onConfirm, modifier = Modifier.testTag(confirmTestTag)) {
          Text(confirmLabel, color = MaterialTheme.colorScheme.error)
        }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel) } },
      modifier = modifier,
  )
}
