// AI assistance was used for implementation support.
package com.swent.roamigo.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import com.swent.roamigo.R

internal object PhotoCreditsDialogTestTags {
  const val TITLE = "trip_ready_photo_title"
  const val CREATOR = "trip_ready_photo_creator"
  const val CREDIT = "trip_ready_photo_credit"
  const val CLOSE = "trip_ready_photo_close"
}

@Composable
internal fun PhotoCreditsDialog(
    title: String?,
    creator: String?,
    credit: String?,
    license: String,
    sourceUrl: String?,
    licenseUrl: String?,
    closeLabel: String,
    onDismiss: () -> Unit,
) {
  val uriHandler = LocalUriHandler.current
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(stringResource(R.string.destination_photo_credits_title)) },
      text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
          title?.let { Text(it, Modifier.semantics { testTag = PhotoCreditsDialogTestTags.TITLE }) }
          Text(
              stringResource(
                  R.string.destination_photo_creator,
                  creator ?: stringResource(R.string.destination_photo_public_domain_creator),
              ),
              Modifier.semantics { testTag = PhotoCreditsDialogTestTags.CREATOR },
          )
          credit
              ?.takeIf { it != creator }
              ?.let { Text(it, Modifier.semantics { testTag = PhotoCreditsDialogTestTags.CREDIT }) }
          Text(stringResource(R.string.destination_photo_license_cropped, license))
          sourceUrl?.let { url ->
            TextButton(onClick = { uriHandler.openUri(url) }) {
              Text(stringResource(R.string.destination_photo_view_original))
            }
          }
          licenseUrl?.let { url ->
            TextButton(onClick = { uriHandler.openUri(url) }) {
              Text(stringResource(R.string.destination_photo_view_license))
            }
          }
        }
      },
      confirmButton = {
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.semantics { testTag = PhotoCreditsDialogTestTags.CLOSE },
        ) {
          Text(closeLabel)
        }
      },
  )
}
