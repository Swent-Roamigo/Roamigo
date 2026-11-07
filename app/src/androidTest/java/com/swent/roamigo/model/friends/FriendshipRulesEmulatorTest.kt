// AI assistance was used for implementation support.
package com.swent.roamigo.model.friends

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.Source
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Direct SDK writes exercise the rules without trusting repository-side validation. */
@RunWith(AndroidJUnit4::class)
class FriendshipRulesEmulatorTest {
  private val apps = mutableListOf<FirebaseApp>()
  private lateinit var alice: User
  private lateinit var bob: User
  private lateinit var charlie: User

  @Before
  fun setUp() = emulatorTest {
    alice = signInUser()
    bob = signInUser()
    charlie = signInUser()
  }

  @After
  fun tearDown() {
    apps.forEach { it.delete() }
    val connection =
        URL(
                "http://$HOST:$FIRESTORE_PORT/emulator/v1/projects/$PROJECT/databases/(default)/documents"
            )
            .openConnection() as HttpURLConnection
    try {
      connection.connectTimeout = 10_000
      connection.readTimeout = 10_000
      connection.requestMethod = "DELETE"
      check(connection.responseCode == HttpURLConnection.HTTP_OK) { "Emulator cleanup failed" }
    } finally {
      connection.disconnect()
    }
  }

  @Test
  fun pendingRequest_isReadableByBothParticipantsWithServerCreationTime() = emulatorTest {
    val requestId = createPending()

    for (user in listOf(alice, bob)) {
      val snapshot = document(user).get(Source.SERVER).await()
      assertEquals(requestId, snapshot.getString("requestId"))
      assertEquals("PENDING", snapshot.getString("status"))
      assertEquals(listOf(alice.id, bob.id), snapshot.get("participantIds"))
      assertNotNull(snapshot.getTimestamp("createdAt"))
      assertEquals(null, snapshot.get("respondedAt"))
    }
  }

  @Test
  fun missingDocument_isReadableOnlyForTheCallersOwnPair() = emulatorTest {
    assertFalse(document(alice).get(Source.SERVER).await().exists())
    assertFalse(document(bob).get(Source.SERVER).await().exists())
    denied { document(charlie).get(Source.SERVER).await() }
    denied { alice.db.collection("friendships").document("malformed").get(Source.SERVER).await() }
  }

  @Test
  fun queries_requireTheAuthenticatedParticipantConstraint() = emulatorTest {
    createPending()

    val outgoing =
        alice.db
            .collection("friendships")
            .whereArrayContains("participantIds", alice.id)
            .whereEqualTo("status", "PENDING")
            .whereEqualTo("senderUserId", alice.id)
            .get(Source.SERVER)
            .await()
    val incoming =
        bob.db
            .collection("friendships")
            .whereArrayContains("participantIds", bob.id)
            .whereEqualTo("status", "PENDING")
            .whereEqualTo("recipientUserId", bob.id)
            .get(Source.SERVER)
            .await()
    assertEquals(1, outgoing.size())
    assertEquals(outgoing.documents.single().id, incoming.documents.single().id)
    assertTrue(
        charlie.db
            .collection("friendships")
            .whereArrayContains("participantIds", charlie.id)
            .get(Source.SERVER)
            .await()
            .isEmpty
    )
    denied { alice.db.collection("friendships").get(Source.SERVER).await() }
    denied {
      charlie.db
          .collection("friendships")
          .whereArrayContains("participantIds", alice.id)
          .get(Source.SERVER)
          .await()
    }
  }

  @Test
  fun outsiders_cannotReadRespondDeleteOrImpersonateTheSender() = emulatorTest {
    createPending()
    denied { document(charlie).get(Source.SERVER).await() }
    denied { respond(charlie, "ACCEPTED") }
    denied { document(charlie).set(pendingData()).await() }
    respond(bob, "ACCEPTED")
    denied { document(charlie).delete().await() }
    assertEquals("ACCEPTED", document(alice).get(Source.SERVER).await().getString("status"))
  }

  @Test
  fun signedOutUsers_cannotReadQueryCreateUpdateOrDelete() = emulatorTest {
    createPending()
    respond(bob, "ACCEPTED")
    alice.auth.signOut()

    denied { document(alice).get(Source.SERVER).await() }
    denied {
      alice.db
          .collection("friendships")
          .whereArrayContains("participantIds", alice.id)
          .get(Source.SERVER)
          .await()
    }
    denied {
      document(alice, alice.id, charlie.id).set(pendingData(recipientId = charlie.id)).await()
    }
    denied { respond(alice, "DECLINED") }
    denied { document(alice).delete().await() }
  }

  @Test
  fun onlyTheRecipient_canAcceptOrDecline() = emulatorTest {
    createPending()
    denied { respond(alice, "ACCEPTED") }
    denied { respond(alice, "DECLINED") }
    respond(bob, "ACCEPTED")

    assertEquals("ACCEPTED", document(alice).get(Source.SERVER).await().getString("status"))
    assertNotNull(document(bob).get(Source.SERVER).await().getTimestamp("respondedAt"))
  }

