// Author : Constantin Dunoyer de Segonzac
// Co-author : ChatGPT-Codex
package com.swent.roamigo.ui.trip.creation

import androidx.lifecycle.ViewModel
import com.swent.roamigo.model.trip.MAX_GROUP_TRAVELER_COUNT
import com.swent.roamigo.model.trip.MIN_GROUP_TRAVELER_COUNT
import com.swent.roamigo.model.trip.SOLO_TRAVELER_COUNT
import com.swent.roamigo.model.trip.TravelMode
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class CreateTripUiState(
    val destinationQuery: String = "",
    val destinations: List<String> = popularDestinations,
    val selectedDestinations: List<String> = emptyList(),
    val visibleMonth: YearMonth = YearMonth.now(),
    val startDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate = startDate.plusDays(DEFAULT_TRIP_DURATION_DAYS - 1),
    val isSelectingEndDate: Boolean = false,
    val flexibleDates: Boolean = false,
    val travelMode: TravelMode = TravelMode.GROUP,
    val travelerCount: Int = DEFAULT_GROUP_SIZE,
) {
  val visibleDestinations: List<String>
    get() =
        if (destinationQuery.isBlank()) destinations
        else destinations.filter { it.contains(destinationQuery.trim(), ignoreCase = true) }

  val tripDays: Long
    get() = endDate.toEpochDay() - startDate.toEpochDay() + 1

  val tripNights: Long
    get() = endDate.toEpochDay() - startDate.toEpochDay()

  val canCreateTrip: Boolean
    get() =
        !isSelectingEndDate &&
            !startDate.isBefore(LocalDate.now()) &&
            !endDate.isBefore(startDate) &&
            selectedDestinations.isNotEmpty() &&
            travelerCount in travelMode.allowedTravelerCounts
}

private val popularDestinations = listOf("Lisbon", "Porto", "Barcelona", "Prague")
private const val DEFAULT_GROUP_SIZE = 3
private const val DEFAULT_TRIP_DURATION_DAYS = 3L

/** Holds the draft shared by the steps of the trip-creation flow. */
class CreateTripViewModel : ViewModel() {
  private val _uiState = MutableStateFlow(CreateTripUiState())
  val uiState: StateFlow<CreateTripUiState> = _uiState.asStateFlow()

  fun updateDestinationQuery(query: String) {
    _uiState.update { it.copy(destinationQuery = query) }
  }

  fun toggleDestination(destination: String) {
    val normalizedDestination = destination.trim()
    if (normalizedDestination.isEmpty()) return

    _uiState.update { state ->
      val selected = state.selectedDestinations
      state.copy(
          selectedDestinations =
              if (normalizedDestination in selected) selected - normalizedDestination
              else selected + normalizedDestination
      )
    }
  }

  fun removeDestination(destination: String) {
    _uiState.update { state ->
      state.copy(selectedDestinations = state.selectedDestinations - destination)
    }
  }

  fun showPreviousMonth() {
    val currentMonth = YearMonth.now()
    _uiState.update { state ->
      if (state.visibleMonth.isAfter(currentMonth)) {
        state.copy(visibleMonth = state.visibleMonth.minusMonths(1))
      } else {
        state
      }
    }
  }

  fun showNextMonth() {
    _uiState.update { it.copy(visibleMonth = it.visibleMonth.plusMonths(1)) }
  }

  fun selectDate(date: LocalDate) {
    if (date.isBefore(LocalDate.now())) return
    _uiState.update { state ->
      when {
        !state.isSelectingEndDate ->
            state.copy(
                startDate = date,
                endDate = date,
                isSelectingEndDate = true,
            )
        date.isBefore(state.startDate) -> state.copy(startDate = date, endDate = date)
        else -> state.copy(endDate = date, isSelectingEndDate = false)
      }
    }
  }

  fun setFlexibleDates(flexible: Boolean) {
    _uiState.update { it.copy(flexibleDates = flexible) }
  }

  fun selectTravelMode(mode: TravelMode) {
    _uiState.update { state ->
      if (state.travelMode == mode) {
        state
      } else {
        state.copy(
            travelMode = mode,
            travelerCount =
                if (mode == TravelMode.SOLO) SOLO_TRAVELER_COUNT
                else {
                  // Returning to Group starts a fresh group draft at its default size.
                  DEFAULT_GROUP_SIZE
                },
        )
      }
    }
  }

  fun decrementTravelers() {
    if (uiState.value.travelMode == TravelMode.SOLO) return
    updateTravelerCount { (it - 1).coerceAtLeast(MIN_GROUP_TRAVELER_COUNT) }
  }

  fun incrementTravelers() {
    if (uiState.value.travelMode == TravelMode.SOLO) return
    updateTravelerCount { (it + 1).coerceAtMost(MAX_GROUP_TRAVELER_COUNT) }
  }

  private fun updateTravelerCount(update: (Int) -> Int) {
    if (uiState.value.travelMode == TravelMode.SOLO) return
    _uiState.update { state ->
      state.copy(
          travelerCount = update(state.travelerCount),
          travelMode = TravelMode.GROUP,
      )
    }
  }
}
