// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.creation

import org.junit.Assert.assertEquals
import org.junit.Test

class CreateTripViewModelTest {
  @Test
  fun initialStateHasFigmaRouteAndPopularDestinations() {
    val viewModel = CreateTripViewModel()

    assertEquals(
        listOf("Lisbon", "Porto"),
        viewModel.uiState.value.selectedDestinations,
    )
    assertEquals(
        listOf("Lisbon", "Porto", "Barcelona", "Prague"),
        viewModel.uiState.value.visibleDestinations,
    )
  }

  @Test
  fun queryFiltersDestinationsIgnoringCase() {
    val viewModel = CreateTripViewModel()
    viewModel.updateDestinationQuery("bar")

    assertEquals(listOf("Barcelona"), viewModel.uiState.value.visibleDestinations)
  }

  @Test
  fun togglingAddsAndRemovesDestinationAndKeepsRouteOrder() {
    val viewModel = CreateTripViewModel()
    val barcelona = "Barcelona"

    viewModel.toggleDestination(barcelona)
    assertEquals(
        listOf("Lisbon", "Porto", "Barcelona"),
        viewModel.uiState.value.selectedDestinations,
    )

    viewModel.toggleDestination(barcelona)
    assertEquals(
        listOf("Lisbon", "Porto"),
        viewModel.uiState.value.selectedDestinations,
    )
  }

  @Test
  fun removingDestinationKeepsRemainingRouteOrder() {
    val viewModel = CreateTripViewModel()
    viewModel.removeDestination("Lisbon")

    assertEquals(listOf("Porto"), viewModel.uiState.value.selectedDestinations)
  }
}
