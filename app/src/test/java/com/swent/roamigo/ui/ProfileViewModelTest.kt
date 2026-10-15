// Implemented with assistance from Claude (Anthropic).
// Sign-out tests implemented with AI assistance.
package com.swent.roamigo.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.R
import com.swent.roamigo.model.authentication.AuthenticatedUser
import com.swent.roamigo.model.authentication.FakeAuthRepository
import com.swent.roamigo.ui.profile.ProfileViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileViewModelTest {
  private val repository =
      FakeAuthRepository(AuthenticatedUser("alex", "Alex", null, null), initiallySignedIn = true)

  @Test
  fun initialStateIsThePlaceholderUser() {
    val state = ProfileViewModel(repository).uiState.value

    assertEquals("Alex", state.user.displayName)
    assertEquals("alex@epfl.ch", state.email)
    assertEquals(4, state.friendCount)
  }

  @Test
  fun signOutClearsSessionAndReportsSuccess() {
    val viewModel = ProfileViewModel(repository)
    viewModel.signOut()
    assertTrue(viewModel.uiState.value.signedOut)
    assertNull(viewModel.uiState.value.errorMessageRes)
    assertNull(repository.getCurrentUser())
  }

  @Test
  fun failureKeepsSessionAndAllowsRetry() {
    repository.signOutFailure = IllegalStateException("Technical details")
    val viewModel = ProfileViewModel(repository)
    viewModel.signOut()
    assertFalse(viewModel.uiState.value.signedOut)
    assertEquals(R.string.profile_sign_out_failed, viewModel.uiState.value.errorMessageRes)
    assertEquals("alex", repository.getCurrentUser()?.uid)
    repository.signOutFailure = null
    viewModel.signOut()
    assertTrue(viewModel.uiState.value.signedOut)
    assertNull(viewModel.uiState.value.errorMessageRes)
  }
}
