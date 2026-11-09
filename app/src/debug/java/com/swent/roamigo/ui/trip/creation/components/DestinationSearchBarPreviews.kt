// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.creation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.swent.roamigo.ui.theme.RoamigoTheme

@Preview(showBackground = true)
@Composable
private fun DestinationSearchBarPreview() {
  RoamigoTheme {
    DestinationSearchBar(
        query = "",
        onQueryChange = {},
    )
  }
}
