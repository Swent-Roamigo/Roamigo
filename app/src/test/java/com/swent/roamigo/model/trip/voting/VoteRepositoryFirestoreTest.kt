// Voting repository tests implemented with assistance from Codex.
package com.swent.roamigo.model.trip.voting

import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Transaction
import java.util.concurrent.Executor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*

/** Tests the Firebase boundary without a network connection or a running emulator. */
@OptIn(ExperimentalCoroutinesApi::class)
class VoteRepositoryFirestoreTest {
  private val db = mock(FirebaseFirestore::class.java)
  private val trips = mock(CollectionReference::class.java)
  private val trip = mock(DocumentReference::class.java)
  private val votes = mock(CollectionReference::class.java)
  private val voteDocument = mock(DocumentReference::class.java)
  private val ballots = mock(CollectionReference::class.java)
  private val ballotDocument = mock(DocumentReference::class.java)
  private val repository: VoteRepository = VoteRepositoryFirestore(db)
  private val timestamp = Timestamp(100, 123)
  private val vote =
      Vote(
          "vote-1",
          "trip-1",
          "Where next?",
          listOf(VoteOption("option-1", "Museum"), VoteOption("option-2", "Park")),
          "user-1",
          VoteStatus.OPEN,
          timestamp,
          null,
          null,
      )
  private val voteData: Map<String, Any?>
    get() =
        mapOf(
            "uid" to vote.uid,
            "tripId" to vote.tripId,
            "question" to vote.question,
            "options" to vote.options.map { mapOf("uid" to it.uid, "name" to it.name) },
            "createdByUserId" to vote.createdByUserId,
            "status" to vote.status.name,
            "creationTime" to vote.creationTime,
            "closingTime" to vote.closingTime,
            "winningOptionId" to vote.winningOptionId,
        )

  @Before
  fun setUp() {
    `when`(db.collection("trips")).thenReturn(trips)
    `when`(trips.document("trip-1")).thenReturn(trip)
    `when`(trip.collection("votes")).thenReturn(votes)
    `when`(votes.document("vote-1")).thenReturn(voteDocument)
    `when`(voteDocument.collection("ballots")).thenReturn(ballots)
    `when`(ballots.document("user-1")).thenReturn(ballotDocument)
  }

  @Test
  fun createVoteWritesAllFieldsUnderItsTrip() = runTest {
    val transaction = mock(Transaction::class.java)
    val snapshot = mock(DocumentSnapshot::class.java)
    `when`(snapshot.exists()).thenReturn(false)
    `when`(transaction.get(voteDocument)).thenReturn(snapshot)
    stubTransaction(transaction)
    repository.createVote(vote)

    verify(db).collection("trips")
    verify(trips).document("trip-1")
    verify(trip).collection("votes")
    verify(votes).document("vote-1")
    val order = inOrder(transaction)
    order.verify(transaction).get(voteDocument)
    order.verify(transaction).set(voteDocument, voteData)
    verify(voteDocument, never()).set(any())
  }

  @Test
  fun createClosedVotePreservesOptionalFields() = runTest {
    val transaction = mock(Transaction::class.java)
    val snapshot = mock(DocumentSnapshot::class.java)
    `when`(snapshot.exists()).thenReturn(false)
    `when`(transaction.get(voteDocument)).thenReturn(snapshot)
    stubTransaction(transaction)
    val closed =
        vote.copy(status = VoteStatus.CLOSED, closingTime = timestamp, winningOptionId = "option-2")

    repository.createVote(closed)

    verify(transaction)
        .set(
            voteDocument,
            voteData +
                mapOf(
                    "status" to "CLOSED",
                    "closingTime" to timestamp,
                    "winningOptionId" to "option-2",
                ),
        )
  }

  @Test
  fun createVoteWithExistingIdFailsWithoutWriting() = runTest {
    val transaction = mock(Transaction::class.java)
    val snapshot = document("vote-1", voteData)
    `when`(snapshot.exists()).thenReturn(true)
    `when`(transaction.get(voteDocument)).thenReturn(snapshot)
    stubTransaction(transaction)
    val failure = runCatching { repository.createVote(vote) }.exceptionOrNull()

    assertTrue(failure is FirebaseFirestoreException)
    assertEquals(
        FirebaseFirestoreException.Code.ALREADY_EXISTS,
        (failure as FirebaseFirestoreException).code,
    )
    verify(transaction).get(voteDocument)
    verifyNoMoreInteractions(transaction)
    verify(voteDocument, never()).set(any())
    verifyNoInteractions(ballots, ballotDocument)
  }

