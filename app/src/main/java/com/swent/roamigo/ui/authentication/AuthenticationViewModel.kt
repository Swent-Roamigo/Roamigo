package com.swent.roamigo.ui.authentication

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.swent.roamigo.R
import com.swent.roamigo.model.authentication.AuthRepository
import com.swent.roamigo.model.authentication.AuthRepositoryFirebase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Coordinates Google credential acquisition and authentication without exposing provider users.
 *
 * Adapted from the SwEnt bootcamp:
 * https://github.com/swent-epfl/public/blob/main/bootcamp/deliverables/B3/1-Authentication.md
 */
class AuthenticationViewModel(private val repository: AuthRepository = AuthRepositoryFirebase()) :
    ViewModel() {
  private val _uiState = MutableStateFlow(AuthenticationUiState(user = repository.getCurrentUser()))
  val uiState = _uiState.asStateFlow()

  /** Clears the error message in the UI state. */
  fun clearErrorMsg() {
    _uiState.update { it.copy(errorMsg = null) }
  }

  private fun getSignInOptions(context: Context) =
      GetSignInWithGoogleOption.Builder(context.getString(R.string.default_web_client_id)).build()

  private fun signInRequest(signInOptions: GetSignInWithGoogleOption) =
      GetCredentialRequest.Builder().addCredentialOption(signInOptions).build()

  private suspend fun getCredential(
      context: Context,
      request: GetCredentialRequest,
      credentialManager: CredentialManager,
  ) = credentialManager.getCredential(context, request).credential

  /** Requests a Google account using the caller's activity context, then authenticates it. */
  fun signIn(context: Context, credentialManager: CredentialManager) {
    if (_uiState.value.isLoading || _uiState.value.user != null) return
    _uiState.update { it.copy(isLoading = true, errorMsg = null) }

    viewModelScope.launch {
      try {
        val signInOptions = getSignInOptions(context)
        val request = signInRequest(signInOptions)
        val credential = getCredential(context, request, credentialManager)
        repository
            .signInWithGoogle(credential)
            .fold(
                onSuccess = { user ->
                  _uiState.update {
                    it.copy(isLoading = false, user = user, errorMsg = null, signedOut = false)
                  }
                },
                onFailure = { failure ->
                  if (failure is CancellationException) throw failure
                  _uiState.update {
                    it.copy(
                        isLoading = false,
                        user = null,
                        errorMsg = failure.localizedMessage ?: "Sign-in failed. Try again.",
                        signedOut = true,
                    )
                  }
                },
            )
      } catch (_: GetCredentialCancellationException) {
        _uiState.update {
          it.copy(isLoading = false, user = null, errorMsg = "Sign-in cancelled", signedOut = true)
        }
      } catch (e: GetCredentialException) {
        _uiState.update {
          it.copy(
              isLoading = false,
              user = null,
              errorMsg = "Failed to get credentials: ${e.localizedMessage ?: "Unknown error"}",
              signedOut = true,
          )
        }
      } catch (e: CancellationException) {
        _uiState.update { it.copy(isLoading = false) }
        throw e
      } catch (e: Exception) {
        _uiState.update {
          it.copy(
              isLoading = false,
              user = null,
              errorMsg = "Unexpected error: ${e.localizedMessage ?: "Unknown error"}",
              signedOut = true,
          )
        }
      }
    }
  }
}
