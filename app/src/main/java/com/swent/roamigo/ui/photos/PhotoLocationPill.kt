// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.swent.roamigo.R
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.RoamigoShapes
import com.swent.roamigo.ui.theme.Spacing

/**
 * Tells whether the photo will be geotagged. [PhotoLocationStatus.Off] can be tapped, to go and
 * turn the location on in the trip map, and [PhotoLocationStatus.NextPhotoOnly], to retake the
 * photo now that the location is on; the pill itself never asks for a permission.
 *
 * @param status the location state of the photo being previewed.
 * @param onOpenMap called when the user taps the pill while the location is off.
 * @param onRetake called when the user taps the pill to retake the photo with the location now on.
 * @param enabled whether the pill can be tapped; when false it still shows the status, but runs no
 *   action, like the buttons it sits above while the photo is saving.
 */
@Composable
fun PhotoLocationPill(
    status: PhotoLocationStatus,
    onOpenMap: () -> Unit,
    onRetake: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
  val action: Pair<String, () -> Unit>? =
      when (status) {
        PhotoLocationStatus.Off ->
            stringResource(R.string.photo_preview_location_open_map) to onOpenMap
        PhotoLocationStatus.NextPhotoOnly ->
            stringResource(R.string.photo_preview_location_retake) to onRetake
        else -> null
      }
  val shape = RoamigoShapes.pill
  // Same dimming as the disabled Cancel button.
  val contentColor = Color.White.copy(alpha = if (enabled) 1f else 0.5f)
  Row(
      modifier
          .testTag(C.Tag.photo_preview_location)
          .then(
              if (action == null) {
                Modifier
              } else if (enabled) {
                Modifier.clip(shape)
                    .clickable(
                        role = Role.Button,
                        onClickLabel = action.first,
                        onClick = action.second,
                    )
                    .minimumInteractiveComponentSize()
              } else {
                // A disabled clickable still exposes its click action, so it is left out.
                Modifier.minimumInteractiveComponentSize().semantics {
                  role = Role.Button
                  disabled()
                }
              }
          )
          .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
          .background(Color.Black.copy(alpha = if (enabled) 0.6f else 0.3f), shape)
          .padding(horizontal = Spacing.large, vertical = Spacing.small),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(Spacing.small),
  ) {
    // Locating has no icon: a progress indicator shows the position is still being read.
    val icon =
        when (status) {
          PhotoLocationStatus.Locating -> null
          is PhotoLocationStatus.Attached -> Icons.Filled.LocationOn
          PhotoLocationStatus.Unavailable,
          PhotoLocationStatus.Off,
          PhotoLocationStatus.NextPhotoOnly -> Icons.Filled.LocationOff
        }
    if (icon == null) {
      CircularProgressIndicator(Modifier.size(16.dp), color = contentColor, strokeWidth = 2.dp)
    } else {
      PillIcon(icon, contentColor)
    }
    Text(
        text =
            stringResource(
                when (status) {
                  PhotoLocationStatus.Locating -> R.string.photo_preview_location_locating
                  is PhotoLocationStatus.Attached -> R.string.photo_preview_location_attached
                  PhotoLocationStatus.Unavailable -> R.string.photo_preview_location_unavailable
                  PhotoLocationStatus.Off -> R.string.photo_preview_location_off
                  PhotoLocationStatus.NextPhotoOnly -> R.string.photo_preview_location_next_photo
                }
            ),
        style = MaterialTheme.typography.bodySmall,
        color = contentColor,
    )
  }
}

/** Decorative: the text next to it is the label. */
@Composable
private fun PillIcon(icon: ImageVector, tint: Color) {
  Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
}
