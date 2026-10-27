// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swent.roamigo.model.trip.Trip
import com.swent.roamigo.model.trip.TripRepository
import com.swent.roamigo.model.trip.isCompletedOn
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TripHistoryViewModel(
    private val tripRepository: TripRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

  private val _uiState = MutableStateFlow(TripHistoryUiState())
  val uiState = _uiState.asStateFlow()
  private var refreshJob: Job? = null

  init {
    refresh()
  }

  fun refresh() {
    refreshJob?.cancel()
    refreshJob = viewModelScope.launch {
      _uiState.value = TripHistoryUiState()

      try {
        val trips = tripRepository.getTrips()
        val today = LocalDate.now(clock)
        val formatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

        val items =
            selectCompletedTrips(trips, today, clock.zone).map { trip ->
              val start = trip.startDate.toDate().toInstant().atZone(clock.zone).toLocalDate()
              val end = trip.endDate.toDate().toInstant().atZone(clock.zone).toLocalDate()

              TripHistoryItemUiModel(
                  id = trip.uid,
                  name = trip.name,
                  destinations = trip.destinations.map { it.name },
                  dateRange = "${start.format(formatter)} – ${end.format(formatter)}",
                  travelerCount = trip.travelerCount,
              )
            }

        _uiState.value = TripHistoryUiState(isLoading = false, trips = items)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.value =
            TripHistoryUiState(
                isLoading = false,
                errorMessage = "Unable to load trip history.",
            )
      }
    }
  }

  companion object {
    fun factory(
        tripRepository: TripRepository,
        clock: Clock = Clock.systemDefaultZone(),
    ): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(TripHistoryViewModel::class.java)) {
              "Unknown ViewModel class: ${modelClass.name}"
            }
            return TripHistoryViewModel(tripRepository, clock) as T
          }
        }
  }
}

/** Selects completed trips independently of their stored status. */
internal fun selectCompletedTrips(
    trips: List<Trip>,
    today: LocalDate,
    zone: ZoneId,
): List<Trip> =
    trips
        .filter { trip -> trip.isCompletedOn(today, zone) }
        .sortedWith(
            compareByDescending<Trip> { it.endDate.seconds }
                .thenByDescending { it.endDate.nanoseconds }
        )
