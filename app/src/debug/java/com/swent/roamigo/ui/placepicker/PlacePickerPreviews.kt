// Implemented with assistance from OpenAI Codex.
package com.swent.roamigo.ui.placepicker

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.place.PlaceCandidate
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
            selectedLocation = TripLocation(46.52, 6.63, "Café Romand", "CH"),
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
