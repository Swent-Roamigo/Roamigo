// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mapbox.geojson.Point
import com.mapbox.maps.ViewAnnotationAnchor
import com.mapbox.maps.extension.compose.annotation.ViewAnnotation
import com.mapbox.maps.viewannotation.annotationAnchors
import com.mapbox.maps.viewannotation.geometry
import com.mapbox.maps.viewannotation.viewAnnotationOptions
import com.swent.roamigo.R
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.components.markNotImplemented
import com.swent.roamigo.ui.theme.Spacing

/**
 * The popup of the tapped [activity], attached to its coordinate so that it moves with the map
 * instead of taking a strip of the screen, which landscape has no room for.
 */
@Composable
internal fun ActivityPopupAnnotation(
    activity: TripMarkerUiState,
    onDirectionsClick: () -> Unit,
    onDetailsClick: () -> Unit,
) {
  val gapPx = with(LocalDensity.current) { ActivityPopupGap.toPx().toDouble() }
  ViewAnnotation(
      options =
          viewAnnotationOptions {
            geometry(Point.fromLngLat(activity.longitude, activity.latitude))
            // The pin stands above its point, so the popup hangs below it. Near the left or right
            // edge of the map, Mapbox shifts it sideways instead of cutting it off. Mapbox's
            // positive offsets point up.
            annotationAnchors(
                {
                  anchor(ViewAnnotationAnchor.TOP)
                  offsetY(-gapPx)
                },
                {
                  anchor(ViewAnnotationAnchor.TOP_LEFT)
                  offsetY(-gapPx)
                },
                {
                  anchor(ViewAnnotationAnchor.TOP_RIGHT)
                  offsetY(-gapPx)
                },
            )
            // The popup is what the user is looking at, so other markers never hide it.
            allowOverlap(true)
          }
  ) {
    ActivityPopup(
        name = activity.title,
        onDirectionsClick = onDirectionsClick,
        onDetailsClick = onDetailsClick,
    )
  }
}

/** The activity's name above its two actions, kept narrow so that it stays compact on tablets. */
@Composable
internal fun ActivityPopup(
    name: String,
    onDirectionsClick: () -> Unit,
    onDetailsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Surface(
      modifier =
          modifier.widthIn(max = ActivityPopupMaxWidth).testTag(C.Tag.trip_map_activity_popup),
      shape = MaterialTheme.shapes.medium,
      color = MaterialTheme.colorScheme.surface,
      shadowElevation = Spacing.extraSmall,
  ) {
    Column(
        modifier = Modifier.padding(horizontal = Spacing.cardPadding, vertical = Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
      Text(
          text = name,
          style = MaterialTheme.typography.titleSmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
      Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Button(
            onClick = onDirectionsClick,
            modifier = Modifier.testTag(C.Tag.trip_map_activity_directions),
        ) {
          Text(stringResource(R.string.trip_map_activity_directions))
        }
        OutlinedButton(
            onClick = onDetailsClick,
            modifier =
                Modifier.testTag(C.Tag.trip_map_activity_details)
                    .markNotImplemented(onDetailsClick),
        ) {
          Text(stringResource(R.string.trip_map_activity_details))
        }
      }
    }
  }
}

private val ActivityPopupGap = 4.dp
private val ActivityPopupMaxWidth = 260.dp
