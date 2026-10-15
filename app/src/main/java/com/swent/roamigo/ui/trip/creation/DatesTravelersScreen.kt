// Author : Constantin Dunoyer de Segonzac
// Co-author : ChatGPT-Codex
package com.swent.roamigo.ui.trip.creation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swent.roamigo.R
import com.swent.roamigo.model.trip.TravelMode
import com.swent.roamigo.model.trip.TripCreationException
import com.swent.roamigo.ui.theme.RoamigoTheme
import com.swent.roamigo.ui.trip.creation.components.CalendarCard
import com.swent.roamigo.ui.trip.creation.components.CreateTripScaffold
import com.swent.roamigo.ui.trip.creation.components.DateSummary
import com.swent.roamigo.ui.trip.creation.components.TravelersCounter
import com.swent.roamigo.ui.trip.creation.components.TripTypeSelector
import java.time.LocalDate

@Composable
fun DatesTravelersScreen(
    createTripViewModel: CreateTripViewModel,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onCreateTrip: () -> Unit,
    onSignIn: () -> Unit = {},
) {
  val uiState by createTripViewModel.uiState.collectAsStateWithLifecycle()
  DatesTravelersScreenContent(
      uiState = uiState,
      onBack = onBack,
      onClose = onClose,
      onCreateTrip = onCreateTrip,
      onSignIn = onSignIn,
      onPreviousMonth = createTripViewModel::showPreviousMonth,
      onNextMonth = createTripViewModel::showNextMonth,
      onSelectDate = createTripViewModel::selectDate,
      onFlexibleDatesChange = createTripViewModel::setFlexibleDates,
      onTravelModeChange = createTripViewModel::selectTravelMode,
      onDecrementTravelers = createTripViewModel::decrementTravelers,
      onIncrementTravelers = createTripViewModel::incrementTravelers,
  )
}

@Composable
fun DatesTravelersScreenContent(
    uiState: CreateTripUiState,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onCreateTrip: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onFlexibleDatesChange: (Boolean) -> Unit,
    onTravelModeChange: (TravelMode) -> Unit,
    onDecrementTravelers: () -> Unit,
    onIncrementTravelers: () -> Unit,
    onSignIn: () -> Unit = {},
) {
  val error = uiState.creationError
  val needsSignIn = error is TripCreationException.NotAuthenticated
  CreateTripScaffold(
      step = 2,
      title = stringResource(R.string.c2_title),
      onBack = onBack,
      onClose = onClose,
      primaryActionLabel =
          stringResource(
              when (error) {
                is TripCreationException.NotAuthenticated -> R.string.create_trip_sign_in
                is TripCreationException.Network,
                is TripCreationException.Unknown -> R.string.create_trip_retry
                else -> R.string.c2_create_trip
              }
          ),
      onPrimaryAction = if (needsSignIn) onSignIn else onCreateTrip,
      primaryActionEnabled = uiState.canCreateTrip,
      navigationEnabled = !uiState.isSubmitting,
  ) {
    if (uiState.isSubmitting) {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
      }
    } else {
      Column(
          modifier =
              Modifier.fillMaxSize()
                  .verticalScroll(rememberScrollState())
                  .testTag(DatesTravelersScreenTestTags.SCREEN),
          verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        if (error != null) {
          Text(
              text =
                  stringResource(
                      when (error) {
                        is TripCreationException.NotAuthenticated -> R.string.create_trip_auth_error
                        is TripCreationException.PermissionDenied ->
                            R.string.create_trip_permission_error
                        is TripCreationException.Validation -> R.string.create_trip_validation_error
                        is TripCreationException.Network -> R.string.create_trip_network_error
                        is TripCreationException.Unknown -> R.string.create_trip_unknown_error
                      }
                  ),
              color = MaterialTheme.colorScheme.error,
          )
        }
        CalendarCard(
            state = uiState,
            onPreviousMonth = onPreviousMonth,
            onNextMonth = onNextMonth,
            onSelectDate = onSelectDate,
        )
        DateSummary(state = uiState, onFlexibleDatesChange = onFlexibleDatesChange)
        Text(
            text = stringResource(R.string.c2_who_is_coming),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        TripTypeSelector(uiState.travelMode, onTravelModeChange)
        if (uiState.travelMode == TravelMode.GROUP) {
          TravelersCounter(
              count = uiState.travelerCount,
              onDecrement = onDecrementTravelers,
              onIncrement = onIncrementTravelers,
          )
        }
      }
    }
  }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun DatesTravelersScreenPreview() {
  RoamigoTheme {
    DatesTravelersScreenContent(
        uiState =
            CreateTripUiState(
                startDate = LocalDate.of(2026, 10, 12),
                endDate = LocalDate.of(2026, 10, 19),
            ),
        onBack = {},
        onClose = {},
        onCreateTrip = {},
        onPreviousMonth = {},
        onNextMonth = {},
        onSelectDate = {},
        onFlexibleDatesChange = {},
        onTravelModeChange = {},
        onDecrementTravelers = {},
        onIncrementTravelers = {},
    )
  }
}

internal object DatesTravelersScreenTestTags {
  const val SCREEN = "c2_screen"
}
