// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test

class TripMapViewportTest {

  @Test
  fun tripMapViewportOf_withoutCoordinates_isNone() {
    assertEquals(TripMapViewport.None, tripMapViewportOf(emptyList()))
  }

  @Test
  fun tripMapViewportOf_withOneCoordinate_isCenteredOnIt() {
    assertEquals(
        TripMapViewport.Centered(latitude = 46.52, longitude = 6.63),
        tripMapViewportOf(listOf(LAUSANNE)),
    )
  }

  @Test
  fun tripMapViewportOf_withSeveralCoordinates_fitsAllOfThem() {
    val coordinates = listOf(LAUSANNE, BERN, GENEVA)

    assertEquals(TripMapViewport.FitAll(coordinates), tripMapViewportOf(coordinates))
  }

  private companion object {
    val LAUSANNE = MapCoordinate(latitude = 46.52, longitude = 6.63)
    val BERN = MapCoordinate(latitude = 46.95, longitude = 7.44)
    val GENEVA = MapCoordinate(latitude = 46.2, longitude = 6.14)
  }

  @Test
  fun aSinglePointIsShownAtCityZoomNotAtStreetLevel() {
    val camera = TripMapViewport.Centered(latitude = 52.23, longitude = 21.01).toCameraOptions()

    assertEquals(SINGLE_POINT_ZOOM, camera.zoom!!, 0.0)
    assertEquals(21.01, camera.center!!.longitude(), 0.0)
    assertEquals(52.23, camera.center!!.latitude(), 0.0)
  }
}
