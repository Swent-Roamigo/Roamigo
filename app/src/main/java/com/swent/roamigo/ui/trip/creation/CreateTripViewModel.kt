// Author : Constantin Dunoyer de Segonzac
// Co-author : ChatGPT-Codex
package com.swent.roamigo.ui.trip.creation

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swent.roamigo.R
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.destination.Destination
import com.swent.roamigo.model.destination.DestinationConfigurationException
import com.swent.roamigo.model.destination.DestinationPhoto
import com.swent.roamigo.model.destination.DestinationRepository
import com.swent.roamigo.model.destination.MAX_DESTINATION_RESULTS
import com.swent.roamigo.model.destination.MIN_DESTINATION_QUERY_LENGTH
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
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CreateTripUiState(
    val destinationQuery: String = "",
    val destinations: List<Destination> = emptyList(),
    val selectedDestinations: List<Destination> = emptyList(),
    val destinationSearchStatus: DestinationSearchStatus = DestinationSearchStatus.INITIAL,
    @field:StringRes val destinationSearchErrorRes: Int? = null,
    val photosByDestinationId: Map<String, DestinationPhoto> = emptyMap(),
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
  val visibleDestinations: List<Destination>
    get() = destinations

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

private const val DEFAULT_GROUP_SIZE = 3
private const val DEFAULT_TRIP_DURATION_DAYS = 3L

enum class DestinationSearchStatus {
  INITIAL,
  LOADING,
  RESULTS,
  EMPTY,
  ERROR,
}

/** Holds the draft shared by the steps of the trip-creation flow. */
class CreateTripViewModel(
    private val repository: TripRepository,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val destinationRepository: DestinationRepository,
    private val requestDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
  private var submittedDraft: TripDraft? = null
  private var submissionId: String? = savedStateHandle["submission_id"]
  private var searchJob: Job? = null
  private val requestedPhotoIds = mutableSetOf<String>()
  private val _uiState = MutableStateFlow(restoreSubmission())
  val uiState: StateFlow<CreateTripUiState> = _uiState.asStateFlow()

  private fun restoreSubmission(): CreateTripUiState {
    val values =
        savedStateHandle.get<ArrayList<String>>("submission_state") ?: return CreateTripUiState()
    return try {
      val destinations =
          values.drop(5).chunked(8).map { item ->
            Destination(
                item[0],
                com.swent.roamigo.model.destination.DestinationKind.valueOf(item[1]),
                item[2],
                item[3],
                item[4],
                item[5].toDouble(),
                item[6].toDouble(),
                item[7].toInt(),
            )
          }
      CreateTripUiState(
          selectedDestinations = destinations,
          startDate = LocalDate.parse(values[0]),
          endDate = LocalDate.parse(values[1]),
          flexibleDates = values[2].toBooleanStrict(),
          travelMode = TravelMode.valueOf(values[3]),
          travelerCount = values[4].toInt(),
          createdTripId = savedStateHandle["created_trip_id"],
      )
    } catch (_: Exception) {
      CreateTripUiState()
    }
  }

  /** Saves the current draft, retaining its ID for an unchanged retry. */
  fun createTrip() {
    val state = uiState.value
    if (!state.canCreateTrip) return
    val draft =
        TripDraft(
            name = state.selectedDestinations.joinToString(" → ") { it.name },
            destinations =
                state.selectedDestinations.map { TripLocation(it.latitude, it.longitude, it.name) },
            startDate = state.startDate.atStartOfDay(zoneId).toInstant(),
            endDate = state.endDate.atStartOfDay(zoneId).toInstant(),
            flexibleDates = state.flexibleDates,
            travelMode = state.travelMode,
            travelerCount = state.travelerCount,
        )
    val errors = draft.validate()
    if (errors.isNotEmpty()) {
      _uiState.update { it.copy(creationError = TripCreationException.Validation(errors)) }
      return
    }
    // Match the restored draft so a retry for the same trip keeps its original submission ID.
    if (
        submittedDraft == null &&
            submissionId != null &&
            savedStateHandle.get<String>("submission_draft") == draft.toString()
    ) {
      submittedDraft = draft
    }
    if (draft != submittedDraft) {
      submittedDraft = draft
      submissionId = UUID.randomUUID().toString()
    }
    val tripId = requireNotNull(submissionId)
    savedStateHandle["submission_id"] = tripId
    savedStateHandle["submission_draft"] = draft.toString()
    savedStateHandle["submission_state"] =
        arrayListOf(
                state.startDate.toString(),
                state.endDate.toString(),
                state.flexibleDates.toString(),
                state.travelMode.name,
                state.travelerCount.toString(),
            )
            .apply {
              state.selectedDestinations.forEach { destination ->
                addAll(
                    listOf(
                        destination.stableId,
                        destination.kind.name,
                        destination.name,
                        destination.countryName,
                        destination.countryCode,
                        destination.latitude.toString(),
                        destination.longitude.toString(),
                        destination.geonameId.toString(),
                    )
                )
              }
            }
    _uiState.update { it.copy(isSubmitting = true, creationError = null) }
    viewModelScope.launch {
      try {
        repository.createTrip(tripId, draft)
        savedStateHandle["created_trip_id"] = tripId
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
    if (uiState.value.isSubmitting || uiState.value.createdTripId != null) return
    edit {
      it.copy(
          destinationQuery = query,
          destinations = emptyList(),
          destinationSearchErrorRes = null,
          destinationSearchStatus =
              if (query.trim().length < MIN_DESTINATION_QUERY_LENGTH)
                  DestinationSearchStatus.INITIAL
              else DestinationSearchStatus.LOADING,
      )
    }
    searchJob?.cancel()
    if (query.trim().length < MIN_DESTINATION_QUERY_LENGTH) return
    searchJob =
        viewModelScope.launch(requestDispatcher) {
          delay(DESTINATION_SEARCH_DEBOUNCE_MS.milliseconds)
          searchDestinations(query.trim())
        }
  }

  fun toggleDestination(destination: Destination) {
    if (
        destination.name.isBlank() ||
            !destination.latitude.isFinite() ||
            !destination.longitude.isFinite()
    )
        return
    edit { state ->
      val selected = state.selectedDestinations
      state.copy(
          selectedDestinations =
              if (selected.any { it.stableId == destination.stableId }) {
                selected.filterNot { it.stableId == destination.stableId }
              } else selected + destination
      )
    }
  }

  fun removeDestination(destination: Destination) {
    edit { state ->
      state.copy(
          selectedDestinations =
              state.selectedDestinations.filterNot { it.stableId == destination.stableId }
      )
    }
  }

  fun retryDestinationSearch() {
    if (uiState.value.isSubmitting || uiState.value.createdTripId != null) return
    val query = uiState.value.destinationQuery.trim()
    if (query.length < MIN_DESTINATION_QUERY_LENGTH) return
    searchJob?.cancel()
    searchJob = viewModelScope.launch(requestDispatcher) { searchDestinations(query) }
  }

  fun requestDestinationPhoto(destination: Destination) {
    if (!synchronized(requestedPhotoIds) { requestedPhotoIds.add(destination.stableId) }) return
    viewModelScope.launch(requestDispatcher) {
      try {
        val photo = destinationRepository.findPhoto(destination)
        if (photo == null) {
          synchronized(requestedPhotoIds) { requestedPhotoIds.remove(destination.stableId) }
        } else {
          _uiState.update { state ->
            state.copy(
                photosByDestinationId =
                    state.photosByDestinationId + (destination.stableId to photo)
            )
          }
        }
      } catch (cancelled: CancellationException) {
        synchronized(requestedPhotoIds) { requestedPhotoIds.remove(destination.stableId) }
        throw cancelled
      } catch (_: Exception) {
        synchronized(requestedPhotoIds) { requestedPhotoIds.remove(destination.stableId) }
      }
    }
  }

  private suspend fun searchDestinations(query: String) {
    try {
      val results = destinationRepository.searchDestinations(query).take(MAX_DESTINATION_RESULTS)
      if (uiState.value.destinationQuery.trim() != query) return
      _uiState.update {
        it.copy(
            destinations = results,
            destinationSearchStatus =
                if (results.isEmpty()) DestinationSearchStatus.EMPTY
                else DestinationSearchStatus.RESULTS,
            destinationSearchErrorRes = null,
        )
      }
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (exception: Exception) {
      if (uiState.value.destinationQuery.trim() != query) return
      _uiState.update {
        it.copy(
            destinations = emptyList(),
            destinationSearchStatus = DestinationSearchStatus.ERROR,
            destinationSearchErrorRes =
                if (exception is DestinationConfigurationException)
                    R.string.destination_search_not_configured
                else R.string.destination_search_error,
        )
      }
    }
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

private const val DESTINATION_SEARCH_DEBOUNCE_MS = 300L
