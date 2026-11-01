// Implemented with assistance from Claude (Anthropic) and OpenAI Codex.
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

  private fun viewModel(user: AuthenticatedUser, signedIn: Boolean = true) =
      ProfileViewModel(FakeAuthRepository(user, initiallySignedIn = signedIn))

  @Test
  fun showsTheSignedInAccount() {
    val state =
        viewModel(AuthenticatedUser("u1", "Sam Rivers", "sam@gmail.com", "https://img/sam.png"))
            .uiState
            .value

    assertEquals("u1", state.user.uid)
    assertEquals("Sam Rivers", state.user.displayName)
    assertEquals("https://img/sam.png", state.user.profilePictureUrl)
    assertEquals("sam@gmail.com", state.email)
  }

  @Test
  fun usesAFallbackNameWhenTheAccountHasNone() {
    assertEquals(
        "Traveler",
        viewModel(AuthenticatedUser("u1", null, "sam@gmail.com", null))
            .uiState
            .value
            .user
            .displayName,
    )
    assertEquals(
        "Traveler",
        viewModel(AuthenticatedUser("u1", "  ", "sam@gmail.com", null))
            .uiState
            .value
            .user
            .displayName,
    )
  }

  @Test
  fun hasNoEmailWhenTheAccountHasNone() {
    assertNull(viewModel(AuthenticatedUser("u1", "Sam", null, null)).uiState.value.email)
  }

  @Test
  fun showsTheFallbackWhenNobodyIsSignedIn() {
    val state =
        viewModel(AuthenticatedUser("u1", "Sam", "sam@gmail.com", null), signedIn = false)
            .uiState
            .value

    assertEquals("Traveler", state.user.displayName)
    assertEquals("", state.user.uid)
    assertNull(state.email)
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
