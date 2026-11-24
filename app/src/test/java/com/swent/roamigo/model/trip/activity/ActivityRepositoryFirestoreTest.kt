// AI assistance was used for implementation support.
package com.swent.roamigo.model.trip.activity

import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Transaction
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.trip.Activity
import com.swent.roamigo.model.trip.ActivityType
import com.swent.roamigo.model.trip.toFirestoreData
import com.swent.roamigo.model.trip.voting.Vote
import com.swent.roamigo.model.trip.voting.VoteStatus
import com.swent.roamigo.model.trip.voting.toFirestoreData as toVoteFirestoreData
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/** Controlled SDK clients; no Firebase app, network connection, or emulator is used. */
@OptIn(ExperimentalCoroutinesApi::class)
class ActivityRepositoryFirestoreTest {
  private val db = mock(FirebaseFirestore::class.java)
  private val trips = mock(CollectionReference::class.java)
  private val tripDocument = mock(DocumentReference::class.java)
  private val activities = mock(CollectionReference::class.java)
  private val document = mock(DocumentReference::class.java)
  private val votes = mock(CollectionReference::class.java)
  private val voteDocument = mock(DocumentReference::class.java)
  private val transaction = mock(Transaction::class.java)
  private val registration = mock(ListenerRegistration::class.java)
  private var userId: String? = "alice"
  private var stored: Activity? = null
  private var storedVote: Vote? = null
  private var transactionFailure: Exception? = null
  private var listener: EventListener<QuerySnapshot>? = null
  private val writes = mutableListOf<Map<String, Any?>>()

  private val draft =
      ActivityDraft(
          name = "Visit EPFL",
          description = "Rolex Learning Center",
          location = TripLocation(46.5197, 6.5668, "EPFL", "CH"),
          startTime = Instant.ofEpochSecond(1_900_000_000),
          endTime = Instant.ofEpochSecond(1_900_003_600),
      )

  private val repository = ActivityRepositoryFirestore(db) { userId }

  @Before
  fun setUp() {
    `when`(db.collection("trips")).thenReturn(trips)
    `when`(trips.document("trip-1")).thenReturn(tripDocument)
    `when`(tripDocument.collection("activities")).thenReturn(activities)
    `when`(activities.document("activity-1")).thenReturn(document)
    `when`(tripDocument.collection("votes")).thenReturn(votes)
    `when`(votes.document("suggestion-activity-1")).thenReturn(voteDocument)
    `when`(transaction.get(voteDocument)).thenAnswer {
      val vote = storedVote
      mock(DocumentSnapshot::class.java).also {
        `when`(it.exists()).thenReturn(vote != null)
        `when`(it.id).thenReturn(vote?.uid.orEmpty())
        `when`(it.data).thenReturn(vote?.toVoteFirestoreData())
      }
    }
    `when`(transaction.get(document)).thenAnswer { snapshot(stored) }
    doAnswer {
          writes += it.getArgument<Map<String, Any?>>(1)
          transaction
        }
        .`when`(transaction)
        .set(any(DocumentReference::class.java), anyMap<String, Any?>())
    doAnswer {
          listener = it.getArgument(0)
          registration
        }
        .`when`(activities)
        .addSnapshotListener(any<EventListener<QuerySnapshot>>())
    doAnswer {
          // The transaction function runs here, as Firestore would run it.
          val function = it.getArgument<Transaction.Function<Any?>>(0)
          transactionFailure?.let { error ->
            return@doAnswer failedTask<Any?>(error)
          }
          successfulTask(function.apply(transaction))
        }
        .`when`(db)
        .runTransaction(any<Transaction.Function<Any?>>())
  }

  @Test
  fun create_storesAnAcceptedActivityAuthoredByTheCurrentUser() = runTest {
    val created = repository.createActivity("trip-1", "activity-1", draft)

    assertEquals("activity-1", created.uid)
    assertEquals("trip-1", created.tripId)
    assertEquals(ActivityType.ACCEPTED, created.activityType)
    assertEquals("alice", created.createdByUserId)
    assertEquals(1, writes.size)
    assertEquals("ACCEPTED", writes.single()["activityType"])
    assertEquals("alice", writes.single()["createdByUserId"])
    assertEquals("Visit EPFL", writes.single()["name"])
  }

