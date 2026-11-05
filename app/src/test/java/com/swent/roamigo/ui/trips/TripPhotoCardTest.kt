// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trips

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.model.destination.DestinationPhoto
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.components.PhotoCreditsDialogTestTags
import com.swent.roamigo.ui.trips.components.MainTripCard
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TripPhotoCardTest {
  @get:Rule val rule = createComposeRule()

  private val trip =
      MainTripUiModel("trip", "Lisbon", "12 – 19 Oct", 8, 2, emptyList(), "Starts today")
  private val photo =
      DestinationPhoto(
          "android.resource://com.swent.roamigo/drawable/destination_placeholder",
          "Photographer",
          "https://example.com/source",
          "CC BY 4.0",
          "https://example.com/license",
          "Lisbon view",
          "Photo credit",
      )

  @Test
  fun loadedPhotoShowsCreditsAndCanReturnToTrip() {
    var opened = 0
    rule.setContent { MaterialTheme { MainTripCard(trip, null, photo, {}, { opened++ }) } }
    rule.waitUntil(10_000) {
      rule.onAllNodesWithText("Photo credits").fetchSemanticsNodes().isNotEmpty()
    }
    rule.onNodeWithText("Photo credits").performClick()
    rule.onNodeWithTag(PhotoCreditsDialogTestTags.TITLE).assertTextEquals("Lisbon view")
    rule.onNodeWithTag(PhotoCreditsDialogTestTags.CREATOR).assertIsDisplayed()
    rule.onNodeWithTag(PhotoCreditsDialogTestTags.CREDIT).assertTextEquals("Photo credit")
    rule.onNodeWithTag(PhotoCreditsDialogTestTags.CLOSE).performClick()
    rule.onNodeWithTag(C.Tag.trips_main_trip_card).performClick()
    rule.runOnIdle { assertEquals(1, opened) }
  }

  @Test
  fun replacingLoadedPhotoWithUnavailablePhotoHidesStaleCredits() {
    var currentPhoto by mutableStateOf<DestinationPhoto?>(photo)
    rule.setContent { MaterialTheme { MainTripCard(trip, null, currentPhoto, {}, {}) } }
    rule.waitUntil(10_000) {
      rule.onAllNodesWithText("Photo credits").fetchSemanticsNodes().isNotEmpty()
    }
    rule.runOnIdle { currentPhoto = photo.copy(imageUrl = "file:///missing-roamigo-image.png") }
    rule.waitForIdle()
    rule.onNodeWithText("Photo credits").assertDoesNotExist()
    rule.onNodeWithTag(C.Tag.trips_main_trip_card).assertIsDisplayed()
    rule.onNodeWithText("Lisbon").assertIsDisplayed()
  }
}
