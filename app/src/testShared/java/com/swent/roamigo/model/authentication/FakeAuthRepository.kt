// AI assistance was used for implementation support.
package com.swent.roamigo.model.authentication

import androidx.credentials.Credential
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-memory authentication for a single supplied account, without Firebase calls. */
internal class FakeAuthRepository(
    private val user: AuthenticatedUser,
    initiallySignedIn: Boolean = false,
) : AuthRepository {
  private val currentAccount = MutableStateFlow(if (initiallySignedIn) user else null)
  var lastCredential: Credential? = null
    private set
  var failure: Exception? = null
  var signOutFailure: Exception? = null

  override suspend fun signInWithGoogle(credential: Credential): Result<AuthenticatedUser> {
    lastCredential = credential
    failure?.let { return Result.failure(it) }
    setCurrentAccount(user)
    return Result.success(user)
  }

  override fun getCurrentUser(): AuthenticatedUser? = currentAccount.value

  override fun observeCurrentUser(): Flow<AuthenticatedUser?> = currentAccount.asStateFlow()

  /** Simulates an external account change without credentials or Firebase. */
  fun setCurrentAccount(account: AuthenticatedUser?) {
    currentAccount.value = account
  }

  override fun signOut(): Result<Unit> {
    signOutFailure?.let { return Result.failure(it) }
    setCurrentAccount(null)
    return Result.success(Unit)
  }
}
