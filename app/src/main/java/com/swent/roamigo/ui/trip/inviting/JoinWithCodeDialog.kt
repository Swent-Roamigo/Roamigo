// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.inviting

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.swent.roamigo.R
import com.swent.roamigo.model.trip.inviting.InvitationLinks
import com.swent.roamigo.resources.C

/**
 * Asks for an invitation link or code, for when a messaging app did not make the link clickable.
 * [onSubmit] receives the token inside what the user pasted.
 */
@Composable
fun JoinWithCodeDialog(onSubmit: (token: String) -> Unit, onDismiss: () -> Unit) {
  var text by rememberSaveable { mutableStateOf("") }
  var showError by rememberSaveable { mutableStateOf(false) }
  AlertDialog(
      onDismissRequest = onDismiss,
      modifier = Modifier.testTag(C.Tag.join_code_dialog),
      title = { Text(stringResource(R.string.join_code_title)) },
      text = {
        Column {
          Text(stringResource(R.string.join_code_hint))
          OutlinedTextField(
              value = text,
              onValueChange = {
                text = it
                showError = false
              },
              modifier = Modifier.fillMaxWidth().testTag(C.Tag.join_code_field),
              label = { Text(stringResource(R.string.join_code_label)) },
              singleLine = true,
              isError = showError,
              supportingText = { if (showError) Text(stringResource(R.string.join_code_error)) },
          )
        }
      },
      confirmButton = {
        TextButton(
            onClick = {
              val token = InvitationLinks.tokenFrom(text)
              if (token == null) showError = true else onSubmit(token)
            },
            modifier = Modifier.testTag(C.Tag.join_code_submit),
        ) {
          Text(stringResource(R.string.join_code_submit))
        }
      },
      dismissButton = {
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.invite_cancel)) }
      },
  )
}