  @Test
  fun createSuggestion_writesAnUnscheduledSuggestionWithRepositoryMetadata() = runTest {
    val before = Timestamp.now()
    val created =
        repository.createSuggestionWithVote(
            "trip-1",
            "activity-1",
            draft.copy(description = null, startTime = null, endTime = null),
        )

    val (activity, vote) = created
    assertEquals("activity-1", activity.uid)
    assertEquals("trip-1", activity.tripId)
    assertEquals(ActivityType.SUGGESTION, activity.activityType)
    assertEquals("alice", activity.createdByUserId)
    assertTrue(activity.creationTime >= before && activity.creationTime <= Timestamp.now())
    assertNull(activity.startTime)
    assertNull(activity.endTime)
    assertNull(activity.description)
    assertEquals("suggestion-activity-1", vote.uid)
    assertEquals(vote.uid, activity.linkedVoteId)
    assertEquals(activity.uid, vote.suggestionId)
    assertEquals(activity.tripId, vote.tripId)
    assertEquals(activity.createdByUserId, vote.createdByUserId)
    assertEquals(activity.creationTime, vote.creationTime)
    assertEquals(activity.name, vote.question)
    assertEquals(listOf("YES", "NO"), vote.options.map { it.uid })
    assertEquals(VoteStatus.OPEN, vote.status)
    assertEquals(1, vote.round)
    assertNull(vote.parentVoteId)
    assertNull(vote.closingTime)
    assertNull(vote.winningOptionId)
    assertNull(vote.resolvedOptionId)
    assertNull(vote.resolvedByUserId)
    assertEquals(listOf(activity.toFirestoreData(), vote.toVoteFirestoreData()), writes)
    val order = org.mockito.Mockito.inOrder(transaction)
    order.verify(transaction).get(document)
    order.verify(transaction).get(voteDocument)
    order.verify(transaction).set(document, activity.toFirestoreData())
    order.verify(transaction).set(voteDocument, vote.toVoteFirestoreData())
  }

  @Test
  fun createSuggestion_retriesReturnTheOriginalAndRejectAnotherAuthor() = runTest {
    val first = repository.createSuggestionWithVote("trip-1", "activity-1", draft)
    stored = first.activity
    storedVote = first.vote

    assertEquals(first, repository.createSuggestionWithVote("trip-1", "activity-1", draft))
    assertEquals(
        first,
        repository.createSuggestionWithVote("trip-1", "activity-1", draft.copy(name = "Changed")),
    )

    val invalid = draft.copy(name = " ")
    assertEquals(first, repository.createSuggestionWithVote("trip-1", "activity-1", invalid))

    userId = "bob"
    val error = runCatching { repository.createSuggestionWithVote("trip-1", "activity-1", invalid) }
    assertTrue(error.exceptionOrNull() is ActivityCreationException.PermissionDenied)
    assertEquals(listOf(first.activity.toFirestoreData(), first.vote.toVoteFirestoreData()), writes)
  }

  @Test
  fun createSuggestion_returnsTheStoredClosedVoteEvenAfterARevote() = runTest {
    val pair = repository.createSuggestionWithVote("trip-1", "activity-1", draft)
    val closed = pair.vote.copy(status = VoteStatus.CLOSED, nextRoundVoteId = "round-2")
    stored = pair.activity
    storedVote = closed

    assertEquals(draft.startTime?.epochSecond, pair.activity.startTime?.seconds)
    assertEquals(draft.endTime?.epochSecond, pair.activity.endTime?.seconds)
    assertEquals(
        pair.copy(vote = closed),
        repository.createSuggestionWithVote("trip-1", "activity-1", draft.copy(name = "")),
    )
    assertEquals(2, writes.size)
  }

