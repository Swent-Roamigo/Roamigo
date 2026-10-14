package com.swent.roamigo.ui.authentication

import android.content.Context
import android.os.Bundle
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.NoCredentialException
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swent.roamigo.model.authentication.AuthRepository
import com.swent.roamigo.model.authentication.AuthenticatedUser
import com.swent.roamigo.model.authentication.FakeAuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Tests presentation outcomes on Android without Google account selection or Firebase. */
@RunWith(AndroidJUnit4::class)
class AuthenticationViewModelTest {
  private val instrumentation = InstrumentationRegistry.getInstrumentation()
  private val context = instrumentation.targetContext
  private val user = AuthenticatedUser("test-user", "Test User", null, null)

  private fun signIn(viewModel: AuthenticationViewModel, manager: TestCredentialManager) {
    instrumentation.runOnMainSync { viewModel.signIn(context, manager) }
    instrumentation.waitForIdleSync()
  }

  @Test
  fun existingSessionDoesNotRequestCredentials() {
    val viewModel = AuthenticationViewModel(FakeAuthRepository(user, initiallySignedIn = true))
    val manager = TestCredentialManager(context)
    signIn(viewModel, manager)
    assertEquals(AuthenticationUiState(user = user), viewModel.uiState.value)
    assertEquals(0, manager.requests)
  }

  @Test
  fun duplicateRequestIsIgnoredWhileCredentialsArePending() {
    val viewModel = AuthenticationViewModel(FakeAuthRepository(user))
    val pending = CompletableDeferred<GetCredentialResponse>()
    val manager = TestCredentialManager(context).apply { pendingResponse = pending }
    signIn(viewModel, manager)
    signIn(viewModel, manager)
    assertTrue(viewModel.uiState.value.isLoading)
    assertEquals(1, manager.requests)

    instrumentation.runOnMainSync { pending.complete(manager.response) }
    instrumentation.waitForIdleSync()
    assertEquals(AuthenticationUiState(user = user), viewModel.uiState.value)
  }

  @Test
  fun repositoryFailureClearsUserAndCanBeRetried() {
    for (message in listOf("Offline", null)) {
      var failure: Exception? = IllegalStateException(message)
      val fake = FakeAuthRepository(user)
      val repository =
          object : AuthRepository by fake {
            override suspend fun signInWithGoogle(credential: Credential) =
                failure?.let { Result.failure<AuthenticatedUser>(it) }
                    ?: fake.signInWithGoogle(credential)
          }
      val viewModel = AuthenticationViewModel(repository)
      val manager = TestCredentialManager(context)
      signIn(viewModel, manager)
      assertEquals(
          AuthenticationUiState(
              errorMsg = message ?: "Sign-in failed. Try again.",
              signedOut = true,
          ),
          viewModel.uiState.value,
      )

      viewModel.clearErrorMsg()
      assertEquals(AuthenticationUiState(signedOut = true), viewModel.uiState.value)
      failure = null
      signIn(viewModel, manager)
      assertEquals(AuthenticationUiState(user = user), viewModel.uiState.value)
    }
  }

  @Test
  fun unexpectedFailureUsesMessageOrFallback() {
    for (message in listOf("Invalid configuration", null)) {
      val viewModel = AuthenticationViewModel(FakeAuthRepository(user))
      val manager =
          TestCredentialManager(context).apply { failure = IllegalStateException(message) }
      signIn(viewModel, manager)
      assertEquals(
          AuthenticationUiState(
              errorMsg = "Unexpected error: ${message ?: "Unknown error"}",
              signedOut = true,
          ),
          viewModel.uiState.value,
      )
    }
  }

  @Test
  fun credentialErrorWithoutMessageUsesFallback() {
    val viewModel = AuthenticationViewModel(FakeAuthRepository(user))
    val manager = TestCredentialManager(context).apply { failure = NoCredentialException() }
    signIn(viewModel, manager)
    assertEquals(
        AuthenticationUiState(
            errorMsg = "Failed to get credentials: Unknown error",
            signedOut = true,
        ),
        viewModel.uiState.value,
    )
  }

  @Test
  fun coroutineCancellationStopsLoadingWithoutReportingLoginFailure() {
    val viewModel = AuthenticationViewModel(FakeAuthRepository(user))
    val manager =
        TestCredentialManager(context).apply { failure = CancellationException("Cancelled") }
    signIn(viewModel, manager)
    assertEquals(AuthenticationUiState(), viewModel.uiState.value)
  }

  @Test
  fun repositoryCancellationIsNotReportedAsLoginFailure() {
    val fake = FakeAuthRepository(user)
    val repository =
        object : AuthRepository by fake {
          override suspend fun signInWithGoogle(credential: Credential) =
              Result.failure<AuthenticatedUser>(CancellationException("Cancelled"))
        }
    val viewModel = AuthenticationViewModel(repository)
    signIn(viewModel, TestCredentialManager(context))
    assertFalse(viewModel.uiState.value.isLoading)
    assertNull(viewModel.uiState.value.errorMsg)
    assertFalse(viewModel.uiState.value.signedOut)
    assertNull(viewModel.uiState.value.user)
  }

  private class TestCredentialManager(context: Context) :
      CredentialManager by CredentialManager.create(context) {
    var requests = 0
    var failure: Exception? = null
    var pendingResponse: CompletableDeferred<GetCredentialResponse>? = null
    val response = GetCredentialResponse(CustomCredential("test-credential", Bundle()))

    override suspend fun getCredential(
        context: Context,
        request: GetCredentialRequest,
    ): GetCredentialResponse {
      requests++
      failure?.let { throw it }
      return pendingResponse?.await() ?: response
    }
  }
}
