// AI assistance was used for implementation support.
package com.swent.roamigo.model.friends

import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SnapshotMetadata
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.Transaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.*

/** Controlled SDK clients; no Firebase app, network connection, or emulator is used. */
@OptIn(ExperimentalCoroutinesApi::class)
class FriendRepositoryFirestoreTest {
  private val db = mock(FirebaseFirestore::class.java)
  private val auth = mock(FirebaseAuth::class.java)
  private val alice = mock(FirebaseUser::class.java)
  private val bob = mock(FirebaseUser::class.java)
  private var currentUser: FirebaseUser? = alice
  private val collection = mock(CollectionReference::class.java)
  private val reference = mock(DocumentReference::class.java)
  private val memberQuery = mock(Query::class.java)
  private val friends = mock(Query::class.java)
  private val pending = mock(Query::class.java)
  private val incoming = mock(Query::class.java)
  private val outgoing = mock(Query::class.java)
  private val transaction = mock(Transaction::class.java)
  private val registration = mock(ListenerRegistration::class.java)
  private val id = friendshipDocumentId("alice", "bob")
  private val timestamp = Timestamp(1_700_000_000, 123_456_789)
  private var stored: Map<String, Any?>? = null
  private var readFailure: Exception? = null
  private var transactionFailure: Exception? = null
  private lateinit var listener: EventListener<QuerySnapshot>
  private lateinit var accountListener: FirebaseAuth.AuthStateListener
  private val writes = mutableListOf<Map<String, Any?>>()

  @Before
  fun setUp() {
    `when`(alice.uid).thenReturn("alice")
    `when`(bob.uid).thenReturn("bob")
    `when`(auth.currentUser).thenAnswer { currentUser }
    `when`(db.collection("friendships")).thenReturn(collection)
    `when`(collection.document(id)).thenReturn(reference)
    `when`(collection.whereArrayContains("participantIds", "alice")).thenReturn(memberQuery)
    `when`(memberQuery.whereEqualTo("status", "ACCEPTED")).thenReturn(friends)
    `when`(memberQuery.whereEqualTo("status", "PENDING")).thenReturn(pending)
    `when`(pending.whereEqualTo("recipientUserId", "alice")).thenReturn(incoming)
    `when`(pending.whereEqualTo("senderUserId", "alice")).thenReturn(outgoing)
    for (query in listOf(friends, incoming, outgoing)) {
      `when`(query.get(Source.SERVER)).thenAnswer {
        readFailure?.let { failedTask<QuerySnapshot>(it) } ?: successfulTask(querySnapshot())
      }
    }
    `when`(reference.get(Source.SERVER)).thenAnswer { successfulTask(document()) }
    `when`(transaction.get(reference)).thenAnswer { document() }
    doAnswer {
          val fields = it.getArgument<Map<String, Any?>>(1)
          writes += fields
          stored = fields.mapValues { (_, value) -> if (value is FieldValue) timestamp else value }
          transaction
        }
        .`when`(transaction)
        .set(eq(reference), any<Any>())
    doAnswer {
          val fields = it.getArgument<Map<String, Any?>>(1)
          writes += fields
          stored =
              requireNotNull(stored) +
                  fields.mapValues { (_, value) -> if (value is FieldValue) timestamp else value }
          transaction
        }
        .`when`(transaction)
        .update(eq(reference), any<Map<String, Any>>())
    doAnswer {
          stored = null
          transaction
        }
        .`when`(transaction)
        .delete(reference)
    doAnswer {
          transactionFailure?.let { error -> failedTask<Any?>(error) }
              ?: try {
                successfulTask(it.getArgument<Transaction.Function<Any?>>(0).apply(transaction))
              } catch (error: Exception) {
                failedTask<Any?>(
                    FirebaseFirestoreException(
                        "Transaction failed",
                        FirebaseFirestoreException.Code.UNKNOWN,
                        error,
                    )
                )
              }
        }
        .`when`(db)
        .runTransaction(any<Transaction.Function<Any?>>())
    doAnswer {
          accountListener = it.getArgument(0)
          null
        }
        .`when`(auth)
        .addAuthStateListener(any<FirebaseAuth.AuthStateListener>())
    doAnswer {
          listener = it.getArgument(1)
          registration
        }
        .`when`(friends)
        .addSnapshotListener(eq(MetadataChanges.INCLUDE), any<EventListener<QuerySnapshot>>())
  }