  @Test
  fun existingBallotCannotBecomeAttachedToReplacementVoteWithDifferentOptions() = runTest {
    val originalBallot = VoteBallot("user-1", "option-1", timestamp)
    doReturn(successfulTask<Void>(null)).`when`(ballotDocument).set(any())
    repository.submitBallot("trip-1", "vote-1", originalBallot)
    val transaction = mock(Transaction::class.java)
    val existing = document("vote-1", voteData)
    `when`(existing.exists()).thenReturn(true)
    `when`(transaction.get(voteDocument)).thenReturn(existing)
    stubTransaction(transaction)
    val replacement = vote.copy(options = listOf(VoteOption("new-option", "Beach")))
    val failure = runCatching { repository.createVote(replacement) }.exceptionOrNull()

    assertEquals(
        FirebaseFirestoreException.Code.ALREADY_EXISTS,
        (failure as FirebaseFirestoreException).code,
    )
    verify(transaction).get(voteDocument)
    verifyNoMoreInteractions(transaction)
    verify(voteDocument, never()).set(any())
    verify(ballots).document("user-1")
    verify(ballotDocument).set(ballotData(originalBallot))
    verifyNoMoreInteractions(ballots, ballotDocument)
  }

  @Test
  fun getVotesMapsNestedOptionsAndUsesTripAndDocumentIds() = runTest {
    val second =
        vote.copy(
            uid = "vote-2",
            status = VoteStatus.CLOSED,
            closingTime = timestamp,
            winningOptionId = "option-2",
        )
    val snapshot =
        query(
            document("vote-1", voteData - "uid" - "tripId"),
            document(
                "vote-2",
                voteData +
                    mapOf(
                        "status" to "CLOSED",
                        "closingTime" to timestamp,
                        "winningOptionId" to "option-2",
                    ),
            ),
        )
    doReturn(successfulTask(snapshot)).`when`(votes).get()
    val result = repository.getVotes("trip-1")

    assertEquals(listOf(vote, second), result)
    verify(trips).document("trip-1")
    verify(trip).collection("votes")
  }

  @Test
  fun getVotesSuspendsUntilTaskCompletes() = runTest {
    val task = successfulTask(query(document("vote-1", voteData)))
    `when`(task.isComplete).thenReturn(false)
    `when`(task.addOnCompleteListener(any<Executor>(), any<OnCompleteListener<QuerySnapshot>>()))
        .thenReturn(task)
    doReturn(task).`when`(votes).get()
    val result = async { repository.getVotes("trip-1") }
    runCurrent()
    assertFalse(result.isCompleted)
    @Suppress("UNCHECKED_CAST")
    val captor =
        ArgumentCaptor.forClass(OnCompleteListener::class.java)
            as ArgumentCaptor<OnCompleteListener<QuerySnapshot>>
    verify(task).addOnCompleteListener(any<Executor>(), captor.capture())
    captor.value.onComplete(task)
    assertEquals(listOf(vote), result.await())
  }

  @Test
  fun getVotesCanBeCancelledWhileAwaitingTask() = runTest {
    val task = successfulTask(query())
    `when`(task.isComplete).thenReturn(false)
    `when`(task.addOnCompleteListener(any<Executor>(), any<OnCompleteListener<QuerySnapshot>>()))
        .thenReturn(task)
    doReturn(task).`when`(votes).get()
    val result = async { repository.getVotes("trip-1") }
    runCurrent()
    result.cancel()
    result.join()
    assertTrue(result.isCancelled)
  }

  @Test
  fun getVotesReturnsEmptyListForTripWithoutVotes() = runTest {
    doReturn(successfulTask(query())).`when`(votes).get()
    val result = repository.getVotes("trip-1")

    assertEquals(emptyList<Vote>(), result)
  }

  @Test
  fun getVotesReportsMalformedDataWithoutPartialSuccess() = runTest {
    val invalidDocuments =
        listOf(
            voteData - "question",
            voteData + ("status" to "UNKNOWN"),
            voteData + ("options" to listOf(mapOf("uid" to "option-1"))),
            voteData + ("creationTime" to "yesterday"),
            voteData + ("closingTime" to "tomorrow"),
            voteData + ("winningOptionId" to 42),
        )
    for (data in invalidDocuments) {
      doReturn(successfulTask(query(document("vote-1", voteData), document("bad", data))))
          .`when`(votes)
          .get()
      val error = runCatching { repository.getVotes("trip-1") }.exceptionOrNull()

      assertTrue(error is IllegalArgumentException)
    }
  }

