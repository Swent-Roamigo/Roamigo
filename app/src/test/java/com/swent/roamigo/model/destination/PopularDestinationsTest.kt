// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.destination

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PopularDestinationsTest {
  @Test
  fun offersFiftyDistinctCities() {
    val all = PopularDestinations.all

    assertEquals(50, all.size)
    assertEquals(50, all.map { it.stableId }.toSet().size)
  }

  @Test
  fun everyCityLooksLikeASearchResult() {
    PopularDestinations.all.forEach {
      assertEquals("geonames:${it.geonameId}", it.stableId)
      assertTrue(it.name.isNotBlank() && it.countryName.isNotBlank())
      assertEquals(2, it.countryCode.length)
      assertTrue(it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0)
    }
  }

  @Test
  fun startsWithTheBestKnownCities() {
    assertEquals(
        listOf("New York", "London", "Paris"),
        PopularDestinations.all.take(3).map { it.name },
    )
  }
}
