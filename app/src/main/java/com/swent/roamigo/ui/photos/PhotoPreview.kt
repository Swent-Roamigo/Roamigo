// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.swent.roamigo.R
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.Spacing
import java.io.File

/**
 * The photo just taken, to keep or retake. Offline, it says the photo will only be saved on this
 * phone for now, so the user is not surprised that nobody sees it yet.
 *
 * @param file the photo taken.
 * @param isOnline whether the device can reach the internet.
 * @param saving whether the photo is being saved; the buttons wait for it.
 * @param saveFailed whether the last attempt to save failed.
 */
@Composable
fun PhotoPreview(
    file: File,
    isOnline: Boolean,
    saving: Boolean,
    saveFailed: Boolean,
    onSave: () -> Unit,
    onRetake: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Box(modifier.fillMaxSize().background(Color.Black).testTag(C.Tag.photo_preview)) {
    AsyncImage(
        model = file,
        contentDescription = stringResource(R.string.photo_preview_description),
        modifier = Modifier.fillMaxSize().testTag(C.Tag.photo_preview_image),
        contentScale = ContentScale.Crop,
    )
    Row(
        Modifier.fillMaxWidth().safeDrawingPadding().padding(Spacing.large),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
      if (!isOnline) {
        Pill(stringResource(R.string.photo_preview_offline), C.Tag.photo_preview_offline)
      } else {
        Box(Modifier)
      }
      // A labelled button on a dark pill: a bare cross disappears on a bright photo.
      TextButton(
          onClick = onClose,
          modifier = Modifier.testTag(C.Tag.photo_preview_close),
          enabled = !saving,
          colors =
              ButtonDefaults.textButtonColors(
                  containerColor = Color.Black.copy(alpha = 0.6f),
                  contentColor = Color.White,
                  disabledContainerColor = Color.Black.copy(alpha = 0.3f),
                  disabledContentColor = Color.White.copy(alpha = 0.5f),
              ),
      ) {
        Icon(Icons.Filled.Close, contentDescription = null)
        Text(
            stringResource(R.string.photo_preview_cancel),
            modifier = Modifier.padding(start = Spacing.small),
        )
      }
    }
    Column(
        Modifier.align(Alignment.BottomCenter)
            .fillMaxWidth()
            .safeDrawingPadding()
            .padding(Spacing.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
      Pill(
          stringResource(
              if (isOnline) R.string.photo_preview_note_online
              else R.string.photo_preview_note_offline
          ),
          C.Tag.photo_preview_note,
      )
      if (saveFailed) {
        Pill(
            stringResource(R.string.photo_preview_save_failed),
            C.Tag.photo_preview_error,
            color = MaterialTheme.colorScheme.error,
        )
      }
      Button(
          onClick = onSave,
          enabled = !saving,
          modifier = Modifier.fillMaxWidth().height(50.dp).testTag(C.Tag.photo_preview_save),
          shape = MaterialTheme.shapes.medium,
      ) {
        Text(
            stringResource(
                if (isOnline) R.string.photo_preview_save_online
                else R.string.photo_preview_save_offline
            )
        )
      }
      Button(
          onClick = onRetake,
          enabled = !saving,
          modifier = Modifier.fillMaxWidth().height(52.dp).testTag(C.Tag.photo_preview_retake),
          shape = MaterialTheme.shapes.medium,
          colors =
              ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.background,
                  contentColor = MaterialTheme.colorScheme.onBackground,
              ),
      ) {
        Text(stringResource(R.string.photo_preview_retake))
      }
    }
  }
}

/** A short caption over the photo, readable on any picture. */
@Composable
private fun Pill(text: String, tag: String, color: Color = Color.White) {
  Text(
      text,
      modifier =
          Modifier.testTag(tag)
              .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
              .padding(horizontal = Spacing.large, vertical = Spacing.small),
      style = MaterialTheme.typography.bodySmall,
      color = color,
  )
}