  @Test
  fun createSuggestion_rejectsPartialPairsAndIncompatibleLinkageWithoutWriting() = runTest {
    val pair = repository.createSuggestionWithVote("trip-1", "activity-1", draft)
    writes.clear()
    val invalidPairs =
        listOf(
            pair.activity to null,
            null to pair.vote,
            pair.activity.copy(linkedVoteId = "other") to pair.vote,
            pair.activity to pair.vote.copy(uid = "other"),
            pair.activity to pair.vote.copy(tripId = "other"),
            pair.activity to pair.vote.copy(suggestionId = null),
            pair.activity to pair.vote.copy(suggestionId = "other"),
            pair.activity to pair.vote.copy(question = "other"),
            pair.activity to pair.vote.copy(options = pair.vote.options.reversed()),
            pair.activity to pair.vote.copy(parentVoteId = "other", round = 2),
        )
    for ((activity, vote) in invalidPairs) {
      stored = activity
      storedVote = vote
      val error = runCatching { repository.createSuggestionWithVote("trip-1", "activity-1", draft) }
      assertTrue(
          error.toString(),
          error.exceptionOrNull() is ActivityCreationException.PairConflict,
      )
    }
    stored = pair.activity
    storedVote = pair.vote.copy(createdByUserId = "bob")
    val ownership = runCatching {
      repository.createSuggestionWithVote("trip-1", "activity-1", draft)
    }
    assertTrue(ownership.exceptionOrNull() is ActivityCreationException.PermissionDenied)
    assertTrue(writes.isEmpty())
  }

  @Test
  fun createSuggestion_validatesIdsAndAuthenticationBeforeReading() = runTest {
    userId = null
    val invalid = runCatching { repository.createSuggestionWithVote("trip-1", "a/b", draft) }
    assertTrue(invalid.exceptionOrNull() is IllegalArgumentException)
    val signedOut = runCatching {
      repository.createSuggestionWithVote("trip-1", "activity-1", draft)
    }
    assertTrue(signedOut.exceptionOrNull() is ActivityCreationException.NotAuthenticated)
    verify(db, never()).runTransaction(any<Transaction.Function<Any?>>())
  }

  @Test
  fun createActivity_rejectsAnExistingSuggestionByTheSameAuthor() = runTest {
    stored =
        draft
            .copy(startTime = null, endTime = null)
            .toActivity("activity-1", "trip-1", "alice", Timestamp(900, 0), ActivityType.SUGGESTION)

    val error = runCatching {
      repository.createActivity("trip-1", "activity-1", draft.copy(name = " "))
    }

    val conflict = error.exceptionOrNull() as ActivityCreationException.TypeConflict
    assertEquals(ActivityType.ACCEPTED, conflict.requested)
    assertEquals(ActivityType.SUGGESTION, conflict.existing)
    assertTrue(writes.isEmpty())
  }

  @Test
  fun createSuggestion_rejectsAnExistingAcceptedActivityByTheSameAuthor() = runTest {
    stored = draft.toActivity("activity-1", "trip-1", "alice", Timestamp(900, 0))

    val error = runCatching {
      repository.createSuggestionWithVote("trip-1", "activity-1", draft.copy(name = " "))
    }

    val conflict = error.exceptionOrNull() as ActivityCreationException.TypeConflict
    assertEquals(ActivityType.SUGGESTION, conflict.requested)
    assertEquals(ActivityType.ACCEPTED, conflict.existing)
    assertTrue(writes.isEmpty())
  }

  @Test
  fun createSuggestion_validatesMissingDocumentsWithoutWriting() = runTest {
    val invalid =
        listOf(
            draft.copy(startTime = null) to ActivityDraftError.EndWithoutStart,
            draft.copy(endTime = draft.startTime!!.minusSeconds(1)) to
                ActivityDraftError.EndBeforeStart,
            draft.copy(name = " ") to ActivityDraftError.BlankName,
            draft.copy(location = draft.location.copy(latitude = 91.0)) to
                ActivityDraftError.InvalidLocationCoordinates,
        )

    invalid.forEach { (invalidDraft, expected) ->
      val result = runCatching {
        repository.createSuggestionWithVote("trip-1", "activity-1", invalidDraft)
      }
      assertEquals(
          listOf(expected),
          (result.exceptionOrNull() as ActivityCreationException.Validation).errors,
      )
    }
    verify(transaction, times(invalid.size)).get(document)
    assertTrue(writes.isEmpty())
  }

