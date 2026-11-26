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
   * Brings the card up to date when the screen comes back: the next day, after a long while, or
   * whenever what is shown is a saved forecast, which is the one a returning connection replaces.
   * What is shown stays until the new answer arrives, and a failed refresh keeps a forecast.
   */
  fun refresh() {
    val now = clock.instant()
    val dayChanged = LocalDate.now(clock) != loadedOn
    val stale = Duration.between(loadedAt, now) >= REFRESH_AFTER
    val current = _state.value
    when {
      current == ForecastUiState.Loading -> Unit
      current.isFailure() -> load()
      // Already on its way, e.g. the screen came back twice in a row.
      job?.isActive == true -> Unit
      current.isSavedForecast() || dayChanged || stale -> load(keepWhileLoading = true)
    }
  }

  private fun load(keepWhileLoading: Boolean = false) {
    job?.cancel()
    val before = _state.value
    if (!keepWhileLoading) _state.value = ForecastUiState.Loading
    job = viewModelScope.launch {
      val startedAt = clock.instant()
      val today = LocalDate.now(clock)
      val next =
          when {
            endDate.isBefore(today) -> ForecastUiState.Hidden
            !ForecastWindow.isAvailable(startDate, today) ->
                ForecastUiState.TooFar(ForecastWindow.availableFrom(startDate))
            else -> fetch()
          }
      // Only a forecast is worth keeping when a refresh fails. Any other message, such as "too far
      // ahead", may be out of date by now. A kept forecast does not count as refreshed, so the next
      // time the screen comes back it tries again.
      if (keepWhileLoading && before is ForecastUiState.Ready && next.isFailure()) {
        _state.value = before
      } else {
        _state.value = next
        loadedAt = startedAt
        loadedOn = today
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
      } catch (_: IllegalArgumentException) {
        // Coordinates that are not on Earth: asking again would fail the same way.
        ForecastUiState.Hidden
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

private fun ForecastUiState.isSavedForecast() = this is ForecastUiState.Ready && fromCache