  @Test
  fun reads_useServerAndTheCurrentUserStatusAndDirectionFilters() = runTest {
    val repository = FriendRepositoryFirestore(db, auth, UnconfinedTestDispatcher(testScheduler))
    stored = record("ACCEPTED")
    assertEquals("request-1", repository.getFriends().single().uid)
    stored = record(sender = "bob", recipient = "alice")
    assertEquals("bob", repository.getIncomingRequests().single().senderUserId)
    stored = record()
    assertEquals("bob", repository.getOutgoingRequests().single().recipientUserId)
    verify(collection, times(3)).whereArrayContains("participantIds", "alice")
    for (query in listOf(friends, incoming, outgoing)) verify(query).get(Source.SERVER)
  }

  @Test
  fun malformedQueryResults_failWithoutReturningPartialLists() = runTest {
    val repository = FriendRepositoryFirestore(db, auth, UnconfinedTestDispatcher(testScheduler))
    stored = record("ACCEPTED") - "createdAt"
    expect<FriendRepositoryException.InvalidData> { repository.getFriends() }
  }

  @Test
  fun send_createsPendingWithServerTimestampAndDoesNotRewriteDuplicates() = runTest {
    val repository = FriendRepositoryFirestore(db, auth, UnconfinedTestDispatcher(testScheduler))
    val first = repository.sendFriendRequest("bob")
    assertEquals(FriendRequestStatus.PENDING, first.status)
    assertEquals("alice", first.senderUserId)
    assertTrue(writes.single()["createdAt"] is FieldValue)
    assertEquals(listOf("alice", "bob"), writes.single()["participantIds"])
    assertEquals(first, repository.sendFriendRequest("bob"))
    assertEquals(1, writes.size)
    verify(reference, times(2)).get(Source.SERVER)
  }

  @Test
  fun crossedSend_acceptsTheOriginalRequestAndPreservesDirection() = runTest {
    val repository = FriendRepositoryFirestore(db, auth, UnconfinedTestDispatcher(testScheduler))
    stored = record(sender = "bob", recipient = "alice")
    val accepted = repository.sendFriendRequest("bob")
    assertEquals("request-1", accepted.uid)
    assertEquals("bob", accepted.senderUserId)
    assertEquals(FriendRequestStatus.ACCEPTED, accepted.status)
    assertEquals(setOf("status", "respondedAt"), writes.single().keys)
    assertTrue(writes.single()["respondedAt"] is FieldValue)
    assertEquals(accepted, repository.sendFriendRequest("bob"))
    assertEquals(1, writes.size)
  }

  @Test
  fun sendAfterDecline_usesANewRequestIdentityImmediately() = runTest {
    val repository = FriendRepositoryFirestore(db, auth, UnconfinedTestDispatcher(testScheduler))
    stored = record("DECLINED")
    val fresh = repository.sendFriendRequest("bob")
    assertNotEquals("request-1", fresh.uid)
    assertEquals(FriendRequestStatus.PENDING, fresh.status)
    assertEquals(null, fresh.respondedAt)
  }

  @Test
  fun acceptAndDecline_areRecipientOnlyAndRepeatedMatchingActionsDoNotWrite() = runTest {
    val repository = FriendRepositoryFirestore(db, auth, UnconfinedTestDispatcher(testScheduler))
    stored = record()
    expect<FriendRepositoryException.PermissionDenied> {
      repository.acceptFriendRequest("bob", "request-1")
    }
    expect<FriendRepositoryException.PermissionDenied> {
      repository.declineFriendRequest("bob", "request-1")
    }
    stored = record(sender = "bob", recipient = "alice")
    val friendship = repository.acceptFriendRequest("bob", "request-1")
    assertEquals(friendship, repository.acceptFriendRequest("bob", "request-1"))
    assertEquals(1, writes.size)
    expect<FriendRepositoryException.Conflict> {
      repository.declineFriendRequest("bob", "request-1")
    }
    stored = record(sender = "bob", recipient = "alice")
    repository.declineFriendRequest("bob", "request-1")
    repository.declineFriendRequest("bob", "request-1")
    assertEquals(2, writes.size)
    expect<FriendRepositoryException.Conflict> {
      repository.acceptFriendRequest("bob", "request-1")
    }
  }

