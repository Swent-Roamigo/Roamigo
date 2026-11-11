// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.trip.creation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.swent.roamigo.R
import com.swent.roamigo.model.destination.Destination
import com.swent.roamigo.model.destination.DestinationPhoto

private const val DESTINATION_GRID_COLUMNS = 2

/** A titled grid of [destinations] as photo cards, used for popular cities and search results. */
@Composable
internal fun DestinationList(
    title: String,
    destinations: List<Destination>,
    selectedDestinations: List<Destination>,
    photosByDestinationId: Map<String, DestinationPhoto>,
    destinationIdsWithoutPhoto: Set<String>,
    onToggleDestination: (Destination) -> Unit,
    onRequestPhoto: (Destination) -> Unit,
    modifier: Modifier = Modifier,
    showGeonamesCredit: Boolean = false,
) {
  Column(modifier = modifier.fillMaxWidth()) {
    Text(
        title,
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
      items(destinations, key = { it.stableId }) { destination ->
        DestinationCard(
            destination = destination,
            selected = selectedDestinations.any { it.stableId == destination.stableId },
            photo = photosByDestinationId[destination.stableId],
            photoUnavailable = destination.stableId in destinationIdsWithoutPhoto,
            onRequestPhoto = { onRequestPhoto(destination) },
            onClick = { onToggleDestination(destination) },
        )
      }
    }
    if (showGeonamesCredit) {
      Text(
          stringResource(R.string.destination_geonames_credit),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
