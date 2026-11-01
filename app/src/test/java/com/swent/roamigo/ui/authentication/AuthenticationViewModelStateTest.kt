// AI assistance was used for implementation support.
package com.swent.roamigo.ui.authentication

import android.content.Context
import android.os.Bundle
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.exceptions.NoCredentialException
import androidx.test.core.app.ApplicationProvider
import com.swent.roamigo.R
import com.swent.roamigo.model.authentication.AuthenticatedUser
import com.swent.roamigo.model.authentication.FakeAuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuthenticationViewModelStateTest {
  private val dispatcher = StandardTestDispatcher()
  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val user = AuthenticatedUser("test-user", "Test User", null, null)
  private val repository = FakeAuthRepository(user)
  private val manager = TestCredentialManager(context)

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun credentialFailuresStopLoadingAndClearFeedbackOnRetry() =
      runTest(dispatcher) {
        listOf(
                GetCredentialCancellationException() to R.string.auth_sign_in_cancelled,
                NoCredentialException() to R.string.auth_no_credentials,
                GetCredentialUnknownException("Technical details") to
                    R.string.auth_credentials_failed,
                IllegalStateException("Technical details") to R.string.auth_sign_in_failed,
            )
            .forEach { (failure, message) ->
              val viewModel = AuthenticationViewModel(FakeAuthRepository(user))
              manager.failure = failure
              viewModel.signIn(context, manager)
              advanceUntilIdle()
              assertEquals(
                  AuthenticationUiState(errorMessageRes = message),
                  viewModel.uiState.value,
              )

              manager.failure = null
              manager.pending = CompletableDeferred()
              viewModel.signIn(context, manager)
              assertEquals(AuthenticationUiState(isLoading = true), viewModel.uiState.value)
              advanceUntilIdle()
              manager.pending!!.complete(manager.response)
              advanceUntilIdle()
              assertEquals(AuthenticationUiState(user = user), viewModel.uiState.value)
              manager.pending = null
            }
      }

  @Test
  fun repositoryFailureStopsLoadingAndRetrySucceeds() =
      runTest(dispatcher) {
        repository.failure = IllegalStateException("Technical details")
        val viewModel = AuthenticationViewModel(repository)
        viewModel.signIn(context, manager)
        advanceUntilIdle()
        assertEquals(
            AuthenticationUiState(errorMessageRes = R.string.auth_sign_in_failed),
            viewModel.uiState.value,
        )
        repository.failure = null
        viewModel.signIn(context, manager)
        advanceUntilIdle()
        assertEquals(AuthenticationUiState(user = user), viewModel.uiState.value)
      }

  @Test
  fun coroutineCancellationFromEitherSourceDoesNotShowAnError() =
      runTest(dispatcher) {
        val viewModel = AuthenticationViewModel(repository)
        manager.failure = CancellationException()
        viewModel.signIn(context, manager)
        advanceUntilIdle()
        assertEquals(AuthenticationUiState(), viewModel.uiState.value)

        manager.failure = null
        repository.failure = CancellationException()
        viewModel.signIn(context, manager)
        advanceUntilIdle()
        assertEquals(AuthenticationUiState(), viewModel.uiState.value)
      }

  @Test
  fun duplicateRequestsAreIgnoredWhileLoadingAndAfterSuccess() =
      runTest(dispatcher) {
        manager.pending = CompletableDeferred()
        val viewModel = AuthenticationViewModel(repository)
        viewModel.signIn(context, manager)
        viewModel.signIn(context, manager)
        advanceUntilIdle()
        assertEquals(1, manager.requests)
        assertEquals(AuthenticationUiState(isLoading = true), viewModel.uiState.value)
        manager.pending!!.complete(manager.response)
        advanceUntilIdle()
        viewModel.signIn(context, manager)
        advanceUntilIdle()
        assertEquals(1, manager.requests)
        assertEquals(AuthenticationUiState(user = user), viewModel.uiState.value)
      }

  private class TestCredentialManager(context: Context) :
      CredentialManager by CredentialManager.create(context) {
    var requests = 0
    var failure: Exception? = null
    var pending: CompletableDeferred<GetCredentialResponse>? = null
    val response = GetCredentialResponse(CustomCredential("test-credential", Bundle()))

    override suspend fun getCredential(
        context: Context,
        request: GetCredentialRequest,
    ): GetCredentialResponse {
      requests++
      failure?.let { throw it }
      return pending?.await() ?: response
    }
  }
}