  @Test
  fun competingResponsePermissionFailure_reconcilesCommittedStateWithoutAnotherWrite() = runTest {
    val repository = FriendRepositoryFirestore(db, auth, UnconfinedTestDispatcher(testScheduler))
    transactionFailure =
        FirebaseFirestoreException(
            "Concurrent response",
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
        )
    stored = record("DECLINED", sender = "bob", recipient = "alice")
    expect<FriendRepositoryException.Conflict> {
      repository.acceptFriendRequest("bob", "request-1")
    }
    repository.declineFriendRequest("bob", "request-1")
    stored = record("ACCEPTED", sender = "bob", recipient = "alice")
    assertEquals("request-1", repository.acceptFriendRequest("bob", "request-1").uid)
    expect<FriendRepositoryException.Conflict> {
      repository.declineFriendRequest("bob", "request-1")
    }
    stored = record(sender = "bob", recipient = "alice")
    expect<FriendRepositoryException.PermissionDenied> {
      repository.acceptFriendRequest("bob", "request-1")
    }
    stored = record("ACCEPTED")
    expect<FriendRepositoryException.PermissionDenied> {
      repository.acceptFriendRequest("bob", "request-1")
    }
    assertTrue(writes.isEmpty())
  }

  @Test
  fun staleAndMissingResponses_areConflictsWithoutWrites() = runTest {
    val repository = FriendRepositoryFirestore(db, auth, UnconfinedTestDispatcher(testScheduler))
    for (state in
        listOf(null, record(sender = "bob", recipient = "alice", requestId = "new-request"))) {
      stored = state
      expect<FriendRepositoryException.Conflict> {
        repository.acceptFriendRequest("bob", "old-request")
      }
      expect<FriendRepositoryException.Conflict> {
        repository.declineFriendRequest("bob", "old-request")
      }
    }
    assertTrue(writes.isEmpty())
  }

  @Test
  fun removal_requiresTheSameAcceptedGenerationAndMissingRemovalIsHarmless() = runTest {
    val repository = FriendRepositoryFirestore(db, auth, UnconfinedTestDispatcher(testScheduler))
    stored = record()
    expect<FriendRepositoryException.Conflict> { repository.removeFriend("bob", "request-1") }
    stored = record("ACCEPTED", requestId = "new-request")
    expect<FriendRepositoryException.Conflict> { repository.removeFriend("bob", "old-request") }
    repository.removeFriend("bob", "new-request")
    repository.removeFriend("bob", "new-request")
    verify(transaction, times(1)).delete(reference)
    assertNull(stored)
  }

  @Test
  fun failureCodes_areClassifiedWithoutMaskingMissingIndexErrorsAsConflicts() = runTest {
    val repository = FriendRepositoryFirestore(db, auth, UnconfinedTestDispatcher(testScheduler))
    val cases =
        mapOf(
            FirebaseFirestoreException.Code.UNAUTHENTICATED to
                FriendRepositoryException.NotAuthenticated::class.java,
            FirebaseFirestoreException.Code.PERMISSION_DENIED to
                FriendRepositoryException.PermissionDenied::class.java,
            FirebaseFirestoreException.Code.UNAVAILABLE to
                FriendRepositoryException.Network::class.java,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED to
                FriendRepositoryException.Network::class.java,
            FirebaseFirestoreException.Code.ABORTED to
                FriendRepositoryException.Conflict::class.java,
            FirebaseFirestoreException.Code.DATA_LOSS to
                FriendRepositoryException.InvalidData::class.java,
            FirebaseFirestoreException.Code.FAILED_PRECONDITION to
                FriendRepositoryException.Unknown::class.java,
        )
    for ((code, expected) in cases) {
      readFailure = FirebaseFirestoreException("Controlled failure", code)
      val error = runCatching { repository.getFriends() }.exceptionOrNull()
      assertTrue("Unexpected error for $code: $error", expected.isInstance(error))
      assertTrue(generateSequence(error) { it.cause }.any { it === readFailure })
    }
    readFailure = null
    assertTrue(repository.getFriends().isEmpty())
  }

