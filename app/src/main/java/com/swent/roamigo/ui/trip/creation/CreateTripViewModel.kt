// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.creation

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class CreateTripUiState(
    val destinationQuery: String = "",
    val destinations: List<String> = popularDestinations,
    val selectedDestinations: List<String> = listOf("Lisbon", "Porto"),
) {
  val visibleDestinations: List<String>
    get() =
        if (destinationQuery.isBlank()) destinations
        else destinations.filter { it.contains(destinationQuery.trim(), ignoreCase = true) }
}

private val popularDestinations = listOf("Lisbon", "Porto", "Barcelona", "Prague")

/** Holds the draft shared by the steps of the trip-creation flow. */
class CreateTripViewModel : ViewModel() {
  private val _uiState = MutableStateFlow(CreateTripUiState())
  val uiState: StateFlow<CreateTripUiState> = _uiState.asStateFlow()

  fun updateDestinationQuery(query: String) {
    _uiState.update { it.copy(destinationQuery = query) }
  }

  fun toggleDestination(destination: String) {
    _uiState.update { state ->
      val selected = state.selectedDestinations
      state.copy(
          selectedDestinations =
              if (destination in selected) selected - destination else selected + destination
      )
    }
  }

  fun removeDestination(destination: String) {
    _uiState.update { state ->
      state.copy(selectedDestinations = state.selectedDestinations - destination)
    }
  }
}
