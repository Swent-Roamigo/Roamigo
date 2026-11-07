// Implemented with assistance from OpenAI Codex.
package com.swent.roamigo.ui.trip

import androidx.activity.ComponentActivity
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.RoamigoTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class TripScreenTest {
  @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()
  private var selectedTab by mutableStateOf(TripSection.Detail)
  private var primaryClicks = 0
  private var tripsClicks = 0
  private var pauseClicks = 0

  private fun show(isSharing: Boolean = false) {
    val date = LocalDate.of(2026, 11, 5)
    composeRule.setContent {
      RoamigoTheme {
        TripScreen(
            trip = TripHeaderUiState("Lisbon & Porto", date, date.plusDays(3), 2),
            selectedTab = selectedTab,
            today = date,
            photoCount = 3,
            isSharing = isSharing,
            onBack = {},
            onTabSelected = { selectedTab = it },
            onPauseSharing = { pauseClicks++ },
            onTripsClick = { tripsClicks++ },
            onMapClick = {},
            onFriendsClick = {},
        ) {
          Button(onClick = { primaryClicks++ }) { Text("Primary action") }
        }
      }
    }
  }

  @Test
  @Config(qualifiers = "w320dp-h480dp")
  fun tripTabsAndPrimaryActionsStayReachableOnSmallDisplays() {
    show()

    composeRule.onNodeWithText("Photos").assertIsDisplayed().performClick().assertIsSelected()
    composeRule.onNodeWithText("Primary action").assertIsDisplayed().performClick()
    composeRule.onNodeWithTag(C.Tag.trips_navigation_trips).assertIsDisplayed().performClick()

    composeRule.runOnIdle {
      assertEquals(TripSection.Photos, selectedTab)
      assertEquals(1, primaryClicks)
      assertEquals(1, tripsClicks)
    }
  }

  @Test
  @Config(qualifiers = "w640dp-h360dp-land")
  fun landscapeKeepsPauseSharingAndThePrimaryActionReachable() {
    selectedTab = TripSection.Votes
    show(isSharing = true)

    composeRule.onNodeWithText("Pause sharing").assertIsDisplayed().performClick()
    composeRule.onNodeWithText("Primary action").assertIsDisplayed().performClick()
    composeRule.onNodeWithTag(C.Tag.trips_navigation_trips).assertIsDisplayed()

    composeRule.runOnIdle {
      assertEquals(1, pauseClicks)
      assertEquals(1, primaryClicks)
    }
  }

  @Test
  fun tripsTabStaysHighlightedInsideATrip() {
    show()

    composeRule.onNodeWithTag(C.Tag.trips_navigation_trips).assertIsSelected()
  }
}
