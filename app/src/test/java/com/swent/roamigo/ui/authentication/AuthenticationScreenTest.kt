// AI assistance was used for implementation support.
package com.swent.roamigo.ui.authentication

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.R
import com.swent.roamigo.ui.theme.RoamigoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

  private fun render(
      state: AuthenticationUiState = AuthenticationUiState(),
      fontScale: Float = 1f,
  ) {
    composeRule.setContent {
      val density = LocalDensity.current
      CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
        RoamigoTheme { AuthenticationScreen(state, onSignInClick = { clicks++ }) }
      }
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

  @Test
  fun welcomeShowsBrandAndGoogleButton() {
    render()
    composeRule.onNodeWithContentDescription("roamigo").assertIsDisplayed()
    composeRule.onNodeWithTag(AuthenticationScreenTestTags.heading).assertIsDisplayed()
    assertSignInAccessible()
  }

  @Test
  fun failureShowsBannerAndRetryAction() {
    render(AuthenticationUiState(errorMessageRes = R.string.auth_sign_in_failed))
    composeRule.onNodeWithText("Sign-in didn’t work").performScrollTo().assertIsDisplayed()
    composeRule
        .onNodeWithText("We couldn’t sign you in with Google. Check your connection and try again.")
        .assertIsDisplayed()
    assertSignInAccessible("Try again")
  }

  @Test
  fun cancellationShowsNoticeAndContinueAction() {
    render(AuthenticationUiState(errorMessageRes = R.string.auth_sign_in_cancelled))
    composeRule.onNodeWithText("Sign-in cancelled").performScrollTo().assertIsDisplayed()
    composeRule
        .onNodeWithText("No problem. Continue with Google when you’re ready.")
        .assertIsDisplayed()
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

  @Test
  @Config(qualifiers = "en-rUS-w796dp-h340dp-land-xhdpi")
  fun landscapeAllowsScrollingToContentAndSignIn() {
    render()
    composeRule
        .onNodeWithTag(AuthenticationScreenTestTags.heading)
        .performScrollTo()
        .assertIsDisplayed()
    assertSignInAccessible()
    assertTermsAccessible()
  }

  @Test
  @Config(qualifiers = "en-rUS-w320dp-h480dp-xhdpi")
  fun smallScreenKeepsSignInAndTermsAccessible() {
    render()
    assertSignInAccessible()
    assertTermsAccessible()
  }

  @Test
  @Config(qualifiers = "en-rUS-w320dp-h480dp-xhdpi")
  fun largeTextWrapsInsideButtonWithoutClipping() {
    render(fontScale = 2f)
    composeRule
        .onNodeWithTag(AuthenticationScreenTestTags.heading)
        .performScrollTo()
        .assertIsDisplayed()
    assertSignInAccessible()
    assertButtonTextFits("Continue with Google")
    assertTermsAccessible()
  }

  @Test
  @Config(qualifiers = "en-rUS-w796dp-h340dp-land-xhdpi")
  fun landscapeWithLargeTextKeepsFeedbackAndRetryAccessible() {
    render(AuthenticationUiState(errorMessageRes = R.string.auth_sign_in_failed), fontScale = 2f)
    composeRule
        .onNodeWithTag(AuthenticationScreenTestTags.errorMessage)
        .performScrollTo()
        .assertIsDisplayed()
    assertSignInAccessible("Try again")
    assertButtonTextFits("Try again")
    assertTermsAccessible()
  }

  private fun assertTermsAccessible() {
    composeRule
        .onNodeWithText("By continuing you agree to the Terms and Privacy Policy.")
        .performScrollTo()
        .assertIsDisplayed()
  }

  private fun assertButtonTextFits(label: String) {
    val results = mutableListOf<TextLayoutResult>()
    val text = composeRule.onNodeWithText(label, useUnmergedTree = true)
    text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
    assertTrue(results.isNotEmpty())
    val layout = results.single()
    assertFalse("Text exceeds its measured height", layout.didOverflowHeight)
    assertFalse("Text was truncated", layout.multiParagraph.didExceedMaxLines)
    val textBounds = text.fetchSemanticsNode().boundsInRoot
    val buttonBounds =
        composeRule
            .onNodeWithTag(AuthenticationScreenTestTags.signInButton)
            .fetchSemanticsNode()
            .boundsInRoot
    assertTrue(textBounds.top >= buttonBounds.top && textBounds.bottom <= buttonBounds.bottom)
    assertTrue(textBounds.left >= buttonBounds.left && textBounds.right <= buttonBounds.right)
  }
}
