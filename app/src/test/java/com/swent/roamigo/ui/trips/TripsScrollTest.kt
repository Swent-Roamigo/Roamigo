// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.trips

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.resources.C
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Checks that a long list of trips can be scrolled to the end on a phone-sized screen. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp")
class TripsScrollTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val manyTrips =
      TripsUiState(
          isLoading = false,
          greeting = "Good morning",
          currentUserName = "Alex",
          currentUserInitial = "A",
          otherTrips =
              (1..15).map { OtherTripUiModel("trip$it", "Trip $it", "1 – 3 Nov", memberCount = 2) },
      )

  @Test
  fun newTripButtonIsReachableByScrollingALongList() {
    composeTestRule.setContent { MaterialTheme { TripsScreen(uiState = manyTrips) } }
    composeTestRule.onNodeWithTag(C.Tag.trips_new_trip_button).assertIsNotDisplayed()

    repeat(4) { composeTestRule.onNode(hasScrollAction()).performTouchInput { swipeUp() } }

    composeTestRule.onNodeWithTag(C.Tag.trips_new_trip_button).assertIsDisplayed()
  }
}
