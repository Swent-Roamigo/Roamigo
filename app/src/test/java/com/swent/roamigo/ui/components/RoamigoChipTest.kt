// AI assistance was used for implementation support.
package com.swent.roamigo.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.ui.theme.RoamigoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoamigoChipTest {
  @get:Rule val composeRule = createComposeRule()

  @Test
  fun everyToneShowsItsLabel() {
    composeRule.setContent {
      RoamigoTheme {
        androidx.compose.foundation.layout.Column {
          ChipTone.entries.forEach { RoamigoChip(it.name, Modifier.testTag(it.name), tone = it) }
        }
      }
    }

    ChipTone.entries.forEach { composeRule.onNodeWithTag(it.name).assertIsDisplayed() }
  }

  @Test
  fun leadingAndTrailingSlotsAreShownAndTheTrailingOneCanAct() {
    var removed = 0
    composeRule.setContent {
      RoamigoTheme {
        RoamigoChip(
            "Paris",
            leading = { Text("lead") },
            trailing = { Text("x", Modifier.testTag("remove").clickable { removed++ }) },
        )
      }
    }

    composeRule.onNodeWithText("lead").assertIsDisplayed()
    composeRule.onNodeWithTag("remove").performClick()

    assertEquals(1, removed)
  }

  @Test
  fun aBadgeShowsItsText() {
    composeRule.setContent { RoamigoTheme { RoamigoBadge("Invite") } }

    composeRule.onNodeWithText("Invite").assertIsDisplayed()
  }
}
