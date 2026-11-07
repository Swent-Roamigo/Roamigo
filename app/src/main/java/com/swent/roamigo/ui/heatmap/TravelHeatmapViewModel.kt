package com.swent.roamigo.ui.heatmap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Contract for loading visits without depending on a concrete repository. */
fun interface VisitedCountryCounts {
  suspend fun visitCounts(): Map<String, Int>
}

sealed interface TravelHeatmapUiState {
  data object Loading : TravelHeatmapUiState

  data class Success(
      val visitCounts: Map<String, Int>,
  ) : TravelHeatmapUiState

  data object Error : TravelHeatmapUiState
}

class TravelHeatmapViewModel(
    private val source: VisitedCountryCounts,
) : ViewModel() {

  private val _uiState = MutableStateFlow<TravelHeatmapUiState>(TravelHeatmapUiState.Loading)

  val uiState = _uiState.asStateFlow()

  private var loadJob: Job? = null

  init {
    refresh()
  }

  fun refresh() {
    loadJob?.cancel()
    _uiState.value = TravelHeatmapUiState.Loading

    loadJob = viewModelScope.launch {
      try {
        val counts =
            source.visitCounts().filter { (code, visits) ->
              code.length == 2 && code.all { it in 'A'..'Z' } && visits > 0
            }

        _uiState.value = TravelHeatmapUiState.Success(counts)
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        _uiState.value = TravelHeatmapUiState.Error
      }
    }
  }

  companion object {
    fun factory(
        source: VisitedCountryCounts,
    ): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(TravelHeatmapViewModel::class.java))
            return TravelHeatmapViewModel(source) as T
          }
        }
  }
}
