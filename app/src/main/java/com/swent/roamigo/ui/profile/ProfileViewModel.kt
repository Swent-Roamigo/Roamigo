// Implemented with assistance from Claude (Anthropic) and OpenAI Codex.
package com.swent.roamigo.ui.profile

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import com.swent.roamigo.R
import com.swent.roamigo.model.authentication.AuthRepository
import com.swent.roamigo.model.authentication.AuthenticatedUser
import com.swent.roamigo.model.users.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Everything the profile screen shows; [email] and [friendCount] are not part of [User]. [email] is
 * null when the account has none.
 */
data class ProfileUiState(
    val user: User,
    val email: String?,
    val friendCount: Int,
    val signedOut: Boolean = false,
    @StringRes val errorMessageRes: Int? = null,
)

/**
 * Shows the signed-in account and signs out. The friend count is a placeholder until friends have a
 * source.
 */
class ProfileViewModel(private val authRepository: AuthRepository) : ViewModel() {
  private val _uiState = MutableStateFlow(authRepository.getCurrentUser().toProfileState())
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

  private companion object {
    const val FALLBACK_NAME = "Traveler"
    const val PLACEHOLDER_FRIEND_COUNT = 4
  }

  private fun AuthenticatedUser?.toProfileState() =
      ProfileUiState(
          user =
              User(
                  uid = this?.uid.orEmpty(),
                  displayName = this?.displayName?.takeIf { it.isNotBlank() } ?: FALLBACK_NAME,
                  profilePictureUrl = this?.photoUrl,
              ),
          email = this?.email,
          friendCount = PLACEHOLDER_FRIEND_COUNT,
      )
}
