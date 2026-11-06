package com.swent.roamigo.ui.trip.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swent.roamigo.model.trip.TripRepository
import com.swent.roamigo.ui.components.rememberNotImplementedAction
import com.swent.roamigo.ui.heatmap.TravelHeatmapViewModel
import com.swent.roamigo.ui.heatmap.VisitedCountryCounts

@Composable
fun TripHistoryRoute(
    tripRepository: TripRepository,
    countryCounts: VisitedCountryCounts,
    sharedOwner: ViewModelStoreOwner,
    onBackClick: () -> Unit,
    onTripsClick: () -> Unit,
    onMapClick: () -> Unit,
    onOpenTrip: (String) -> Unit,
    onOpenWorldMapClick: () -> Unit,
    onFriendsClick: () -> Unit = rememberNotImplementedAction(),
) {
  val historyViewModel: TripHistoryViewModel =
      viewModel(
          viewModelStoreOwner = sharedOwner,
          factory = TripHistoryViewModel.factory(tripRepository),
      )

  val heatmapViewModel: TravelHeatmapViewModel =
      viewModel(
          viewModelStoreOwner = sharedOwner,
          factory = TravelHeatmapViewModel.factory(countryCounts),
      )

  val historyState by historyViewModel.uiState.collectAsStateWithLifecycle()

  val heatmapState by heatmapViewModel.uiState.collectAsStateWithLifecycle()

  TripHistoryScreen(
      uiState = historyState,
      heatmapUiState = heatmapState,
      onOpenTrip = onOpenTrip,
      onOpenWorldMapClick = onOpenWorldMapClick,
      onBackClick = onBackClick,
      onTripsClick = onTripsClick,
      onMapClick = onMapClick,
      onFriendsClick = onFriendsClick,
      onRetryClick = historyViewModel::refresh,
  )
}
