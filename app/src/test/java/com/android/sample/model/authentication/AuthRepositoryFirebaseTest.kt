package com.android.sample.model.authentication

import android.os.Bundle
import androidx.credentials.CustomCredential
import androidx.credentials.PasswordCredential
import com.google.android.gms.tasks.Tasks
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.swent.roamigo.model.authentication.AuthRepositoryFirebase
import com.swent.roamigo.model.authentication.GoogleSignInHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AuthRepositoryFirebaseTest {

  private val idToken = "google-id-token"

  private lateinit var auth: FirebaseAuth
  private lateinit var helper: GoogleSignInHelper
  private lateinit var repository: AuthRepositoryFirebase

  private lateinit var credentialData: Bundle
  private lateinit var googleCredential: CustomCredential
  private lateinit var firebaseCredential: AuthCredential
  private lateinit var user: FirebaseUser

  @Before
  fun setUp() {
    auth = mockk()
    helper = mockk()
    repository = AuthRepositoryFirebase(auth, helper)

    credentialData = Bundle()
    googleCredential = CustomCredential(TYPE_GOOGLE_ID_TOKEN_CREDENTIAL, credentialData)
    firebaseCredential = mockk()
    user = mockk()

    val idTokenCredential = mockk<GoogleIdTokenCredential>()
    every { idTokenCredential.idToken } returns idToken
    every { helper.extractIdTokenCredential(credentialData) } returns idTokenCredential
    every { helper.toFirebaseCredential(idToken) } returns firebaseCredential
  }

  private fun stubFirebaseSignIn(user: FirebaseUser?) {
    val authResult = mockk<AuthResult>()
    every { authResult.user } returns user
    every { auth.signInWithCredential(firebaseCredential) } returns Tasks.forResult(authResult)
  }

  @Test
  fun signInWithGoogle_returnsUser_whenFirebaseSignInSucceeds() = runTest {
    stubFirebaseSignIn(user)

    val result = repository.signInWithGoogle(googleCredential)

    assertTrue(result.isSuccess)
    assertSame(user, result.getOrNull())
  }

  @Test
  fun signInWithGoogle_passesExpectedValuesToHelperAndFirebase() = runTest {
    stubFirebaseSignIn(user)

    repository.signInWithGoogle(googleCredential)

    verify(exactly = 1) { helper.extractIdTokenCredential(credentialData) }
    verify(exactly = 1) { helper.toFirebaseCredential(idToken) }
    verify(exactly = 1) { auth.signInWithCredential(firebaseCredential) }
  }

  @Test
  fun signInWithGoogle_fails_whenCredentialIsNotCustomCredential() = runTest {
    val result = repository.signInWithGoogle(PasswordCredential("user", "password"))

    assertFailureMessage("Login failed: Credential is not of type Google ID", result)
    verify(exactly = 0) { auth.signInWithCredential(any()) }
  }

  @Test
  fun signInWithGoogle_fails_whenCustomCredentialIsNotGoogleIdToken() = runTest {
    val result = repository.signInWithGoogle(CustomCredential("other.type", Bundle()))

    assertFailureMessage("Login failed: Credential is not of type Google ID", result)
    verify(exactly = 0) { auth.signInWithCredential(any()) }
  }

  @Test
  fun signInWithGoogle_fails_whenFirebaseReturnsNoUser() = runTest {
    stubFirebaseSignIn(null)

    val result = repository.signInWithGoogle(googleCredential)

    assertFailureMessage("Login failed : Could not retrieve user information", result)
  }

  @Test
  fun signInWithGoogle_fails_whenIdTokenExtractionThrows() = runTest {
    every { helper.extractIdTokenCredential(credentialData) } throws
        IllegalArgumentException("Malformed credential data")

    val result = repository.signInWithGoogle(googleCredential)

    assertFailureMessage("Login failed: Malformed credential data", result)
    verify(exactly = 0) { auth.signInWithCredential(any()) }
  }

  @Test
  fun signInWithGoogle_fails_whenFirebaseCredentialConversionThrows() = runTest {
    every { helper.toFirebaseCredential(idToken) } throws IllegalStateException("Invalid token")

    val result = repository.signInWithGoogle(googleCredential)

    assertFailureMessage("Login failed: Invalid token", result)
    verify(exactly = 0) { auth.signInWithCredential(any()) }
  }

  @Test
  fun signInWithGoogle_fails_whenFirebaseSignInFails() = runTest {
    every { auth.signInWithCredential(firebaseCredential) } returns
        Tasks.forException(RuntimeException("Network error"))

    val result = repository.signInWithGoogle(googleCredential)

    assertFailureMessage("Login failed: Network error", result)
  }

  @Test
  fun signOut_returnsSuccess_whenFirebaseSignOutSucceeds() {
    every { auth.signOut() } returns Unit

    val result = repository.signOut()

    assertEquals(Result.success(Unit), result)
    verify(exactly = 1) { auth.signOut() }
  }

  @Test
  fun signOut_fails_whenFirebaseSignOutThrows() {
    every { auth.signOut() } throws IllegalStateException("Sign-out error")

    val result = repository.signOut()

    assertFailureMessage("Logout failed: Sign-out error", result)
  }

  private fun assertFailureMessage(expected: String, result: Result<*>) {
    assertTrue(result.isFailure)
    assertEquals(expected, result.exceptionOrNull()?.message)
  }
}
