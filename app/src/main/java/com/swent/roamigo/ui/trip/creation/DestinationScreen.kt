// Author : Constantin Dunoyer de Segonzac
// Co-author : ChatGPT-Codex
// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.creation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.swent.roamigo.R
import com.swent.roamigo.model.destination.Destination
import com.swent.roamigo.ui.trip.creation.components.CreateTripScaffold
import com.swent.roamigo.ui.trip.creation.components.DestinationList
import com.swent.roamigo.ui.trip.creation.components.DestinationScreenTestTags
import com.swent.roamigo.ui.trip.creation.components.DestinationSearchBar
import com.swent.roamigo.ui.trip.creation.components.SelectedDestinations

/** Renders destination selection from [uiState] and forwards user actions. */
@Composable
fun DestinationScreen(
    uiState: CreateTripUiState,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onQueryChange: (String) -> Unit,
    onToggleDestination: (Destination) -> Unit,
    onRemoveDestination: (Destination) -> Unit,
    onRetry: () -> Unit = {},
    onRequestPhoto: (Destination) -> Unit = {},
    onContinue: () -> Unit,
) {
  CreateTripScaffold(
      step = 1,
      title = stringResource(R.string.create_trip_destination_title),
      subtitle = stringResource(R.string.create_trip_destination_subtitle),
      onBack = onBack,
      onClose = onClose,
      primaryActionLabel =
          pluralStringResource(
              R.plurals.create_trip_destination_continue,
              uiState.selectedDestinations.size,
              uiState.selectedDestinations.size,
          ),
      onPrimaryAction = onContinue,
      primaryActionEnabled = uiState.selectedDestinations.isNotEmpty(),
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      DestinationSearchBar(
          query = uiState.destinationQuery,
          onQueryChange = onQueryChange,
      )
      Spacer(Modifier.height(16.dp))
      SelectedDestinations(
          destinations = uiState.selectedDestinations,
          onRemove = onRemoveDestination,
      )
      Spacer(Modifier.height(20.dp))
      when (uiState.destinationSearchStatus) {
        DestinationSearchStatus.INITIAL ->
            // Nothing typed yet: offer well-known cities instead of an empty screen.
            DestinationList(
                title = stringResource(R.string.destination_popular_title),
                destinations = uiState.popularDestinations,
                selectedDestinations = uiState.selectedDestinations,
                photosByDestinationId = uiState.photosByDestinationId,
                destinationIdsWithoutPhoto = uiState.destinationIdsWithoutPhoto,
                onToggleDestination = onToggleDestination,
                onRequestPhoto = onRequestPhoto,
                modifier = Modifier.weight(1f),
            )
        DestinationSearchStatus.LOADING ->
            androidx.compose.foundation.layout.Box(
                Modifier.weight(1f).fillMaxWidth().semantics {
                  testTag = DestinationScreenTestTags.LOADING
                },
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
              CircularProgressIndicator()
            }
        DestinationSearchStatus.EMPTY ->
            Text(
                stringResource(R.string.destination_search_empty),
                modifier =
                    Modifier.weight(1f).semantics { testTag = DestinationScreenTestTags.EMPTY },
                style = MaterialTheme.typography.bodyLarge,
            )
        DestinationSearchStatus.ERROR ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
              Text(
                  stringResource(
                      uiState.destinationSearchErrorRes ?: R.string.destination_search_error
                  ),
                  modifier = Modifier.semantics { testTag = DestinationScreenTestTags.ERROR },
              )
              Button(
                  onClick = onRetry,
                  modifier = Modifier.semantics { testTag = DestinationScreenTestTags.RETRY },
              ) {
                Text(stringResource(R.string.destination_search_retry))
              }
            }
        DestinationSearchStatus.RESULTS ->
            DestinationList(
                title = stringResource(R.string.destination_search_results),
                destinations = uiState.visibleDestinations,
                selectedDestinations = uiState.selectedDestinations,
                photosByDestinationId = uiState.photosByDestinationId,
                destinationIdsWithoutPhoto = uiState.destinationIdsWithoutPhoto,
                onToggleDestination = onToggleDestination,
                onRequestPhoto = onRequestPhoto,
                modifier = Modifier.weight(1f),
                showGeonamesCredit = true,
            )
      }
    }
  }
}
