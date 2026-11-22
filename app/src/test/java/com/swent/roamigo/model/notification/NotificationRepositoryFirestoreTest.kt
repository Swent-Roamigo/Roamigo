// AI assistance was used for implementation support.
package com.swent.roamigo.model.notification

import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SnapshotMetadata
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationRepositoryFirestoreTest {
  private val db = mock(FirebaseFirestore::class.java)
  private val auth = mock(FirebaseAuth::class.java)
  private val recipient = mock(FirebaseUser::class.java)
  private val profiles = mock(CollectionReference::class.java)
  private val profile = mock(DocumentReference::class.java)
  private val inbox = mock(CollectionReference::class.java)
  private val reference = mock(DocumentReference::class.java)
  private val query = mock(Query::class.java)
  private val registration = mock(ListenerRegistration::class.java)
  private var currentUser: FirebaseUser? = recipient
  private lateinit var accountListener: FirebaseAuth.AuthStateListener
  private lateinit var snapshotListener: EventListener<QuerySnapshot>

  @Before
  fun setUp() {
    `when`(auth.currentUser).thenAnswer { currentUser }
    `when`(recipient.uid).thenReturn("recipient")
    `when`(db.collection("users")).thenReturn(profiles)
    `when`(profiles.document("recipient")).thenReturn(profile)
    `when`(profile.collection("notifications")).thenReturn(inbox)
    `when`(inbox.document("request")).thenReturn(reference)
    `when`(inbox.orderBy("createdAt", Query.Direction.DESCENDING)).thenReturn(query)
    doAnswer {
          accountListener = it.getArgument(0)
          null
        }
        .`when`(auth)
        .addAuthStateListener(any<FirebaseAuth.AuthStateListener>())
    doAnswer {
          snapshotListener = it.getArgument(1)
          registration
        }
        .`when`(query)
        .addSnapshotListener(eq(MetadataChanges.INCLUDE), any<EventListener<QuerySnapshot>>())
  }

  @Test
  fun inboxStartsEmptyAndEmitsBothEventTypesNewestFirst() = runTest {
    val states = mutableListOf<List<Notification>>()
    val collection = launch { repository().observeNotifications().take(2).toList(states) }
    runCurrent()
    emit()
    runCurrent()
    emit(document("vote", voteFields), document("request", requestFields))
    collection.join()

    assertEquals(emptyList<Notification>(), states.first())
    assertEquals(
        listOf(
            Notification(
                "vote",
                "recipient",
                NotificationEvent.TripVoteCreated("trip-id", "vote-id"),
                Instant.ofEpochSecond(2),
                true,
            ),
            Notification(
                "request",
                "recipient",
                NotificationEvent.FriendRequestReceived("request-id", "sender"),
                Instant.ofEpochSecond(1, 123),
                false,
            ),
        ),
        states.last(),
    )
    verify(inbox).orderBy("createdAt", Query.Direction.DESCENDING)
    verify(registration).remove()
    verify(auth).removeAuthStateListener(accountListener)
  }

  @Test
  fun unreadCountChangesAfterAnAcknowledgedRead() = runTest {
    val counts = mutableListOf<Int>()
    val collection = launch { repository().observeUnreadCount().take(2).toList(counts) }
    runCurrent()
    emit(document("request", requestFields))
    runCurrent()

    val read = document("request", requestFields + ("isRead" to true))
    emit(read, pending = true)
    runCurrent()
    assertEquals(listOf(1), counts)

    emit(read)
    collection.join()
    assertEquals(listOf(1, 0), counts)
  }

  @Test
  fun accountChangeEndsObservationAndDropsQueuedSnapshots() = runTest {
    val states = mutableListOf<List<Notification>>()
    val collection = launch { repository().observeNotifications().toList(states) }
    runCurrent()
    emit()
    runCurrent()
    emit(document("request", requestFields))
    currentUser = mock(FirebaseUser::class.java)
    accountListener.onAuthStateChanged(auth)
    emit(document("vote", voteFields))
    collection.join()

    assertEquals(listOf(emptyList<Notification>()), states)
    verify(registration).remove()
    verify(auth).removeAuthStateListener(accountListener)
  }

  @Test
  fun failedObservationCanBeCollectedAgain() = runTest {
    val repository = repository()
    val flow = repository.observeNotifications()
    val error =
        FirebaseFirestoreException("Unavailable", FirebaseFirestoreException.Code.UNAVAILABLE)
    val failed = async { runCatching { flow.first() } }
    runCurrent()
    snapshotListener.onEvent(null, error)
    assertSame(error, failed.await().exceptionOrNull())
    verify(registration).remove()

    val retry = async { flow.first() }
    runCurrent()
    emit(document("request", requestFields))
    assertEquals("request", retry.await().single().uid)
    verify(registration, times(2)).remove()
  }

  @Test
  fun invalidSnapshotFailsWithoutEmittingAPartialInbox() = runTest {
    val repository = repository()
    val malformedEntries =
        listOf(
            requestFields + ("recipientUserId" to "other"),
            requestFields + ("type" to "UNKNOWN"),
            requestFields + ("requestId" to "nested/request"),
            requestFields - "createdAt",
            requestFields + ("isRead" to "false"),
            voteFields - "tripId",
        )
    for (fields in malformedEntries) {
      val result = async { runCatching { repository.observeNotifications().first() } }
      runCurrent()
      emit(document("request", requestFields), document("malformed", fields))
      assertTrue(result.await().exceptionOrNull() is IllegalArgumentException)
    }
    verify(registration, times(malformedEntries.size)).remove()
  }

  @Test
  fun markReadUpdatesOnlyTheRequestedFieldAndCanBeRetried() = runTest {
    val repository = repository()
    val error =
        FirebaseFirestoreException("Unavailable", FirebaseFirestoreException.Code.UNAVAILABLE)
    val failedWrite = writeTask(error)
    val successfulWrite = writeTask()
    `when`(reference.update("isRead", true)).thenReturn(failedWrite, successfulWrite)

    assertSame(error, runCatching { repository.markAsRead("request") }.exceptionOrNull())
    repository.markAsRead("request")
    repository.markAsRead("request")

    verify(reference, times(3)).update("isRead", true)
  }

  @Test
  fun invalidIdsAndSignedOutCallsCannotAccessFirestore() = runTest {
    val repository = repository()
    for (id in listOf("", ".", "..", "nested/request")) {
      assertTrue(
          runCatching { repository.markAsRead(id) }.exceptionOrNull() is IllegalArgumentException
      )
    }
    verifyNoInteractions(reference)

    currentUser = null
    assertTrue(
        runCatching { repository.markAsRead("request") }.exceptionOrNull() is IllegalStateException
    )
    assertTrue(
        runCatching { repository.observeNotifications().first() }.exceptionOrNull()
            is IllegalStateException
    )
    verifyNoInteractions(query, reference)
  }

  private fun TestScope.repository() =
      NotificationRepositoryFirestore(db, auth, StandardTestDispatcher(testScheduler))

  private fun document(id: String, fields: Map<String, Any>): DocumentSnapshot =
      mock(DocumentSnapshot::class.java).also {
        `when`(it.id).thenReturn(id)
        `when`(it.data).thenReturn(fields)
      }

  private fun emit(vararg documents: DocumentSnapshot, pending: Boolean = false) {
    val metadata = mock(SnapshotMetadata::class.java)
    `when`(metadata.hasPendingWrites()).thenReturn(pending)
    val snapshot = mock(QuerySnapshot::class.java)
    `when`(snapshot.documents).thenReturn(documents.toList())
    `when`(snapshot.metadata).thenReturn(metadata)
    snapshotListener.onEvent(snapshot, null)
  }

  @Suppress("UNCHECKED_CAST")
  private fun writeTask(error: Exception? = null): Task<Void> =
      (mock(Task::class.java) as Task<Void>).also {
        `when`(it.isComplete).thenReturn(true)
        `when`(it.exception).thenReturn(error)
      }

  private val requestFields =
      mapOf(
          "recipientUserId" to "recipient",
          "type" to "FRIEND_REQUEST_RECEIVED",
          "requestId" to "request-id",
          "senderUserId" to "sender",
          "createdAt" to Timestamp(1, 123),
          "isRead" to false,
      )

  private val voteFields =
      mapOf(
          "recipientUserId" to "recipient",
          "type" to "TRIP_VOTE_CREATED",
          "tripId" to "trip-id",
          "voteId" to "vote-id",
          "createdAt" to Timestamp(2, 0),
          "isRead" to true,
      )
}
