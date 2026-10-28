// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.map

/**
 * Geographic coordinate shown on a trip map, independent of any map SDK type.
 *
 * @property latitude latitude in degrees.
 * @property longitude longitude in degrees.
 */
data class MapCoordinate(val latitude: Double, val longitude: Double)

/**
 * Camera target of a trip map, computed without the map SDK so that ViewModels can expose it. The
 * map applies it once its dimensions are known.
 */
sealed interface TripMapViewport {
  /** There is nothing to show: the camera keeps its current position. */
  data object None : TripMapViewport

  /**
   * The camera is centered on a single point.
   *
   * @property latitude latitude of the center, in degrees.
   * @property longitude longitude of the center, in degrees.
   */
  data class Centered(val latitude: Double, val longitude: Double) : TripMapViewport

  /**
   * The camera fits all the given points.
   *
   * @property coordinates the points to fit, at least two.
   */
  data class FitAll(val coordinates: List<MapCoordinate>) : TripMapViewport
}

/**
 * Builds the viewport showing the given destinations.
 *
 * @param coordinates the destinations to show.
 * @return [TripMapViewport.None] without destination, [TripMapViewport.Centered] on a single one,
 *   and [TripMapViewport.FitAll] otherwise.
 */
fun tripMapViewportOf(coordinates: List<MapCoordinate>): TripMapViewport =
    when (coordinates.size) {
      0 -> TripMapViewport.None
      1 -> TripMapViewport.Centered(coordinates.single().latitude, coordinates.single().longitude)
      else -> TripMapViewport.FitAll(coordinates)
    }
