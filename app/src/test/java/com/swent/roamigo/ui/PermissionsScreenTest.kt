package com.swent.roamigo.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
  fun continueButtonInvokesCallback() {
    var continued = false
    composeRule.setContent { RoamigoTheme { PermissionsScreen(onContinue = { continued = true }) } }

    composeRule.onNodeWithTag(C.Tag.permissions_continue).performClick()

    assertTrue(continued)
  }
}
