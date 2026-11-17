// Implemented with assistance from OpenAI Codex.
package com.swent.roamigo.ui.placepicker

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.swent.roamigo.R
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.place.PlaceCandidate
import com.swent.roamigo.ui.map.MapCoordinate
import com.swent.roamigo.ui.theme.RoamigoTheme

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun PlacePickerResultsPreview() =
    PickerPreview(
        PlacePickerUiState(
            query = "café",
            searchStatus = PlaceSearchStatus.RESULTS,
            candidates =
                listOf(
                    PlaceCandidate("1", "Café Romand", "Place Saint-François, Lausanne"),
                    PlaceCandidate("2", "Café de Grancy", "Avenue du Rond-Point, Lausanne"),
                ),
        )
    )

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun PlacePickerSelectedPreview() =
    PickerPreview(
        PlacePickerUiState(
            query = "café",
            selectedLocation =
                TripLocation(46.52, 6.63, "Café Romand – Place Saint-François, Lausanne", "CH"),
        )
    )

@Preview(showBackground = true, widthDp = 640, heightDp = 360)
@Composable
private fun PlacePickerErrorPreview() =
    PickerPreview(
        PlacePickerUiState(
            query = "café",
            searchStatus = PlaceSearchStatus.ERROR,
            searchError = PlacePickerError.UNAVAILABLE,
        )
    )

@Composable
private fun PickerPreview(state: PlacePickerUiState) {
  RoamigoTheme { PlacePickerScreen(state, {}, {}, {}, {}, {}) }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun PlacePickerManualEmptyPreview() = ManualPickerPreview(PlacePickerManualDraft())

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun PlacePickerManualReadyPreview() =
    ManualPickerPreview(PlacePickerManualDraft(MapCoordinate(46.52, 6.63), "Meeting point"))

@Composable
private fun ManualPickerPreview(draft: PlacePickerManualDraft) {
  RoamigoTheme {
    PlacePickerMapScreen(
        PlacePickerUiState(isMapMode = true, manualDraft = draft),
        {},
        {},
        {},
        {},
    ) { modifier ->
      Surface(
          modifier,
          color = MaterialTheme.colorScheme.surfaceVariant,
      ) {
        // Keep the stand-in self-contained: do not load the Mapbox adapter even for its marker.
        Box(contentAlignment = Alignment.Center) {
          if (draft.point != null) {
            Icon(
                painter = painterResource(R.drawable.ic_map_marker),
                contentDescription = stringResource(R.string.place_picker_map_marker),
                tint = Color.Unspecified,
            )
          }
        }
      }
    }
  }
}
