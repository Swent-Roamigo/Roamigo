package com.swent.roamigo.ui.heatmap

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.swent.roamigo.R
import com.swent.roamigo.resources.C

@Composable
fun TravelHeatmapPreview(
    uiState: TravelHeatmapUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val description = stringResource(R.string.heatmap_open)

  val counts = (uiState as? TravelHeatmapUiState.Success)?.visitCounts.orEmpty()

  Box(
      modifier =
          modifier
              .fillMaxWidth()
              .testTag(C.Tag.trip_history_world_map)
              .semantics { contentDescription = description }
              .clickable(onClick = onClick),
  ) {
    DottedWorldMap(visitCounts = counts)

    if (uiState is TravelHeatmapUiState.Error) {
      Text(
          text = stringResource(R.string.heatmap_load_error),
          modifier = Modifier.align(Alignment.Center).testTag(C.Tag.heatmap_error),
          color = MaterialTheme.colorScheme.error,
      )
    }
  }
}
