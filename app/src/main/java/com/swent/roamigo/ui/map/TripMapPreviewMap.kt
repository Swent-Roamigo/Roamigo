// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import com.swent.roamigo.model.location.MemberLocation
import kotlin.math.roundToInt

/**
 * Share of the stand-in's width and height kept free on each side, so that markers anchored on the
 * outermost locations stay on screen.
 */
internal const val PREVIEW_MAP_PADDING = 0.15f

/**
 * Projects coordinates linearly into a unit box, framing all of [frame] with [padding] on each
 * side. North is up. An axis along which the frame has no extent is centered.
 *
 * @param frame the coordinates that must fit in the box.
 * @param padding share of the box kept free on each side, between 0 and 0.5.
 * @return a function giving the position of a coordinate, as fractions of the box's width and
 *   height.
 */
internal fun previewMapProjection(
    frame: List<MapCoordinate>,
    padding: Float = PREVIEW_MAP_PADDING,
): (MapCoordinate) -> Offset {
  val minLatitude = frame.minOfOrNull { it.latitude } ?: 0.0
  val maxLatitude = frame.maxOfOrNull { it.latitude } ?: 0.0
  val minLongitude = frame.minOfOrNull { it.longitude } ?: 0.0
  val maxLongitude = frame.maxOfOrNull { it.longitude } ?: 0.0

  fun scale(value: Double, min: Double, max: Double): Float =
      if (max == min) 0.5f
      else padding + ((value - min) / (max - min)).toFloat() * (1 - 2 * padding)

  return { coordinate ->
    Offset(
        x = scale(coordinate.longitude, minLongitude, maxLongitude),
        y = 1f - scale(coordinate.latitude, minLatitude, maxLatitude),
    )
  }
}

/**
 * Static stand-in for the Mapbox map of [TripMapScreen], used only in Android Studio previews.
 * Mapbox and its view annotations cannot render there, so the previews would show none of the
 * markers.
 *
 * The markers are the real map's, placed with [previewMapProjection] so that [viewport] and every
 * marker are framed, and anchored like the real map's annotations.
 */
@Composable
internal fun TripMapPreviewMap(
    viewport: TripMapViewport,
    activityMarkers: List<TripMarkerUiState>,
    memberLocations: List<MemberLocation>,
    members: Map<String, MemberDisplayInfo>,
    staleMemberIds: Set<String>,
    myLocation: MemberLocation?,
    nowMillis: Long,
    modifier: Modifier = Modifier,
) {
  Layout(
      content = {
        activityMarkers.forEach { marker ->
          Box(Modifier.pin(marker.latitude, marker.longitude, PinAnchor.BOTTOM)) {
            TripMarkerIcon(locationName = marker.locationName, kind = marker.kind)
          }
        }
        memberLocations.forEach { location ->
          MemberLocationMarker(
              location = location,
              member = members[location.userId] ?: memberDisplayInfo(location.userId, null),
              nowMillis = nowMillis,
              isStale = location.userId in staleMemberIds,
              modifier = Modifier.pin(location.latitude, location.longitude, PinAnchor.BOTTOM),
          )
        }
        if (myLocation != null) {
          Box(Modifier.pin(myLocation.latitude, myLocation.longitude, PinAnchor.CENTER)) {
            MyLocationDot()
          }
        }
      },
      modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
  ) { measurables, constraints ->
    val pins = measurables.map { it.layoutId as Pin }
    val project = previewMapProjection(viewport.coordinates() + pins.map { it.coordinate })
    val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
    layout(constraints.maxWidth, constraints.maxHeight) {
      placeables.zip(pins).forEach { (placeable, pin) ->
        val position = project(pin.coordinate)
        val anchorY =
            when (pin.anchor) {
              PinAnchor.BOTTOM -> placeable.height
              PinAnchor.CENTER -> placeable.height / 2
            }
        placeable.place(
            x = (position.x * constraints.maxWidth).roundToInt() - placeable.width / 2,
            y = (position.y * constraints.maxHeight).roundToInt() - anchorY,
        )
      }
    }
  }
}

private enum class PinAnchor {
  BOTTOM,
  CENTER,
}

private data class Pin(val coordinate: MapCoordinate, val anchor: PinAnchor)

private fun Modifier.pin(latitude: Double, longitude: Double, anchor: PinAnchor): Modifier =
    layoutId(Pin(MapCoordinate(latitude, longitude), anchor))

private fun TripMapViewport.coordinates(): List<MapCoordinate> =
    when (this) {
      TripMapViewport.None -> emptyList()
      is TripMapViewport.Centered -> listOf(MapCoordinate(latitude, longitude))
      is TripMapViewport.FitAll -> coordinates
    }
