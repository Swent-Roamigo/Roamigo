// Author : Constantin Dunoyer de Segonzac
// Co-author : ChatGPT-Codex
package com.swent.roamigo.ui.trip.creation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.trip.FakeTripRepository
import com.swent.roamigo.model.trip.MAX_GROUP_TRAVELER_COUNT
import com.swent.roamigo.model.trip.MIN_GROUP_TRAVELER_COUNT
import com.swent.roamigo.model.trip.SOLO_TRAVELER_COUNT
import com.swent.roamigo.model.trip.TravelMode
import com.swent.roamigo.model.trip.TripCreationException
import com.swent.roamigo.model.trip.TripDraft
import com.swent.roamigo.model.trip.TripRepository
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val isSubmitting: Boolean = false,
    val creationError: TripCreationException? = null,
    val createdTripId: String? = null,
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
        !isSubmitting &&
            createdTripId == null &&
            !isSelectingEndDate &&
            !startDate.isBefore(LocalDate.now()) &&
            !endDate.isBefore(startDate) &&
            selectedDestinations.isNotEmpty() &&
            travelerCount in travelMode.allowedTravelerCounts
}

private val destinationLocations =
    listOf(
            TripLocation(38.7223, -9.1393, "Lisbon"),
            TripLocation(41.1579, -8.6291, "Porto"),
            TripLocation(41.3874, 2.1686, "Barcelona"),
            TripLocation(50.0755, 14.4378, "Prague"),
        )
        .associateBy { it.name }
private val popularDestinations = destinationLocations.keys.toList()
private const val DEFAULT_GROUP_SIZE = 3
private const val DEFAULT_TRIP_DURATION_DAYS = 3L

/** Holds the draft shared by the steps of the trip-creation flow. */
class CreateTripViewModel(
    private val repository: TripRepository = FakeTripRepository(),
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
  private var submittedDraft: TripDraft? = null
  private var submissionId: String? = null
  private val _uiState = MutableStateFlow(CreateTripUiState())
  val uiState: StateFlow<CreateTripUiState> = _uiState.asStateFlow()

  /** Saves the current draft, retaining its ID for an unchanged retry. */
  fun createTrip() {
    val state = uiState.value
    if (!state.canCreateTrip) return
    val draft =
        TripDraft(
            name = state.selectedDestinations.joinToString(" → "),
            destinations = state.selectedDestinations.map { destinationLocations.getValue(it) },
            startDate = Timestamp(Date.from(state.startDate.atStartOfDay(zoneId).toInstant())),
            endDate = Timestamp(Date.from(state.endDate.atStartOfDay(zoneId).toInstant())),
            flexibleDates = state.flexibleDates,
            travelMode = state.travelMode,
            travelerCount = state.travelerCount,
        )
    val errors = draft.validate()
    if (errors.isNotEmpty()) {
      _uiState.update { it.copy(creationError = TripCreationException.Validation(errors)) }
      return
    }
    if (draft != submittedDraft) {
      submittedDraft = draft
      submissionId = UUID.randomUUID().toString()
    }
    val tripId = requireNotNull(submissionId)
    _uiState.update { it.copy(isSubmitting = true, creationError = null) }
    viewModelScope.launch {
      try {
        repository.createTrip(tripId, draft)
        _uiState.update { it.copy(isSubmitting = false, createdTripId = tripId) }
      } catch (error: CancellationException) {
        _uiState.update { it.copy(isSubmitting = false) }
        throw error
      } catch (error: Exception) {
        _uiState.update {
          it.copy(
              isSubmitting = false,
              creationError =
                  error as? TripCreationException ?: TripCreationException.Unknown(error),
          )
        }
      }
    }
  }

  private fun edit(update: (CreateTripUiState) -> CreateTripUiState) {
    _uiState.update { state ->
      if (state.isSubmitting || state.createdTripId != null) state
      else update(state).copy(creationError = null)
    }
  }

  fun updateDestinationQuery(query: String) {
    edit { it.copy(destinationQuery = query) }
  }

  fun toggleDestination(destination: String) {
    val normalizedDestination = destination.trim()
    if (normalizedDestination !in destinationLocations) return

    edit { state ->
      val selected = state.selectedDestinations
      state.copy(
          selectedDestinations =
              if (normalizedDestination in selected) selected - normalizedDestination
              else selected + normalizedDestination
      )
    }
  }

  fun removeDestination(destination: String) {
    edit { state -> state.copy(selectedDestinations = state.selectedDestinations - destination) }
  }

  fun showPreviousMonth() {
    val currentMonth = YearMonth.now()
    edit { state ->
      if (state.visibleMonth.isAfter(currentMonth)) {
        state.copy(visibleMonth = state.visibleMonth.minusMonths(1))
      } else {
        state
      }
    }
  }

  fun showNextMonth() {
    edit { it.copy(visibleMonth = it.visibleMonth.plusMonths(1)) }
  }

  fun selectDate(date: LocalDate) {
    if (date.isBefore(LocalDate.now())) return
    edit { state ->
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
    edit { it.copy(flexibleDates = flexible) }
  }

  fun selectTravelMode(mode: TravelMode) {
    edit { state ->
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
    edit { state ->
      state.copy(
          travelerCount = update(state.travelerCount),
          travelMode = TravelMode.GROUP,
      )
    }
  }
}
