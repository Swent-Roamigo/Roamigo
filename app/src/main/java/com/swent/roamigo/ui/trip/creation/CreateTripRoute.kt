// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.creation

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swent.roamigo.R
import com.swent.roamigo.model.destination.DestinationRepository
import com.swent.roamigo.model.trip.TripRepository
import com.swent.roamigo.resources.C

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
    onTripCreated: (String) -> Unit,
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
  var confirmingDiscard by rememberSaveable { mutableStateOf(false) }
  // Leaving with a chosen destination would lose the draft, so ask first.
  val close: () -> Unit = {
    if (!state.isSubmitting) {
      if (state.selectedDestinations.isEmpty()) onClose() else confirmingDiscard = true
    }
  }
  val back: () -> Unit = {
    if (!state.isSubmitting) {
      if (datesStep) datesStep = false else close()
    }
  }
  BackHandler(onBack = back)
  if (confirmingDiscard) {
    AlertDialog(
        onDismissRequest = { confirmingDiscard = false },
        title = { Text(stringResource(R.string.create_trip_discard_title)) },
        text = { Text(stringResource(R.string.create_trip_discard_message)) },
        confirmButton = {
          TextButton(
              onClick = {
                confirmingDiscard = false
                onClose()
              },
              modifier = Modifier.testTag(C.Tag.create_trip_discard_confirm),
          ) {
            Text(
                stringResource(R.string.create_trip_discard),
                color = MaterialTheme.colorScheme.error,
            )
          }
        },
        dismissButton = {
          TextButton(onClick = { confirmingDiscard = false }) {
            Text(stringResource(R.string.create_trip_keep_editing))
          }
        },
    )
  }
  LaunchedEffect(state.createdTripId) { state.createdTripId?.let(onTripCreated) }
  if (datesStep) {
    DatesTravelersScreen(
        uiState = state,
        onBack = back,
        onClose = close,
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
        onClose = close,
        onQueryChange = creationViewModel::updateDestinationQuery,
        onToggleDestination = creationViewModel::toggleDestination,
        onRemoveDestination = creationViewModel::removeDestination,
        onRetry = creationViewModel::retryDestinationSearch,
        onRequestPhoto = creationViewModel::requestDestinationPhoto,
        onContinue = { datesStep = true },
    )
  }
}
