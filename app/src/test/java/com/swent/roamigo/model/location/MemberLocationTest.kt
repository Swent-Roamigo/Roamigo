// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MemberLocationTest {

  private val location =
      MemberLocation(
          userId = "user-1",
          latitude = 46.5197,
          longitude = 6.6323,
          lastUpdated = 1_700_000_000_000L,
      )

  private val validData =
      mapOf(
          "latitude" to 46.5197,
          "longitude" to 6.6323,
          "updatedAt" to 1_700_000_000_000L,
      )

  @Test
  fun toMap_containsAllFieldsExceptUserId() {
    assertEquals(validData, location.toMap())
  }

  @Test
  fun toMapThenFromMap_returnsEqualLocation() {
    assertEquals(location, MemberLocation.fromMap(location.userId, location.toMap()))
  }

  @Test
  fun fromMap_acceptsWholeNumberCoordinatesAndDoubleTimestamp() {
    val data = mapOf("latitude" to 46L, "longitude" to 6L, "updatedAt" to 1.0e12)

    assertEquals(
        MemberLocation("user-1", 46.0, 6.0, 1_000_000_000_000L),
        MemberLocation.fromMap("user-1", data),
    )
  }

  @Test
  fun fromMap_acceptsBoundaryCoordinates() {
    val data = validData + mapOf("latitude" to -90.0, "longitude" to 180.0)

    assertEquals(-90.0, MemberLocation.fromMap("user-1", data)?.latitude)
  }

  @Test
  fun fromMap_returnsNullForBlankUserId() {
    assertNull(MemberLocation.fromMap(" ", validData))
  }

  @Test
  fun fromMap_returnsNullForMissingField() {
    listOf("latitude", "longitude", "updatedAt").forEach { field ->
      assertNull(field, MemberLocation.fromMap("user-1", validData - field))
    }
  }

  @Test
  fun fromMap_returnsNullForWrongType() {
    listOf("latitude", "longitude", "updatedAt").forEach { field ->
      assertNull(field, MemberLocation.fromMap("user-1", validData + (field to "invalid")))
    }
  }

  @Test
  fun fromMap_returnsNullForOutOfRangeCoordinates() {
    listOf("latitude" to 90.1, "latitude" to -90.1, "longitude" to 180.1, "longitude" to -180.1)
        .forEach { entry ->
          assertNull(entry.toString(), MemberLocation.fromMap("user-1", validData + entry))
        }
  }

  @Test
  fun fromMap_returnsNullForNaNCoordinate() {
    assertNull(MemberLocation.fromMap("user-1", validData + ("latitude" to Double.NaN)))
  }
}
