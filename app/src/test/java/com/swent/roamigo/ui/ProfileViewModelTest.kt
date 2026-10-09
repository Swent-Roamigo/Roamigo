// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui

import com.swent.roamigo.model.users.User
import com.swent.roamigo.ui.profile.ProfileUiState
import com.swent.roamigo.ui.profile.ProfileViewModel
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileViewModelTest {
  private fun initialOf(name: String) =
      ProfileUiState(User("u", name, null), "u@epfl.ch", 0).initial

  @Test
  fun initialIsTheUppercasedFirstLetter() {
    assertEquals("S", initialOf("sam"))
  }

  @Test
  fun initialFallsBackToQuestionMarkForBlankNames() {
    assertEquals("?", initialOf(""))
    assertEquals("?", initialOf("   "))
  }

  @Test
  fun initialKeepsAnEmojiInOnePiece() {
    assertEquals("😀", initialOf("😀 Sam"))
  }

  @Test
  fun initialStateIsThePlaceholderUser() {
    val state = ProfileViewModel().uiState.value

    assertEquals("Alex", state.user.displayName)
    assertEquals("alex@epfl.ch", state.email)
    assertEquals(4, state.friendCount)
  }
}
