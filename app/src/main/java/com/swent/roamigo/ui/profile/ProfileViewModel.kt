package com.swent.roamigo.ui.profile

import androidx.lifecycle.ViewModel
import com.swent.roamigo.model.users.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ProfileUiState(val user: User, val email: String, val friendCount: Int)

class ProfileViewModel : ViewModel() {
  // Placeholder until a repository provides the signed-in user.
  private val _uiState =
      MutableStateFlow(ProfileUiState(User("alex", "Alex", null), "alex@epfl.ch", 4))
  val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()
}
