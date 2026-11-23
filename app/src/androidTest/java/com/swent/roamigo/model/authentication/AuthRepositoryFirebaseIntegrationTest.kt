// AI assistance was used for code review and implementation support.
package com.swent.roamigo.model.authentication

import android.os.Bundle
import androidx.credentials.CustomCredential
import androidx.credentials.PasswordCredential
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthRepositoryFirebaseIntegrationTest {

  companion object {
    private const val testAppName = "auth-emulator-test"

    @AfterClass
    @JvmStatic
    fun deleteTestApp() {
      val context = InstrumentationRegistry.getInstrumentation().targetContext
      FirebaseApp.getApps(context).firstOrNull { it.name == testAppName }?.delete()
    }
  }

  private lateinit var app: FirebaseApp
  private lateinit var auth: FirebaseAuth
  private lateinit var repository: AuthRepositoryFirebase

  private val testEmail = "auth-emulator@example.test"
  private val testName = "Roamigo Emulator User"
  private val testPhotoUrl = "https://example.test/profile.png"

  @Before
  fun setUp() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val options =
        FirebaseOptions.Builder()
            .setProjectId("demo-roamigo")
            .setApplicationId("1:1234567890:android:roamigo-auth-emulator")
            .setApiKey("fake-api-key-for-auth-emulator")
            .build()

    // Reuse the app because the SDK caches emulator configuration across test methods.
    app =
        FirebaseApp.getApps(context).firstOrNull { it.name == testAppName }
            ?: FirebaseApp.initializeApp(context, options, testAppName)
    auth = FirebaseAuth.getInstance(app)
    auth.useEmulator("10.0.2.2", 9099)
    repository = AuthRepositoryFirebase(auth)
  }

  @After
  fun tearDown() {
    auth.signOut()
  }

  @Test
  fun passwordCredentialDoesNotCreateSession() = runBlocking {
    val result = repository.signInWithGoogle(PasswordCredential(testEmail, "test-password"))

    assertTrue(result.isFailure)
    assertNull(repository.getCurrentUser())
  }

  @Test
  fun unsupportedCredentialDoesNotCreateSession() = runBlocking {
    val result = repository.signInWithGoogle(CustomCredential("unsupported", Bundle()))

    assertTrue(result.isFailure)
    assertNull(repository.getCurrentUser())
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
      emulatorGoogleCredential("roamigo-emulator-user", testEmail, testName, testPhotoUrl)
}