  @Test
  fun createSuggestion_propagatesCancellation() = runTest {
    val cancellation = CancellationException("cancelled")
    transactionFailure = cancellation

    val error = runCatching { repository.createSuggestionWithVote("trip-1", "activity-1", draft) }

    assertSame(cancellation, error.exceptionOrNull())
  }

  @Test
  fun create_withTheSameIdByTheSameAuthorReturnsTheStoredActivityWithoutWriting() = runTest {
    val existing = draft.toActivity("activity-1", "trip-1", "alice", Timestamp(900, 0))
    stored = existing

    val result = repository.createActivity("trip-1", "activity-1", draft.copy(name = "Other"))

    assertEquals(existing, result)
    assertEquals(
        existing,
        repository.createActivity("trip-1", "activity-1", draft.copy(startTime = null)),
    )
    assertTrue(writes.isEmpty())
  }

  @Test
  fun create_withTheSameIdByAnotherAuthorIsDenied() = runTest {
    stored = draft.toActivity("activity-1", "trip-1", "bob", Timestamp(900, 0))

    val error = runCatching { repository.createActivity("trip-1", "activity-1", draft) }

    assertTrue(error.exceptionOrNull() is ActivityCreationException.PermissionDenied)
    assertTrue(writes.isEmpty())
  }

  @Test
  fun create_whenSignedOutFailsBeforeAnyRequest() = runTest {
    userId = null

    val error = runCatching { repository.createActivity("trip-1", "activity-1", draft) }

    assertTrue(error.exceptionOrNull() is ActivityCreationException.NotAuthenticated)
    verify(db, never()).runTransaction(any<Transaction.Function<Any?>>())
  }

  @Test
  fun create_withAnInvalidDraftAndNewIdFailsWithoutWriting() = runTest {
    val error = runCatching {
      repository.createActivity("trip-1", "activity-1", draft.copy(name = " "))
    }

    val validation = error.exceptionOrNull() as ActivityCreationException.Validation
    assertEquals(listOf(ActivityDraftError.BlankName), validation.errors)
    verify(transaction).get(document)
    assertTrue(writes.isEmpty())
  }

  @Test
  fun create_withANewIdButNoStartTimeFailsWithoutWriting() = runTest {
    val error = runCatching {
      repository.createActivity(
          "trip-1",
          "activity-1",
          draft.copy(startTime = null, endTime = null),
      )
    }

    val validation = error.exceptionOrNull() as ActivityCreationException.Validation
    assertEquals(listOf(ActivityDraftError.MissingStartTime), validation.errors)
    verify(transaction).get(document)
    assertTrue(writes.isEmpty())
  }

  @Test
  fun create_mapsFirestoreFailuresToCreationExceptions() = runTest {
    val expected =
        mapOf(
            Code.UNAUTHENTICATED to ActivityCreationException.NotAuthenticated::class.java,
            Code.PERMISSION_DENIED to ActivityCreationException.PermissionDenied::class.java,
            Code.UNAVAILABLE to ActivityCreationException.Network::class.java,
            Code.DEADLINE_EXCEEDED to ActivityCreationException.Network::class.java,
            Code.ABORTED to ActivityCreationException.Unknown::class.java,
        )

    expected.forEach { (code, type) ->
      transactionFailure = FirebaseFirestoreException("failure", code)

      val error = runCatching { repository.createActivity("trip-1", "activity-1", draft) }

      assertEquals(code.name, type, error.exceptionOrNull()?.javaClass)
      val pairError = runCatching {
        repository.createSuggestionWithVote("trip-1", "activity-1", draft)
      }
      assertEquals(code.name, type, pairError.exceptionOrNull()?.javaClass)
    }
  }

  @Test
  fun create_wrapsUnexpectedFailuresAndKeepsTheCause() = runTest {
    val cause = IOException("disk")
    transactionFailure = cause

    val error = runCatching { repository.createActivity("trip-1", "activity-1", draft) }

    assertTrue(error.exceptionOrNull() is ActivityCreationException.Unknown)
    assertEquals(cause, error.exceptionOrNull()?.cause)
  }

