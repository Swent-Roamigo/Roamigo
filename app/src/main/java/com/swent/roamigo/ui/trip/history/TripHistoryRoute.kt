// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swent.roamigo.model.trip.TripRepository
import com.swent.roamigo.ui.components.rememberNotImplementedAction

@Composable
fun TripHistoryRoute(
    tripRepository: TripRepository,
    onBackClick: () -> Unit,
    onTripsClick: () -> Unit,
    onMapClick: () -> Unit,
    onOpenTrip: (String) -> Unit,
    onFriendsClick: () -> Unit = rememberNotImplementedAction(),
) {
  val historyViewModel: TripHistoryViewModel =
      viewModel(factory = TripHistoryViewModel.factory(tripRepository))

  val historyState by historyViewModel.uiState.collectAsStateWithLifecycle()

  TripHistoryScreen(
      uiState = historyState,
      onOpenTrip = onOpenTrip,
      onBackClick = onBackClick,
      onTripsClick = onTripsClick,
      onFriendsClick = onFriendsClick,
      onMapClick = onMapClick,
      onRetryClick = historyViewModel::refresh,
  )
}
