// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.trips

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.resources.C
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TripsScreenPhotosTabTest {
  @get:Rule val composeRule = createComposeRule()

  private val mainTrip =
      MainTripUiModel(
          id = "main",
          name = "Day trip",
          dateRange = "8 Oct",
          durationDays = 1,
          stopCount = 1,
          memberInitials = emptyList(),
          badge = null,
      )

  private val opened = mutableListOf<Pair<String, String>>()
  private var fallbackClicks = 0

  private fun show(state: TripsUiState, withOpener: Boolean = true) {
    composeRule.setContent {
      MaterialTheme {
        TripsScreen(
            uiState = state,
            onPhotosClick = { fallbackClicks++ },
            onOpenTripPhotos = if (withOpener) { id, name -> opened += id to name } else null,
        )
      }
    }
  }

  @Test
  fun thePhotosTabOpensThePhotosOfTheHighlightedTrip() {
    show(TripsUiState(isLoading = false, mainTrip = mainTrip))

    composeRule.onNodeWithTag(C.Tag.trips_navigation_photos).performClick()

    assertEquals(listOf("main" to "Day trip"), opened)
    assertEquals(0, fallbackClicks)
  }

  @Test
  fun withoutATripThereIsNothingToShowSoTheFallbackRuns() {
    show(TripsUiState(isLoading = false, mainTrip = null))

    composeRule.onNodeWithTag(C.Tag.trips_navigation_photos).performClick()

    assertEquals(emptyList<Pair<String, String>>(), opened)
    assertEquals(1, fallbackClicks)
  }

  @Test
  fun withoutAnOpenerTheFallbackRuns() {
    show(TripsUiState(isLoading = false, mainTrip = mainTrip), withOpener = false)

    composeRule.onNodeWithTag(C.Tag.trips_navigation_photos).performClick()

    assertEquals(1, fallbackClicks)
  }
}
