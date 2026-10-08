package com.swent.roamigo.model.authentication

import androidx.credentials.Credential
import androidx.credentials.CustomCredential
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * A Firebase implementation of [AuthRepository].
 *
 * Extracts the Google ID token from a supplied credential and authenticates with Firebase.
 *
 * Adapted from the SwEnt bootcamp:
 * https://github.com/swent-epfl/public/blob/main/bootcamp/deliverables/B3/1-Authentication.md
 *
 * @param auth The [FirebaseAuth] instance for Firebase authentication.
 * @param helper A [GoogleSignInHelper] to extract Google ID token credentials and convert them to
 *   Firebase credentials.
 */
class AuthRepositoryFirebase(
    private val auth: FirebaseAuth = Firebase.auth,
    private val helper: GoogleSignInHelper = DefaultGoogleSignInHelper(),
) : AuthRepository {

  fun getGoogleSignInOption(serverClientId: String) =
      GetSignInWithGoogleOption.Builder(serverClientId = serverClientId).build()

  override suspend fun signInWithGoogle(credential: Credential): Result<AuthenticatedUser> {
    return try {
      if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val idToken = helper.extractIdTokenCredential(credential.data).idToken
        val firebaseCred = helper.toFirebaseCredential(idToken)

        // Sign in with Firebase
        val user =
            auth.signInWithCredential(firebaseCred).await().user
                ?: return Result.failure(
                    IllegalStateException("Login failed : Could not retrieve user information")
                )

        Result.success(user.toAuthenticatedUser())
      } else {
        Result.failure(IllegalStateException("Login failed: Credential is not of type Google ID"))
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(
          IllegalStateException("Login failed: ${e.localizedMessage ?: "Unexpected error."}")
      )
    }
  }

  override fun getCurrentUser(): AuthenticatedUser? = auth.currentUser?.toAuthenticatedUser()

  private fun FirebaseUser.toAuthenticatedUser() =
      AuthenticatedUser(
          uid = uid,
          displayName = displayName,
          email = email,
          photoUrl = photoUrl?.toString(),
      )

  override fun signOut(): Result<Unit> {
    return try {
      // Firebase sign out
      auth.signOut()

      Result.success(Unit)
    } catch (e: Exception) {
      Result.failure(
          IllegalStateException("Logout failed: ${e.localizedMessage ?: "Unexpected error."}")
      )
    }
  }
}
