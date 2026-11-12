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
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SnapshotMetadata
import com.google.firebase.firestore.Source
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
  private val resolutionCalls = mutableListOf<List<String>>()
  private val resolutionService =
      object : VoteResolutionService {
        override suspend fun createRevote(tripId: String, parentVoteId: String, voteId: String) {
          resolutionCalls += listOf("revote", tripId, parentVoteId, voteId)
        }

        override suspend fun resolveByOwner(tripId: String, voteId: String, optionId: String) {
          resolutionCalls += listOf("owner", tripId, voteId, optionId)
        }
      }
  private val repository: VoteRepository = VoteRepositoryFirestore(db, resolutionService)
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

  private fun serverTie(
      extra: Map<String, Any?> = emptyMap(),
      end: Timestamp = Timestamp(4102444800L, 0),
  ): DocumentSnapshot {
    val snapshot = document(vote.uid, voteData + mapOf("status" to "CLOSED") + extra)
    doReturn(successfulTask(snapshot)).`when`(voteDocument).get(Source.SERVER)
    doReturn(successfulTask(snapshot)).`when`(voteDocument).get()
    val final =
        query(
            document("one", mapOf("optionId" to "option-1", "updatedAt" to timestamp)),
            document("two", mapOf("optionId" to "option-2", "updatedAt" to timestamp)),
        )
    `when`(final.metadata).thenReturn(mock(SnapshotMetadata::class.java))
    doReturn(successfulTask(final)).`when`(ballots).get(Source.SERVER)
    val tripSnapshot = mock(DocumentSnapshot::class.java)
    `when`(tripSnapshot.getString("ownerId")).thenReturn("trip-owner")
    `when`(tripSnapshot.getTimestamp("endDate")).thenReturn(end)
    doReturn(successfulTask(tripSnapshot)).`when`(trip).get(Source.SERVER)
    return tripSnapshot
  }

  @Test
  fun ownerDecisionUsesTripOwnerAndDelegatesToTrustedServiceWithoutDirectWrites() = runTest {
    serverTie()
    repository.resolveByOwner("trip-1", "vote-1", "option-2", "trip-owner")
    assertEquals(listOf(listOf("owner", "trip-1", "vote-1", "option-2")), resolutionCalls)
    verify(voteDocument).get(Source.SERVER)
    verify(voteDocument, never()).get()
    verify(ballots).get(Source.SERVER)
    verify(trip).get(Source.SERVER)
    verify(db, never()).runTransaction(any<Transaction.Function<Unit>>())
    verify(ballotDocument, never()).set(any())
    assertTrue(
        runCatching { repository.resolveByOwner("trip-1", "vote-1", "option-1", "user-1") }
            .isFailure
    )
    assertTrue(
        runCatching { repository.resolveByOwner("trip-1", "vote-1", "missing", "trip-owner") }
            .isFailure
    )
    assertEquals(1, resolutionCalls.size)
  }

  @Test
  fun nonTiedOptionAndUnconfirmedBallotsDoNotReachTrustedService() = runTest {
    serverTie(
        mapOf(
            "options" to
                (vote.options + VoteOption("option-3", "Pizza")).map {
                  mapOf("uid" to it.uid, "name" to it.name)
                }
        )
    )
    assertTrue(
        runCatching { repository.resolveByOwner("trip-1", "vote-1", "option-3", "trip-owner") }
            .isFailure
    )
    val pending = query()
    val metadata = mock(SnapshotMetadata::class.java)
    `when`(metadata.hasPendingWrites()).thenReturn(true)
    `when`(pending.metadata).thenReturn(metadata)
    doReturn(successfulTask(pending)).`when`(ballots).get(Source.SERVER)
    assertTrue(
        runCatching { repository.resolveByOwner("trip-1", "vote-1", "option-1", "trip-owner") }
            .isFailure
    )
    assertTrue(resolutionCalls.isEmpty())
  }

  @Test
  fun missingOriginalAndCalendarEndedTripCannotCreateRevote() = runTest {
    serverTie(end = Timestamp(1, 0))
    val next = vote.copy(uid = "round2", parentVoteId = vote.uid, round = 2)
    assertTrue(runCatching { repository.createRevote(next) }.isFailure)
    assertTrue(
        runCatching { repository.resolveByOwner("trip-1", "vote-1", "option-1", "trip-owner") }
            .isFailure
    )
    doReturn(successfulTask(mock(DocumentSnapshot::class.java)))
        .`when`(voteDocument)
        .get(Source.SERVER)
    assertTrue(runCatching { repository.createRevote(next) }.isFailure)
    assertTrue(resolutionCalls.isEmpty())
  }

  @Test
  fun resolvedOrDelegatedTieCannotBeResolvedAgain() = runTest {
    for (extra in
        listOf(mapOf("resolvedOptionId" to "option-1"), mapOf("nextRoundVoteId" to "round2"))) {
      serverTie(extra)
      assertTrue(
          runCatching { repository.resolveByOwner("trip-1", "vote-1", "option-2", "trip-owner") }
              .isFailure
      )
    }
    assertTrue(resolutionCalls.isEmpty())
  }

  @Test
  fun revoteDelegatesOnlyIdsToTrustedServiceAndKeepsSourceUnchanged() = runTest {
    serverTie(
        mapOf(
            "options" to
                (vote.options + VoteOption("option-3", "Pizza")).map {
                  mapOf("uid" to it.uid, "name" to it.name)
                }
        )
    )
    val next = vote.copy(uid = "round2", parentVoteId = vote.uid, round = 2)
    repository.createRevote(next)
    assertEquals(listOf(listOf("revote", "trip-1", "vote-1", "round2")), resolutionCalls)
    verify(voteDocument).get(Source.SERVER)
    verify(voteDocument, never()).get()
    verify(ballots).get(Source.SERVER)
    verify(trip).get(Source.SERVER)
    verify(db, never()).runTransaction(any<Transaction.Function<Unit>>())
    verify(ballotDocument, never()).set(any())
    assertTrue(
        runCatching { repository.createRevote(next.copy(options = next.options.reversed())) }
            .isFailure
    )
    assertEquals(1, resolutionCalls.size)
  }

  @Test
  fun revoteRejectsStaleLinkChangedQuestionAndWrongRoundBeforeCallingService() = runTest {
    val next = vote.copy(uid = "round2", parentVoteId = vote.uid, round = 2)
    serverTie()
    for (invalid in
        listOf(
            next.copy(parentVoteId = null),
            next.copy(uid = vote.uid),
            next.copy(status = VoteStatus.CLOSED),
            next.copy(question = "Changed?"),
            next.copy(round = 3),
        )) {
      assertTrue(runCatching { repository.createRevote(invalid) }.isFailure)
    }
    serverTie(mapOf("nextRoundVoteId" to "other-child"))
    assertTrue(runCatching { repository.createRevote(next) }.isFailure)
    serverTie(mapOf("resolvedOptionId" to "option-1"))
    assertTrue(runCatching { repository.createRevote(next) }.isFailure)
    assertTrue(resolutionCalls.isEmpty())
    // Retrying the same linked child after an ambiguous response is allowed.
    serverTie(mapOf("nextRoundVoteId" to next.uid))
    repository.createRevote(next)
    assertEquals(listOf(listOf("revote", "trip-1", "vote-1", "round2")), resolutionCalls)
  }

  @Test
  fun readingResolutionMetadataPreservesRoundLinksAndOwnerIdentity() = runTest {
    val metadata =
        mapOf(
            "parentVoteId" to "previous",
            "nextRoundVoteId" to "next",
            "round" to 2L,
            "resolvedOptionId" to "option-1",
            "resolvedByUserId" to "trip-owner",
        )
    doReturn(successfulTask(document(vote.uid, voteData + metadata))).`when`(voteDocument).get()
    assertEquals(
        vote.copy(
            parentVoteId = "previous",
            nextRoundVoteId = "next",
            round = 2,
            resolvedOptionId = "option-1",
            resolvedByUserId = "trip-owner",
        ),
        repository.getVote("trip-1", "vote-1"),
    )
  }

  @Test
  fun independentPreflightReadsStartBeforeTheVoteReadCompletes() = runTest {
    serverTie()
    val snapshot = document(vote.uid, voteData + ("status" to "CLOSED"))
    val task = successfulTask(snapshot)
    `when`(task.isComplete).thenReturn(false)
    `when`(task.addOnCompleteListener(any<Executor>(), any<OnCompleteListener<DocumentSnapshot>>()))
        .thenReturn(task)
    doReturn(task).`when`(voteDocument).get(Source.SERVER)
    val job = async { repository.resolveByOwner("trip-1", "vote-1", "option-1", "trip-owner") }
    runCurrent()
    verify(ballots).get(Source.SERVER)
    verify(trip).get(Source.SERVER)
    assertFalse(job.isCompleted)
    assertTrue(resolutionCalls.isEmpty())
    @Suppress("UNCHECKED_CAST")
    val captor =
        ArgumentCaptor.forClass(OnCompleteListener::class.java)
            as ArgumentCaptor<OnCompleteListener<DocumentSnapshot>>
    verify(task).addOnCompleteListener(any<Executor>(), captor.capture())
    captor.value.onComplete(task)
    job.await()
    verify(voteDocument, times(1)).get(Source.SERVER)
    verify(voteDocument, never()).get()
    assertEquals(1, resolutionCalls.size)
  }

  @Test
  fun cachedFinalBallotsAndServerReadFailuresNeverReachTheCallable() = runTest {
    serverTie()
    val cached = query()
    val metadata = mock(SnapshotMetadata::class.java)
    `when`(metadata.isFromCache).thenReturn(true)
    `when`(cached.metadata).thenReturn(metadata)
    doReturn(successfulTask(cached)).`when`(ballots).get(Source.SERVER)
    assertTrue(
        runCatching { repository.resolveByOwner("trip-1", "vote-1", "option-1", "trip-owner") }
            .isFailure
    )
    serverTie()
    val failure = IllegalStateException("offline")
    doReturn(failedTask<DocumentSnapshot>(failure)).`when`(trip).get(Source.SERVER)
    val thrown = runCatching {
      repository.createRevote(vote.copy(uid = "child", parentVoteId = vote.uid, round = 2))
    }
        .exceptionOrNull()
    assertTrue(thrown is IllegalStateException)
    assertEquals(failure.message, thrown?.message)
    assertTrue(resolutionCalls.isEmpty())
  }

  @Test
  fun roundMetadataRejectsMalformedValuesAndInconsistentLinkage() = runTest {
    val invalid =
        listOf(
            mapOf("round" to 0L),
            mapOf("round" to -1L),
            mapOf("round" to 2147483648L),
            mapOf("round" to 1.5),
            mapOf("round" to 2.0),
            mapOf("round" to Double.NaN),
            mapOf("round" to "2"),
            mapOf("round" to null),
            mapOf("round" to 2L),
            mapOf("parentVoteId" to "parent"),
            mapOf("parentVoteId" to "parent", "round" to 1L),
            mapOf("parentVoteId" to "", "round" to 2L),
            mapOf("parentVoteId" to vote.uid, "round" to 2L),
        )
    for (metadata in invalid) {
      doReturn(successfulTask(document(vote.uid, voteData + metadata))).`when`(voteDocument).get()
      assertTrue(
          "Accepted $metadata",
          runCatching { repository.getVote("trip-1", "vote-1") }.exceptionOrNull()
              is IllegalArgumentException,
      )
    }
    doReturn(successfulTask(document(vote.uid, voteData + ("round" to 1L))))
        .`when`(voteDocument)
        .get()
    assertEquals(vote, repository.getVote("trip-1", "vote-1"))
  }

  @Test
  fun linkedRoundMetadataSurvivesSerializationAndLegacyShapeStaysUnchanged() = runTest {
    val transaction = mock(Transaction::class.java)
    `when`(transaction.get(voteDocument)).thenReturn(mock(DocumentSnapshot::class.java))
    stubTransaction(transaction)
    val linked = vote.copy(parentVoteId = "parent", round = 2)
    repository.createVote(linked)
    val data = voteData + mapOf("parentVoteId" to "parent", "round" to 2)
    verify(transaction).set(voteDocument, data)
    doReturn(successfulTask(document(vote.uid, data))).`when`(voteDocument).get()
    assertEquals(linked, repository.getVote("trip-1", "vote-1"))
    assertTrue(runCatching { repository.createVote(vote.copy(round = 2)) }.isFailure)
    assertTrue(runCatching { repository.createRevote(linked.copy(parentVoteId = null)) }.isFailure)
    // Ordinary vote serialization is also checked by createVoteWritesAllFieldsUnderItsTrip.
    serverTie(mapOf("parentVoteId" to "earlier", "round" to Int.MAX_VALUE.toLong()))
    assertTrue(
        runCatching {
          repository.createRevote(linked.copy(uid = "child", parentVoteId = vote.uid, round = 2))
        }
            .isFailure
    )
    assertTrue(resolutionCalls.isEmpty())
  }

  @Test
  fun resolutionUsesLocalCalendarAcrossDstAndTimezonesAndIgnoresStoredStatus() = runTest {
    for ((zoneName, endDate, stillActive, ended) in
        listOf(
            listOf("Europe/Zurich", "2026-10-25", "2026-10-25T22:30:00Z", "2026-10-25T23:00:00Z"),
            listOf("Europe/Zurich", "2026-03-29", "2026-03-29T21:30:00Z", "2026-03-29T22:00:00Z"),
            listOf(
                "America/New_York",
                "2026-11-01",
                "2026-11-02T04:30:00Z",
                "2026-11-02T05:00:00Z",
            ),
        )) {
      resolutionCalls.clear()
      val zone = java.time.ZoneId.of(zoneName)
      val end = java.time.LocalDate.parse(endDate).atStartOfDay(zone).toInstant()
      val tripSnapshot = serverTie(end = Timestamp(end.epochSecond, 0))
      `when`(tripSnapshot.getString("status")).thenReturn("COMPLETED")
      fun at(instant: String) =
          VoteRepositoryFirestore(
              db,
              resolutionService,
              java.time.Clock.fixed(java.time.Instant.parse(instant), zone),
          )
      at(stillActive).resolveByOwner("trip-1", "vote-1", "option-1", "trip-owner")
      assertEquals(1, resolutionCalls.size)
      assertTrue(
          runCatching { at(ended).resolveByOwner("trip-1", "vote-1", "option-1", "trip-owner") }
              .isFailure
      )
      assertEquals(1, resolutionCalls.size)
    }
  }

  @Test
  fun observeSelectedDocumentEmitsStatusAndDeletionAndCleansUp() = runTest {
    val registration = mock(ListenerRegistration::class.java)
    `when`(voteDocument.addSnapshotListener(any<EventListener<DocumentSnapshot>>()))
        .thenReturn(registration)
    val changes = mutableListOf<Vote?>()
    val job = launch { repository.observeVote("trip-1", "vote-1").collect { changes += it } }
    runCurrent()
    val listener = capturedDocumentListener()
    listener.onEvent(document(vote.uid, voteData), null)
    runCurrent()
    listener.onEvent(document(vote.uid, voteData + ("status" to "CLOSED")), null)
    runCurrent()
    listener.onEvent(mock(DocumentSnapshot::class.java), null)
    runCurrent()
    assertEquals(listOf(vote, vote.copy(status = VoteStatus.CLOSED), null), changes)
    verify(votes, never()).addSnapshotListener(any<EventListener<QuerySnapshot>>())
    job.cancel()
    runCurrent()
    verify(registration).remove()
  }

  @Test
  fun selectedDocumentPropagatesOwnListenerAndMappingErrors() = runTest {
    for (malformed in listOf(false, true)) {
      reset(voteDocument)
      val registration = mock(ListenerRegistration::class.java)
      `when`(voteDocument.addSnapshotListener(any<EventListener<DocumentSnapshot>>()))
          .thenReturn(registration)
      val result = async { runCatching { repository.observeVote("trip-1", "vote-1").first() } }
      runCurrent()
      val listener = capturedDocumentListener()
      val failure =
          FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)
      if (malformed) listener.onEvent(document(vote.uid, emptyMap()), null)
      else listener.onEvent(null, failure)
      val error = result.await().exceptionOrNull()
      if (malformed) assertTrue(error is IllegalArgumentException) else assertSame(failure, error)
      verify(registration).remove()
    }
  }

  @Test
  fun selectedDocumentRejectsInvalidIdsAndRegistrationFailure() = runTest {
    assertTrue(
        runCatching { repository.observeVote("trip-1", "bad/id").first() }.exceptionOrNull()
            is IllegalArgumentException
    )
    val failure = IllegalStateException("registration failed")
    `when`(voteDocument.addSnapshotListener(any<EventListener<DocumentSnapshot>>()))
        .thenThrow(failure)
    val actual = runCatching {
      repository.observeVote("trip-1", "vote-1").first()
    }
        .exceptionOrNull()
    assertTrue(actual is IllegalStateException)
    assertEquals(failure.message, actual?.message)
  }

  @Suppress("UNCHECKED_CAST")
  private fun capturedDocumentListener(): EventListener<DocumentSnapshot> {
    val captor =
        ArgumentCaptor.forClass(EventListener::class.java)
            as ArgumentCaptor<EventListener<DocumentSnapshot>>
    verify(voteDocument).addSnapshotListener(captor.capture())
    return captor.value
  }

  @Test
  fun closeTransactionReadsThenUpdatesOnlyStatus() = runTest {
    val transaction = mock(Transaction::class.java)
    val snapshot = document(vote.uid, voteData)
    `when`(snapshot.getString("status")).thenReturn("OPEN")
    `when`(transaction.get(voteDocument)).thenReturn(snapshot)
    stubTransaction(transaction)
    repository.closeVote("trip-1", "vote-1")
    val order = inOrder(transaction)
    order.verify(transaction).get(voteDocument)
    order.verify(transaction).update(voteDocument, "status", "CLOSED")
    verifyNoMoreInteractions(transaction)
    verify(voteDocument, never()).update(anyString(), any())
  }

  @Test
  fun closeRejectsMissingAndAlreadyClosedWithoutWriting() = runTest {
    val transaction = mock(Transaction::class.java)
    val snapshot = mock(DocumentSnapshot::class.java)
    `when`(transaction.get(voteDocument)).thenReturn(snapshot)
    stubTransaction(transaction)
    for ((exists, status, code) in
        listOf(
            Triple(false, null, FirebaseFirestoreException.Code.NOT_FOUND),
            Triple(true, "CLOSED", FirebaseFirestoreException.Code.FAILED_PRECONDITION),
        )) {
      `when`(snapshot.exists()).thenReturn(exists)
      `when`(snapshot.getString("status")).thenReturn(status)
      val failure = runCatching { repository.closeVote("trip-1", "vote-1") }.exceptionOrNull()
      assertEquals(code, (failure as FirebaseFirestoreException).code)
    }
    verify(transaction, never()).update(eq(voteDocument), anyString(), any())
  }

  @Test
  fun closeOfflineFailurePropagatesAndCanRetryOnline() = runTest {
    val offline = FirebaseFirestoreException("offline", FirebaseFirestoreException.Code.UNAVAILABLE)
    doReturn(failedTask<Unit>(offline)).`when`(db).runTransaction(any<Transaction.Function<Unit>>())
    assertSame(offline, runCatching { repository.closeVote("trip-1", "vote-1") }.exceptionOrNull())
    verifyNoInteractions(voteDocument)
    val transaction = mock(Transaction::class.java)
    val snapshot = document(vote.uid, voteData)
    `when`(snapshot.getString("status")).thenReturn("OPEN")
    `when`(transaction.get(voteDocument)).thenReturn(snapshot)
    stubTransaction(transaction)
    repository.closeVote("trip-1", "vote-1")
    verify(transaction).update(voteDocument, "status", "CLOSED")
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
  fun createVoteWithConflictingQuestionFailsWithoutWriting() = runTest {
    val transaction = mock(Transaction::class.java)
    val snapshot = document("vote-1", voteData)
    `when`(snapshot.exists()).thenReturn(true)
    `when`(transaction.get(voteDocument)).thenReturn(snapshot)
    stubTransaction(transaction)
    doReturn(successfulTask(snapshot)).`when`(voteDocument).get()
    val failure = runCatching {
      repository.createVote(vote.copy(question = "Different?"))
    }
        .exceptionOrNull()

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
    doReturn(successfulTask(existing)).`when`(voteDocument).get()
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
  fun getVoteReturnsExistingVoteUsingDirectPath() = runTest {
    doReturn(successfulTask(document("vote-1", voteData))).`when`(voteDocument).get()
    assertEquals(vote, repository.getVote("trip-1", "vote-1"))
    verify(votes).document("vote-1")
    verify(votes, never()).get()
  }

  @Test
  fun getVoteReturnsNullForMissingDocument() = runTest {
    val missing = mock(DocumentSnapshot::class.java)
    doReturn(successfulTask(missing)).`when`(voteDocument).get()
    assertNull(repository.getVote("trip-1", "vote-1"))
  }

  @Test
  fun getVotePropagatesFirestoreFailure() = runTest {
    val error = FirebaseFirestoreException("offline", FirebaseFirestoreException.Code.UNAVAILABLE)
    doReturn(failedTask<DocumentSnapshot>(error)).`when`(voteDocument).get()
    assertSame(error, runCatching { repository.getVote("trip-1", "vote-1") }.exceptionOrNull())
  }

  @Test
  fun duplicateCreationSucceedsEvenAfterStatusAndWinnerChange() = runTest {
    for (data in
        listOf(voteData, voteData + mapOf("status" to "CLOSED", "winningOptionId" to "option-1"))) {
      val transaction = mock(Transaction::class.java)
      val existing = document("vote-1", data)
      `when`(transaction.get(voteDocument)).thenReturn(existing)
      stubTransaction(transaction)
      doReturn(successfulTask(existing)).`when`(voteDocument).get()
      repository.createVote(vote)
      verify(transaction, never()).set(any(), any())
    }
  }

  @Test
  fun duplicateCreationRejectsEachConflictingCreationField() = runTest {
    val conflicts =
        listOf(
            voteData + ("question" to "Other?"),
            voteData +
                ("options" to
                    vote.options.reversed().map { mapOf("uid" to it.uid, "name" to it.name) }),
            voteData +
                ("options" to
                    listOf(
                        mapOf("uid" to "new-id", "name" to "Museum"),
                        mapOf("uid" to "option-2", "name" to "Park"),
                    )),
            voteData +
                ("options" to
                    listOf(
                        mapOf("uid" to "option-1", "name" to "Beach"),
                        mapOf("uid" to "option-2", "name" to "Park"),
                    )),
            voteData + ("createdByUserId" to "other-user"),
            voteData + ("creationTime" to Timestamp(101, 123)),
            voteData + ("closingTime" to timestamp),
        )
    for (data in conflicts) {
      val transaction = mock(Transaction::class.java)
      val existing = document("vote-1", data)
      `when`(transaction.get(voteDocument)).thenReturn(existing)
      stubTransaction(transaction)
      doReturn(successfulTask(existing)).`when`(voteDocument).get()
      val error = runCatching { repository.createVote(vote) }.exceptionOrNull()
      assertEquals(
          FirebaseFirestoreException.Code.ALREADY_EXISTS,
          (error as FirebaseFirestoreException).code,
      )
      verify(transaction, never()).set(any(), any())
    }
  }

  @Test
  fun duplicateCreationWithMissingDocumentRemainsFailure() = runTest {
    val error = FirebaseFirestoreException("exists", FirebaseFirestoreException.Code.ALREADY_EXISTS)
    doReturn(failedTask<Unit>(error)).`when`(db).runTransaction(any<Transaction.Function<Unit>>())
    doReturn(successfulTask(mock(DocumentSnapshot::class.java))).`when`(voteDocument).get()
    assertSame(error, runCatching { repository.createVote(vote) }.exceptionOrNull())
  }

  @Test
  fun duplicateCreationPropagatesLookupFailure() = runTest {
    val duplicate =
        FirebaseFirestoreException("exists", FirebaseFirestoreException.Code.ALREADY_EXISTS)
    val readError =
        FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)
    doReturn(failedTask<Unit>(duplicate))
        .`when`(db)
        .runTransaction(any<Transaction.Function<Unit>>())
    doReturn(failedTask<DocumentSnapshot>(readError)).`when`(voteDocument).get()
    assertSame(readError, runCatching { repository.createVote(vote) }.exceptionOrNull())
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
            { repository.observeVotes("").first() },
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
  fun observeVotesEmitsInitialAdditionsUpdatesAndRemovalsAndCleansUp() = runTest {
    val registration = mock(ListenerRegistration::class.java)
    `when`(
            votes.addSnapshotListener(
                any<EventListener<QuerySnapshot>>(),
            )
        )
        .thenReturn(registration)
    val changes = mutableListOf<List<Vote>>()
    val flow = repository.observeVotes("trip-1")
    verifyNoInteractions(votes)
    val collection = launch { flow.take(5).collect { changes.add(it) } }
    runCurrent()
    val listener = capturedListener(votes)
    val second = vote.copy(uid = "vote-2")
    val updated = vote.copy(question = "Updated?", status = VoteStatus.CLOSED)
    listener.onEvent(query(), null)
    listener.onEvent(query(document(vote.uid, voteData)), null)
    listener.onEvent(query(document(vote.uid, voteData), document(second.uid, voteData)), null)
    listener.onEvent(
        query(document(vote.uid, voteData + mapOf("question" to "Updated?", "status" to "CLOSED"))),
        null,
    )
    listener.onEvent(query(), null)
    collection.join()

    assertEquals(
        listOf(emptyList(), listOf(vote), listOf(vote, second), listOf(updated), emptyList()),
        changes,
    )
    verify(trips).document("trip-1")
    verify(trip).collection("votes")
    verify(registration).remove()
  }

  @Test
  fun observeVotesIsIsolatedToTheRequestedTripAndUsesPathIds() = runTest {
    val otherTrip = mock(DocumentReference::class.java)
    val otherVotes = mock(CollectionReference::class.java)
    `when`(trips.document("trip-2")).thenReturn(otherTrip)
    `when`(otherTrip.collection("votes")).thenReturn(otherVotes)
    val registration = mock(ListenerRegistration::class.java)
    val otherRegistration = mock(ListenerRegistration::class.java)
    `when`(
            votes.addSnapshotListener(
                any<EventListener<QuerySnapshot>>(),
            )
        )
        .thenReturn(registration)
    `when`(
            otherVotes.addSnapshotListener(
                any<EventListener<QuerySnapshot>>(),
            )
        )
        .thenReturn(otherRegistration)
    val current = mutableListOf<List<Vote>>()
    val other = mutableListOf<List<Vote>>()
    val firstCollection = launch { repository.observeVotes("trip-1").collect { current.add(it) } }
    val secondCollection = launch { repository.observeVotes("trip-2").collect { other.add(it) } }
    runCurrent()
    val listener = capturedListener(votes)
    val otherListener = capturedListener(otherVotes)
    listener.onEvent(query(), null)
    otherListener.onEvent(query(document("other-vote", voteData)), null)
    runCurrent()
    assertEquals(listOf(emptyList<Vote>()), current)
    assertEquals(listOf(listOf(vote.copy(uid = "other-vote", tripId = "trip-2"))), other)
    listener.onEvent(
        query(document(vote.uid, voteData + mapOf("tripId" to "trip-2", "uid" to "wrong"))),
        null,
    )
    runCurrent()
    assertEquals(listOf(emptyList(), listOf(vote)), current)
    firstCollection.cancel()
    secondCollection.cancel()
    firstCollection.join()
    secondCollection.join()
    verify(registration).remove()
    verify(otherRegistration).remove()
  }

  @Test
  fun observeVotesRemovesListenerWhenCollectionIsCancelled() = runTest {
    val registration = mock(ListenerRegistration::class.java)
    `when`(
            votes.addSnapshotListener(
                any<EventListener<QuerySnapshot>>(),
            )
        )
        .thenReturn(registration)
    val collection = launch { repository.observeVotes("trip-1").collect() }
    runCurrent()
    collection.cancel()
    collection.join()
    verify(registration).remove()
  }

  @Test
  fun observeVotesPropagatesObservationFailureAndRemovesListener() = runTest {
    val registration = mock(ListenerRegistration::class.java)
    `when`(
            votes.addSnapshotListener(
                any<EventListener<QuerySnapshot>>(),
            )
        )
        .thenReturn(registration)
    val error =
        FirebaseFirestoreException("Unavailable", FirebaseFirestoreException.Code.UNAVAILABLE)
    val result = async { runCatching { repository.observeVotes("trip-1").first() } }
    runCurrent()
    capturedListener(votes).onEvent(null, error)
    assertSame(error, result.await().exceptionOrNull())
    verify(registration).remove()
  }

  @Test
  fun observeVotesRejectsMalformedSnapshotsWithoutEmittingPartialLists() = runTest {
    for (snapshot in
        listOf(query(document(vote.uid, voteData), document("bad", voteData - "question")), null)) {
      reset(votes)
      val registration = mock(ListenerRegistration::class.java)
      `when`(
              votes.addSnapshotListener(
                  any<EventListener<QuerySnapshot>>(),
              )
          )
          .thenReturn(registration)
      val result = async { runCatching { repository.observeVotes("trip-1").first() } }
      runCurrent()
      capturedListener(votes).onEvent(snapshot, null)
      assertTrue(result.await().exceptionOrNull() is IllegalArgumentException)
      verify(registration).remove()
    }
  }

  @Test
  fun observeVotesReportsListenerRegistrationFailure() = runTest {
    val error = IllegalStateException("Unable to register")
    `when`(
            votes.addSnapshotListener(
                any<EventListener<QuerySnapshot>>(),
            )
        )
        .thenThrow(error)
    val failure = runCatching { repository.observeVotes("trip-1").first() }.exceptionOrNull()
    assertTrue(failure is IllegalStateException)
    assertEquals(error.message, failure?.message)
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

  @Test
  fun finalBallotsIgnoreCachedAndPendingSnapshotsUntilServerConfirmedIncludingMetadataOnlyChanges() =
      runTest {
        val registration = mock(ListenerRegistration::class.java)
        `when`(
                ballots.addSnapshotListener(
                    eq(MetadataChanges.INCLUDE),
                    any<EventListener<QuerySnapshot>>(),
                )
            )
            .thenReturn(registration)
        val changes = mutableListOf<List<VoteBallot>>()
        val job = launch {
          repository.observeFinalBallots("trip-1", "vote-1").collect { changes += it }
        }
        runCurrent()
        val listener = capturedListener(serverOnly = true)
        val ballot = VoteBallot("user-1", "option-2", timestamp)
        val document = document("user-1", ballotData(ballot))
        fun snapshot(cached: Boolean, pending: Boolean, withBallot: Boolean = true): QuerySnapshot {
          val snapshot = if (withBallot) query(document) else query()
          val metadata = mock(SnapshotMetadata::class.java)
          `when`(metadata.isFromCache).thenReturn(cached)
          `when`(metadata.hasPendingWrites()).thenReturn(pending)
          `when`(snapshot.metadata).thenReturn(metadata)
          return snapshot
        }
        listener.onEvent(snapshot(cached = true, pending = false), null)
        runCurrent()
        assertTrue(changes.isEmpty())
        listener.onEvent(snapshot(cached = false, pending = true), null)
        runCurrent()
        assertTrue(changes.isEmpty())
        // Same document data becomes committed: the metadata-only event must reach the UI.
        listener.onEvent(snapshot(cached = false, pending = false), null)
        runCurrent()
        assertEquals(listOf(listOf(ballot)), changes)
        listener.onEvent(snapshot(cached = false, pending = false, withBallot = false), null)
        runCurrent()
        assertEquals(listOf(listOf(ballot), emptyList<VoteBallot>()), changes)
        job.cancel()
        job.join()
        verify(registration).remove()
        verify(ballots, never()).addSnapshotListener(any<EventListener<QuerySnapshot>>())
      }

  @Test
  fun finalBallotsPropagateErrorAndReleaseListenerWhileWaitingForServer() = runTest {
    val registration = mock(ListenerRegistration::class.java)
    `when`(
            ballots.addSnapshotListener(
                eq(MetadataChanges.INCLUDE),
                any<EventListener<QuerySnapshot>>(),
            )
        )
        .thenReturn(registration)
    val failure =
        FirebaseFirestoreException("Unavailable", FirebaseFirestoreException.Code.UNAVAILABLE)
    val result = async {
      runCatching { repository.observeFinalBallots("trip-1", "vote-1").first() }
    }
    runCurrent()
    capturedListener(serverOnly = true).onEvent(null, failure)
    assertSame(failure, result.await().exceptionOrNull())
    verify(registration).remove()
  }

  private fun ballotData(ballot: VoteBallot): Map<String, Any?> =
      mapOf(
          "userId" to ballot.userId,
          "optionId" to ballot.optionId,
          "updatedAt" to ballot.updatedAt,
      )

  private fun document(id: String, data: Map<String, Any?>): DocumentSnapshot =
      mock(DocumentSnapshot::class.java).also {
        `when`(it.exists()).thenReturn(true)
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
  private fun capturedListener(
      collection: CollectionReference = ballots,
      serverOnly: Boolean = false,
  ): EventListener<QuerySnapshot> {
    val captor =
        ArgumentCaptor.forClass(EventListener::class.java)
            as ArgumentCaptor<EventListener<QuerySnapshot>>
    if (serverOnly)
        verify(collection).addSnapshotListener(eq(MetadataChanges.INCLUDE), captor.capture())
    else verify(collection).addSnapshotListener(captor.capture())
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
