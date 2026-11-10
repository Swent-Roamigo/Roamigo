// AI assistance was used for implementation support.
package com.swent.roamigo.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.R
import com.swent.roamigo.ui.theme.RoamigoTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InfoRowTest {
  @get:Rule val composeRule = createComposeRule()

  @Test
  fun compactRowShowsItsTextInASmallerCardThanTheRegularRow() {
    composeRule.setContent {
      RoamigoTheme {
        Column {
          InfoRow(R.drawable.ic_bell, "Regular title", "Regular text", Modifier.testTag("regular"))
          InfoRow(
              R.drawable.ic_bell,
              "Compact title",
              "Compact text",
              Modifier.testTag("compact"),
              compact = true,
          )
        }
      }
    }

    composeRule.onNodeWithText("Compact title").assertIsDisplayed()
    composeRule.onNodeWithText("Compact text").assertIsDisplayed()
    val regular = composeRule.onNodeWithTag("regular").getBoundsInRoot()
    val compact = composeRule.onNodeWithTag("compact").getBoundsInRoot()
    assertTrue(compact.height < regular.height)
  }
}
