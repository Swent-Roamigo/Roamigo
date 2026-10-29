// AI assistance was used for implementation support.
package com.swent.roamigo.ui.authentication

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.R
import com.swent.roamigo.ui.theme.RoamigoTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rUS-w386dp-h796dp-xhdpi")
class AuthenticationScreenTest {
  @get:Rule val composeRule = createComposeRule()
  private var clicks = 0

  private fun render(state: AuthenticationUiState = AuthenticationUiState()) {
    composeRule.setContent {
      RoamigoTheme { AuthenticationScreen(state, onSignInClick = { clicks++ }) }
    }
  }

  private fun assertSignInAccessible(label: String = "Continue with Google") {
    composeRule
        .onNodeWithTag(AuthenticationScreenTestTags.signInButton)
        .performScrollTo()
        .assertIsDisplayed()
        .assertIsEnabled()
        .performClick()
    composeRule.onNodeWithText(label).assertIsDisplayed()
    assertEquals(1, clicks)
  }

  private fun screenshot(name: String) {
    val file = File("build/reports/authentication/$name.png")
    file.parentFile?.mkdirs()
    file.outputStream().use {
      composeRule
          .onNodeWithTag(AuthenticationScreenTestTags.screen)
          .captureToImage()
          .asAndroidBitmap()
          .compress(Bitmap.CompressFormat.PNG, 100, it)
    }
  }

  @Test
  fun welcomeShowsBrandAndGoogleButton() {
    render()
    composeRule.onNodeWithContentDescription("roamigo").assertIsDisplayed()
    composeRule.onNodeWithTag(AuthenticationScreenTestTags.heading).assertIsDisplayed()
    screenshot("welcome")
    assertSignInAccessible()
  }

  @Test
  fun failureShowsBannerAndRetryAction() {
    render(AuthenticationUiState(errorMessageRes = R.string.auth_sign_in_failed))
    composeRule.onNodeWithText("Sign-in didn’t work").performScrollTo().assertIsDisplayed()
    composeRule
        .onNodeWithText("We couldn’t sign you in with Google. Check your connection and try again.")
        .assertIsDisplayed()
    screenshot("failure")
    assertSignInAccessible("Try again")
  }

  @Test
  fun cancellationShowsNoticeAndContinueAction() {
    render(AuthenticationUiState(errorMessageRes = R.string.auth_sign_in_cancelled))
    composeRule.onNodeWithText("Sign-in cancelled").performScrollTo().assertIsDisplayed()
    composeRule
        .onNodeWithText("No problem. Continue with Google when you’re ready.")
        .assertIsDisplayed()
    screenshot("cancelled")
    assertSignInAccessible()
  }

  @Test
  fun loadingShowsProgressAndDisablesButton() {
    render(AuthenticationUiState(isLoading = true))
    composeRule
        .onNodeWithTag(AuthenticationScreenTestTags.signInButton)
        .performScrollTo()
        .assertIsNotEnabled()
    composeRule.onNodeWithContentDescription("Signing in with Google").assertIsDisplayed()
  }
}
