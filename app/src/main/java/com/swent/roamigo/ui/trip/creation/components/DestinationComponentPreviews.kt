// Author : Constantin Dunoyer de Segonzac
// Co-author : ChatGPT-Codex
package com.swent.roamigo.ui.trip.creation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.swent.roamigo.model.destination.Destination
import com.swent.roamigo.model.destination.DestinationKind
import com.swent.roamigo.ui.theme.RoamigoTheme

@Preview(showBackground = true, widthDp = 169)
@Composable
private fun DestinationCardPreview() {
  RoamigoTheme {
    DestinationCard(
        destination =
            Destination(
                stableId = "geonames:1",
                kind = DestinationKind.CITY,
                name = "Lisbon",
                countryName = "Portugal",
                countryCode = "PT",
                latitude = 0.0,
                longitude = 0.0,
                geonameId = 1,
                wikidataId = null,
            ),
        selected = true,
        photo = null,
        onRequestPhoto = {},
        onClick = {},
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun SelectedDestinationsPreview() {
  RoamigoTheme {
    SelectedDestinations(
        destinations =
            listOf(
                previewDestination("geonames:1", "Lisbon", 1),
                previewDestination("geonames:2", "Porto", 2),
            ),
        onRemove = {},
    )
  }
}

private fun previewDestination(stableId: String, name: String, geonameId: Int) =
    Destination(
        stableId = stableId,
        kind = DestinationKind.CITY,
        name = name,
        countryName = "Portugal",
        countryCode = "PT",
        latitude = 0.0,
        longitude = 0.0,
        geonameId = geonameId,
        wikidataId = null,
    )
