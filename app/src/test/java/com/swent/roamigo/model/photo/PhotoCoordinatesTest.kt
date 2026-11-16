// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.photo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PhotoCoordinatesTest {
  private val invalidPairs =
      listOf(
          Double.NaN to 6.6,
          46.5 to Double.NaN,
          Double.POSITIVE_INFINITY to 6.6,
          46.5 to Double.NEGATIVE_INFINITY,
          90.1 to 6.6,
          -90.1 to 6.6,
          46.5 to 180.1,
          46.5 to -180.1,
      )

  private val boundaryPairs = listOf(90.0 to 180.0, -90.0 to -180.0, 90.0 to -180.0, -90.0 to 180.0)

  @Test
  fun constructorRejectsNonFiniteAndOutOfRangeValues() {
    invalidPairs.forEach { (latitude, longitude) ->
      assertThrows("$latitude, $longitude", IllegalArgumentException::class.java) {
        PhotoCoordinates(latitude, longitude)
      }
    }
  }

  @Test
  fun constructorAcceptsTheExactBounds() {
    boundaryPairs.forEach { (latitude, longitude) ->
      val coordinates = PhotoCoordinates(latitude, longitude)
      assertEquals(latitude, coordinates.latitude, 0.0)
      assertEquals(longitude, coordinates.longitude, 0.0)
    }
  }

  @Test
  fun orNullReturnsNullForNonFiniteAndOutOfRangeValues() {
    invalidPairs.forEach { (latitude, longitude) ->
      assertNull("$latitude, $longitude", PhotoCoordinates.orNull(latitude, longitude))
    }
  }

  @Test
  fun orNullReturnsCoordinatesForValidValuesAndTheExactBounds() {
    (boundaryPairs + (46.5 to 6.6)).forEach { (latitude, longitude) ->
      assertEquals(
          PhotoCoordinates(latitude, longitude),
          PhotoCoordinates.orNull(latitude, longitude),
      )
    }
  }
}
