// AI assistance was used for implementation support.
package com.swent.roamigo.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
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
class TripActionButtonTest {
  @get:Rule val composeRule = createComposeRule()

  @Test
  fun filledAndOutlinedButtonsShareTheSameMinimumHeight() {
    composeRule.setContent {
      RoamigoTheme {
        Column {
          TripActionButton(onClick = {}, modifier = Modifier.testTag("filled")) { Text("Filled") }
          TripActionButton(onClick = {}, modifier = Modifier.testTag("outlined"), outlined = true) {
            Text("Outlined")
          }
        }
      }
    }

    composeRule
        .onNodeWithTag("filled")
        .assertIsDisplayed()
        .assertHeightIsAtLeast(TripActionButtonMinHeight)
    composeRule
        .onNodeWithTag("outlined")
        .assertIsDisplayed()
        .assertHeightIsAtLeast(TripActionButtonMinHeight)
  }

  @Test
  fun clickingEitherStyleInvokesTheAction() {
    var clicks = 0
    composeRule.setContent {
      RoamigoTheme {
        Column {
          TripActionButton(onClick = { clicks++ }, modifier = Modifier.testTag("filled")) {
            Text("Filled")
          }
          TripActionButton(
              onClick = { clicks++ },
              modifier = Modifier.testTag("outlined"),
              outlined = true,
          ) {
            Text("Outlined")
          }
        }
      }
    }

    composeRule.onNodeWithTag("filled").performClick()
    composeRule.onNodeWithTag("outlined").performClick()

    assertEquals(2, clicks)
  }
}
