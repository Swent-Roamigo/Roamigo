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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swent.roamigo.R
import com.swent.roamigo.model.destination.Destination
import com.swent.roamigo.ui.theme.RoamigoTheme
import com.swent.roamigo.ui.trip.creation.components.CreateTripScaffold
import com.swent.roamigo.ui.trip.creation.components.DestinationCard
import com.swent.roamigo.ui.trip.creation.components.DestinationScreenTestTags
import com.swent.roamigo.ui.trip.creation.components.DestinationSearchBar
import com.swent.roamigo.ui.trip.creation.components.SelectedDestinations

@Composable
fun DestinationScreen(
    createTripViewModel: CreateTripViewModel,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onContinue: () -> Unit,
) {
  val uiState by createTripViewModel.uiState.collectAsStateWithLifecycle()
  DestinationScreenContent(
      uiState = uiState,
      onBack = onBack,
      onClose = onClose,
      onQueryChange = createTripViewModel::updateDestinationQuery,
      onToggleDestination = createTripViewModel::toggleDestination,
      onRemoveDestination = createTripViewModel::removeDestination,
      onRetry = createTripViewModel::retryDestinationSearch,
      onRequestPhoto = createTripViewModel::requestDestinationPhoto,
      onContinue = onContinue,
  )
}

@Composable
fun DestinationScreenContent(
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
            Text(
                stringResource(R.string.destination_search_initial),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
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
        DestinationSearchStatus.RESULTS -> {
          Text(
              stringResource(R.string.destination_search_results),
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onBackground,
          )
          Spacer(Modifier.height(12.dp))
          LazyVerticalGrid(
              columns = GridCells.Fixed(DESTINATION_GRID_COLUMNS),
              modifier = Modifier.weight(1f).fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
              verticalArrangement = Arrangement.spacedBy(16.dp),
          ) {
            items(uiState.visibleDestinations, key = { it.stableId }) { destination ->
              DestinationCard(
                  destination = destination,
                  selected =
                      uiState.selectedDestinations.any { it.stableId == destination.stableId },
                  photo = uiState.photosByDestinationId[destination.stableId],
                  onRequestPhoto = { onRequestPhoto(destination) },
                  onClick = { onToggleDestination(destination) },
              )
            }
          }
          Text(
              stringResource(R.string.destination_geonames_credit),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

private const val DESTINATION_GRID_COLUMNS = 2

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun DestinationScreenPreview() {
  RoamigoTheme {
    DestinationScreenContent(
        uiState = CreateTripUiState(),
        onBack = {},
        onClose = {},
        onQueryChange = {},
        onToggleDestination = {},
        onRemoveDestination = {},
        onRetry = {},
        onRequestPhoto = {},
        onContinue = {},
    )
  }
}
