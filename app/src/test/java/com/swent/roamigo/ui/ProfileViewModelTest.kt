// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui

import com.swent.roamigo.ui.profile.ProfileViewModel
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileViewModelTest {
  @Test
  fun initialStateIsThePlaceholderUser() {
    val state = ProfileViewModel().uiState.value

    assertEquals("Alex", state.user.displayName)
    assertEquals("alex@epfl.ch", state.email)
    assertEquals(4, state.friendCount)
  }
}
