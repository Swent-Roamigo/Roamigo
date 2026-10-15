// Implemented with assistance from Claude (Anthropic).
// Sign-out implemented with AI assistance.
package com.swent.roamigo.ui.profile

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import com.swent.roamigo.R
import com.swent.roamigo.model.authentication.AuthRepository
import com.swent.roamigo.model.users.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Everything the profile screen shows; [email] and [friendCount] are not part of [User]. */
data class ProfileUiState(
    val user: User,
    val email: String,
    val friendCount: Int,
    val signedOut: Boolean = false,
    @StringRes val errorMessageRes: Int? = null,
)

class ProfileViewModel(private val authRepository: AuthRepository) : ViewModel() {
  // Placeholder until a repository provides the signed-in user.
  private val _uiState =
      MutableStateFlow(ProfileUiState(User("alex", "Alex", null), "alex@epfl.ch", 4))
  val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

  /** Ends the current session; navigation reacts only after successful sign-out. */
  fun signOut() {
    if (_uiState.value.signedOut) return
    authRepository
        .signOut()
        .fold(
            onSuccess = {
              _uiState.value = _uiState.value.copy(signedOut = true, errorMessageRes = null)
            },
            onFailure = {
              Log.w("ProfileViewModel", "Sign-out failed", it)
              _uiState.value =
                  _uiState.value.copy(errorMessageRes = R.string.profile_sign_out_failed)
            },
        )
  }
}
