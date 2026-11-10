// AI assistance was used for implementation support and code review.
package com.swent.roamigo.model.users

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.MemoryLruGcSettings
import com.google.firebase.firestore.Source
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real SDK tests against isolated Auth and Firestore emulators. */
@RunWith(AndroidJUnit4::class)
class UserDirectoryEmulatorTest {

  private val apps = mutableListOf<FirebaseApp>()
  private lateinit var alice: Client
  private lateinit var bob: Client

  @Before
  fun setUp() = runBlocking {
    alice = newClient()
    bob = newClient()
  }

  @After
  fun tearDown() {
    apps.forEach { it.delete() }
    apps.clear()
  }

  @Test
  fun searchFindsPublishedNamesCaseInsensitively() = runBlocking {
    val prefix = "Travel${UUID.randomUUID().toString().take(8)}"

    alice.rename("${prefix}Sam")
    bob.rename("${prefix}Sara")

    alice.repository.publishCurrentUser()
    bob.repository.publishCurrentUser()

    assertEquals(
        setOf(alice.id, bob.id),
        alice.repository.searchUsers("  ${prefix.lowercase()}s ").map(User::uid).toSet(),
    )

    assertEquals(
        listOf(bob.id),
        alice.repository.getUsers(setOf(bob.id)).map(User::uid),
    )

    assertTrue(alice.repository.getUsers(setOf("missing-${UUID.randomUUID()}")).isEmpty())

    assertTrue(alice.repository.searchUsers("  ").isEmpty())

    assertTrue(alice.repository.searchUsers("does-not-match-${UUID.randomUUID()}").isEmpty())

    assertEquals(
        "${prefix}Sam",
        alice.repository.getCurrentUser().displayName,
    )
  }

  @Test
  fun repeatPublishUpdatesNameAndPhotoWithServerTimestamp() = runBlocking {
    val prefix = "Name${UUID.randomUUID().toString().take(8)}"

    alice.rename("${prefix}Before", "https://example.com/avatar.png")
    alice.repository.publishCurrentUser()

    alice.rename("${prefix}After")
    alice.repository.publishCurrentUser()

    val profile = bob.repository.getUsers(setOf(alice.id)).single()

    assertEquals("${prefix}After", profile.displayName)
    assertEquals(
        "https://example.com/avatar.png",
        profile.profilePictureUrl,
    )

    assertTrue(bob.repository.searchUsers("${prefix}Before").isEmpty())

    assertEquals(
        alice.id,
        bob.repository.searchUsers("${prefix}After").single().uid,
    )

    assertNotNull(
        alice.db
            .collection("users")
            .document(alice.id)
            .get(Source.SERVER)
            .await()
            .getTimestamp("updatedAt")
    )

    assertFalse(
        alice.db
            .collection("userSearchMetadata")
            .document(alice.id)
            .get(Source.SERVER)
            .await()
            .contains("rawEmail")
    )
  }

  @Test
  fun verifiedEmailsAreMatchedExactlyWithoutExposingRawEmails() = runBlocking {
    val email = "Search.${UUID.randomUUID()}@Example.org"
    val account = newClient(email)

    assertTrue(requireNotNull(account.auth.currentUser).isEmailVerified)

    account.rename("Lookup Person")
    account.repository.publishCurrentUser()

    assertEquals(
        listOf(account.id),
        alice.repository.searchUsers("  ${email.uppercase()}  ").map(User::uid),
    )

    assertTrue(alice.repository.searchUsers(email.substringBefore('@') + "@wrong.org").isEmpty())

    assertFalse(
        alice.db
            .collection("users")
            .document(account.id)
            .get(Source.SERVER)
            .await()
            .contains("email")
    )

    assertNotNull(
        account.db
            .collection("userSearchMetadata")
            .document(account.id)
            .get(Source.SERVER)
            .await()
            .getString("emailHash")
    )
  }

  @Test
  fun rulesBlockUnauthorizedAndMalformedWrites() = runBlocking {
    alice.repository.publishCurrentUser()

    val profile = alice.db.collection("users").document(alice.id)

    val legitimate =
        mapOf(
            "uid" to alice.id,
            "displayName" to "Alice",
            "nameKey" to "alice",
            "photoUrl" to null,
            "updatedAt" to FieldValue.serverTimestamp(),
        )

    denied { bob.db.collection("users").document(alice.id).set(legitimate).await() }

    denied { profile.set(legitimate + ("nameKey" to "imposter")).await() }

    denied { profile.set(legitimate + ("email" to "private@example.com")).await() }

    denied { profile.delete().await() }

    denied { bob.db.collection("users").get(Source.SERVER).await() }

    denied { bob.db.collection("users").limit(21).get(Source.SERVER).await() }

    assertEquals(
        1,
        bob.db
            .collection("users")
            .whereEqualTo("uid", alice.id)
            .limit(20)
            .get(Source.SERVER)
            .await()
            .size(),
    )

    denied { bob.db.collection("userSearchMetadata").document(alice.id).get(Source.SERVER).await() }

    denied { bob.db.collection("userEmailLookups").limit(20).get(Source.SERVER).await() }
  }

