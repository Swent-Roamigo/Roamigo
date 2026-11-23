// Author : Constantin Dunoyer de Segonzac
// Co-author : Claude (Anthropic)
// AI assistance was used for implementation support.
package com.swent.roamigo.ui.authentication

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.model.authentication.AuthenticatedUser
import com.swent.roamigo.model.authentication.FakeAuthRepository
import com.swent.roamigo.model.destination.sampleDestinationRepository
import com.swent.roamigo.model.friends.FakeFriendRepository
import com.swent.roamigo.model.trip.voting.InMemoryVoteRepository
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.RoamigoApp
import com.swent.roamigo.ui.theme.RoamigoTheme
import java.io.File
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Walks one user from the sign-in screen through the profile to sign-out on the JVM, and saves a
 * PNG of each screen in [SCREENSHOT_DIR] (part of the CI `checks-outputs` artifact) so pull
 * requests that change these screens can show them.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class SignInToSignOutScreensTest {
  @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()
  private val user = AuthenticatedUser("owner", "Trip Owner", "trip.owner@example.test", null)

  @Test
  fun eachScreenFromSignInToSignOutIsShownAndSaved() {
    val authRepository = FakeAuthRepository(user)
    composeRule.setContent {
      RoamigoTheme {
        RoamigoApp(
            friendRepository = FakeFriendRepository(user.uid),
            voteRepository = InMemoryVoteRepository(),
            destinationRepository = sampleDestinationRepository(),
            authRepository = authRepository,
            credentialManager = AnsweringCredentialManager(composeRule.activity),
        )
      }
    }
    saveScreen(AuthenticationScreenTestTags.heading, "1-sign-in")
    composeRule
        .onNodeWithTag(AuthenticationScreenTestTags.signInButton)
        .performScrollTo()
        .performClick()
    saveScreen(C.Tag.permissions_title, "2-permissions")
    composeRule.onNodeWithTag(C.Tag.permissions_continue).performClick()
    saveScreen(C.Tag.trips_screen, "3-trips")
    composeRule.onNodeWithTag(C.Tag.trips_profile_avatar).performClick()
    saveScreen(C.Tag.profile_name, "4-profile")
    composeRule.onNodeWithTag(C.Tag.profile_name).assertTextEquals(user.displayName!!)
    composeRule.onNodeWithTag(C.Tag.profile_log_out).performClick()
    composeRule.onNodeWithTag(C.Tag.profile_log_out_confirm).performClick()
    saveScreen(AuthenticationScreenTestTags.heading, "5-signed-out")
    assertNull(authRepository.getCurrentUser())
  }

  /**
   * Waits until the screen showing [tag] is drawn, then writes it to [SCREENSHOT_DIR]/[name].png.
   */
  private fun saveScreen(tag: String, name: String) {
    composeRule.waitUntil(timeoutMillis = WAIT_MILLIS) {
      composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
    val image = composeRule.onAllNodes(isRoot()).onFirst().captureToImage().asAndroidBitmap()
    assertTrue("$name is empty", image.width > 0 && image.height > 0)
    val file = File(SCREENSHOT_DIR.apply { mkdirs() }, "$name.png")
    file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
    assertTrue("$name was not saved", file.length() > 0)
  }

  /** Answers sign-in with a test credential and accepts sign-out, without Google's picker. */
  private class AnsweringCredentialManager(context: Context) :
      CredentialManager by CredentialManager.create(context) {
    override suspend fun getCredential(
        context: Context,
        request: GetCredentialRequest,
    ): GetCredentialResponse = GetCredentialResponse(CustomCredential("test-credential", Bundle()))

    override suspend fun clearCredentialState(request: ClearCredentialStateRequest) {}
  }

  private companion object {
    /**
     * Gradle runs unit tests from the module folder, so this is `app/build/outputs/screenshots`.
     */
    val SCREENSHOT_DIR = File("build/outputs/screenshots")
    const val WAIT_MILLIS = 10_000L
    /** PNG is lossless; Android ignores the quality value for it. */
    const val PNG_QUALITY = 100
  }
}
