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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.swent.roamigo.ui.theme.RoamigoTheme

internal object SelectedDestinationsTestTags {
  const val REMOVE_PREFIX = "selected_destination_remove_"

  fun removeFor(destination: String): String = "$REMOVE_PREFIX${destination.lowercase()}"
}

@Composable
fun SelectedDestinations(
    destinations: List<String>,
    onRemove: (String) -> Unit,
) {
  Row(
      modifier = Modifier.horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    destinations.forEachIndexed { index, destination ->
      if (index > 0) Text("→", color = MaterialTheme.colorScheme.onSurfaceVariant)
      DestinationChip(destination, onRemove = { onRemove(destination) })
    }
  }
}

@Composable
private fun DestinationChip(destination: String, onRemove: () -> Unit) {
  Row(
      modifier =
          Modifier.clip(CircleShape)
              .background(MaterialTheme.colorScheme.primary)
              .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Text(
        destination,
        color = MaterialTheme.colorScheme.onPrimary,
        style = MaterialTheme.typography.labelMedium,
    )
    Text(
        "×",
        modifier =
            Modifier.clip(CircleShape)
                .clickable(onClick = onRemove)
                .padding(horizontal = 2.dp)
                .semantics { testTag = SelectedDestinationsTestTags.removeFor(destination) },
        color = MaterialTheme.colorScheme.onPrimary,
        style = MaterialTheme.typography.labelMedium,
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun SelectedDestinationsPreview() {
  RoamigoTheme {
    SelectedDestinations(
        destinations = listOf("Lisbon", "Porto"),
        onRemove = {},
    )
  }
}
