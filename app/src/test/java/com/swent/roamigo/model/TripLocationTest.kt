// AI assistance was used for implementation support and code review.
package com.swent.roamigo.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class TripLocationTest {

  @Test
  fun validCountryCodeIsAccepted() {
    val location = TripLocation(38.7223, -9.1393, "Lisbon", "PT")

    assertEquals("PT", location.countryCode)
  }

  @Test
  fun missingCountryCodeIsAccepted() {
    val location = TripLocation(38.7223, -9.1393, "Lisbon")

    assertNull(location.countryCode)
  }

  @Test
  fun invalidCountryCodesAreRejected() {
    listOf("", "pt", "PRT", "P1", " Pt", "12").forEach { code ->
      assertThrows(IllegalArgumentException::class.java) {
        TripLocation(38.7223, -9.1393, "Lisbon", code)
      }
    }
  }

  @Test
  fun copyingWithInvalidCountryCodeIsRejected() {
    val location = TripLocation(38.7223, -9.1393, "Lisbon", "PT")

    assertThrows(IllegalArgumentException::class.java) { location.copy(countryCode = "pt") }
  }
}
