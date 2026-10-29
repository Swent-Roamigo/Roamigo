// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.creation.components

import android.os.Bundle
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
import com.swent.roamigo.ui.trip.creation.TripReadyTestTags

@Composable
internal fun TripReadyPhotoCreditsDialog(photo: Bundle, onDismiss: () -> Unit) {
  val uriHandler = LocalUriHandler.current
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(stringResource(R.string.destination_photo_credits_title)) },
      text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
          photo.getString("title")?.let {
            Text(it, Modifier.semantics { testTag = TripReadyTestTags.PHOTO_TITLE })
          }
          Text(
              stringResource(
                  R.string.destination_photo_creator,
                  photo.getString("creator")
                      ?: stringResource(R.string.destination_photo_public_domain_creator),
              ),
              Modifier.semantics { testTag = TripReadyTestTags.PHOTO_CREATOR },
          )
          photo
              .getString("credit")
              ?.takeIf { it != photo.getString("creator") }
              ?.let { Text(it, Modifier.semantics { testTag = TripReadyTestTags.PHOTO_CREDIT }) }
          Text(
              stringResource(
                  R.string.destination_photo_license_cropped,
                  photo.getString("license").orEmpty(),
              )
          )
          photo.getString("source")?.let { url ->
            TextButton(onClick = { uriHandler.openUri(url) }) {
              Text(stringResource(R.string.destination_photo_view_original))
            }
          }
          photo.getString("licenseUrl")?.let { url ->
            TextButton(onClick = { uriHandler.openUri(url) }) {
              Text(stringResource(R.string.destination_photo_view_license))
            }
          }
        }
      },
      confirmButton = {
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.semantics { testTag = TripReadyTestTags.PHOTO_CLOSE },
        ) {
          Text(stringResource(android.R.string.ok))
        }
      },
  )
}
