// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
 * @param location the photo's location status, or null to show none.
 * @param onOpenMap opens the trip's Map tab, where location can be turned on.
 */
@Composable
fun PhotoPreview(
    file: File,
    isOnline: Boolean,
    saving: Boolean,
    saveFailed: Boolean,
    location: PhotoLocationStatus?,
    onSave: () -> Unit,
    onRetake: () -> Unit,
    onClose: () -> Unit,
    onOpenMap: () -> Unit,
    modifier: Modifier = Modifier,
) {
  BoxWithConstraints(modifier.fillMaxSize().background(Color.Black).testTag(C.Tag.photo_preview)) {
    AsyncImage(
        model = file,
        contentDescription = stringResource(R.string.photo_preview_description),
        modifier = Modifier.fillMaxSize().testTag(C.Tag.photo_preview_image),
        contentScale = ContentScale.Crop,
    )
    if (maxHeight < COMPACT_HEIGHT) {
      CompactOverlay(
          isOnline = isOnline,
          saving = saving,
          saveFailed = saveFailed,
          location = location,
          onSave = onSave,
          onRetake = onRetake,
          onClose = onClose,
          onOpenMap = onOpenMap,
      )
    } else {
      Row(
          Modifier.fillMaxWidth().safeDrawingPadding().padding(Spacing.large),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
      ) {
        if (!isOnline) OfflinePill() else Box(Modifier)
        CloseButton(saving, onClose)
      }
      Column(
          Modifier.align(Alignment.BottomCenter)
              .fillMaxWidth()
              .safeDrawingPadding()
              .padding(Spacing.screenPadding),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(Spacing.medium),
      ) {
        StatusPills(isOnline, saveFailed, location, onOpenMap, onRetake)
        SaveButton(isOnline, saving, onSave, Modifier.fillMaxWidth().height(50.dp))
        RetakeButton(saving, onRetake, Modifier.fillMaxWidth().height(52.dp))
      }
    }
  }
}

/**
 * The preview in a window too short for the stacked layout, such as a landscape phone under the
 * trip's bars: the messages wrap in one band beside Cancel, and the actions sit side by side.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompactOverlay(
    isOnline: Boolean,
    saving: Boolean,
    saveFailed: Boolean,
    location: PhotoLocationStatus?,
    onSave: () -> Unit,
    onRetake: () -> Unit,
    onClose: () -> Unit,
    onOpenMap: () -> Unit,
) {
  Column(
      Modifier.fillMaxSize().safeDrawingPadding().padding(Spacing.small),
      verticalArrangement = Arrangement.SpaceBetween,
  ) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
      FlowRow(
          Modifier.weight(1f),
          horizontalArrangement = Arrangement.spacedBy(Spacing.small),
          verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
      ) {
        if (!isOnline) OfflinePill()
        StatusPills(isOnline, saveFailed, location, onOpenMap, onRetake)
      }
      CloseButton(saving, onClose)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
      SaveButton(isOnline, saving, onSave, Modifier.weight(1f).height(48.dp))
      RetakeButton(saving, onRetake, Modifier.weight(1f).height(48.dp))
    }
  }
}

@Composable
private fun OfflinePill() {
  Pill(stringResource(R.string.photo_preview_offline), C.Tag.photo_preview_offline)
}

/** The location status, what saving will do, and the last failure, if any. */
@Composable
private fun StatusPills(
    isOnline: Boolean,
    saveFailed: Boolean,
    location: PhotoLocationStatus?,
    onOpenMap: () -> Unit,
    onRetake: () -> Unit,
) {
  location?.let { PhotoLocationPill(it, onOpenMap, onRetake) }
  Pill(
      stringResource(
          if (isOnline) R.string.photo_preview_note_online else R.string.photo_preview_note_offline
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
}

// A labelled button on a dark pill: a bare cross disappears on a bright photo.
@Composable
private fun CloseButton(saving: Boolean, onClose: () -> Unit) {
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

@Composable
private fun SaveButton(isOnline: Boolean, saving: Boolean, onSave: () -> Unit, modifier: Modifier) {
  Button(
      onClick = onSave,
      enabled = !saving,
      modifier = modifier.testTag(C.Tag.photo_preview_save),
      shape = MaterialTheme.shapes.medium,
  ) {
    Text(
        stringResource(
            if (isOnline) R.string.photo_preview_save_online
            else R.string.photo_preview_save_offline
        )
    )
  }
}

@Composable
private fun RetakeButton(saving: Boolean, onRetake: () -> Unit, modifier: Modifier) {
  Button(
      onClick = onRetake,
      enabled = !saving,
      modifier = modifier.testTag(C.Tag.photo_preview_retake),
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

/** Below this height the stacked layout no longer leaves room for the actions. */
private val COMPACT_HEIGHT = 320.dp

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
