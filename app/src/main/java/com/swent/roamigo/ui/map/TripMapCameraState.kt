// Implemented with assistance from OpenAI Codex.
package com.swent.roamigo.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * UI-owned camera interaction state, independent of the map SDK. New bounds remain pending after a
 * gesture until the user explicitly asks to show all locations.
 */
@Stable
class TripMapCameraState
internal constructor(
    hasUserMovedCamera: Boolean = false,
    viewportAtGesture: TripMapViewport = TripMapViewport.None,
) {
  internal var hasUserMovedCamera by mutableStateOf(hasUserMovedCamera)
    private set

  private var viewportAtGesture by mutableStateOf(viewportAtGesture)

  /** Call from map gesture callbacks to pause automatic framing at the current viewport. */
  internal fun onCameraGesture(viewport: TripMapViewport) {
    if (!hasUserMovedCamera) {
      viewportAtGesture = viewport
      hasUserMovedCamera = true
    }
  }

  /** Use to decide whether updated bounds warrant showing the "Show all locations" button. */
  internal fun shouldShowAllLocations(viewport: TripMapViewport): Boolean =
      hasUserMovedCamera && viewport != TripMapViewport.None && viewport != viewportAtGesture

  /** Call when "Show all locations" is tapped to resume framing with the latest viewport. */
  internal fun showAllLocations() {
    hasUserMovedCamera = false
  }

  internal companion object {
    val Saver =
        listSaver<TripMapCameraState, Any>(
            save = { state ->
              val coordinates =
                  when (val viewport = state.viewportAtGesture) {
                    TripMapViewport.None -> emptyList()
                    is TripMapViewport.Centered ->
                        listOf(MapCoordinate(viewport.latitude, viewport.longitude))
                    is TripMapViewport.FitAll -> viewport.coordinates
                  }
              listOf(
                  state.hasUserMovedCamera,
                  coordinates.flatMap { listOf(it.latitude, it.longitude) }.toDoubleArray(),
              )
            },
            restore = { saved ->
              val coordinates = saved[1] as DoubleArray
              TripMapCameraState(
                  hasUserMovedCamera = saved[0] as Boolean,
                  viewportAtGesture =
                      tripMapViewportOf(
                          coordinates.indices.step(2).map {
                            MapCoordinate(coordinates[it], coordinates[it + 1])
                          }
                      ),
              )
            },
        )
  }
}

/** Remembers camera interaction state across recompositions and saved state restoration. */
@Composable
fun rememberTripMapCameraState(): TripMapCameraState =
    rememberSaveable(saver = TripMapCameraState.Saver) { TripMapCameraState() }
