// AI assistance was used for implementation support.
package com.swent.roamigo.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.ui.theme.RoamigoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoamigoButtonTest {
  @get:Rule val composeRule = createComposeRule()

  @Test
  fun everyVariantHasTheSameMinimumHeightAndAnswersClicks() {
    var clicks = 0
    composeRule.setContent {
      RoamigoTheme {
        Column {
          ButtonVariant.entries.forEach {
            RoamigoButton({ clicks++ }, Modifier.testTag(it.name), variant = it) { Text(it.name) }
          }
        }
      }
    }

    ButtonVariant.entries.forEach {
      composeRule
          .onNodeWithTag(it.name)
          .assertHeightIsAtLeast(RoamigoButtonMinHeight)
          .performClick()
    }

    assertEquals(ButtonVariant.entries.size, clicks)
  }

  @Test
  fun aDisabledOrLoadingButtonIgnoresClicks() {
    var clicks = 0
    composeRule.setContent {
      RoamigoTheme {
        Column {
          RoamigoButton({ clicks++ }, Modifier.testTag("off"), enabled = false) { Text("Off") }
          RoamigoButton({ clicks++ }, Modifier.testTag("busy"), loading = true) { Text("Busy") }
          RoamigoButton({ clicks++ }, Modifier.testTag("on")) { Text("On") }
        }
      }
    }

    composeRule.onNodeWithTag("off").assertIsNotEnabled()
    composeRule.onNodeWithTag("busy").assertIsNotEnabled()
    composeRule.onNodeWithTag("on").assertIsEnabled().performClick()

    assertEquals(1, clicks)
  }
}
