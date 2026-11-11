// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.RoamigoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EndedTripBannerTest {
  @get:Rule val composeRule = createComposeRule()

  @Test
  fun saysTheTripIsViewOnlyAndWhatStaysAvailable() {
    composeRule.setContent { RoamigoTheme { EndedTripBanner() } }

    composeRule.onNodeWithTag(C.Tag.trip_ended_banner).assertIsDisplayed()
    composeRule
        .onNodeWithText("This trip has ended · view only. Photos and memories stay with the group.")
        .assertIsDisplayed()
  }
}
