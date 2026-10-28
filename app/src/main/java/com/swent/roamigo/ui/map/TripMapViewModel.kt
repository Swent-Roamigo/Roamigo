// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swent.roamigo.model.trip.TripRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Loads a single trip for the trip map and exposes it with the viewport showing its destinations.
 *
 * @param tripId identifier of the trip to show.
 * @param tripRepository the repository providing the user's trips.
 */
class TripMapViewModel(
    private val tripId: String,
    private val tripRepository: TripRepository,
) : ViewModel() {
  private val _uiState = MutableStateFlow<TripMapUiState>(TripMapUiState.Loading)
  val uiState: StateFlow<TripMapUiState> = _uiState.asStateFlow()

  private var loadJob: Job? = null

  init {
    retry()
  }

  /** Loads the trip again, cancelling any running load so only the latest one updates the UI. */
  fun retry() {
    loadJob?.cancel()
    loadJob = viewModelScope.launch {
      _uiState.value = TripMapUiState.Loading
      // The repository has no single-trip getter, so the trip is picked among the user's trips.
      val result = runCatching { tripRepository.getTrips().find { it.uid == tripId } }
      _uiState.value =
          result.fold(
              onSuccess = { trip ->
                if (trip == null) {
                  TripMapUiState.Error(TRIP_NOT_FOUND_MESSAGE)
                } else {
                  val coordinates =
                      trip.destinations.map { MapCoordinate(it.latitude, it.longitude) }
                  TripMapUiState.Loaded(trip, tripMapViewportOf(coordinates))
                }
              },
              onFailure = { error ->
                if (error is CancellationException) throw error
                TripMapUiState.Error(error.localizedMessage ?: LOAD_FAILED_MESSAGE)
              },
          )
    }
  }

  companion object {

    internal const val TRIP_NOT_FOUND_MESSAGE = "This trip could not be found."

    internal const val LOAD_FAILED_MESSAGE = "Unable to load the trip."

    /**
     * Creates a factory that supplies the dependencies required by [TripMapViewModel].
     *
     * @param tripId identifier of the trip to show.
     * @param tripRepository the repository providing the user's trips.
     * @return a factory capable of creating [TripMapViewModel] instances.
     */
    fun factory(
        tripId: String,
        tripRepository: TripRepository,
    ): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {

          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(TripMapViewModel::class.java)) {
              return TripMapViewModel(tripId = tripId, tripRepository = tripRepository) as T
            }

            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
          }
        }
  }
}
