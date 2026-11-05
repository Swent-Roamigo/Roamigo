// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.model.authentication.AuthenticatedUser
import com.swent.roamigo.model.authentication.FakeAuthRepository
import com.swent.roamigo.model.destination.sampleDestinationRepository
import com.swent.roamigo.model.trip.voting.InMemoryVoteRepository
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.RoamigoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Checks that the profile shows the account the user signed in with. */
@RunWith(AndroidJUnit4::class)
class RoamigoAppProfileTest {
  @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun tripsAndProfileShowTheSameSignedInAccount() {
    val authRepository =
        FakeAuthRepository(
            AuthenticatedUser("u1", "Sam Rivers", null, "https://example.com/sam.jpg"),
            initiallySignedIn = true,
        )
    composeRule.setContent {
      RoamigoTheme {
        RoamigoApp(
            voteRepository = InMemoryVoteRepository(),
            destinationRepository = sampleDestinationRepository(),
            authRepository = authRepository,
        )
      }
    }
    composeRule.waitUntil {
      composeRule.onAllNodesWithTag(C.Tag.trips_profile_avatar).fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithText("Sam", substring = true).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trips_profile_photo, useUnmergedTree = true).assertExists()
    composeRule.onNodeWithTag(C.Tag.trips_profile_avatar).performClick()
    composeRule.onNodeWithTag(C.Tag.profile_name).assertTextEquals("Sam Rivers")
    composeRule.onNodeWithTag(C.Tag.profile_photo).assertExists()
  }

  @Test
  fun tripsAndProfileUseInitialWhenAccountHasNoPhoto() {
    val authRepository =
        FakeAuthRepository(
            AuthenticatedUser("u1", "Sam Rivers", null, null),
            initiallySignedIn = true,
        )
    composeRule.setContent {
      RoamigoTheme {
        RoamigoApp(
            voteRepository = InMemoryVoteRepository(),
            destinationRepository = sampleDestinationRepository(),
            authRepository = authRepository,
        )
      }
    }
    composeRule.waitUntil {
      composeRule.onAllNodesWithTag(C.Tag.trips_profile_avatar).fetchSemanticsNodes().isNotEmpty()
    }
    composeRule
        .onAllNodesWithTag(C.Tag.trips_profile_photo, useUnmergedTree = true)
        .assertCountEquals(0)
    composeRule.onNodeWithText("S", useUnmergedTree = true).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trips_profile_avatar).performClick()
    composeRule.onNodeWithTag(C.Tag.profile_avatar).assertTextEquals("S")
    composeRule.onAllNodesWithTag(C.Tag.profile_photo).assertCountEquals(0)
  }

  @Test
  fun profileShowsTheSignedInAccount() {
    val authRepository =
        FakeAuthRepository(
            AuthenticatedUser("u1", "Sam Rivers", "sam@gmail.com", null),
            initiallySignedIn = true,
        )
    composeRule.setContent {
      RoamigoTheme {
        RoamigoApp(
            voteRepository = InMemoryVoteRepository(),
            destinationRepository = sampleDestinationRepository(),
            authRepository = authRepository,
        )
      }
    }
    composeRule.waitUntil {
      composeRule.onAllNodesWithTag(C.Tag.trips_profile_avatar).fetchSemanticsNodes().isNotEmpty()
    }

    composeRule.onNodeWithTag(C.Tag.trips_profile_avatar).performClick()

    composeRule.onNodeWithTag(C.Tag.profile_name).assertTextEquals("Sam Rivers")
    composeRule
        .onNodeWithTag(C.Tag.profile_email)
        .assertTextContains("sam@gmail.com", substring = true)
  }
}
