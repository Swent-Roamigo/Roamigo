// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.creation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swent.roamigo.model.destination.DestinationPhoto
import com.swent.roamigo.model.destination.DestinationRepository
import com.swent.roamigo.model.trip.TripRepository

/**
 * Owns the draft shared by the destination and date/traveler steps.
 *
 * [savedStateHandle] is the existing navigation-entry handle used to retain the draft. Navigation
 * and result delivery are delegated to the caller.
 */
@Composable
fun CreateTripRoute(
    tripRepository: TripRepository,
    destinationRepository: DestinationRepository,
    savedStateHandle: SavedStateHandle,
    onClose: () -> Unit,
    onSignIn: () -> Unit,
    onTripCreated: (String, DestinationPhoto?) -> Unit,
) {
  val creationViewModel = viewModel {
    CreateTripViewModel(
        repository = tripRepository,
        destinationRepository = destinationRepository,
        savedStateHandle = savedStateHandle,
    )
  }
  val state by creationViewModel.uiState.collectAsStateWithLifecycle()
  var datesStep by rememberSaveable { mutableStateOf(false) }
  val back: () -> Unit = {
    if (!state.isSubmitting) {
      if (datesStep) datesStep = false else onClose()
    }
  }
  BackHandler(onBack = back)
  LaunchedEffect(state.createdTripId) {
    if (state.createdTripId != null) {
      val first = state.selectedDestinations.firstOrNull()
      val photo = first?.let { state.photosByDestinationId[it.stableId] }
      onTripCreated(state.createdTripId!!, photo)
    }
  }
  if (datesStep) {
    DatesTravelersScreen(
        uiState = state,
        onBack = back,
        onClose = { if (!state.isSubmitting) onClose() },
        onCreateTrip = creationViewModel::createTrip,
        onSignIn = onSignIn,
        onPreviousMonth = creationViewModel::showPreviousMonth,
        onNextMonth = creationViewModel::showNextMonth,
        onSelectDate = creationViewModel::selectDate,
        onFlexibleDatesChange = creationViewModel::setFlexibleDates,
        onTravelModeChange = creationViewModel::selectTravelMode,
        onDecrementTravelers = creationViewModel::decrementTravelers,
        onIncrementTravelers = creationViewModel::incrementTravelers,
    )
  } else {
    DestinationScreen(
        uiState = state,
        onBack = back,
        onClose = onClose,
        onQueryChange = creationViewModel::updateDestinationQuery,
        onToggleDestination = creationViewModel::toggleDestination,
        onRemoveDestination = creationViewModel::removeDestination,
        onRetry = creationViewModel::retryDestinationSearch,
        onRequestPhoto = creationViewModel::requestDestinationPhoto,
        onContinue = { datesStep = true },
    )
  }
}
