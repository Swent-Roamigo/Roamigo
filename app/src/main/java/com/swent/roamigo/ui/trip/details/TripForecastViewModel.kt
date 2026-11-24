// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.trip.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.weather.DailyForecast
import com.swent.roamigo.model.weather.ForecastWindow
import com.swent.roamigo.model.weather.WeatherRepository
import com.swent.roamigo.model.weather.WeatherUnavailableException
import java.time.Clock
import java.time.Duration
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

  /** The service refuses for now because it was asked too often, and none is saved. */
  data object Busy : ForecastUiState

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
  private var loadedAt: Instant = clock.instant()
  private var loadedOn: LocalDate = LocalDate.now(clock)

  init {
    load()
  }

  /** Tries again after a failure. */
  fun retry() {
    if (_state.value.isFailure()) load()
  }

  /**
   * Brings the card up to date when the screen comes back, e.g. the next day or after a long while.
   * What is shown stays until the new answer arrives, and a failed refresh keeps it.
   */
  fun refresh() {
    val now = clock.instant()
    val dayChanged = LocalDate.now(clock) != loadedOn
    val stale = Duration.between(loadedAt, now) >= REFRESH_AFTER
    when (_state.value) {
      ForecastUiState.Loading -> Unit
      ForecastUiState.Failed,
      ForecastUiState.Busy -> load()
      else -> if (dayChanged || stale) load(keepWhileLoading = true)
    }
  }

  private fun load(keepWhileLoading: Boolean = false) {
    job?.cancel()
    val before = _state.value
    if (!keepWhileLoading) _state.value = ForecastUiState.Loading
    loadedAt = clock.instant()
    loadedOn = LocalDate.now(clock)
    job = viewModelScope.launch {
      val today = LocalDate.now(clock)
      val next =
          when {
            endDate.isBefore(today) -> ForecastUiState.Hidden
            !ForecastWindow.isAvailable(startDate, today) ->
                ForecastUiState.TooFar(ForecastWindow.availableFrom(startDate))
            else -> fetch()
          }
      // A refresh that fails must not replace a forecast that is still worth showing.
      _state.value = if (keepWhileLoading && next.isFailure()) before else next
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
      } catch (unavailable: WeatherUnavailableException) {
        if (unavailable.rateLimited) ForecastUiState.Busy else ForecastUiState.Failed
      } catch (_: Exception) {
        ForecastUiState.Failed
      }

  private companion object {
    val REFRESH_AFTER: Duration = Duration.ofHours(1)
  }
}

private fun ForecastUiState.isFailure() =
    this == ForecastUiState.Failed || this == ForecastUiState.Busy
