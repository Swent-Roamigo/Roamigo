package com.swent.roamigo.ui.trip.creation

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import com.swent.roamigo.model.destination.sampleDestinations
import com.swent.roamigo.ui.trip.creation.components.DestinationCardTestTags
import com.swent.roamigo.ui.trip.creation.components.DestinationSearchBarTestTags

/** Exercises the online search interaction using injected, local provider results. */
fun ComposeTestRule.searchSampleDestinations() {
  onNodeWithTag(DestinationSearchBarTestTags.SEARCH_FIELD).performTextInput("all")
  waitUntil(5_000) {
    onAllNodesWithTag(DestinationCardTestTags.forDestination(sampleDestinations.first().stableId))
        .fetchSemanticsNodes().isNotEmpty()
  }
}
