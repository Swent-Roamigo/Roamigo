// AI assistance was used for implementation support.
package com.swent.roamigo.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.R
import com.swent.roamigo.ui.theme.RoamigoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmptyStateTest {
  @get:Rule val composeRule = createComposeRule()

  @Test
  fun showsTheTitleAndTheDescription() {
    composeRule.setContent { RoamigoTheme { EmptyState("No votes yet", "Start one.") } }

    composeRule.onNodeWithText("No votes yet").assertIsDisplayed()
    composeRule.onNodeWithText("Start one.").assertIsDisplayed()
  }

  @Test
  fun anIconDoesNotReplaceTheText() {
    composeRule.setContent {
      RoamigoTheme {
        EmptyState("No photos", "Take one.", icon = R.drawable.ic_trip_details_photos)
      }
    }

    composeRule.onNodeWithText("No photos").assertIsDisplayed()
  }
}
