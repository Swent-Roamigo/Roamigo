package com.swent.roamigo.ui.trip.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swent.roamigo.model.trip.TripRepository
import com.swent.roamigo.ui.components.rememberNotImplementedAction

@Composable
fun TripHistoryRoute(
    tripRepository: TripRepository,
    onBackClick: () -> Unit,
    onTripsClick: () -> Unit,
    onFriendsClick: () -> Unit = rememberNotImplementedAction(),
    onMapClick: () -> Unit = rememberNotImplementedAction(),
) {
  val historyViewModel: TripHistoryViewModel =
      viewModel(factory = TripHistoryViewModel.factory(tripRepository))

  val historyState by historyViewModel.uiState.collectAsState()

  TripHistoryScreen(
      uiState = historyState,
      onBackClick = onBackClick,
      onTripsClick = onTripsClick,
      onFriendsClick = onFriendsClick,
      onMapClick = onMapClick,
      onRetryClick = historyViewModel::refresh,
  )
}
