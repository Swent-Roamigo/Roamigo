// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.resources.C
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
  fun showsTitleAndEveryPermissionRow() {
    composeRule.setContent { RoamigoTheme { PermissionsScreen(onContinue = {}) } }

    composeRule.onNodeWithTag(C.Tag.permissions_title).assertIsDisplayed()
    composeRule.onAllNodesWithTag(C.Tag.permissions_row).assertCountEquals(4)
  }

  @Test
  fun contentScrollsAndContinueStaysReachableOnSmallScreens() {
    composeRule.setContent {
      RoamigoTheme { Box(Modifier.height(300.dp)) { PermissionsScreen(onContinue = {}) } }
    }

    composeRule.onAllNodesWithTag(C.Tag.permissions_row)[3].performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.permissions_continue).assertIsDisplayed()
  }

  @Test
  fun continueButtonInvokesCallback() {
    var continued = false
    composeRule.setContent { RoamigoTheme { PermissionsScreen(onContinue = { continued = true }) } }

    composeRule.onNodeWithTag(C.Tag.permissions_continue).performClick()

    assertTrue(continued)
  }
}
