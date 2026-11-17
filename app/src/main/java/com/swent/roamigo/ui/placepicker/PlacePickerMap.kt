// Implemented with assistance from OpenAI Codex.
package com.swent.roamigo.ui.placepicker

import android.view.MotionEvent
import android.view.View
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.UiComposable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.mapbox.geojson.Point
import com.mapbox.maps.MapLoadingErrorType
import com.mapbox.maps.Style
import com.mapbox.maps.ViewAnnotationAnchor
import com.mapbox.maps.extension.compose.DisposableMapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.ViewAnnotation
import com.mapbox.maps.extension.compose.rememberMapState
import com.mapbox.maps.extension.compose.style.MapStyle
import com.mapbox.maps.extension.compose.style.MapboxStyleComposable
import com.mapbox.maps.viewannotation.annotationAnchor
import com.mapbox.maps.viewannotation.geometry
import com.mapbox.maps.viewannotation.viewAnnotationOptions
import com.swent.roamigo.R

/** Thin Mapbox adapter. The screen owns layout; the ViewModel owns all selection decisions. */
@Composable
@UiComposable
internal fun PlacePickerMap(
    state: PlacePickerUiState,
    onPointSelected: (Double, Double) -> Unit,
    onLoadingFailed: () -> Unit,
    onLoaded: () -> Unit,
    modifier: Modifier = Modifier,
    style: String = Style.STANDARD,
) {
  // Initialise once, not on marker/name changes. The SDK saver also preserves pan/zoom on rotation.
  val viewport = rememberMapViewportState {
    setCameraOptions {
      center(
          state.mapInitialCenter?.let { Point.fromLngLat(it.longitude, it.latitude) }
              ?: Point.fromLngLat(0.0, 20.0)
      )
      zoom(if (state.mapInitialCenter != null) 12.0 else 1.0)
    }
  }
  val mapState = rememberMapState()
  val currentOnFailed by rememberUpdatedState(onLoadingFailed)
  val currentOnLoaded by rememberUpdatedState(onLoaded)
  LaunchedEffect(mapState) {
    mapState.mapLoadingErrorEvents.collect { if (it.type.blocksPlacePickerMap()) currentOnFailed() }
  }
  // Recovery should not wait for every detail tile or glyph to finish loading.
  LaunchedEffect(mapState) { mapState.styleLoadedEvents.collect { currentOnLoaded() } }
  val description = stringResource(R.string.place_picker_map_description)
  MapboxMap(
      modifier =
          modifier.testTag(PlacePickerTestTags.MAP).semantics { contentDescription = description },
      // Keep UI ornament and Mapbox style/map composition targets distinct during inference.
      compass = { Compass() },
      scaleBar = { ScaleBar() },
      logo = { Logo() },
      attribution = { Attribution() },
      mapViewportState = viewport,
      mapState = mapState,
      onMapLongClickListener = { point ->
        onPointSelected(point.latitude(), point.longitude())
        true
      },
      // Retry only the style: rebuilding the whole map would discard the user's camera position.
      style =
          @Composable @MapboxStyleComposable {
            key(state.mapLoadAttempt) { MapStyle(style = style) }
          },
      content = {
        DisposableMapEffect(Unit) { mapView ->
          mapView.setOnTouchListener(placePickerMapTouchListener)
          onDispose { mapView.setOnTouchListener(null) }
        }
        state.manualDraft.point?.let { point ->
          ViewAnnotation(
              options =
                  viewAnnotationOptions {
                    geometry(Point.fromLngLat(point.longitude, point.latitude))
                    annotationAnchor { anchor(ViewAnnotationAnchor.BOTTOM) }
                  },
              modifier = Modifier.testTag(PlacePickerTestTags.MAP_MARKER),
          ) {
            PlacePickerMapMarker()
          }
        }
      },
  )
}

// Style/source metadata failures can prevent loading; individual tiles, glyphs and sprites do not.
internal fun MapLoadingErrorType.blocksPlacePickerMap() =
    this == MapLoadingErrorType.STYLE || this == MapLoadingErrorType.SOURCE

// Let the native map own a gesture started inside it, instead of the surrounding vertical scroller.
// Returning false leaves pan, zoom and long-press detection to Mapbox.
internal val placePickerMapTouchListener = View.OnTouchListener { view, event ->
  when (event.actionMasked) {
    MotionEvent.ACTION_DOWN -> view.parent?.requestDisallowInterceptTouchEvent(true)
    MotionEvent.ACTION_UP,
    MotionEvent.ACTION_CANCEL -> view.parent?.requestDisallowInterceptTouchEvent(false)
  }
  false
}