  @Test
  fun invalidIdsAndSignedOutSessionsFailSafely() = runBlocking {
    alice.repository.publishCurrentUser()

    assertTrue(
        runCatching {
          alice.repository.getUsers(setOf("../bad"))
        }
            .exceptionOrNull() is IllegalArgumentException
    )

    assertTrue(
        runCatching {
          alice.repository.searchUsers("a".repeat(121))
        }
            .exceptionOrNull() is IllegalArgumentException
    )

    bob.auth.signOut()

    assertTrue(
        runCatching {
          bob.repository.searchUsers("Alice")
        }
            .exceptionOrNull() is UserRepositoryException.NotAuthenticated
    )

    assertTrue(
        runCatching {
          bob.repository.publishCurrentUser()
        }
            .exceptionOrNull() is UserRepositoryException.NotAuthenticated
    )
  }

  @Test
  fun serverOnlySearchReportsNetworkFailure() = runBlocking {
    alice.repository.publishCurrentUser()

    bob.db.disableNetwork().await()

    try {
      assertTrue(
          runCatching {
            bob.repository.searchUsers("Traveler")
          }
              .exceptionOrNull() is UserRepositoryException.Network
      )
    } finally {
      bob.db.enableNetwork().await()
    }

    assertEquals(
        alice.id,
        bob.repository.getUsers(setOf(alice.id)).single().uid,
    )
  }

  @Test
  fun changingVerifiedEmailReplacesOldLookup() = runBlocking {
    val suffix = UUID.randomUUID().toString()
    val oldEmail = "old.$suffix@example.org"
    val newEmail = "new.$suffix@example.org"

    val account = newClient(oldEmail)
    account.rename("Email Change Test")

    account.repository.publishCurrentUser()

    assertEquals(
        account.id,
        alice.repository.searchUsers(oldEmail).single().uid,
    )

    // Update the existing account rather than signing in as a new user.
    account.updateEmulatedAccount(newEmail, verified = true)

    account.repository.publishCurrentUser()

    assertTrue(alice.repository.searchUsers(oldEmail).isEmpty())

    assertEquals(
        account.id,
        alice.repository.searchUsers(newEmail).single().uid,
    )

    // Publishing again must not create duplicates.
    account.repository.publishCurrentUser()

    assertEquals(
        listOf(account.id),
        alice.repository.searchUsers(newEmail).map(User::uid),
    )

    assertTrue(alice.repository.searchUsers(oldEmail).isEmpty())
  }

  @Test
  fun losingEmailVerificationRemovesExistingLookup() = runBlocking {
    val email = "verification.${UUID.randomUUID()}@example.org"
    val name = "Verification ${UUID.randomUUID().toString().take(8)}"
    val account = newClient(email)

    account.rename(name)
    account.repository.publishCurrentUser()

    assertEquals(
        account.id,
        alice.repository.searchUsers(email).single().uid,
    )

    account.updateEmulatedAccount(email, verified = false)
    account.repository.publishCurrentUser()

    assertTrue(alice.repository.searchUsers(email).isEmpty())

    val metadata =
        account.db.collection("userSearchMetadata").document(account.id).get(Source.SERVER).await()

    assertEquals(null, metadata.getString("emailHash"))

    assertEquals(
        account.id,
        alice.repository.searchUsers(name).single().uid,
    )
  }

  @Test
  fun unverifiedEmailIsNotDiscoverable() = runBlocking {
    val email = "unverified.${UUID.randomUUID()}@example.org"
    val name = "Unverified ${UUID.randomUUID().toString().take(8)}"
    val account = newClient(email)

    account.rename(name)

    // Change the verification state of the existing account.
    account.updateEmulatedAccount(email, verified = false)

    assertFalse(requireNotNull(account.auth.currentUser).isEmailVerified)

    account.repository.publishCurrentUser()

    assertTrue(alice.repository.searchUsers(email).isEmpty())

    assertEquals(
        null,
        account.db
            .collection("userSearchMetadata")
            .document(account.id)
            .get(Source.SERVER)
            .await()
            .getString("emailHash"),
    )

    // An unverified email must not hide the public name profile.
    assertEquals(
        account.id,
        alice.repository.searchUsers(name).single().uid,
    )
  }

  @Test
  fun currentUserIsAvailableBeforePublishingProfile() = runBlocking {
    alice.rename("Unpublished Traveler")

    val profiles = alice.repository.getUsers(setOf(alice.id))

    assertEquals(
        listOf(User(alice.id, "Unpublished Traveler", null)),
        profiles,
    )
  }

  @Test
  fun cachedProfilesRemainAvailableOffline() = runBlocking {
    val name = "Cached ${UUID.randomUUID().toString().take(8)}"
    alice.rename(name)
    alice.repository.publishCurrentUser()

    // Populate Bob's cache while online.
    val online = bob.repository.getUsers(setOf(alice.id))
    assertEquals(1, online.size)

    // Verify that the document was actually retained in the local cache.
    val cached = bob.db.collection("users").document(alice.id).get(Source.CACHE).await()

    assertTrue(cached.exists())
    assertEquals(name, cached.getString("displayName"))

    bob.db.disableNetwork().await()
    try {
      assertEquals(
          online,
          bob.repository.getUsers(setOf(alice.id)),
      )
    } finally {
      bob.db.enableNetwork().await()
    }
  }

