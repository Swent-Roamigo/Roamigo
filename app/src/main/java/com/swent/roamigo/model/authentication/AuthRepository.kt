package com.swent.roamigo.model.authentication

import androidx.credentials.Credential

/**
 * Handles authentication operations such as signing in with Google and signing out.
 *
 * Adapted from the SwEnt bootcamp:
 * https://github.com/swent-epfl/public/blob/main/bootcamp/deliverables/B3/1-Authentication.md
 */
interface AuthRepository {

  /**
   * Signs in the user using a Google account through the Credential Manager API.
   *
   * @return The authenticated account on success, or an exception on failure.
   */
  suspend fun signInWithGoogle(credential: Credential): Result<AuthenticatedUser>

  /** Returns a snapshot of the current account, or null when no user is signed in. */
  fun getCurrentUser(): AuthenticatedUser?

  /**
   * Signs out the currently authenticated Firebase user. The caller clears Credential Manager
   * state.
   *
   * @return A [Result] indicating success or failure.
   */
  fun signOut(): Result<Unit>
}
