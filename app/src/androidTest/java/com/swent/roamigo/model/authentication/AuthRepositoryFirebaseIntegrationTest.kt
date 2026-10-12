// AI assistance was used for code review and implementation support.
package com.swent.roamigo.model.authentication

import android.os.Bundle
import android.util.Base64
import androidx.credentials.CustomCredential
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthRepositoryFirebaseIntegrationTest {

  private lateinit var app: FirebaseApp
  private lateinit var auth: FirebaseAuth
  private lateinit var repository: AuthRepositoryFirebase

  private val testEmail = "auth-emulator@example.test"
  private val testName = "Roamigo Emulator User"
  private val testPhotoUrl = "https://example.test/profile.png"
  private val googleIdToken =
      listOf(
              encodeJwtSegment("""{"alg":"RS256","typ":"JWT"}"""),
              encodeJwtSegment(
                  """{"iss":"https://accounts.google.com","sub":"roamigo-emulator-user","email":"$testEmail","email_verified":true,"name":"$testName","picture":"$testPhotoUrl"}"""
              ),
              "c2lnbmF0dXJl",
          )
          .joinToString(".")

  private val helper =
      object : GoogleSignInHelper {
        override fun extractIdTokenCredential(bundle: Bundle) =
            GoogleIdTokenCredential.Builder().setId(testEmail).setIdToken(googleIdToken).build()

        override fun toFirebaseCredential(idToken: String) =
            GoogleAuthProvider.getCredential(idToken, null)
      }

  @Before
  fun setUp() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val options =
        FirebaseOptions.Builder()
            .setProjectId("demo-roamigo")
            .setApplicationId("1:1234567890:android:roamigo-auth-emulator")
            .setApiKey("fake-api-key-for-auth-emulator")
            .build()

    app = FirebaseApp.initializeApp(context, options, "auth-test-${UUID.randomUUID()}")
    auth = FirebaseAuth.getInstance(app)
    auth.useEmulator("10.0.2.2", 9099)
    repository = AuthRepositoryFirebase(auth, helper)
  }

  @After
  fun tearDown() {
    auth.signOut()
    app.delete()
  }

  @Test
  fun googleCredentialSignsInAndMapsUserProfile() = runBlocking {
    val result = repository.signInWithGoogle(googleCredential())

    assertTrue(result.exceptionOrNull()?.stackTraceToString(), result.isSuccess)
    val user = result.getOrThrow()
    assertNotNull(user.uid)
    assertEquals(testName, user.displayName)
    assertEquals(testEmail, user.email)
    assertEquals(testPhotoUrl, user.photoUrl)
    assertEquals(user, repository.getCurrentUser())

    assertTrue(repository.signOut().isSuccess)
    assertNull(repository.getCurrentUser())
  }

  private fun googleCredential() =
      CustomCredential(GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL, Bundle())

  private fun encodeJwtSegment(value: String) =
      Base64.encodeToString(
          value.toByteArray(Charsets.UTF_8),
          Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
      )
}
