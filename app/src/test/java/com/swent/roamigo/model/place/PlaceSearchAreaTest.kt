// AI assistance was used for implementation support.
package com.swent.roamigo.model.place

import com.swent.roamigo.model.TripLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaceSearchAreaTest {
  private val london = TripLocation(51.5, -0.12, "London", "GB")
  private val paris = TripLocation(48.85, 2.35, "Paris", "FR")
  private val unknown = TripLocation(38.7, -9.1, "Lisbon")

  @Test
  fun aTripWithoutDestinationsHasNoArea() {
    assertNull(placeSearchAreaOf(emptyList()))
  }

  @Test
  fun theFirstDestinationRanksTheResultsAndItsCountryLimitsThem() {
    assertEquals(
        PlaceSearchArea(PlaceProximity(51.5, -0.12), setOf("GB")),
        placeSearchAreaOf(listOf(london)),
    )
  }

  @Test
  fun everyCountryOfARouteIsAllowed() {
    assertEquals(
        PlaceSearchArea(PlaceProximity(51.5, -0.12), setOf("GB", "FR")),
        placeSearchAreaOf(listOf(london, paris, london)),
    )
  }

  @Test
  fun aDestinationWithoutACountryOnlyRanksSoNoPlaceIsHidden() {
    assertEquals(
        PlaceSearchArea(PlaceProximity(51.5, -0.12), emptySet()),
        placeSearchAreaOf(listOf(london, unknown)),
    )
    assertEquals(
        PlaceSearchArea(PlaceProximity(38.7, -9.1), emptySet()),
        placeSearchAreaOf(listOf(unknown)),
    )
  }
}