  @Test
  fun missingUncachedProfilesDoNotBreakOfflineLookup() = runBlocking {
    val missingId = "missing-${UUID.randomUUID()}"

    bob.db.disableNetwork().await()
    try {
      assertTrue(bob.repository.getUsers(setOf(missingId)).isEmpty())
    } finally {
      bob.db.enableNetwork().await()
    }
  }

  private suspend fun denied(action: suspend () -> Any?) {
    val error = runCatching { action() }.exceptionOrNull()

    assertTrue(
        "Expected PERMISSION_DENIED, got $error",
        error is FirebaseFirestoreException &&
            error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED,
    )
  }

  private data class Client(
      val id: String,
      val auth: FirebaseAuth,
      val db: FirebaseFirestore,
      val googleSubject: String? = null,
  ) {
    val repository: UserRepository by lazy { UserRepositoryFirestore(db, auth) }

    suspend fun rename(
        name: String,
        photo: String? = null,
    ) {
      val request = UserProfileChangeRequest.Builder().setDisplayName(name)

      if (photo != null) {
        request.setPhotoUri(Uri.parse(photo))
      }

      requireNotNull(auth.currentUser).updateProfile(request.build()).await()
    }

    /**
     * Changes the existing account in the local Auth emulator.
     *
     * This test-only privileged operation must never be used in production.
     */
    suspend fun updateEmulatedAccount(
        email: String,
        verified: Boolean = true,
    ) {
      withContext(Dispatchers.IO) {
        val connection =
            URL(
                    "http://10.0.2.2:9099/" +
                        "identitytoolkit.googleapis.com/v1/" +
                        "projects/demo-roamigo/accounts:update"
                )
                .openConnection() as HttpURLConnection

        try {
          connection.requestMethod = "POST"
          connection.setRequestProperty(
              "Content-Type",
              "application/json",
          )
          connection.setRequestProperty(
              "Authorization",
              "Bearer owner",
          )
          connection.connectTimeout = 10_000
          connection.readTimeout = 10_000
          connection.doOutput = true

          val body =
              JSONObject()
                  .put("localId", id)
                  .put("email", email)
                  .put("emailVerified", verified)
                  .toString()

          connection.outputStream.use { output -> output.write(body.toByteArray(Charsets.UTF_8)) }

          val status = connection.responseCode

          val response =
              (if (status in 200..299) {
                    connection.inputStream
                  } else {
                    connection.errorStream
                  })
                  ?.bufferedReader()
                  ?.use { it.readText() }
                  .orEmpty()

          check(status == HttpURLConnection.HTTP_OK) {
            "Auth emulator update failed ($status): $response"
          }
        } finally {
          connection.disconnect()
        }
      }

      // The administrative email update can revoke the previous session.
      // Sign in again using the same simulated Google identity.
      val googleToken =
          """{"sub":"${requireNotNull(googleSubject)}","email":"$email","email_verified":$verified}"""

      auth.signOut()

      val signedInUser =
          requireNotNull(
              auth
                  .signInWithCredential(GoogleAuthProvider.getCredential(googleToken, null))
                  .await()
                  .user
          )

      // Ensure the emulator did not create a different account.
      assertEquals(id, signedInUser.uid)
      assertEquals(email, signedInUser.email)
      assertEquals(verified, signedInUser.isEmailVerified)

      signedInUser.getIdToken(true).await()
    }
  }

  private suspend fun newClient(
      email: String? = null,
  ): Client {
    val name = UUID.randomUUID().toString()

    val options =
        FirebaseOptions.Builder()
            .setProjectId("demo-roamigo")
            .setApplicationId("1:1234567890:android:user-directory")
            .setApiKey("test-key-$name")
            .build()

    val app =
        FirebaseApp.initializeApp(
            InstrumentationRegistry.getInstrumentation().targetContext,
            options,
            name,
        )

    apps += app

    val auth = FirebaseAuth.getInstance(app).apply { useEmulator("10.0.2.2", 9099) }

    val db =
        FirebaseFirestore.getInstance(app).apply {
          useEmulator("10.0.2.2", 8080)

          firestoreSettings =
              FirebaseFirestoreSettings.Builder()
                  .setLocalCacheSettings(
                      MemoryCacheSettings.newBuilder()
                          .setGcSettings(MemoryLruGcSettings.newBuilder().build())
                          .build()
                  )
                  .build()
        }

    val user =
        if (email == null) {
          auth.signInAnonymously().await().user
        } else {
          // Mock Google token accepted by the local Auth emulator.
          val idToken = """{"sub":"$name","email":"$email","email_verified":true}"""

          auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await().user
        }

    return Client(
        id = requireNotNull(user).uid,
        auth = auth,
        db = db,
        googleSubject = if (email != null) name else null,
    )
  }
}
