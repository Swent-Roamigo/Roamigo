// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.Ink
import com.swent.roamigo.ui.theme.Ochre

/** Test tag of the photo marker of the group at [coordinate]. */
internal fun photoMarkerTag(coordinate: MapCoordinate): String =
    "${C.Tag.trip_map_photo_marker_prefix}${coordinate.latitude}_${coordinate.longitude}"

/**
 * A square camera badge, unlike the activity pins and the members' round avatars, with the number
 * of photos when the group holds more than one. The caller provides the accessible label, since it
 * also owns the click.
 */
@Composable
internal fun PhotoMarkerIcon(photoCount: Int) {
  // Room is kept around the badge so the count can overlap its corner without being clipped.
  Box(modifier = Modifier.padding(PhotoMarkerCountOverhang)) {
    Surface(
        modifier = Modifier.size(PhotoMarkerSize),
        shape = RoundedCornerShape(10.dp),
        color = Ochre,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface),
        shadowElevation = 2.dp,
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
            imageVector = Icons.Outlined.PhotoCamera,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
      }
    }
    if (photoCount > 1) {
      Surface(
          modifier =
              Modifier.align(Alignment.TopEnd)
                  .offset(x = PhotoMarkerCountOverhang, y = -PhotoMarkerCountOverhang)
                  .sizeIn(minWidth = 20.dp, minHeight = 20.dp)
                  .testTag(C.Tag.trip_map_photo_marker_count)
                  // The marker's label already says how many photos it holds.
                  .clearAndSetSemantics {},
          shape = CircleShape,
          color = Ink,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.surface),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Text(
              text = photoCount.toString(),
              modifier = Modifier.padding(horizontal = 4.dp),
              style = MaterialTheme.typography.labelSmall,
              color = Color.White,
              textAlign = TextAlign.Center,
          )
        }
      }
    }
  }
}

private val PhotoMarkerSize = 36.dp
private val PhotoMarkerCountOverhang = 8.dp