  @Test
  fun changingChoiceReplacesTheSameUserBallotDocument() = runTest {
    doReturn(successfulTask<Void>(null)).`when`(ballotDocument).set(any())
    val initial = VoteBallot("user-1", "option-1", timestamp)
    val updated = initial.copy(optionId = "option-2", updatedAt = Timestamp(200, 0))

    repository.submitBallot("trip-1", "vote-1", initial)
    repository.submitBallot("trip-1", "vote-1", updated)

    verify(ballots, times(2)).document("user-1")
    verify(ballotDocument).set(ballotData(initial))
    verify(ballotDocument).set(ballotData(updated))
    verify(ballots, never()).document()
  }

  @Test
  fun differentUsersWriteSeparateBallotDocuments() = runTest {
    val otherDocument = mock(DocumentReference::class.java)
    `when`(ballots.document("user-2")).thenReturn(otherDocument)
    doReturn(successfulTask<Void>(null)).`when`(ballotDocument).set(any())
    doReturn(successfulTask<Void>(null)).`when`(otherDocument).set(any())
    val first = VoteBallot("user-1", "option-1", timestamp)
    val second = first.copy(userId = "user-2")

    repository.submitBallot("trip-1", "vote-1", first)
    repository.submitBallot("trip-1", "vote-1", second)

    verify(ballotDocument).set(ballotData(first))
    verify(otherDocument).set(ballotData(second))
  }

