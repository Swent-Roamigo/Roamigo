// AI assistance was used for implementation support.
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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.ClearCredentialUnknownException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.model.authentication.AuthenticatedUser
import com.swent.roamigo.model.authentication.FakeAuthRepository
import com.swent.roamigo.model.trip.voting.InMemoryVoteRepository
import com.swent.roamigo.model.destination.FakeDestinationRepository
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.RoamigoApp
import com.swent.roamigo.ui.theme.RoamigoTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Android UI tests using one fake account; no Firebase emulator or Google account is needed. */
@RunWith(AndroidJUnit4::class)
class AuthenticationFlowTest {
  @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()
  private val user = AuthenticatedUser("test-user", "Test User", "test@example.test", null)
  private lateinit var repository: FakeAuthRepository
  private lateinit var credentialManager: TestCredentialManager

  private fun launchApp(signedIn: Boolean = false) {
    repository = FakeAuthRepository(user, initiallySignedIn = signedIn)
    credentialManager = TestCredentialManager(composeRule.activity)
    composeRule.setContent {
      RoamigoTheme {
        RoamigoApp(
            voteRepository = InMemoryVoteRepository(),
            destinationRepository = FakeDestinationRepository(),
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
    launchApp()
    assertAuthenticationIsFirstScreen()
    assertNull(repository.getCurrentUser())
    signIn()
    assertSignedInOnTrips()
    assertSame(credentialManager.credential, repository.lastCredential)

    pressBackAndWaitForExit()
  }

  @Test
  fun cancelledAccountSelectionStaysOnAuthenticationAndAllowsRetry() {
    launchApp()
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
    launchApp()
    credentialManager.failure = NoCredentialException("No test credential")
    assertAuthenticationIsFirstScreen()
    signIn()
    waitForTag(AuthenticationScreenTestTags.errorMessage)

    composeRule
        .onNodeWithTag(AuthenticationScreenTestTags.errorMessage)
        .assertTextEquals("No Google credentials available. Try signing in again.")
    composeRule.onNodeWithTag(AuthenticationScreenTestTags.signInButton).assertIsEnabled()
    composeRule.onAllNodesWithTag(C.Tag.trips_screen).assertCountEquals(0)
    assertNull(repository.getCurrentUser())

    composeRule.runOnIdle { credentialManager.failure = null }
    signIn()
    assertSignedInOnTrips()
  }

  @Test
  fun pendingCredentialsShowProgressAndFailureAllowsRetry() {
    launchApp()
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
        .assertTextEquals("No Google credentials available. Try signing in again.")
    composeRule.onNodeWithTag(AuthenticationScreenTestTags.signInButton).assertIsEnabled()

    composeRule.runOnIdle { credentialManager.pendingResponse = null }
    signIn()
    assertSignedInOnTrips()
  }

  @Test
  fun backFromAuthenticationDoesNotReturnToPermissions() {
    launchApp()
    assertAuthenticationIsFirstScreen()
    pressBackAndWaitForExit()
  }

  @Test
  fun signOutOpensAuthenticationAndBackDoesNotRestoreTrips() {
    launchApp(signedIn = true)
    waitForTag(C.Tag.trips_profile_avatar)
    composeRule.onNodeWithTag(C.Tag.trips_profile_avatar).performClick()
    waitForTag(C.Tag.profile_log_out)
    composeRule.onNodeWithTag(C.Tag.profile_log_out).performClick()
    assertAuthenticationIsFirstScreen()
    composeRule.onAllNodesWithTag(C.Tag.profile_title).assertCountEquals(0)
    assertNull(repository.getCurrentUser())
    assertEquals(1, credentialManager.clearRequests)
    pressBackAndWaitForExit()
  }

  @Test
  fun signOutFailureStaysOnProfileAndAllowsRetry() {
    launchApp(signedIn = true)
    repository.signOutFailure = IllegalStateException("Technical backend details")
    waitForTag(C.Tag.trips_profile_avatar)
    composeRule.onNodeWithTag(C.Tag.trips_profile_avatar).performClick()
    waitForTag(C.Tag.profile_log_out)
    composeRule.onNodeWithTag(C.Tag.profile_log_out).performClick()
    composeRule
        .onNodeWithText(
            composeRule.activity.getString(com.swent.roamigo.R.string.profile_sign_out_failed)
        )
        .assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.profile_title).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.profile_log_out).assertIsEnabled()
    composeRule.onAllNodesWithTag(AuthenticationScreenTestTags.heading).assertCountEquals(0)
    assertEquals(user, repository.getCurrentUser())
    assertEquals(0, credentialManager.clearRequests)
    composeRule.runOnIdle { repository.signOutFailure = null }
    composeRule.onNodeWithTag(C.Tag.profile_log_out).performClick()
    assertAuthenticationIsFirstScreen()
    assertNull(repository.getCurrentUser())
    assertEquals(1, credentialManager.clearRequests)
  }

  @Test
  fun credentialClearFailureStillOpensAuthentication() {
    launchApp(signedIn = true)
    credentialManager.clearFailure = ClearCredentialUnknownException("Provider unavailable")
    waitForTag(C.Tag.trips_profile_avatar)
    composeRule.onNodeWithTag(C.Tag.trips_profile_avatar).performClick()
    waitForTag(C.Tag.profile_log_out)
    composeRule.onNodeWithTag(C.Tag.profile_log_out).performClick()
    assertAuthenticationIsFirstScreen()
    assertNull(repository.getCurrentUser())
    assertEquals(1, credentialManager.clearRequests)
  }

  @Test
  fun navigationWaitsForCredentialStateToBeCleared() {
    launchApp(signedIn = true)
    val pending = CompletableDeferred<Unit>()
    credentialManager.pendingClear = pending
    waitForTag(C.Tag.trips_profile_avatar)
    composeRule.onNodeWithTag(C.Tag.trips_profile_avatar).performClick()
    waitForTag(C.Tag.profile_log_out)
    composeRule.onNodeWithTag(C.Tag.profile_log_out).performClick()
    composeRule.onNodeWithTag(C.Tag.profile_title).assertIsDisplayed()
    composeRule.onAllNodesWithTag(AuthenticationScreenTestTags.heading).assertCountEquals(0)
    assertEquals(1, credentialManager.clearRequests)
    composeRule.runOnIdle { pending.complete(Unit) }
    assertAuthenticationIsFirstScreen()
  }

  @Test
  fun existingSessionOpensTripsWithoutAuthenticationOrPermissions() {
    launchApp(signedIn = true)
    waitForTag(C.Tag.trips_screen)
    composeRule.onNodeWithTag(C.Tag.trips_screen).assertIsDisplayed()
    composeRule.onAllNodesWithTag(AuthenticationScreenTestTags.heading).assertCountEquals(0)
    composeRule.onAllNodesWithTag(C.Tag.permissions_title).assertCountEquals(0)
    assertEquals(user, repository.getCurrentUser())
    assertNull(repository.lastCredential)
  }

  @Test
  fun repositoryFailureStaysOnAuthenticationAndAllowsRetry() {
    launchApp()
    repository.failure = IllegalStateException("Technical backend details")
    assertAuthenticationIsFirstScreen()
    signIn()
    waitForTag(AuthenticationScreenTestTags.errorMessage)
    composeRule
        .onNodeWithTag(AuthenticationScreenTestTags.errorMessage)
        .assertTextEquals("Sign-in failed. Try again.")
    composeRule.onNodeWithTag(AuthenticationScreenTestTags.signInButton).assertIsEnabled()
    composeRule.onAllNodesWithTag(C.Tag.trips_screen).assertCountEquals(0)
    composeRule.onAllNodesWithTag(C.Tag.permissions_title).assertCountEquals(0)
    assertNull(repository.getCurrentUser())
    assertSame(credentialManager.credential, repository.lastCredential)

    composeRule.runOnIdle { repository.failure = null }
    signIn()
    assertSignedInOnTrips()
  }

  /** Supplies credentials without opening Google's account picker. */
  private class TestCredentialManager(context: Context) :
      CredentialManager by CredentialManager.create(context) {
    var failure: GetCredentialException? = null
    var pendingResponse: CompletableDeferred<GetCredentialResponse>? = null
    val credential = CustomCredential("test-credential", Bundle())
    var clearRequests = 0
    var clearFailure: ClearCredentialUnknownException? = null
    var pendingClear: CompletableDeferred<Unit>? = null

    override suspend fun clearCredentialState(request: ClearCredentialStateRequest) {
      clearRequests++
      pendingClear?.await()
      clearFailure?.let { throw it }
    }

    override suspend fun getCredential(
        context: Context,
        request: GetCredentialRequest,
    ): GetCredentialResponse {
      failure?.let { throw it }
      pendingResponse?.let {
        return it.await()
      }
      return GetCredentialResponse(credential)
    }
  }
}