  @Test
  fun transactionNetworkFailure_doesNotWriteAndCanRecover() = runTest {
    val repository = FriendRepositoryFirestore(db, auth, UnconfinedTestDispatcher(testScheduler))
    transactionFailure =
        FirebaseFirestoreException("Offline", FirebaseFirestoreException.Code.UNAVAILABLE)
    expect<FriendRepositoryException.Network> { repository.sendFriendRequest("bob") }
    assertNull(stored)
    transactionFailure = null
    assertEquals(FriendRequestStatus.PENDING, repository.sendFriendRequest("bob").status)
  }

  @Test
  fun committedAcceptanceWithLostReadback_isRecoveredByRefreshingWithoutAnotherWrite() = runTest {
    val repository = FriendRepositoryFirestore(db, auth, UnconfinedTestDispatcher(testScheduler))
    stored = record(sender = "bob", recipient = "alice")
    doAnswer {
          failedTask<DocumentSnapshot>(
              FirebaseFirestoreException(
                  "Connection lost",
                  FirebaseFirestoreException.Code.UNAVAILABLE,
              )
          )
        }
        .`when`(reference)
        .get(Source.SERVER)

    expect<FriendRepositoryException.Network> { repository.acceptFriendRequest("bob", "request-1") }
    assertEquals("ACCEPTED", stored?.get("status"))
    assertEquals("request-1", repository.getFriends().single().uid)
    assertEquals(1, writes.size)
  }

  @Test
  fun signedOutInvalidAndCancelledCalls_doNotStartTransactions() = runTest {
    val repository = FriendRepositoryFirestore(db, auth, UnconfinedTestDispatcher(testScheduler))
    currentUser = null
    expect<FriendRepositoryException.NotAuthenticated> { repository.getFriends() }
    expect<FriendRepositoryException.NotAuthenticated> { repository.observeFriends().first() }
    expect<FriendRepositoryException.NotAuthenticated> { repository.sendFriendRequest("bob") }
    currentUser = alice
    for (invalid in listOf("alice", "", ".", "..", "bad/id")) {
      expect<IllegalArgumentException> { repository.sendFriendRequest(invalid) }
    }
    val cancelled =
        async(start = CoroutineStart.UNDISPATCHED) {
          currentCoroutineContext().cancel()
          repository.sendFriendRequest("bob")
        }
    expect<CancellationException> { cancelled.await() }
    verify(db, never()).runTransaction(any<Transaction.Function<Any?>>())
  }

  @Test
  fun accountChangeDuringRead_doesNotReturnThePreviousUsersData() = runTest {
    val repository = FriendRepositoryFirestore(db, auth, UnconfinedTestDispatcher(testScheduler))
    stored = record("ACCEPTED")
    `when`(friends.get(Source.SERVER)).thenAnswer {
      currentUser = bob
      successfulTask(querySnapshot())
    }
    expect<FriendRepositoryException.NotAuthenticated> { repository.getFriends() }
  }

