// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui

import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.model.users.User
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.profile.ProfileScreen
import com.swent.roamigo.ui.profile.ProfileUiState
import com.swent.roamigo.ui.theme.RoamigoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileScreenTest {
  @get:Rule val composeRule = createComposeRule()

  private val state = ProfileUiState(User("u1", "Sam", null), "sam@epfl.ch", 2)

  private fun show(modifier: Modifier = Modifier) {
    composeRule.setContent { RoamigoTheme { ProfileScreen(state, modifier) } }
  }

  @Test
  fun showsAccountDetailsFromState() {
    show()
    composeRule.onNodeWithTag(C.Tag.profile_title).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.profile_avatar).assertTextEquals(state.user.displayName.take(1))
    composeRule.onNodeWithTag(C.Tag.profile_name).assertTextEquals(state.user.displayName)
    composeRule.onNodeWithTag(C.Tag.profile_email).assertTextContains(state.email, substring = true)
    // The row only carries the tag; its text lives in child nodes.
    composeRule
        .onNodeWithTag(C.Tag.profile_friends_row)
        .assert(hasAnyDescendant(hasText(state.friendCount.toString(), substring = true)))
  }

  @Test
  fun showsActionsAndFooter() {
    show()
    composeRule.onNodeWithTag(C.Tag.profile_log_out).assertIsDisplayed()
    composeRule.onAllNodesWithTag(C.Tag.profile_action).assertCountEquals(3)
    composeRule.onAllNodesWithTag(C.Tag.profile_footer_tab).assertCountEquals(3)
  }

  @Test
  fun contentScrollsAndLogOutStaysReachableOnSmallScreens() {
    show(Modifier.height(300.dp))

    composeRule.onNodeWithTag(C.Tag.profile_friends_row).performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.profile_log_out).assertIsDisplayed()
  }
}
