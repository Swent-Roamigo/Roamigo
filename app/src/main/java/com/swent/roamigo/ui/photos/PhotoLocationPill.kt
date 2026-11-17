// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.swent.roamigo.R
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.Spacing

/**
 * Tells whether the photo will be geotagged. Only [PhotoLocationStatus.Off] can be tapped, to go
 * and turn the location on in the trip map; the pill itself never asks for a permission.
 *
 * @param status the location state of the photo being previewed.
 * @param onOpenMap called when the user taps the pill while the location is off.
 */
@Composable
fun PhotoLocationPill(
    status: PhotoLocationStatus,
    onOpenMap: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val openMapLabel = stringResource(R.string.photo_preview_location_open_map)
  val isOff = status is PhotoLocationStatus.Off
  val shape = RoundedCornerShape(50)
  Row(
      modifier
          .testTag(C.Tag.photo_preview_location)
          .then(
              if (isOff) {
                Modifier.clip(shape)
                    .clickable(role = Role.Button, onClickLabel = openMapLabel, onClick = onOpenMap)
                    .minimumInteractiveComponentSize()
              } else {
                Modifier
              }
          )
          .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
          .background(Color.Black.copy(alpha = 0.6f), shape)
          .padding(horizontal = Spacing.large, vertical = Spacing.small),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(Spacing.small),
  ) {
    when (status) {
      PhotoLocationStatus.Locating ->
          CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
      is PhotoLocationStatus.Attached -> PillIcon(Icons.Filled.LocationOn)
      PhotoLocationStatus.Unavailable,
      PhotoLocationStatus.Off -> PillIcon(Icons.Filled.LocationOff)
    }
    Text(
        text =
            stringResource(
                when (status) {
                  PhotoLocationStatus.Locating -> R.string.photo_preview_location_locating
                  is PhotoLocationStatus.Attached -> R.string.photo_preview_location_attached
                  PhotoLocationStatus.Unavailable -> R.string.photo_preview_location_unavailable
                  PhotoLocationStatus.Off -> R.string.photo_preview_location_off
                }
            ),
        style = MaterialTheme.typography.bodySmall,
        color = Color.White,
    )
  }
}

/** Decorative: the text next to it is the label. */
@Composable
private fun PillIcon(icon: ImageVector) {
  Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
}
