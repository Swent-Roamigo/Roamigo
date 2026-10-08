package com.swent.roamigo.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.model.users.User
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

  @Test
  fun showsAccountDetailsFromState() {
    composeRule.setContent { RoamigoTheme { ProfileScreen(state) } }

    listOf("Profile", "S", "sam@epfl.ch · Signed in with Google", "Log out").forEach {
      composeRule.onNodeWithText(it).assertIsDisplayed()
    }
    composeRule.onNodeWithText("2 friends", substring = true).assertIsDisplayed()
    // The name is shown both as the heading and in the "Display name" row.
    composeRule.onAllNodesWithText("Sam").assertCountEquals(2)
  }

  @Test
  fun showsEditActionsAndFooter() {
    composeRule.setContent { RoamigoTheme { ProfileScreen(state) } }

    listOf("Edit", "Change", "View", "Trips", "Map", "Photos").forEach {
      composeRule.onNodeWithText(it).assertIsDisplayed()
    }
  }
}