  @Test
  fun observation_skipsPendingWritesEmitsCompleteListsAndReleasesBothListeners() = runTest {
    val repository = FriendRepositoryFirestore(db, auth, UnconfinedTestDispatcher(testScheduler))
    val updates = mutableListOf<List<Friendship>>()
    val observer =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
          repository.observeFriends().toList(updates)
        }
    runCurrent()
    listener.onEvent(querySnapshot(), null)
    runCurrent()
    stored = record("ACCEPTED") + ("respondedAt" to null)
    listener.onEvent(querySnapshot(pendingWrites = true), null)
    runCurrent()
    assertEquals(listOf(emptyList<Friendship>()), updates)
    stored = record("ACCEPTED")
    listener.onEvent(querySnapshot(), null)
    runCurrent()
    listener.onEvent(querySnapshot(), null)
    runCurrent()
    assertEquals(2, updates.size)
    assertEquals("request-1", updates.last().single().uid)
    observer.cancelAndJoin()
    verify(registration).remove()
    verify(auth).removeAuthStateListener(accountListener)
  }

  @Test
  fun observation_accountChangeEndsCollectionBeforeQueuedDataCanBeExposed() = runTest {
    val repository = FriendRepositoryFirestore(db, auth, StandardTestDispatcher(testScheduler))
    val updates = mutableListOf<List<Friendship>>()
    val observer = backgroundScope.launch {
      runCatching { repository.observeFriends().toList(updates) }
    }
    runCurrent()
    stored = record("ACCEPTED")
    listener.onEvent(querySnapshot(), null)
    currentUser = bob
    accountListener.onAuthStateChanged(auth)
    runCurrent()
    assertTrue(observer.isCompleted)
    assertTrue(updates.isEmpty())
    verify(registration).remove()
    verify(auth).removeAuthStateListener(accountListener)
  }

  @Test
  fun observation_errorAndMalformedDataTerminateAndCleanUp() = runTest {
    val repository = FriendRepositoryFirestore(db, auth, UnconfinedTestDispatcher(testScheduler))
    val result = async { runCatching { repository.observeFriends().first() } }
    runCurrent()
    listener.onEvent(
        null,
        FirebaseFirestoreException("Denied", FirebaseFirestoreException.Code.PERMISSION_DENIED),
    )
    assertTrue(result.await().exceptionOrNull() is FriendRepositoryException.PermissionDenied)
    stored = record("ACCEPTED") - "createdAt"
    val malformed = async { runCatching { repository.observeFriends().first() } }
    runCurrent()
    listener.onEvent(querySnapshot(), null)
    assertTrue(malformed.await().exceptionOrNull() is FriendRepositoryException.InvalidData)
    verify(registration, times(2)).remove()
    verify(auth, times(2)).removeAuthStateListener(any<FirebaseAuth.AuthStateListener>())
  }

  private fun record(
      status: String = "PENDING",
      sender: String = "alice",
      recipient: String = "bob",
      requestId: String = "request-1",
  ): Map<String, Any?> =
      mapOf(
          "requestId" to requestId,
          "senderUserId" to sender,
          "recipientUserId" to recipient,
          "participantIds" to listOf(sender, recipient),
          "status" to status,
          "createdAt" to timestamp,
          "respondedAt" to if (status == "PENDING") null else timestamp,
      )

  private fun document(): DocumentSnapshot =
      mock(DocumentSnapshot::class.java).also {
        `when`(it.exists()).thenReturn(stored != null)
        `when`(it.id).thenReturn(id)
        `when`(it.data).thenReturn(stored)
      }

  private fun querySnapshot(pendingWrites: Boolean = false): QuerySnapshot {
    val documents = if (stored == null) emptyList() else listOf(document())
    return mock(QuerySnapshot::class.java).also {
      `when`(it.documents).thenReturn(documents)
      val metadata = mock(SnapshotMetadata::class.java)
      `when`(metadata.hasPendingWrites()).thenReturn(pendingWrites)
      `when`(it.metadata).thenReturn(metadata)
    }
  }

  private suspend inline fun <reified T : Throwable> expect(action: suspend () -> Unit) {
    val error = runCatching { action() }.exceptionOrNull()
    assertTrue("Expected ${T::class.java.simpleName}, got $error", error is T)
  }

  @Suppress("UNCHECKED_CAST")
  private fun <T> successfulTask(value: T): Task<T> =
      (mock(Task::class.java) as Task<T>).also {
        `when`(it.isComplete).thenReturn(true)
        `when`(it.result).thenReturn(value)
      }

  @Suppress("UNCHECKED_CAST")
  private fun <T> failedTask(error: Exception): Task<T> =
      (mock(Task::class.java) as Task<T>).also {
        `when`(it.isComplete).thenReturn(true)
        `when`(it.exception).thenReturn(error)
      }
}
