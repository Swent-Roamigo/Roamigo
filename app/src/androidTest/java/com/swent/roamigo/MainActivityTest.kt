// AI assistance was used for implementation support.
package com.swent.roamigo

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kaspersky.kaspresso.testcases.api.testcase.TestCase
import com.swent.roamigo.screen.MainScreen
import io.github.kakaocup.compose.node.element.ComposeScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Checks that launching [MainActivity] shows the app's first screen, the sign-in heading. */
@RunWith(AndroidJUnit4::class)
class MainActivityTest : TestCase() {

  @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun launchShowsTheSignInHeading() = run {
    step("Start Main Activity") {
      ComposeScreen.onComposeScreen<MainScreen>(composeTestRule) { title { assertIsDisplayed() } }
    }
  }
}
