// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.model.authentication.AuthenticatedUser
import com.swent.roamigo.model.authentication.FakeAuthRepository
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.RoamigoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Rotation recreates the activity; the user must stay on the screen they were on (#75). */
@RunWith(AndroidJUnit4::class)
class RoamigoAppRestorationTest {
  @get:Rule val composeRule = createComposeRule()

  @Test
  fun staysOnTheProfileAfterTheActivityIsRecreated() {
    val restoration = StateRestorationTester(composeRule)
    val authRepository =
        FakeAuthRepository(AuthenticatedUser("alex", "Alex", null, null), initiallySignedIn = true)
    restoration.setContent { RoamigoTheme { RoamigoApp(authRepository = authRepository) } }
    composeRule.onNodeWithTag(C.Tag.permissions_continue).performClick()
    composeRule.waitUntil {
      composeRule.onAllNodesWithTag(C.Tag.trips_profile_avatar).fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag(C.Tag.trips_profile_avatar).performClick()
    composeRule.waitUntil {
      composeRule.onAllNodesWithTag(C.Tag.profile_title).fetchSemanticsNodes().isNotEmpty()
    }

    restoration.emulateSavedInstanceStateRestore()

    composeRule.onNodeWithTag(C.Tag.profile_title).assertIsDisplayed()
  }
}