  @Test
  fun crossedRequest_canAcceptOnlyTheExistingRequestIdentity() = emulatorTest {
    val requestId = createPending()
    denied { document(bob).set(pendingData(senderId = bob.id, recipientId = alice.id)).await() }
    respond(bob, "ACCEPTED")

    val accepted = document(alice).get(Source.SERVER).await()
    assertEquals(requestId, accepted.getString("requestId"))
    assertEquals(alice.id, accepted.getString("senderUserId"))
    assertEquals(bob.id, accepted.getString("recipientUserId"))
  }

  @Test
  fun directCreation_cannotEstablishAnAcceptedOrDeclinedRelationship() = emulatorTest {
    for (status in listOf("ACCEPTED", "DECLINED")) {
      denied {
        document(alice)
            .set(
                pendingData() +
                    mapOf(
                        "status" to status,
                        "respondedAt" to FieldValue.serverTimestamp(),
                    )
            )
            .await()
      }
    }
    createPending()
  }

  @Test
  fun invalidFieldsAndSelfRequests_areRejected() = emulatorTest {
    val valid = pendingData()
    val invalid =
        listOf(
            valid + ("requestId" to " "),
            valid + ("requestId" to "."),
            valid + ("requestId" to ".."),
            valid + ("requestId" to "bad\n/id"),
            valid + ("senderUserId" to ""),
            valid + ("recipientUserId" to "bad/id"),
            valid + ("participantIds" to listOf(alice.id)),
            valid + ("participantIds" to listOf(alice.id, bob.id, charlie.id)),
            valid + ("participantIds" to listOf(bob.id, alice.id)),
            valid + ("participantIds" to "invalid"),
            valid + ("status" to "UNKNOWN"),
            valid + ("createdAt" to "invalid"),
            valid + ("createdAt" to Timestamp(1, 0)),
            valid + ("respondedAt" to FieldValue.serverTimestamp()),
            valid + ("extra" to true),
            valid - "respondedAt",
        )
    invalid.forEach { fields -> denied { document(alice).set(fields).await() } }
    denied { document(alice, alice.id, alice.id).set(pendingData(recipientId = alice.id)).await() }
    createPending()
  }

  @Test
  fun duplicateAndNoncanonicalDocumentNames_areRejected() = emulatorTest {
    val requestId = createPending()
    denied { document(alice).set(pendingData()).await() }
    val parts = documentId(alice.id, bob.id).split('_')
    for (name in listOf("arbitrary", "${parts[1]}_${parts[0]}")) {
      denied { alice.db.collection("friendships").document(name).set(pendingData()).await() }
    }
    assertEquals(documentId(alice.id, bob.id), documentId(bob.id, alice.id))
    assertEquals(requestId, document(bob).get(Source.SERVER).await().getString("requestId"))
  }

  @Test
  fun identifiersContainingSeparators_haveDistinctValidDocumentNames() = emulatorTest {
    val recipients = listOf("friend:one", "friend_one", "friend_二")
    assertEquals(recipients.size, recipients.map { documentId(alice.id, it) }.toSet().size)
    for (recipientId in recipients) {
      val ref = document(alice, alice.id, recipientId)
      assertFalse(ref.get(Source.SERVER).await().exists())
      ref.set(pendingData(recipientId = recipientId)).await()
      assertEquals(recipientId, ref.get(Source.SERVER).await().getString("recipientUserId"))
    }
  }

  @Test
  fun responses_cannotRewriteRequestIdentityParticipantsOrCreationTime() = emulatorTest {
    createPending()
    for (changes in
        listOf(
            mapOf("requestId" to UUID.randomUUID().toString()),
            mapOf(
                "senderUserId" to bob.id,
                "recipientUserId" to alice.id,
                "participantIds" to listOf(bob.id, alice.id),
            ),
            mapOf("participantIds" to listOf(alice.id, bob.id, charlie.id)),
            mapOf("createdAt" to FieldValue.serverTimestamp()),
        )) {
      denied {
        document(bob)
            .update(
                changes +
                    mapOf(
                        "status" to "ACCEPTED",
                        "respondedAt" to FieldValue.serverTimestamp(),
                    )
            )
            .await()
      }
    }
    denied {
      document(bob).update(mapOf("status" to "ACCEPTED", "respondedAt" to Timestamp(1, 0))).await()
    }
    respond(bob, "ACCEPTED")
  }

  @Test
  fun terminalStates_cannotBeChangedAndIdenticalWritesAreHarmless() = emulatorTest {
    createPending()
    respond(bob, "ACCEPTED")
    val accepted = requireNotNull(document(alice).get(Source.SERVER).await().data)
    document(alice).set(accepted).await()
    document(bob).set(accepted).await()
    denied { respond(bob, "DECLINED") }
    denied { document(bob).update("respondedAt", FieldValue.serverTimestamp()).await() }
    denied { document(alice).set(pendingData()).await() }
  }

