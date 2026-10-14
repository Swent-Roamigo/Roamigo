// AI assistance was used for implementation support.
package com.swent.roamigo.model.authentication

import androidx.credentials.Credential

/** In-memory authentication for a single supplied account, without Firebase calls. */
internal class FakeAuthRepository(
    private val user: AuthenticatedUser,
    initiallySignedIn: Boolean = false,
) : AuthRepository {
  private var currentAccount: AuthenticatedUser? = if (initiallySignedIn) user else null
  var lastCredential: Credential? = null
    private set
  var failure: Exception? = null

  override suspend fun signInWithGoogle(credential: Credential): Result<AuthenticatedUser> {
    lastCredential = credential
    failure?.let { return Result.failure(it) }
    currentAccount = user
    return Result.success(user)
  }

  override fun getCurrentUser(): AuthenticatedUser? = currentAccount

  override fun signOut(): Result<Unit> {
    currentAccount = null
    return Result.success(Unit)
  }
}
