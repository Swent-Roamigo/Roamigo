package com.swent.roamigo.ui.heatmap

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swent.roamigo.model.trip.TripRepository
import com.swent.roamigo.ui.trip.history.TripHistoryUiState
import com.swent.roamigo.ui.trip.history.TripHistoryViewModel

/** Reuses the ViewModels owned by the Trip History back stack entry. */
@Composable
fun ExploredWorldRoute(
    tripRepository: TripRepository,
    countryCounts: VisitedCountryCounts,
    sharedOwner: ViewModelStoreOwner,
    onBackClick: () -> Unit,
) {
  val heatmapViewModel: TravelHeatmapViewModel =
      viewModel(
          viewModelStoreOwner = sharedOwner,
          factory = TravelHeatmapViewModel.factory(countryCounts),
      )

  val historyViewModel: TripHistoryViewModel =
      viewModel(
          viewModelStoreOwner = sharedOwner,
          factory = TripHistoryViewModel.factory(tripRepository),
      )

  val heatmapState by heatmapViewModel.uiState.collectAsStateWithLifecycle()

  val historyState by historyViewModel.uiState.collectAsStateWithLifecycle()

  val completedTripCount = (historyState as? TripHistoryUiState.Success)?.trips?.size

  ExploredWorldScreen(
      uiState = heatmapState,
      completedTripCount = completedTripCount,
      onBackClick = onBackClick,
  )
}
