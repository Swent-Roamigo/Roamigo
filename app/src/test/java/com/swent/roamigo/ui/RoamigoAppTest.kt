package com.swent.roamigo.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.resources.C
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

    composeRule.onNodeWithTag(C.Tag.permissions_title).assertIsDisplayed()
    composeRule.onAllNodesWithTag(C.Tag.profile_footer_tab).assertCountEquals(0)

    composeRule.onNodeWithTag(C.Tag.permissions_continue).performClick()

    composeRule.onNodeWithTag(C.Tag.profile_title).assertIsDisplayed()
    composeRule.onAllNodesWithTag(C.Tag.profile_footer_tab).assertCountEquals(3)
    composeRule.onAllNodesWithTag(C.Tag.permissions_title).assertCountEquals(0)
  }
}
