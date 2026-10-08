// Voting repository tests implemented with assistance from Codex.
package com.swent.roamigo.model.trip.voting

import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
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
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*

/** Tests the Firebase boundary without a network connection or a running emulator. */
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
  fun createVoteWritesAllFieldsUnderItsTrip() {
    val transaction = mock(Transaction::class.java)
    val snapshot = mock(DocumentSnapshot::class.java)
    `when`(snapshot.exists()).thenReturn(false)
    `when`(transaction.get(voteDocument)).thenReturn(snapshot)
    stubTransaction(transaction)
    var completed = false

    repository.createVote(vote, { completed = true }, ::unexpectedFailure)

    verify(db).collection("trips")
    verify(trips).document("trip-1")
    verify(trip).collection("votes")
    verify(votes).document("vote-1")
    val order = inOrder(transaction)
    order.verify(transaction).get(voteDocument)
    order.verify(transaction).set(voteDocument, voteData)
    verify(voteDocument, never()).set(any())
    assertTrue(completed)
  }

  @Test
  fun createClosedVotePreservesOptionalFields() {
    val transaction = mock(Transaction::class.java)
    val snapshot = mock(DocumentSnapshot::class.java)
    `when`(snapshot.exists()).thenReturn(false)
    `when`(transaction.get(voteDocument)).thenReturn(snapshot)
    stubTransaction(transaction)
    val closed =
        vote.copy(status = VoteStatus.CLOSED, closingTime = timestamp, winningOptionId = "option-2")

    repository.createVote(closed, {}, ::unexpectedFailure)

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
  fun createVoteWithExistingIdFailsWithoutWriting() {
    val transaction = mock(Transaction::class.java)
    val snapshot = document("vote-1", voteData)
    `when`(snapshot.exists()).thenReturn(true)
    `when`(transaction.get(voteDocument)).thenReturn(snapshot)
    stubTransaction(transaction)
    var failure: Exception? = null

    repository.createVote(vote, { fail("Existing vote must not be replaced") }, { failure = it })

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
  fun existingBallotCannotBecomeAttachedToReplacementVoteWithDifferentOptions() {
    val originalBallot = VoteBallot("user-1", "option-1", timestamp)
    doReturn(successfulTask<Void>(null)).`when`(ballotDocument).set(any())
    repository.submitBallot("trip-1", "vote-1", originalBallot, {}, ::unexpectedFailure)
    val transaction = mock(Transaction::class.java)
    val existing = document("vote-1", voteData)
    `when`(existing.exists()).thenReturn(true)
    `when`(transaction.get(voteDocument)).thenReturn(existing)
    stubTransaction(transaction)
    val replacement = vote.copy(options = listOf(VoteOption("new-option", "Beach")))
    var failure: Exception? = null

    repository.createVote(replacement, { fail("Must not replace vote options") }, { failure = it })

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
  fun getVotesMapsNestedOptionsAndUsesTripAndDocumentIds() {
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
    var result: List<Vote>? = null

    repository.getVotes("trip-1", { result = it }, ::unexpectedFailure)

    assertEquals(listOf(vote, second), result)
    verify(trips).document("trip-1")
    verify(trip).collection("votes")
  }

  @Test
  fun getVotesReturnsEmptyListForTripWithoutVotes() {
    doReturn(successfulTask(query())).`when`(votes).get()
    var result: List<Vote>? = null

    repository.getVotes("trip-1", { result = it }, ::unexpectedFailure)

    assertEquals(emptyList<Vote>(), result)
  }

  @Test
  fun getVotesReportsMalformedDataWithoutPartialSuccess() {
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
      var error: Exception? = null

      repository.getVotes("trip-1", { fail("Must not return partial votes") }, { error = it })

      assertTrue(error is IllegalArgumentException)
    }
  }

  @Test
  fun changingChoiceReplacesTheSameUserBallotDocument() {
    doReturn(successfulTask<Void>(null)).`when`(ballotDocument).set(any())
    val initial = VoteBallot("user-1", "option-1", timestamp)
    val updated = initial.copy(optionId = "option-2", updatedAt = Timestamp(200, 0))
    var completions = 0

    repository.submitBallot("trip-1", "vote-1", initial, { completions++ }, ::unexpectedFailure)
    repository.submitBallot("trip-1", "vote-1", updated, { completions++ }, ::unexpectedFailure)

    verify(ballots, times(2)).document("user-1")
    verify(ballotDocument).set(ballotData(initial))
    verify(ballotDocument).set(ballotData(updated))
    verify(ballots, never()).document()
    assertEquals(2, completions)
  }

  @Test
  fun differentUsersWriteSeparateBallotDocuments() {
    val otherDocument = mock(DocumentReference::class.java)
    `when`(ballots.document("user-2")).thenReturn(otherDocument)
    doReturn(successfulTask<Void>(null)).`when`(ballotDocument).set(any())
    doReturn(successfulTask<Void>(null)).`when`(otherDocument).set(any())
    val first = VoteBallot("user-1", "option-1", timestamp)
    val second = first.copy(userId = "user-2")

    repository.submitBallot("trip-1", "vote-1", first, {}, ::unexpectedFailure)
    repository.submitBallot("trip-1", "vote-1", second, {}, ::unexpectedFailure)

    verify(ballotDocument).set(ballotData(first))
    verify(otherDocument).set(ballotData(second))
  }

  @Test
  fun firestoreErrorsArePropagatedUnchangedForReadsAndWrites() {
    val error =
        FirebaseFirestoreException("Denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)
    doReturn(failedTask<Unit>(error)).`when`(db).runTransaction(any<Transaction.Function<Unit>>())
    doReturn(failedTask<QuerySnapshot>(error)).`when`(votes).get()
    doReturn(failedTask<Void>(error)).`when`(ballotDocument).set(any())
    val failures = mutableListOf<Exception>()

    repository.createVote(vote, { fail("Write should fail") }, failures::add)
    repository.getVotes("trip-1", { fail("Read should fail") }, failures::add)
    repository.submitBallot(
        "trip-1",
        "vote-1",
        VoteBallot("user-1", "option-1", timestamp),
        { fail("Write should fail") },
        failures::add,
    )

    assertEquals(3, failures.size)
    failures.forEach { assertSame(error, it) }
  }

  @Test
  fun synchronousFirestoreErrorsUseFailureCallback() {
    val error = IllegalStateException("Firestore unavailable")
    `when`(db.collection("trips")).thenThrow(error)
    var failure: Exception? = null

    repository.createVote(vote, { fail("Write should fail") }, { failure = it })

    assertSame(error, failure)
  }

  @Test
  fun invalidDocumentIdsAreReportedBeforeFirestoreAccess() {
    var failures = 0
    repository.createVote(vote.copy(uid = "bad/id"), { fail() }, { failures++ })
    repository.getVotes("", { fail() }, { failures++ })
    repository.submitBallot(
        "trip-1",
        "vote-1",
        VoteBallot("bad/id", "option-1", timestamp),
        { fail() },
        { failures++ },
    )
    repository.observeBallots("trip-1", "bad/id", { fail() }, { failures++ }).remove()

    assertEquals(4, failures)
    verifyNoInteractions(db)
  }

  @Test
  fun observeBallotsEmitsInitialChangesAndRemovalsAndCanBeRemoved() {
    val registration = mock(ListenerRegistration::class.java)
    `when`(ballots.addSnapshotListener(any<EventListener<QuerySnapshot>>()))
        .thenReturn(registration)
    val changes = mutableListOf<List<VoteBallot>>()
    val subscription =
        repository.observeBallots("trip-1", "vote-1", changes::add, ::unexpectedFailure)
    val listener = capturedListener()
    val first = VoteBallot("user-1", "option-1", timestamp)
    val updated = first.copy(optionId = "option-2", updatedAt = Timestamp(200, 0))

    listener.onEvent(query(), null)
    listener.onEvent(query(document("user-1", ballotData(first))), null)
    listener.onEvent(query(document("user-1", ballotData(updated))), null)
    listener.onEvent(query(), null)
    subscription.remove()

    assertEquals(listOf(emptyList(), listOf(first), listOf(updated), emptyList()), changes)
    verify(registration).remove()
  }

  @Test
  fun observeBallotsUsesDocumentUserIdAndReportsMalformedSnapshots() {
    `when`(ballots.addSnapshotListener(any<EventListener<QuerySnapshot>>()))
        .thenReturn(mock(ListenerRegistration::class.java))
    val changes = mutableListOf<List<VoteBallot>>()
    val errors = mutableListOf<Exception>()
    repository.observeBallots("trip-1", "vote-1", changes::add, errors::add)
    val listener = capturedListener()
    val ballot = VoteBallot("user-1", "option-1", timestamp)

    listener.onEvent(query(document("user-1", ballotData(ballot) - "userId")), null)
    listener.onEvent(query(document("user-1", ballotData(ballot) - "optionId")), null)
    listener.onEvent(
        query(document("user-1", ballotData(ballot) + ("updatedAt" to "invalid"))),
        null,
    )
    listener.onEvent(null, null)

    assertEquals(listOf(listOf(ballot)), changes)
    assertEquals(3, errors.size)
    errors.forEach { assertTrue(it is IllegalArgumentException) }
  }

  @Test
  fun observeBallotsPropagatesListenerErrorWithoutEmittingData() {
    `when`(ballots.addSnapshotListener(any<EventListener<QuerySnapshot>>()))
        .thenReturn(mock(ListenerRegistration::class.java))
    val error =
        FirebaseFirestoreException("Unavailable", FirebaseFirestoreException.Code.UNAVAILABLE)
    var failure: Exception? = null
    repository.observeBallots(
        "trip-1",
        "vote-1",
        { fail("Must not emit on failure") },
        { failure = it },
    )

    capturedListener().onEvent(null, error)

    assertSame(error, failure)
  }

  @Test
  fun observeBallotsReportsListenerRegistrationFailure() {
    val error = IllegalStateException("Unable to register")
    `when`(ballots.addSnapshotListener(any<EventListener<QuerySnapshot>>())).thenThrow(error)
    var failure: Exception? = null

    val subscription = repository.observeBallots("trip-1", "vote-1", { fail() }, { failure = it })
    subscription.remove()

    assertSame(error, failure)
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

  // Execute Task callbacks synchronously so these JUnit tests do not depend on an Android Looper.
  @Suppress("UNCHECKED_CAST")
  private fun <T> successfulTask(result: T?): Task<T> {
    val task = mock(Task::class.java) as Task<T>
    `when`(task.addOnSuccessListener(any<OnSuccessListener<T>>())).thenAnswer {
      it.getArgument<OnSuccessListener<T>>(0).onSuccess(result)
      task
    }
    `when`(task.addOnFailureListener(any<OnFailureListener>())).thenReturn(task)
    return task
  }

  @Suppress("UNCHECKED_CAST")
  private fun <T> failedTask(error: Exception): Task<T> {
    val task = mock(Task::class.java) as Task<T>
    `when`(task.addOnSuccessListener(any<OnSuccessListener<T>>())).thenReturn(task)
    `when`(task.addOnFailureListener(any<OnFailureListener>())).thenAnswer {
      it.getArgument<OnFailureListener>(0).onFailure(error)
      task
    }
    return task
  }

  private fun unexpectedFailure(error: Exception) {
    throw AssertionError("Unexpected repository failure", error)
  }
}
