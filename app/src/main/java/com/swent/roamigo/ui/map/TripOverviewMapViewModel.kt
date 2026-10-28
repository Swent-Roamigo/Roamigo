// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swent.roamigo.model.trip.TripRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** ViewModel responsible for loading trips and exposing [TripOverviewMapUiState]. */
class TripOverviewMapViewModel(
    private val tripRepository: TripRepository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(TripOverviewMapUiState(isLoading = true))
  val uiState: StateFlow<TripOverviewMapUiState> = _uiState.asStateFlow()

  private var loadTripsJob: Job? = null

  init {
    loadTrips()
  }

  /** Loads trip markers, cancelling any previous load so only the latest request updates the UI. */
  fun loadTrips() {
    loadTripsJob?.cancel()
    loadTripsJob = viewModelScope.launch {
      _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
      val result = runCatching { tripRepository.getTrips() }
      result.fold(
          onSuccess = { trips ->
            val markers = trips.flatMap { it.toTripMarkerUiStates() }
            _uiState.value = TripOverviewMapUiState(markers = markers, isLoading = false)
          },
          onFailure = { error ->
            if (error is CancellationException) throw error
            _uiState.value =
                TripOverviewMapUiState(
                    markers = emptyList(),
                    isLoading = false,
                    errorMessage = error.localizedMessage,
                )
          },
      )
    }
  }
}
