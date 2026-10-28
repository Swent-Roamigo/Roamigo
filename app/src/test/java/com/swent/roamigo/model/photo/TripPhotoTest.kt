// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.photo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TripPhotoTest {
  @Test
  fun photosWithTheSameContentAreEqual() {
    assertEquals(tripPhoto(image = byteArrayOf(1, 2)), tripPhoto(image = byteArrayOf(1, 2)))
    assertEquals(
        tripPhoto(image = byteArrayOf(1, 2)).hashCode(),
        tripPhoto(image = byteArrayOf(1, 2)).hashCode(),
    )
  }

  @Test
  fun anyDifferenceMakesPhotosDifferent() {
    val base = tripPhoto(latitude = 1.0, longitude = 2.0)

    assertNotEquals(base, tripPhoto(id = "b", latitude = 1.0, longitude = 2.0))
    assertNotEquals(base, tripPhoto(uploadedBy = "u2", latitude = 1.0, longitude = 2.0))
    assertNotEquals(base, tripPhoto(takenAtMillis = 2, latitude = 1.0, longitude = 2.0))
    assertNotEquals(base, tripPhoto(latitude = 9.0, longitude = 2.0))
    assertNotEquals(base, tripPhoto(latitude = 1.0, longitude = 9.0))
    assertNotEquals(base, tripPhoto(latitude = 1.0, longitude = 2.0, image = byteArrayOf(9)))
  }

  @Test
  fun aPhotoIsNotEqualToSomethingElse() {
    assertNotEquals(tripPhoto(), "a photo")
    assertNotEquals(tripPhoto(), null)
  }
}