  @Test
  fun create_withAnInvalidIdIsACallerErrorAndSendsNoRequest() = runTest {
    listOf("trip-1" to "a/b", "trip-1" to " ", "trip-1" to "..", " " to "activity-1").forEach {
        (tripId, activityId) ->
      val error = runCatching { repository.createActivity(tripId, activityId, draft) }

      assertTrue(error.toString(), error.exceptionOrNull() is IllegalArgumentException)
    }
    verify(db, never()).runTransaction(any<Transaction.Function<Any?>>())
  }

  @Test
  fun create_doesNotWrapCancellation() = runTest {
    transactionFailure = CancellationException("cancelled")

    val error = runCatching { repository.createActivity("trip-1", "activity-1", draft) }

    assertTrue(error.exceptionOrNull() is CancellationException)
  }

  @Test
  fun observe_emitsEveryListSnapshotMappedFromItsDocuments() = runTest {
    val activity = draft.toActivity("activity-1", "trip-1", "alice", Timestamp(900, 0))
    val suggestion =
        draft
            .copy(startTime = null, endTime = null)
            .toActivity(
                "suggestion-1",
                "trip-1",
                "alice",
                Timestamp(900, 0),
                ActivityType.SUGGESTION,
            )
            .copy(linkedVoteId = "suggestion-suggestion-1")
    val emitted = mutableListOf<List<Activity>>()
    val job =
        launch(UnconfinedTestDispatcher(testScheduler)) {
          repository.observeActivities("trip-1").toList(emitted)
        }

    listener!!.onEvent(querySnapshotOf(listOf(activity)), null)
    listener!!.onEvent(querySnapshotOf(listOf(activity, suggestion)), null)
    listener!!.onEvent(querySnapshotOf(emptyList()), null)

    assertEquals(listOf(listOf(activity), listOf(activity, suggestion), emptyList()), emitted)
    job.cancel()
    verify(registration).remove()
  }

  @Test
  fun observe_failsWhenFirestoreReportsAnError() = runTest {
    val failure = FirebaseFirestoreException("denied", Code.PERMISSION_DENIED)
    val result = launchObserver()

    listener!!.onEvent(null, failure)

    assertEquals(failure, result())
    verify(registration).remove()
  }

  @Test
  fun observe_failsWhenAStoredActivityIsMalformed() = runTest {
    val activity = draft.toActivity("activity-1", "trip-1", "alice", Timestamp(900, 0))
    val result = launchObserver()
    val malformed = snapshot(activity, data = activity.toFirestoreData() - "name")

    listener!!.onEvent(querySnapshot(listOf(malformed)), null)

    assertTrue(result() is IllegalArgumentException)
  }

  @Test
  fun observe_failsWhenTheSnapshotIsMissing() = runTest {
    val result = launchObserver()

    listener!!.onEvent(null, null)

    assertTrue(result() is IllegalArgumentException)
  }

  @Test
  fun observe_withAnInvalidTripIdFailsOnCollection() = runTest {
    val error = runCatching { repository.observeActivities(" ").first() }

    assertTrue(error.exceptionOrNull() is IllegalArgumentException)
  }

  /** Starts collecting and returns a function giving the error that ended the collection. */
  private fun kotlinx.coroutines.test.TestScope.launchObserver(): () -> Throwable? {
    var failure: Throwable? = null
    launch(UnconfinedTestDispatcher(testScheduler)) {
      try {
        repository.observeActivities("trip-1").collect {}
      } catch (error: Throwable) {
        failure = error
      }
    }
    return { failure }
  }

  private fun snapshot(
      activity: Activity?,
      data: Map<String, Any?>? = activity?.toFirestoreData(),
  ): DocumentSnapshot =
      mock(DocumentSnapshot::class.java).also {
        `when`(it.exists()).thenReturn(activity != null)
        `when`(it.id).thenReturn(activity?.uid.orEmpty())
        `when`(it.data).thenReturn(data)
        `when`(it.reference).thenReturn(document)
      }

  private fun querySnapshot(documents: List<DocumentSnapshot>): QuerySnapshot =
      mock(QuerySnapshot::class.java).also { `when`(it.documents).thenReturn(documents) }

  private fun querySnapshotOf(activities: List<Activity>): QuerySnapshot =
      querySnapshot(activities.map { snapshot(it) })

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
