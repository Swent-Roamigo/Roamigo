// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.mapbox.geojson.Point
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.extension.compose.animation.viewport.MapViewportState
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState

/** Remembers the map viewport and fits it when the destination markers change. */
@Composable
internal fun rememberTripDestinationsMapViewport(
    markers: List<TripMarkerUiState>
): MapViewportState {
  val mapViewportState = rememberMapViewportState()

  LaunchedEffect(markers, mapViewportState) { mapViewportState.fitMarkers(markers) }

  return mapViewportState
}

// Camera fitting depends on the rendered map's dimensions and stays beside the SDK viewport.
private suspend fun MapViewportState.fitMarkers(markers: List<TripMarkerUiState>) {
  if (markers.isEmpty()) return

  val cameraOptions =
      cameraForCoordinates(
          coordinates = markers.map { Point.fromLngLat(it.longitude, it.latitude) },
          coordinatesPadding = EdgeInsets(100.0, 50.0, 100.0, 50.0),
      )
  val adjustedCameraOptions =
      cameraOptions.zoom.let { zoom -> cameraOptions.toBuilder().zoom(zoom?.minus(0.3)).build() }
  if (adjustedCameraOptions.center != null) {
    setCameraOptions(adjustedCameraOptions)
  }
}
