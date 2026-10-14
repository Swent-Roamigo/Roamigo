// AI assistance was used for implementation support.
package com.swent.roamigo.ui.authentication

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
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
    _uiState.update { it.copy(isLoading = true, errorMessageRes = null) }

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
                    it.copy(
                        isLoading = false,
                        user = user,
                        errorMessageRes = null,
                        signedOut = false,
                    )
                  }
                },
                onFailure = { failure ->
                  if (failure is CancellationException) throw failure
                  Log.w("AuthenticationViewModel", "Repository sign-in failed", failure)
                  _uiState.update {
                    it.copy(
                        isLoading = false,
                        user = null,
                        errorMessageRes = R.string.auth_sign_in_failed,
                        signedOut = true,
                    )
                  }
                },
            )
      } catch (_: GetCredentialCancellationException) {
        _uiState.update {
          it.copy(
              isLoading = false,
              user = null,
              errorMessageRes = R.string.auth_sign_in_cancelled,
              signedOut = true,
          )
        }
      } catch (_: NoCredentialException) {
        _uiState.update {
          it.copy(
              isLoading = false,
              user = null,
              errorMessageRes = R.string.auth_no_credentials,
              signedOut = true,
          )
        }
      } catch (e: GetCredentialException) {
        Log.w("AuthenticationViewModel", "Credential acquisition failed", e)
        _uiState.update {
          it.copy(
              isLoading = false,
              user = null,
              errorMessageRes = R.string.auth_credentials_failed,
              signedOut = true,
          )
        }
      } catch (e: CancellationException) {
        _uiState.update { it.copy(isLoading = false) }
        throw e
      } catch (e: Exception) {
        Log.w("AuthenticationViewModel", "Unexpected sign-in failure", e)
        _uiState.update {
          it.copy(
              isLoading = false,
              user = null,
              errorMessageRes = R.string.auth_sign_in_failed,
              signedOut = true,
          )
        }
      }
    }
  }
}
