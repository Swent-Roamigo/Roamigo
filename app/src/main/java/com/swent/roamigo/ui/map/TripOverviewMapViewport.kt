// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapboxExperimental
import com.mapbox.maps.extension.compose.MapState
import com.mapbox.maps.extension.compose.animation.viewport.MapViewportState
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.gestures.GestureInput
import com.mapbox.maps.extension.compose.rememberMapState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Remembers the map viewport and fits it when the destination markers change. */
@Composable
internal fun rememberTripDestinationsMapViewport(
    markers: List<TripMarkerUiState>
): MapViewportState {
  return rememberTripMapViewportState(
      tripMapViewportOf(markers.map { MapCoordinate(it.latitude, it.longitude) })
  )
}

/** Applies changing bounds only while [autoFrame] allows automatic camera movement. */
@Composable
internal fun rememberTripMapViewportState(
    viewport: TripMapViewport,
    autoFrame: Boolean = true,
): MapViewportState {
  val mapViewportState = rememberMapViewportState()

  LaunchedEffect(viewport, autoFrame, mapViewportState) {
    if (autoFrame) mapViewportState.apply(viewport)
  }

  return mapViewportState
}

/** Observes camera gestures even before any trip coordinates have arrived. */
@OptIn(MapboxExperimental::class)
@Composable
internal fun rememberTripMapGesturesState(onCameraGesture: () -> Unit): MapState {
  val mapState = rememberMapState()
  val onGesture by rememberUpdatedState(onCameraGesture)
  mapState.gesturesState.GestureInput {
    coroutineScope {
      launch { detectMoveGestures(onMoveBegin = { onGesture() }) }
      launch { detectScaleGestures(onScaleBegin = { onGesture() }) }
      launch { detectRotateGestures(onRotateBegin = { onGesture() }) }
      launch { detectShoveGestures(onShoveBegin = { onGesture() }) }
    }
  }
  // Double-tap zoom and mouse-wheel animations do not begin a scale gesture.
  LaunchedEffect(mapState) {
    mapState.cameraChangedEvents.collect {
      val gestures = mapState.gesturesState
      if (gestures.isUserAnimationInProgress()) {
        onGesture()
      }
    }
  }
  return mapState
}

private suspend fun MapViewportState.apply(viewport: TripMapViewport) {
  when (viewport) {
    TripMapViewport.None -> Unit
    // Fitting one point would zoom in as far as the map allows, to street level.
    is TripMapViewport.Centered -> setCameraOptions(viewport.toCameraOptions())
    is TripMapViewport.FitAll -> fitCoordinates(viewport.coordinates)
  }
}

/** Zoom showing a whole city around a single point, instead of the closest the map allows. */
internal const val SINGLE_POINT_ZOOM = 11.0

internal fun TripMapViewport.Centered.toCameraOptions(): CameraOptions =
    CameraOptions.Builder()
        .center(Point.fromLngLat(longitude, latitude))
        .zoom(SINGLE_POINT_ZOOM)
        .build()

// Camera fitting depends on the rendered map's dimensions and stays beside the SDK viewport.
private suspend fun MapViewportState.fitCoordinates(coordinates: List<MapCoordinate>) {
  val cameraOptions =
      cameraForCoordinates(
          coordinates = coordinates.map { Point.fromLngLat(it.longitude, it.latitude) },
          coordinatesPadding = EdgeInsets(100.0, 50.0, 100.0, 50.0),
      )
  val adjustedCameraOptions =
      cameraOptions.zoom.let { zoom -> cameraOptions.toBuilder().zoom(zoom?.minus(0.3)).build() }
  // Coordinate fitting can suspend until the map has dimensions; a gesture may cancel it.
  currentCoroutineContext().ensureActive()
  if (adjustedCameraOptions.center != null) {
    setCameraOptions(adjustedCameraOptions)
  }
}
