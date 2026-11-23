// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.trip.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.weather.DailyForecast
import com.swent.roamigo.model.weather.ForecastWindow
import com.swent.roamigo.model.weather.WeatherRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the forecast card shows. */
sealed interface ForecastUiState {
  /** No card: the trip is over, or the provider has no day for it. */
  data object Hidden : ForecastUiState

  data object Loading : ForecastUiState

  /** The trip is too far ahead; the forecast comes on [availableFrom]. */
  data class TooFar(val availableFrom: LocalDate) : ForecastUiState

  /** The forecast could not be loaded and none is saved. */
  data object Failed : ForecastUiState

  /**
   * @param fetchedAt when the forecast was produced.
   * @param fromCache whether it was saved earlier and could not be refreshed.
   */
  data class Ready(
      val days: List<DailyForecast>,
      val fetchedAt: Instant,
      val fromCache: Boolean,
  ) : ForecastUiState
}

/** Loads the weather forecast of a trip's first destination for the days of the trip. */
class TripForecastViewModel(
    private val location: TripLocation,
    private val startDate: LocalDate,
    private val endDate: LocalDate,
    private val repository: WeatherRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {
  private val _state = MutableStateFlow<ForecastUiState>(ForecastUiState.Loading)
  val state: StateFlow<ForecastUiState> = _state.asStateFlow()
  private var job: Job? = null

  init {
    load()
  }

  /** Tries again after a failure. */
  fun retry() {
    if (_state.value == ForecastUiState.Failed) load()
  }

  private fun load() {
    job?.cancel()
    _state.value = ForecastUiState.Loading
    job = viewModelScope.launch {
      val today = LocalDate.now(clock)
      _state.value =
          when {
            endDate.isBefore(today) -> ForecastUiState.Hidden
            !ForecastWindow.isAvailable(startDate, today) ->
                ForecastUiState.TooFar(ForecastWindow.availableFrom(startDate))
            else -> fetch()
          }
    }
  }

  private suspend fun fetch(): ForecastUiState =
      try {
        val forecast =
            repository.forecast(location.latitude, location.longitude, startDate, endDate)
        if (forecast.days.isEmpty()) ForecastUiState.Hidden
        else ForecastUiState.Ready(forecast.days, forecast.fetchedAt, forecast.fromCache)
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: Exception) {
        ForecastUiState.Failed
      }
}
