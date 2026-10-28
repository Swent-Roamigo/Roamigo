package com.swent.roamigo.ui.trip.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swent.roamigo.model.trip.TripRepository

@Composable
fun TripHistoryRoute(
    tripRepository: TripRepository,
    onBackClick: () -> Unit,
    onTripsClick: () -> Unit,
) {
  val historyViewModel: TripHistoryViewModel =
      viewModel(factory = TripHistoryViewModel.factory(tripRepository))

  val historyState by historyViewModel.uiState.collectAsState()

  TripHistoryScreen(
      uiState = historyState,
      onBackClick = onBackClick,
      onTripsClick = onTripsClick,
      onRetryClick = historyViewModel::refresh,
  )
}