  @Test
  fun firestoreErrorsArePropagatedUnchangedForReadsAndWrites() = runTest {
    val error =
        FirebaseFirestoreException("Denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)
    doReturn(failedTask<Unit>(error)).`when`(db).runTransaction(any<Transaction.Function<Unit>>())
    doReturn(failedTask<QuerySnapshot>(error)).`when`(votes).get()
    doReturn(failedTask<Void>(error)).`when`(ballotDocument).set(any())
    val failures = mutableListOf<Exception>()

    listOf<suspend () -> Unit>(
            { repository.createVote(vote) },
            { repository.getVotes("trip-1") },
            {
              repository.submitBallot(
                  "trip-1",
                  "vote-1",
                  VoteBallot("user-1", "option-1", timestamp),
              )
            },
        )
        .forEach { operation ->
          failures.add(runCatching { operation() }.exceptionOrNull() as Exception)
        }

    assertEquals(3, failures.size)
    failures.forEach { assertSame(error, it) }
  }

  @Test
  fun synchronousFirestoreErrorsAreThrown() = runTest {
    val error = IllegalStateException("Firestore unavailable")
    `when`(db.collection("trips")).thenThrow(error)
    val failure = runCatching { repository.createVote(vote) }.exceptionOrNull()

    assertSame(error, failure)
  }

  @Test
  fun invalidDocumentIdsAreReportedBeforeFirestoreAccess() = runTest {
    val operations =
        listOf<suspend () -> Unit>(
            { repository.createVote(vote.copy(uid = "bad/id")) },
            { repository.getVotes("") },
            {
              repository.submitBallot(
                  "trip-1",
                  "vote-1",
                  VoteBallot("bad/id", "option-1", timestamp),
              )
            },
            { repository.observeBallots("trip-1", "bad/id").first() },
        )
    operations.forEach {
      assertTrue(runCatching { it() }.exceptionOrNull() is IllegalArgumentException)
    }
    verifyNoInteractions(db)
  }

  @Test
  fun observeBallotsEmitsInitialChangesAndRemovalsAndCleansUpOnCompletion() = runTest {
    val registration = mock(ListenerRegistration::class.java)
    `when`(ballots.addSnapshotListener(any<EventListener<QuerySnapshot>>()))
        .thenReturn(registration)
    val changes = mutableListOf<List<VoteBallot>>()
    val flow = repository.observeBallots("trip-1", "vote-1")
    verifyNoInteractions(ballots)
    val collection = launch { flow.take(4).collect { changes.add(it) } }
    runCurrent()
    val listener = capturedListener()
    val first = VoteBallot("user-1", "option-1", timestamp)
    val updated = first.copy(optionId = "option-2", updatedAt = Timestamp(200, 0))
    listener.onEvent(query(), null)
    listener.onEvent(query(document("user-1", ballotData(first) - "userId")), null)
    listener.onEvent(query(document("user-1", ballotData(updated))), null)
    listener.onEvent(query(), null)
    collection.join()
    assertEquals(listOf(emptyList(), listOf(first), listOf(updated), emptyList()), changes)
    verify(registration).remove()
  }

  @Test
  fun observeBallotsRemovesListenerOnCancellation() = runTest {
    val registration = mock(ListenerRegistration::class.java)
    `when`(ballots.addSnapshotListener(any<EventListener<QuerySnapshot>>()))
        .thenReturn(registration)
    val collection = launch { repository.observeBallots("trip-1", "vote-1").collect() }
    runCurrent()
    collection.cancel()
    collection.join()
    verify(registration).remove()
  }

  @Test
  fun observeBallotsReportsMalformedSnapshotsAndRemovesListener() = runTest {
    val ballot = VoteBallot("user-1", "option-1", timestamp)
    val snapshots =
        listOf(
            query(
                document("user-1", ballotData(ballot)),
                document("bad", ballotData(ballot) - "optionId"),
            ),
            query(document("user-1", ballotData(ballot) + ("updatedAt" to "invalid"))),
            null,
        )
    for (snapshot in snapshots) {
      reset(ballots)
      val registration = mock(ListenerRegistration::class.java)
      `when`(ballots.addSnapshotListener(any<EventListener<QuerySnapshot>>()))
          .thenReturn(registration)
      val result = async { runCatching { repository.observeBallots("trip-1", "vote-1").first() } }
      runCurrent()
      capturedListener().onEvent(snapshot, null)
      assertTrue(result.await().exceptionOrNull() is IllegalArgumentException)
      verify(registration).remove()
    }
  }

  @Test
  fun observeBallotsPropagatesListenerErrorAndRemovesListener() = runTest {
    val registration = mock(ListenerRegistration::class.java)
    `when`(ballots.addSnapshotListener(any<EventListener<QuerySnapshot>>()))
        .thenReturn(registration)
    val error =
        FirebaseFirestoreException("Unavailable", FirebaseFirestoreException.Code.UNAVAILABLE)
    val result = async { runCatching { repository.observeBallots("trip-1", "vote-1").first() } }
    runCurrent()
    capturedListener().onEvent(null, error)
    assertSame(error, result.await().exceptionOrNull())
    verify(registration).remove()
  }

  @Test
  fun observeBallotsReportsListenerRegistrationFailure() = runTest {
    val error = IllegalStateException("Unable to register")
    `when`(ballots.addSnapshotListener(any<EventListener<QuerySnapshot>>())).thenThrow(error)
    val failure = runCatching {
      repository.observeBallots("trip-1", "vote-1").first()
    }
        .exceptionOrNull()
    assertTrue(failure is IllegalStateException)
    assertEquals(error.message, failure?.message)
  }

  private fun ballotData(ballot: VoteBallot): Map<String, Any?> =
      mapOf(
          "userId" to ballot.userId,
          "optionId" to ballot.optionId,
          "updatedAt" to ballot.updatedAt,
      )

  private fun document(id: String, data: Map<String, Any?>): DocumentSnapshot =
      mock(DocumentSnapshot::class.java).also {
        `when`(it.id).thenReturn(id)
        `when`(it.data).thenReturn(data)
      }

  private fun query(vararg documents: DocumentSnapshot): QuerySnapshot =
      mock(QuerySnapshot::class.java).also { `when`(it.documents).thenReturn(documents.toList()) }

  private fun stubTransaction(transaction: Transaction) {
    doAnswer {
          try {
            successfulTask(it.getArgument<Transaction.Function<Unit>>(0).apply(transaction))
          } catch (error: Exception) {
            failedTask<Unit>(error)
          }
        }
        .`when`(db)
        .runTransaction(any<Transaction.Function<Unit>>())
  }

  @Suppress("UNCHECKED_CAST")
  private fun capturedListener(): EventListener<QuerySnapshot> {
    val captor =
        ArgumentCaptor.forClass(EventListener::class.java)
            as ArgumentCaptor<EventListener<QuerySnapshot>>
    verify(ballots).addSnapshotListener(captor.capture())
    return captor.value
  }

  // Completed tasks exercise await() without requiring an Android Looper.
  @Suppress("UNCHECKED_CAST")
  private fun <T> successfulTask(result: T?): Task<T> =
      (mock(Task::class.java) as Task<T>).also {
        `when`(it.isComplete).thenReturn(true)
        `when`(it.result).thenReturn(result)
      }

  @Suppress("UNCHECKED_CAST")
  private fun <T> failedTask(error: Exception): Task<T> =
      (mock(Task::class.java) as Task<T>).also {
        `when`(it.isComplete).thenReturn(true)
        `when`(it.exception).thenReturn(error)
      }
}
