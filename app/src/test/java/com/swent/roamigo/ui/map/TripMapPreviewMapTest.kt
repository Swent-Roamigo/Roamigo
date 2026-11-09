// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.map

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripMapPreviewMapTest {

  @Test
  fun previewMapProjection_placesFrameCornersInsideThePadding() {
    val project = previewMapProjection(listOf(SOUTH_WEST, NORTH_EAST), padding = 0.1f)

    assertOffset(Offset(0.1f, 0.9f), project(SOUTH_WEST))
    assertOffset(Offset(0.9f, 0.1f), project(NORTH_EAST))
    assertOffset(
        Offset(0.1f, 0.1f),
        project(MapCoordinate(NORTH_EAST.latitude, SOUTH_WEST.longitude)),
    )
    assertOffset(
        Offset(0.9f, 0.9f),
        project(MapCoordinate(SOUTH_WEST.latitude, NORTH_EAST.longitude)),
    )
  }

  @Test
  fun previewMapProjection_placesPointsLinearly() {
    val project = previewMapProjection(listOf(SOUTH_WEST, NORTH_EAST), padding = 0f)

    assertOffset(Offset(0.25f, 0.5f), project(MapCoordinate(latitude = 46.5, longitude = 6.5)))
  }

  @Test
  fun previewMapProjection_keepsEveryFramedPointWithinThePadding() {
    val frame = listOf(SOUTH_WEST, NORTH_EAST, MapCoordinate(46.3, 7.9), MapCoordinate(46.8, 6.1))
    val project = previewMapProjection(frame, padding = 0.15f)

    frame.map(project).forEach { position ->
      assertTrue(position.x in (0.15f - TOLERANCE)..(0.85f + TOLERANCE))
      assertTrue(position.y in (0.15f - TOLERANCE)..(0.85f + TOLERANCE))
    }
  }

  @Test
  fun previewMapProjection_withASinglePoint_centersIt() {
    val project = previewMapProjection(listOf(SOUTH_WEST))

    assertOffset(Offset(0.5f, 0.5f), project(SOUTH_WEST))
  }

  @Test
  fun previewMapProjection_withoutExtentOnAnAxis_centersThatAxis() {
    val project = previewMapProjection(listOf(SOUTH_WEST, MapCoordinate(46.0, 8.0)), padding = 0.1f)

    assertOffset(Offset(0.9f, 0.5f), project(MapCoordinate(46.0, 8.0)))
  }

  @Test
  fun previewMapProjection_withoutFrame_centersEveryPoint() {
    assertOffset(Offset(0.5f, 0.5f), previewMapProjection(emptyList())(NORTH_EAST))
  }

  private fun assertOffset(expected: Offset, actual: Offset) {
    assertEquals(expected.x, actual.x, TOLERANCE)
    assertEquals(expected.y, actual.y, TOLERANCE)
  }

  private companion object {
    const val TOLERANCE = 1e-5f
    val SOUTH_WEST = MapCoordinate(latitude = 46.0, longitude = 6.0)
    val NORTH_EAST = MapCoordinate(latitude = 47.0, longitude = 8.0)
  }
}
