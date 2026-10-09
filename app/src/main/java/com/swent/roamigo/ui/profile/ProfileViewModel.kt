// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.profile

import androidx.lifecycle.ViewModel
import com.swent.roamigo.model.users.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Everything the profile screen shows; [email] and [friendCount] are not part of [User]. */
data class ProfileUiState(val user: User, val email: String, val friendCount: Int) {
  /** First letter of the name for the avatar; `?` when the name is blank. */
  val initial: String
    get() {
      val name = user.displayName.trim()
      // Step by code point, not char, so a leading emoji is not cut in half.
      return if (name.isEmpty()) "?"
      else name.substring(0, name.offsetByCodePoints(0, 1)).uppercase()
    }
}

class ProfileViewModel : ViewModel() {
  // Placeholder until a repository provides the signed-in user.
  private val _uiState =
      MutableStateFlow(ProfileUiState(User("alex", "Alex", null), "alex@epfl.ch", 4))
  val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()
}
