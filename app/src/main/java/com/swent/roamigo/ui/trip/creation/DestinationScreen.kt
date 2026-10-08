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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swent.roamigo.ui.theme.RoamigoTheme
import com.swent.roamigo.ui.trip.creation.components.CreateTripScaffold
import com.swent.roamigo.ui.trip.creation.components.DestinationCard
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
      onContinue = onContinue,
  )
}

@Composable
fun DestinationScreenContent(
    uiState: CreateTripUiState,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onQueryChange: (String) -> Unit,
    onToggleDestination: (String) -> Unit,
    onRemoveDestination: (String) -> Unit,
    onContinue: () -> Unit,
) {
  CreateTripScaffold(
      step = 1,
      title = "Where to?",
      subtitle = "Add one city or a whole route. You can change it later.",
      onBack = onBack,
      onClose = onClose,
      primaryActionLabel = "Continue · ${uiState.selectedDestinations.size} stops",
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
      Text(
          "Popular with students",
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onBackground,
      )
      Spacer(Modifier.height(12.dp))
      LazyVerticalGrid(
          columns = GridCells.Fixed(2),
          modifier = Modifier.weight(1f).fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        items(uiState.visibleDestinations, key = { it }) { destination ->
          DestinationCard(
              destination = destination,
              selected = destination in uiState.selectedDestinations,
              onClick = { onToggleDestination(destination) },
          )
        }
      }
    }
  }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun DestinationScreenPreview() {
  RoamigoTheme {
    DestinationScreenContent(
        uiState = CreateTripUiState(selectedDestinations = listOf("Lisbon", "Porto")),
        onBack = {},
        onClose = {},
        onQueryChange = {},
        onToggleDestination = {},
        onRemoveDestination = {},
        onContinue = {},
    )
  }
}
