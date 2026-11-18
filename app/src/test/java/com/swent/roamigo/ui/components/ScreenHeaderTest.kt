// AI assistance was used for implementation support.
package com.swent.roamigo.ui.components

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
class ScreenHeaderTest {
  @get:Rule val composeRule = createComposeRule()

  @Test
  fun showsOnlyTheTitleByDefault() {
    composeRule.setContent { RoamigoTheme { ScreenHeader("Friends") } }

    composeRule.onNodeWithText("Friends").assertIsDisplayed()
    composeRule.onNodeWithContentDescription("Back").assertDoesNotExist()
  }

  @Test
  fun showsEveryOptionalPartAndTheBackButtonWorks() {
    var backs = 0
    composeRule.setContent {
      RoamigoTheme {
        ScreenHeader(
            title = "History",
            eyebrow = "Good morning",
            subtitle = "8 trips",
            onBack = { backs++ },
            backLabel = "Back",
            backModifier = Modifier.testTag("back"),
            trailing = { Text("end") },
            below = { Text("more") },
        )
      }
    }

    listOf("History", "Good morning", "8 trips", "end", "more").forEach {
      composeRule.onNodeWithText(it).assertIsDisplayed()
    }
    composeRule.onNodeWithTag("back").performClick()

    assertEquals(1, backs)
  }
}
