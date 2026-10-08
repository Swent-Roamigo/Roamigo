package com.swent.roamigo.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.ui.theme.RoamigoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoamigoAppTest {
  @get:Rule val composeRule = createComposeRule()

  @Test
  fun continueOpensProfileAndOnlyProfileHasFooter() {
    composeRule.setContent { RoamigoTheme { RoamigoApp() } }

    composeRule.onNodeWithText("We ask only when needed").assertIsDisplayed()
    composeRule.onNodeWithText("Trips").assertDoesNotExist()

    composeRule.onNodeWithText("Continue").performClick()

    composeRule.onNodeWithText("The account you signed in with").assertIsDisplayed()
    composeRule.onNodeWithText("Trips").assertIsDisplayed()
    composeRule.onNodeWithText("We ask only when needed").assertDoesNotExist()
  }
}