  @Test
  fun decline_allowsImmediateNewRequestsFromEitherParticipantWithANewId() = emulatorTest {
    var previousId = createPending()
    respond(bob, "DECLINED")
    document(bob).set(requireNotNull(document(bob).get(Source.SERVER).await().data)).await()
    denied { respond(bob, "ACCEPTED") }
    denied { document(alice).set(pendingData(requestId = previousId)).await() }

    for ((sender, recipient) in listOf(alice to bob, bob to alice)) {
      val data = pendingData(senderId = sender.id, recipientId = recipient.id)
      document(sender).set(data).await()
      val fresh = document(recipient).get(Source.SERVER).await()
      assertNotEquals(previousId, fresh.getString("requestId"))
      assertEquals("PENDING", fresh.getString("status"))
      assertEquals(sender.id, fresh.getString("senderUserId"))
      respond(recipient, "DECLINED")
      previousId = requireNotNull(fresh.getString("requestId"))
    }
  }

  @Test
  fun removal_requiresAcceptanceAndEitherFriendCanRemoveAndRequestAgain() = emulatorTest {
    for (remover in listOf(alice, bob)) {
      val requestId = createPending()
      denied { document(remover).delete().await() }
      respond(bob, "ACCEPTED")
      document(remover).delete().await()
      assertFalse(document(alice).get(Source.SERVER).await().exists())
      assertFalse(document(bob).get(Source.SERVER).await().exists())
      val newId = createPending()
      assertNotEquals(requestId, newId)
      respond(bob, "DECLINED")
    }
  }

  @Test
  fun staleAcceptedSnapshots_cannotRestoreOrReplaceANewerRequest() = emulatorTest {
    createPending()
    respond(bob, "ACCEPTED")
    val old = requireNotNull(document(bob).get(Source.SERVER).await().data)
    document(alice).delete().await()
    denied { document(bob).set(old).await() }
    val newId = createPending()
    denied { document(bob).set(old).await() }
    assertEquals(newId, document(alice).get(Source.SERVER).await().getString("requestId"))
  }

  private class User(val id: String, val auth: FirebaseAuth, val db: FirebaseFirestore)

  private suspend fun signInUser(): User {
    val name = "friendship-rules-${UUID.randomUUID()}"
    val options =
        FirebaseOptions.Builder()
            .setProjectId(PROJECT)
            .setApplicationId("1:1234567890:android:friendship-rules")
            .setApiKey("fake-api-key-$name")
            .build()
    val app =
        FirebaseApp.initializeApp(
            InstrumentationRegistry.getInstrumentation().targetContext,
            options,
            name,
        )
    apps += app
    val auth = FirebaseAuth.getInstance(app).apply { useEmulator(HOST, AUTH_PORT) }
    val db =
        FirebaseFirestore.getInstance(app).apply {
          useEmulator(HOST, FIRESTORE_PORT)
          firestoreSettings =
              FirebaseFirestoreSettings.Builder()
                  .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                  .build()
        }
    return User(requireNotNull(auth.signInAnonymously().await().user).uid, auth, db)
  }

  private fun document(
      user: User,
      firstId: String = alice.id,
      secondId: String = bob.id,
  ): DocumentReference = user.db.collection("friendships").document(documentId(firstId, secondId))

  private fun documentId(firstId: String, secondId: String): String =
      listOf(firstId, secondId)
          .map { id ->
            MessageDigest.getInstance("SHA-256")
                .digest(id.toByteArray(Charsets.UTF_8))
                .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
          }
          .sorted()
          .joinToString("_")

  private fun pendingData(
      senderId: String = alice.id,
      recipientId: String = bob.id,
      requestId: String = UUID.randomUUID().toString(),
  ): Map<String, Any?> =
      mapOf(
          "requestId" to requestId,
          "senderUserId" to senderId,
          "recipientUserId" to recipientId,
          "participantIds" to listOf(senderId, recipientId),
          "status" to "PENDING",
          "createdAt" to FieldValue.serverTimestamp(),
          "respondedAt" to null,
      )

  private suspend fun createPending(): String {
    val data = pendingData()
    document(alice).set(data).await()
    return data.getValue("requestId") as String
  }

  private suspend fun respond(user: User, status: String) {
    document(user)
        .update(mapOf("status" to status, "respondedAt" to FieldValue.serverTimestamp()))
        .await()
  }

  private suspend fun denied(action: suspend () -> Unit) {
    val error = runCatching { action() }.exceptionOrNull()
    assertTrue(
        error.toString(),
        error is FirebaseFirestoreException &&
            error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED,
    )
  }

  private fun emulatorTest(action: suspend () -> Unit): Unit = runBlocking {
    withTimeout(30_000) { action() }
  }

  private companion object {
    const val PROJECT = "demo-roamigo"
    const val HOST = "10.0.2.2"
    const val AUTH_PORT = 9099
    const val FIRESTORE_PORT = 8080
  }
}
