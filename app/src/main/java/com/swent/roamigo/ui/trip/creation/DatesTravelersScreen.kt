// Author : Constantin Dunoyer de Segonzac
// Co-author : ChatGPT-Codex
package com.swent.roamigo.ui.trip.creation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swent.roamigo.R
import com.swent.roamigo.resources.C
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
) {
  val uiState by createTripViewModel.uiState.collectAsStateWithLifecycle()
  DatesTravelersScreenContent(
      uiState = uiState,
      onBack = onBack,
      onClose = onClose,
      onPreviousMonth = createTripViewModel::showPreviousMonth,
      onNextMonth = createTripViewModel::showNextMonth,
      onSelectDate = createTripViewModel::selectDate,
      onFlexibleDatesChange = createTripViewModel::setFlexibleDates,
      onTripTypeChange = createTripViewModel::selectTripType,
      onDecrementTravelers = createTripViewModel::decrementTravelers,
      onIncrementTravelers = createTripViewModel::incrementTravelers,
  )
}

@Composable
fun DatesTravelersScreenContent(
    uiState: CreateTripUiState,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onFlexibleDatesChange: (Boolean) -> Unit,
    onTripTypeChange: (TripType) -> Unit,
    onDecrementTravelers: () -> Unit,
    onIncrementTravelers: () -> Unit,
) {
  CreateTripScaffold(
      step = 2,
      title = stringResource(R.string.c2_title),
      onBack = onBack,
      onClose = onClose,
      primaryActionLabel = stringResource(R.string.c2_create_trip),
      onPrimaryAction = {},
      primaryActionEnabled = uiState.canCreateTrip,
      primaryActionTestTag = C.Tag.c2_create_trip,
  ) {
    Column(
        modifier =
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).semantics {
              testTag = C.Tag.c2_screen
            },
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
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
      TripTypeSelector(uiState.tripType, onTripTypeChange)
      if (uiState.tripType == TripType.GROUP) {
        TravelersCounter(
            count = uiState.travelerCount,
            tripType = uiState.tripType,
            onDecrement = onDecrementTravelers,
            onIncrement = onIncrementTravelers,
        )
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
        onPreviousMonth = {},
        onNextMonth = {},
        onSelectDate = {},
        onFlexibleDatesChange = {},
        onTripTypeChange = {},
        onDecrementTravelers = {},
        onIncrementTravelers = {},
    )
  }
}
