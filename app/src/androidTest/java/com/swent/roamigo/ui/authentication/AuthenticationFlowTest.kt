package com.swent.roamigo.ui.authentication

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.model.authentication.AuthenticatedUser
import com.swent.roamigo.model.authentication.FakeAuthRepository
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.RoamigoApp
import com.swent.roamigo.ui.theme.RoamigoTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Android UI tests using one fake account; no Firebase emulator or Google account is needed. */
@RunWith(AndroidJUnit4::class)
class AuthenticationFlowTest {
  @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()
  private val user = AuthenticatedUser("test-user", "Test User", "test@example.test", null)
  private val repository = FakeAuthRepository(user)
  private lateinit var credentialManager: TestCredentialManager

  @Before
  fun setUp() {
    credentialManager = TestCredentialManager(composeRule.activity)
    composeRule.setContent {
      RoamigoTheme {
        RoamigoApp(
            authRepository = repository,
            credentialManager = credentialManager,
        )
      }
    }
  }

  private fun waitForTag(tag: String) {
    composeRule.waitUntil(timeoutMillis = 10_000) {
      composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }
  }

  private fun assertAuthenticationIsFirstScreen() {
    waitForTag(AuthenticationScreenTestTags.heading)
    composeRule.onNodeWithTag(AuthenticationScreenTestTags.heading).assertIsDisplayed()
    composeRule.onAllNodesWithTag(C.Tag.trips_screen).assertCountEquals(0)
    composeRule.onAllNodesWithTag(C.Tag.permissions_title).assertCountEquals(0)
  }

  private fun signIn() {
    composeRule.onNodeWithTag(AuthenticationScreenTestTags.signInButton).performClick()
  }

  private fun pressBackAndWaitForExit() {
    val activity = composeRule.activity
    composeRule.runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
    // Android can background the root task instead of finishing its activity.
    composeRule.waitUntil(timeoutMillis = 5_000) {
      activity.isFinishing || !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    }
  }

  private fun assertSignedInOnTrips() {
    waitForTag(C.Tag.permissions_title)
    composeRule.onNodeWithTag(C.Tag.permissions_title).assertIsDisplayed()
    composeRule.onAllNodesWithTag(AuthenticationScreenTestTags.heading).assertCountEquals(0)
    assertEquals(user, repository.getCurrentUser())
    composeRule.onNodeWithTag(C.Tag.permissions_continue).performClick()
    waitForTag(C.Tag.trips_screen)
    composeRule.onNodeWithTag(C.Tag.trips_screen).assertIsDisplayed()
    composeRule.onAllNodesWithTag(AuthenticationScreenTestTags.heading).assertCountEquals(0)
    composeRule.onAllNodesWithTag(C.Tag.permissions_title).assertCountEquals(0)
    assertEquals(user, repository.getCurrentUser())
  }

  @Test
  fun signInOpensTripsAndBackDoesNotReturnToAuthentication() {
    assertAuthenticationIsFirstScreen()
    assertNull(repository.getCurrentUser())
    signIn()
    assertSignedInOnTrips()

    pressBackAndWaitForExit()
  }

  @Test
  fun cancelledAccountSelectionStaysOnAuthenticationAndAllowsRetry() {
    credentialManager.failure = GetCredentialCancellationException()
    assertAuthenticationIsFirstScreen()
    signIn()
    waitForTag(AuthenticationScreenTestTags.errorMessage)

    composeRule
        .onNodeWithTag(AuthenticationScreenTestTags.errorMessage)
        .assertTextEquals("Sign-in cancelled")
    composeRule.onNodeWithTag(AuthenticationScreenTestTags.signInButton).assertIsEnabled()
    composeRule.onAllNodesWithTag(C.Tag.trips_screen).assertCountEquals(0)
    assertNull(repository.getCurrentUser())

    composeRule.runOnIdle { credentialManager.failure = null }
    signIn()
    assertSignedInOnTrips()
  }

  @Test
  fun credentialErrorStaysOnAuthenticationAndAllowsRetry() {
    credentialManager.failure = NoCredentialException("No test credential")
    assertAuthenticationIsFirstScreen()
    signIn()
    waitForTag(AuthenticationScreenTestTags.errorMessage)

    composeRule
        .onNodeWithTag(AuthenticationScreenTestTags.errorMessage)
        .assertTextEquals("Failed to get credentials: No test credential")
    composeRule.onNodeWithTag(AuthenticationScreenTestTags.signInButton).assertIsEnabled()
    composeRule.onAllNodesWithTag(C.Tag.trips_screen).assertCountEquals(0)
    assertNull(repository.getCurrentUser())

    composeRule.runOnIdle { credentialManager.failure = null }
    signIn()
    assertSignedInOnTrips()
  }

  @Test
  fun pendingCredentialsShowProgressAndFailureAllowsRetry() {
    val pending = CompletableDeferred<GetCredentialResponse>()
    credentialManager.pendingResponse = pending
    assertAuthenticationIsFirstScreen()
    signIn()
    waitForTag(AuthenticationScreenTestTags.loadingIndicator)
    composeRule.onNodeWithTag(AuthenticationScreenTestTags.signInButton).assertIsNotEnabled()
    composeRule
        .onNodeWithTag(AuthenticationScreenTestTags.loadingIndicator, useUnmergedTree = true)
        .assertIsDisplayed()
        .assertContentDescriptionEquals("Signing in with Google")

    composeRule.runOnIdle {
      pending.completeExceptionally(NoCredentialException("No test credential"))
    }
    waitForTag(AuthenticationScreenTestTags.errorMessage)
    composeRule
        .onNodeWithTag(AuthenticationScreenTestTags.errorMessage)
        .assertTextEquals("Failed to get credentials: No test credential")
    composeRule.onNodeWithTag(AuthenticationScreenTestTags.signInButton).assertIsEnabled()

    composeRule.runOnIdle { credentialManager.pendingResponse = null }
    signIn()
    assertSignedInOnTrips()
  }

  @Test
  fun backFromAuthenticationDoesNotReturnToPermissions() {
    assertAuthenticationIsFirstScreen()
    pressBackAndWaitForExit()
  }

  /** Supplies credentials without opening Google's account picker. */
  private class TestCredentialManager(context: Context) :
      CredentialManager by CredentialManager.create(context) {
    var failure: GetCredentialException? = null
    var pendingResponse: CompletableDeferred<GetCredentialResponse>? = null

    override suspend fun getCredential(
        context: Context,
        request: GetCredentialRequest,
    ): GetCredentialResponse {
      failure?.let { throw it }
      pendingResponse?.let {
        return it.await()
      }
      return GetCredentialResponse(CustomCredential("test-credential", Bundle()))
    }
  }
}
