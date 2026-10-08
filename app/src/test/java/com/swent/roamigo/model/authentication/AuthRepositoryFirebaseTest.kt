package com.swent.roamigo.model.authentication

import android.net.Uri
import android.os.Bundle
import androidx.credentials.CustomCredential
import com.google.android.gms.tasks.Tasks
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuthRepositoryFirebaseTest {
  // Structurally valid JWT for the SDK parser; no real account or signing key is used.
  private val idToken =
      "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ0ZXN0IiwiZW1haWwiOiJ0ZXN0QGV4YW1wbGUuY29tIn0.c2lnbmF0dXJl"
  private val auth = mock(FirebaseAuth::class.java)
  private val firebaseCredential = mock(AuthCredential::class.java)
  private val helper =
      object : GoogleSignInHelper {
        override fun extractIdTokenCredential(bundle: Bundle) =
            GoogleIdTokenCredential.Builder().setId("test@example.com").setIdToken(idToken).build()

        override fun toFirebaseCredential(idToken: String): AuthCredential {
          assertEquals(this@AuthRepositoryFirebaseTest.idToken, idToken)
          return firebaseCredential
        }
      }
  private val repository = AuthRepositoryFirebase(auth, helper)

  private fun credential() =
      CustomCredential(GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL, Bundle())

  private val expectedUser =
      AuthenticatedUser("user-id", "Test User", "test@example.com", "https://example.com/photo.jpg")

  private fun firebaseUser(): FirebaseUser {
    val user = mock(FirebaseUser::class.java)
    `when`(user.uid).thenReturn(expectedUser.uid)
    `when`(user.displayName).thenReturn(expectedUser.displayName)
    `when`(user.email).thenReturn(expectedUser.email)
    `when`(user.photoUrl).thenReturn(Uri.parse(expectedUser.photoUrl))
    return user
  }

  @Test
  fun validCredentialAuthenticatesWithFirebase() = runTest {
    val result = mock(AuthResult::class.java)
    val user = firebaseUser()
    `when`(result.user).thenReturn(user)
    `when`(auth.signInWithCredential(firebaseCredential)).thenReturn(Tasks.forResult(result))
    val signInResult = repository.signInWithGoogle(credential())
    assertTrue(signInResult.exceptionOrNull()?.message, signInResult.isSuccess)
    assertEquals(expectedUser, signInResult.getOrThrow())
    verify(auth).signInWithCredential(firebaseCredential)
  }

  @Test
  fun unsupportedCredentialDoesNotContactFirebase() = runTest {
    assertTrue(repository.signInWithGoogle(CustomCredential("unsupported", Bundle())).isFailure)
    verifyNoInteractions(auth)
  }

  @Test
  fun missingFirebaseUserFailsSignIn() = runTest {
    `when`(auth.signInWithCredential(firebaseCredential))
        .thenReturn(Tasks.forResult(mock(AuthResult::class.java)))
    assertTrue(repository.signInWithGoogle(credential()).isFailure)
  }

  @Test
  fun firebaseFailureIsReturned() = runTest {
    `when`(auth.signInWithCredential(firebaseCredential))
        .thenReturn(Tasks.forException(IllegalStateException("offline")))
    assertTrue(repository.signInWithGoogle(credential()).isFailure)
  }

  @Test
  fun coroutineCancellationIsPropagated() = runTest {
    `when`(auth.signInWithCredential(firebaseCredential))
        .thenThrow(CancellationException("cancelled"))
    try {
      val signInResult = repository.signInWithGoogle(credential())
      assertTrue(signInResult.exceptionOrNull()?.message, signInResult.isSuccess)
      fail("Cancellation must propagate")
    } catch (_: CancellationException) {
      // Cancellation must not be turned into an authentication failure.
    }
  }

  @Test
  fun currentUserReturnsAccountInformation() {
    val user = firebaseUser()
    `when`(auth.currentUser).thenReturn(user)
    assertEquals(expectedUser, repository.getCurrentUser())
  }

  @Test
  fun currentUserIsNullWhenSignedOut() {
    assertNull(repository.getCurrentUser())
  }

  @Test
  fun missingOptionalProfileFieldsRemainNull() = runTest {
    val user = mock(FirebaseUser::class.java)
    `when`(user.uid).thenReturn("user-id")
    val result = mock(AuthResult::class.java)
    `when`(result.user).thenReturn(user)
    `when`(auth.signInWithCredential(firebaseCredential)).thenReturn(Tasks.forResult(result))
    `when`(auth.currentUser).thenReturn(user)
    val expected = AuthenticatedUser("user-id", null, null, null)
    assertEquals(expected, repository.signInWithGoogle(credential()).getOrThrow())
    assertEquals(expected, repository.getCurrentUser())
  }

  @Test
  fun signOutClearsFirebaseSession() {
    assertTrue(repository.signOut().isSuccess)
    verify(auth).signOut()
  }

  @Test
  fun signOutFailureIsReturned() {
    doThrow(IllegalStateException("failed")).`when`(auth).signOut()
    assertTrue(repository.signOut().isFailure)
  }
}
