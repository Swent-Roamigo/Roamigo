package com.swent.roamigo.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.ui.permissions.PermissionsScreen
import com.swent.roamigo.ui.theme.RoamigoTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PermissionsScreenTest {
  @get:Rule val composeRule = createComposeRule()

  @Test
  fun showsEveryPermissionRow() {
    composeRule.setContent { RoamigoTheme { PermissionsScreen(onContinue = {}) } }

    listOf(
            "We ask only when needed",
            "Location sharing",
            "Camera",
            "Notifications",
            "Trip members only",
        )
        .forEach { composeRule.onNodeWithText(it).assertIsDisplayed() }
  }

  @Test
  fun continueButtonInvokesCallback() {
    var continued = false
    composeRule.setContent { RoamigoTheme { PermissionsScreen(onContinue = { continued = true }) } }

    composeRule.onNodeWithText("Continue").performClick()

    assertTrue(continued)
  }
}
