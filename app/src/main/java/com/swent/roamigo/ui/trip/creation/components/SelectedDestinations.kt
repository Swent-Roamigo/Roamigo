// Author : Constantin Dunoyer de Segonzac
// Co-author : ChatGPT-Codex
// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.creation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import com.swent.roamigo.R
import com.swent.roamigo.model.destination.Destination
import com.swent.roamigo.ui.theme.Spacing

internal object SelectedDestinationsTestTags {
  const val REMOVE_PREFIX = "selected_destination_remove_"

  fun removeFor(destinationId: String): String = "$REMOVE_PREFIX${destinationId.lowercase()}"
}

@Composable
fun SelectedDestinations(
    destinations: List<Destination>,
    onRemove: (Destination) -> Unit,
) {
  Row(
      modifier = Modifier.horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(Spacing.small),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    destinations.forEachIndexed { index, destination ->
      if (index > 0) Text("→", color = MaterialTheme.colorScheme.onSurfaceVariant)
      DestinationChip(destination, onRemove = { onRemove(destination) })
    }
  }
}

@Composable
private fun DestinationChip(destination: Destination, onRemove: () -> Unit) {
  val removeDescription =
      stringResource(
          R.string.destination_remove_description,
          destination.name,
          destination.countryName,
      )
  Row(
      modifier =
          Modifier.clip(CircleShape)
              .background(MaterialTheme.colorScheme.primary)
              .padding(
                  start = Spacing.medium,
                  end = Spacing.small,
                  top = Spacing.snug,
                  bottom = Spacing.snug,
              ),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(Spacing.snug),
  ) {
    Text(
        destination.name,
        color = MaterialTheme.colorScheme.onPrimary,
        style = MaterialTheme.typography.labelMedium,
    )
    Text(
        "×",
        modifier =
            Modifier.clip(CircleShape)
                .clickable(onClick = onRemove)
                .padding(horizontal = Spacing.extraSmall)
                .semantics {
                  contentDescription = removeDescription
                  testTag = SelectedDestinationsTestTags.removeFor(destination.stableId)
                },
        color = MaterialTheme.colorScheme.onPrimary,
        style = MaterialTheme.typography.labelMedium,
    )
  }
}
