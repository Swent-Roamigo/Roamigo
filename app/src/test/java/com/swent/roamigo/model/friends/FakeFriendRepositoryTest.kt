// AI assistance was used for implementation support.
package com.swent.roamigo.model.friends

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakeFriendRepositoryTest {
  private val now = Instant.parse("2026-11-07T10:00:00Z")
  private val clock = Clock.fixed(now, ZoneOffset.UTC)
  private val store = FakeFriendRepository.Store()
  private val alice = FakeFriendRepository("alice", store, clock)
  private val bob = FakeFriendRepository("bob", store, clock)
  private val charlie = FakeFriendRepository("charlie", store, clock)

  @Test
  fun newAccount_hasNoFriendsOrRequests() = runTest {
    assertTrue(alice.getFriends().isEmpty())
    assertTrue(alice.getIncomingRequests().isEmpty())
    assertTrue(alice.getOutgoingRequests().isEmpty())
  }

  @Test
  fun sendRequest_isOutgoingForSenderAndIncomingForRecipientWithoutFriendship() = runTest {
    val request = alice.sendFriendRequest("bob")

    assertEquals("alice", request.senderUserId)
    assertEquals("bob", request.recipientUserId)
    assertEquals(FriendRequestStatus.PENDING, request.status)
    assertEquals(now, request.createdAt)
    assertEquals(listOf(request), alice.getOutgoingRequests())
    assertEquals(listOf(request), bob.getIncomingRequests())
    assertTrue(alice.getIncomingRequests().isEmpty())
    assertTrue(bob.getOutgoingRequests().isEmpty())
    assertTrue(alice.getFriends().isEmpty())
    assertTrue(bob.getFriends().isEmpty())
  }

  @Test
  fun duplicateSend_returnsTheExistingPendingRequest() = runTest {
    val original = alice.sendFriendRequest("bob")

    assertEquals(original, alice.sendFriendRequest("bob"))
    assertEquals(listOf(original), bob.getIncomingRequests())
  }

  @Test
  fun acceptRequest_establishesOneMutualFriendshipAndDuplicateAcceptanceIsHarmless() = runTest {
    val request = alice.sendFriendRequest("bob")
    val friendship = bob.acceptFriendRequest("alice", request.uid)

    assertEquals(request.uid, friendship.uid)
    assertEquals(now, friendship.acceptedAt)
    assertEquals(listOf(friendship), alice.getFriends())
    assertEquals(listOf(friendship), bob.getFriends())
    assertTrue(alice.getOutgoingRequests().isEmpty())
    assertTrue(bob.getIncomingRequests().isEmpty())
    assertEquals(friendship, bob.acceptFriendRequest("alice", request.uid))
    assertEquals(FriendRequestStatus.ACCEPTED, alice.sendFriendRequest("bob").status)
    assertEquals(listOf(friendship), alice.getFriends())
  }

  @Test
  fun crossedRequest_acceptsTheOriginalRequestWithoutReversingItsDirection() = runTest {
    val original = alice.sendFriendRequest("bob")
    val accepted = bob.sendFriendRequest("alice")

    assertEquals(original.uid, accepted.uid)
    assertEquals(original.senderUserId, accepted.senderUserId)
    assertEquals(original.recipientUserId, accepted.recipientUserId)
    assertEquals(FriendRequestStatus.ACCEPTED, accepted.status)
    assertEquals(now, accepted.respondedAt)
    assertEquals(1, alice.getFriends().size)
    assertEquals(alice.getFriends(), bob.getFriends())
    assertTrue(alice.getOutgoingRequests().isEmpty())
    assertTrue(bob.getIncomingRequests().isEmpty())
  }

  @Test
  fun acceptRequest_withDifferentClocks_neverRespondsBeforeCreation() = runTest {
    for ((responseTime, expectedTime) in
        listOf(now.minusSeconds(60) to now, now.plusSeconds(60) to now.plusSeconds(60))) {
      val recipient = FakeFriendRepository("bob", store, Clock.fixed(responseTime, ZoneOffset.UTC))
      val request = alice.sendFriendRequest("bob")

      val friendship = recipient.acceptFriendRequest("alice", request.uid)

      assertEquals(expectedTime, friendship.acceptedAt)
      assertEquals(listOf(friendship), alice.getFriends())
      assertEquals(listOf(friendship), recipient.getFriends())
      assertEquals(friendship, recipient.acceptFriendRequest("alice", request.uid))
      alice.removeFriend("bob", friendship.uid)
    }
  }

  @Test
  fun declineRequest_withDifferentClocks_clearsPendingRequests() = runTest {
    for (offset in listOf(-60L, 60L)) {
      val recipient =
          FakeFriendRepository("bob", store, Clock.fixed(now.plusSeconds(offset), ZoneOffset.UTC))
      val request = alice.sendFriendRequest("bob")

      recipient.declineFriendRequest("alice", request.uid)
      recipient.declineFriendRequest("alice", request.uid)

      assertTrue(alice.getOutgoingRequests().isEmpty())
      assertTrue(recipient.getIncomingRequests().isEmpty())
      assertTrue(alice.getFriends().isEmpty())
      assertTrue(recipient.getFriends().isEmpty())
    }
  }

  @Test
  fun crossedRequest_withDifferentClocks_neverRespondsBeforeCreation() = runTest {
    for ((responseTime, expectedTime) in
        listOf(now.minusSeconds(60) to now, now.plusSeconds(60) to now.plusSeconds(60))) {
      val recipient = FakeFriendRepository("bob", store, Clock.fixed(responseTime, ZoneOffset.UTC))
      val request = alice.sendFriendRequest("bob")

      val accepted = recipient.sendFriendRequest("alice")

      assertEquals(request.uid, accepted.uid)
      assertEquals(request.createdAt, accepted.createdAt)
      assertEquals(FriendRequestStatus.ACCEPTED, accepted.status)
      assertEquals(expectedTime, accepted.respondedAt)
      assertEquals(alice.getFriends(), recipient.getFriends())
      alice.removeFriend("bob", accepted.uid)
    }
  }

  @Test
  fun declineRequest_clearsPendingListsAndDuplicateDeclineIsHarmless() = runTest {
    val request = alice.sendFriendRequest("bob")
    bob.declineFriendRequest("alice", request.uid)
    bob.declineFriendRequest("alice", request.uid)

    assertTrue(alice.getOutgoingRequests().isEmpty())
    assertTrue(bob.getIncomingRequests().isEmpty())
    assertTrue(alice.getFriends().isEmpty())
    assertTrue(bob.getFriends().isEmpty())
    expectFailure<FriendRepositoryException.Conflict> {
      bob.acceptFriendRequest("alice", request.uid)
    }
  }

  @Test
  fun afterDecline_eitherUserCanSendImmediatelyWithANewIdentifier() = runTest {
    var request = alice.sendFriendRequest("bob")
    bob.declineFriendRequest("alice", request.uid)

    for ((sender, recipient) in listOf(alice to bob, bob to alice)) {
      val fresh = sender.sendFriendRequest(requireNotNull(recipient.currentUserId))
      assertNotEquals(request.uid, fresh.uid)
      assertEquals(now, fresh.createdAt)
      assertEquals(listOf(fresh), recipient.getIncomingRequests())
      recipient.declineFriendRequest(requireNotNull(sender.currentUserId), fresh.uid)
      request = fresh
    }
  }

  @Test
  fun removeFriend_eitherParticipantCanRemoveForBothAndRepeatRemoval() = runTest {
    for (remover in listOf(alice, bob)) {
      val friendship = establishFriendship()
      val otherUserId = if (remover === alice) "bob" else "alice"
      remover.removeFriend(otherUserId, friendship.uid)
      remover.removeFriend(otherUserId, friendship.uid)

      assertTrue(alice.getFriends().isEmpty())
      assertTrue(bob.getFriends().isEmpty())
    }
  }

  @Test
  fun afterRemoval_eitherUserCanSendImmediatelyWithoutRestoringOldFriendship() = runTest {
    for ((sender, recipient) in listOf(alice to bob, bob to alice)) {
      val friendship = establishFriendship()
      alice.removeFriend("bob", friendship.uid)
      val fresh = sender.sendFriendRequest(requireNotNull(recipient.currentUserId))

      assertNotEquals(friendship.uid, fresh.uid)
      assertEquals(now, fresh.createdAt)
      assertEquals(FriendRequestStatus.PENDING, fresh.status)
      assertTrue(sender.getFriends().isEmpty())
      assertTrue(recipient.getFriends().isEmpty())
      recipient.declineFriendRequest(requireNotNull(sender.currentUserId), fresh.uid)
    }
  }

  @Test
  fun staleResponses_cannotChangeTheReplacementRequest() = runTest {
    val original = alice.sendFriendRequest("bob")
    bob.declineFriendRequest("alice", original.uid)
    val fresh = alice.sendFriendRequest("bob")

    expectFailure<FriendRepositoryException.Conflict> {
      bob.acceptFriendRequest("alice", original.uid)
    }
    expectFailure<FriendRepositoryException.Conflict> {
      bob.declineFriendRequest("alice", original.uid)
    }
    assertEquals(listOf(fresh), bob.getIncomingRequests())
    assertEquals(fresh.uid, bob.acceptFriendRequest("alice", fresh.uid).uid)
  }

  @Test
  fun staleAcceptance_cannotRestoreARemovedFriendship() = runTest {
    val friendship = establishFriendship()
    alice.removeFriend("bob", friendship.uid)

    expectFailure<FriendRepositoryException.Conflict> {
      bob.acceptFriendRequest("alice", friendship.uid)
    }
    assertTrue(alice.getFriends().isEmpty())
    assertTrue(bob.getFriends().isEmpty())
  }

  @Test
  fun staleRemoval_cannotRemoveANewPendingRequestOrFriendship() = runTest {
    val original = establishFriendship()
    alice.removeFriend("bob", original.uid)
    val fresh = alice.sendFriendRequest("bob")

    expectFailure<FriendRepositoryException.Conflict> { alice.removeFriend("bob", original.uid) }
    assertEquals(listOf(fresh), bob.getIncomingRequests())
    val friendship = bob.acceptFriendRequest("alice", fresh.uid)
    expectFailure<FriendRepositoryException.Conflict> { alice.removeFriend("bob", original.uid) }
    assertEquals(listOf(friendship), alice.getFriends())
    assertEquals(listOf(friendship), bob.getFriends())
  }

  @Test
  fun senderCannotAcceptOrDeclineAndPendingRequestsCannotBeRemoved() = runTest {
    val request = alice.sendFriendRequest("bob")

    expectFailure<FriendRepositoryException.PermissionDenied> {
      alice.acceptFriendRequest("bob", request.uid)
    }
    expectFailure<FriendRepositoryException.PermissionDenied> {
      alice.declineFriendRequest("bob", request.uid)
    }
    expectFailure<FriendRepositoryException.Conflict> { alice.removeFriend("bob", request.uid) }
    assertEquals(listOf(request), bob.getIncomingRequests())
  }

  @Test
  fun acceptedRequest_cannotBeDeclined() = runTest {
    val friendship = establishFriendship()

    expectFailure<FriendRepositoryException.Conflict> {
      bob.declineFriendRequest("alice", friendship.uid)
    }
    assertEquals(listOf(friendship), alice.getFriends())
  }

  @Test
  fun unrelatedUser_cannotReadOrRespondToAnotherPairsRequest() = runTest {
    val request = alice.sendFriendRequest("bob")

    assertTrue(charlie.getIncomingRequests().isEmpty())
    assertTrue(charlie.getOutgoingRequests().isEmpty())
    assertTrue(charlie.getFriends().isEmpty())
    expectFailure<FriendRepositoryException.Conflict> {
      charlie.acceptFriendRequest("alice", request.uid)
    }
    expectFailure<FriendRepositoryException.Conflict> {
      charlie.declineFriendRequest("alice", request.uid)
    }
    val friendship = bob.acceptFriendRequest("alice", request.uid)
    charlie.removeFriend("alice", friendship.uid)
    assertTrue(charlie.getFriends().isEmpty())
    assertEquals(listOf(friendship), alice.getFriends())
  }

  @Test
  fun invalidIdentifiersAndSelfRequest_areRejectedWithoutChangingState() = runTest {
    val request = alice.sendFriendRequest("bob")
    expectFailure<IllegalArgumentException> { alice.sendFriendRequest("alice") }

    for (invalid in listOf("", " ", ".", "..", "alice/bob")) {
      val operations =
          listOf<suspend () -> Unit>(
              { alice.sendFriendRequest(invalid) },
              { bob.acceptFriendRequest(invalid, request.uid) },
              { bob.declineFriendRequest(invalid, request.uid) },
              { alice.removeFriend(invalid, request.uid) },
              { bob.acceptFriendRequest("alice", invalid) },
              { bob.declineFriendRequest("alice", invalid) },
              { alice.removeFriend("bob", invalid) },
          )
      operations.forEach { expectFailure<IllegalArgumentException>(it) }
    }
    assertEquals(listOf(request), bob.getIncomingRequests())
  }

  @Test
  fun signedOutUser_cannotReadObserveOrWrite() = runTest {
    val signedOut = FakeFriendRepository(store = store, clock = clock)
    val operations =
        listOf<suspend () -> Unit>(
            { signedOut.getFriends() },
            { signedOut.getIncomingRequests() },
            { signedOut.getOutgoingRequests() },
            { signedOut.observeFriends().first() },
            { signedOut.observeIncomingRequests().first() },
            { signedOut.observeOutgoingRequests().first() },
            { signedOut.sendFriendRequest("bob") },
            { signedOut.acceptFriendRequest("bob", "request") },
            { signedOut.declineFriendRequest("bob", "request") },
            { signedOut.removeFriend("bob", "request") },
        )

    operations.forEach { expectFailure<FriendRepositoryException.NotAuthenticated>(it) }
    assertTrue(bob.getIncomingRequests().isEmpty())
  }

  @Test
  fun configuredFailures_leaveDataUnchangedAndClearingFailureAllowsRecovery() = runTest {
    val request = alice.sendFriendRequest("bob")
    val sourceFailure = IllegalStateException("Service failure")
    val operations =
        listOf<suspend () -> Unit>(
            { bob.getFriends() },
            { bob.getIncomingRequests() },
            { bob.getOutgoingRequests() },
            { bob.observeFriends().first() },
            { bob.observeIncomingRequests().first() },
            { bob.observeOutgoingRequests().first() },
            { bob.sendFriendRequest("charlie") },
            { bob.acceptFriendRequest("alice", request.uid) },
            { bob.declineFriendRequest("alice", request.uid) },
            { bob.removeFriend("alice", request.uid) },
        )
    for (failure in
        listOf(
            FriendRepositoryException.Network(sourceFailure),
            FriendRepositoryException.PermissionDenied(sourceFailure),
            FriendRepositoryException.InvalidData(sourceFailure),
            FriendRepositoryException.Unknown(sourceFailure),
        )) {
      bob.failure = failure
      operations.forEach {
        val actual = expectFailure<FriendRepositoryException>(it)
        assertEquals(failure.javaClass, actual.javaClass)
        assertTrue(generateSequence<Throwable>(actual) { it.cause }.any { it === sourceFailure })
      }
    }
    bob.failure = null

    assertEquals(listOf(request), bob.getIncomingRequests())
    assertTrue(charlie.getIncomingRequests().isEmpty())
    assertEquals(request.uid, bob.acceptFriendRequest("alice", request.uid).uid)
  }

  @Test
  fun observeRequests_updatesBothSidesOnSendDeclineAcceptAndCrossedRequest() = runTest {
    val incoming = mutableListOf<List<FriendRequest>>()
    val outgoing = mutableListOf<List<FriendRequest>>()
    val incomingObserver =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
          bob.observeIncomingRequests().toList(incoming)
        }
    val outgoingObserver =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
          alice.observeOutgoingRequests().toList(outgoing)
        }
    runCurrent()
    assertEquals(listOf(emptyList<FriendRequest>()), incoming)
    assertEquals(incoming, outgoing)

    val declined = alice.sendFriendRequest("bob")
    runCurrent()
    bob.declineFriendRequest("alice", declined.uid)
    runCurrent()
    val accepted = alice.sendFriendRequest("bob")
    runCurrent()
    bob.acceptFriendRequest("alice", accepted.uid)
    runCurrent()
    alice.removeFriend("bob", accepted.uid)
    runCurrent()
    val crossed = alice.sendFriendRequest("bob")
    runCurrent()
    bob.sendFriendRequest("alice")
    runCurrent()

    assertEquals(
        listOf(
            emptyList(),
            listOf(declined),
            emptyList(),
            listOf(accepted),
            emptyList(),
            listOf(crossed),
            emptyList(),
        ),
        incoming,
    )
    assertEquals(incoming, outgoing)
    outgoingObserver.cancelAndJoin()
    assertEquals(0, alice.activeObservers)
    assertEquals(1, bob.activeObservers)
    incomingObserver.cancelAndJoin()
    assertEquals(0, bob.activeObservers)
  }

  @Test
  fun observeRequests_emitsExistingPendingAndIgnoresOtherDirectionsAndPairs() = runTest {
    val request = alice.sendFriendRequest("bob")
    bob.sendFriendRequest("charlie")
    charlie.sendFriendRequest("alice")
    val incoming = mutableListOf<List<FriendRequest>>()
    val outgoing = mutableListOf<List<FriendRequest>>()
    val incomingObserver =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
          bob.observeIncomingRequests().toList(incoming)
        }
    val outgoingObserver =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
          alice.observeOutgoingRequests().toList(outgoing)
        }
    runCurrent()
    charlie.sendFriendRequest("dave")
    runCurrent()

    assertEquals(listOf(listOf(request)), incoming)
    assertEquals(incoming, outgoing)
    incomingObserver.cancelAndJoin()
    outgoingObserver.cancelAndJoin()
    assertEquals(0, alice.activeObservers)
    assertEquals(0, bob.activeObservers)
  }

  @Test
  fun requestObservation_failureTerminatesReleasesAndAllowsNewCollection() = runTest {
    val request = alice.sendFriendRequest("bob")
    for (repository in listOf(alice, bob)) {
      val flow =
          if (repository === alice) repository.observeOutgoingRequests()
          else repository.observeIncomingRequests()
      val updates = mutableListOf<List<FriendRequest>>()
      var observedFailure: Throwable? = null
      val observer =
          backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            observedFailure = runCatching { flow.toList(updates) }.exceptionOrNull()
          }
      runCurrent()
      val sourceFailure = IllegalStateException("Controlled observation failure")
      val failure = FriendRepositoryException.Network(sourceFailure)
      repository.failure = failure
      runCurrent()

      assertTrue(observer.isCompleted)
      assertTrue(observedFailure is FriendRepositoryException.Network)
      assertTrue(generateSequence(observedFailure) { it.cause }.any { it === sourceFailure })
      assertEquals(listOf(listOf(request)), updates)
      assertEquals(0, repository.activeObservers)
      repository.failure = null
      assertEquals(listOf(request), flow.first())
      assertEquals(0, repository.activeObservers)
    }
  }

  @Test
  fun requestObservation_accountChangeOrQuickSignOutEndsBothSubscriptions() = runTest {
    for (nextUser in listOf(null, "charlie")) {
      val incoming = mutableListOf<List<FriendRequest>>()
      val outgoing = mutableListOf<List<FriendRequest>>()
      val incomingObserver =
          backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            alice.observeIncomingRequests().toList(incoming)
          }
      val outgoingObserver =
          backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            alice.observeOutgoingRequests().toList(outgoing)
          }
      runCurrent()
      assertEquals(2, alice.activeObservers)
      alice.currentUserId = nextUser
      if (nextUser == null) alice.currentUserId = "alice"
      runCurrent()

      assertTrue(incomingObserver.isCompleted)
      assertTrue(outgoingObserver.isCompleted)
      assertEquals(1, incoming.size)
      assertEquals(1, outgoing.size)
      assertEquals(0, alice.activeObservers)
      alice.currentUserId = "alice"
    }
  }

  @Test
  fun requestFlows_resolveTheAccountWhenCollectionStarts() = runTest {
    val incoming = alice.observeIncomingRequests()
    val outgoing = alice.observeOutgoingRequests()
    val request = alice.sendFriendRequest("bob")
    alice.currentUserId = "bob"

    assertEquals(listOf(request), incoming.first())
    assertTrue(outgoing.first().isEmpty())
    assertEquals(0, alice.activeObservers)
  }

  @Test
  fun observeFriends_emitsInitialAcceptanceAndRemovalForBothUsers() = runTest {
    val aliceUpdates = mutableListOf<List<Friendship>>()
    val bobUpdates = mutableListOf<List<Friendship>>()
    val aliceObserver =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
          alice.observeFriends().toList(aliceUpdates)
        }
    val bobObserver =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
          bob.observeFriends().toList(bobUpdates)
        }
    runCurrent()
    assertEquals(listOf(emptyList<Friendship>()), aliceUpdates)
    val friendship = establishFriendship()
    runCurrent()
    alice.removeFriend("bob", friendship.uid)
    runCurrent()

    assertEquals(listOf(emptyList(), listOf(friendship), emptyList()), aliceUpdates)
    assertEquals(aliceUpdates, bobUpdates)
    aliceObserver.cancelAndJoin()
    bobObserver.cancelAndJoin()
    assertEquals(0, alice.activeObservers)
    assertEquals(0, bob.activeObservers)
  }

  @Test
  fun observers_haveIndependentLifetimesAndIgnoreUnrelatedChanges() = runTest {
    val firstUpdates = mutableListOf<List<Friendship>>()
    val secondUpdates = mutableListOf<List<Friendship>>()
    val first =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
          alice.observeFriends().toList(firstUpdates)
        }
    val second =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
          alice.observeFriends().toList(secondUpdates)
        }
    runCurrent()
    assertEquals(2, alice.activeObservers)
    first.cancelAndJoin()
    assertEquals(1, alice.activeObservers)
    charlie.sendFriendRequest("dave")
    runCurrent()
    assertEquals(listOf(emptyList<Friendship>()), secondUpdates)
    val friendship = establishFriendship()
    runCurrent()

    assertEquals(listOf(emptyList<Friendship>()), firstUpdates)
    assertEquals(listOf(emptyList(), listOf(friendship)), secondUpdates)
    second.cancelAndJoin()
    assertEquals(0, alice.activeObservers)
  }

  @Test
  fun observationFailure_terminatesAndCanBeCollectedAgainAfterRecovery() = runTest {
    val updates = mutableListOf<List<Friendship>>()
    var observedFailure: Throwable? = null
    val observer =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
          observedFailure = runCatching { alice.observeFriends().toList(updates) }.exceptionOrNull()
        }
    runCurrent()
    val sourceFailure = IllegalStateException("Service failure")
    val failure = FriendRepositoryException.Network(sourceFailure)
    alice.failure = failure
    runCurrent()

    assertTrue(observer.isCompleted)
    assertTrue(observedFailure is FriendRepositoryException.Network)
    assertTrue(
        generateSequence<Throwable>(observedFailure) { it.cause }.any { it === sourceFailure }
    )
    assertEquals(listOf(emptyList<Friendship>()), updates)
    assertEquals(0, alice.activeObservers)
    alice.failure = null
    val friendship = establishFriendship()
    assertEquals(listOf(friendship), alice.observeFriends().first())
    assertEquals(0, alice.activeObservers)
  }

  @Test
  fun accountChange_endsExistingObservationAndNewCollectionUsesNewAccount() = runTest {
    establishFriendship()
    val updates = mutableListOf<List<Friendship>>()
    val observer =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
          alice.observeFriends().toList(updates)
        }
    runCurrent()
    alice.currentUserId = "charlie"
    runCurrent()

    assertTrue(observer.isCompleted)
    assertEquals(1, updates.size)
    assertEquals(0, alice.activeObservers)
    assertTrue(alice.observeFriends().first().isEmpty())
  }

  @Test
  fun signOutAndSignInBeforeCollectorResumes_stillEndsOldObservation() = runTest {
    val observer = backgroundScope.launch { alice.observeFriends().toList() }
    runCurrent()
    assertEquals(1, alice.activeObservers)
    alice.currentUserId = null
    alice.currentUserId = "alice"
    runCurrent()

    assertTrue(observer.isCompleted)
    assertEquals(0, alice.activeObservers)
  }

  @Test
  fun flow_resolvesTheAccountWhenCollectionStarts() = runTest {
    establishFriendship()
    val flow = alice.observeFriends()
    alice.currentUserId = "charlie"

    assertTrue(flow.first().isEmpty())
    assertEquals(0, alice.activeObservers)
  }

  @Test
  fun cancelledOperation_doesNotWrite() = runTest {
    val operation =
        async(start = CoroutineStart.UNDISPATCHED) {
          currentCoroutineContext().cancel()
          alice.sendFriendRequest("bob")
        }

    expectFailure<CancellationException> { operation.await() }
    assertTrue(alice.getOutgoingRequests().isEmpty())
    assertTrue(bob.getIncomingRequests().isEmpty())
  }

  @Test
  fun concurrentDuplicateSends_createOnePendingRequest() = runTest {
    val start = CompletableDeferred<Unit>()
    val operations =
        List(16) {
          async(Dispatchers.Default) {
            start.await()
            alice.sendFriendRequest("bob")
          }
        }
    start.complete(Unit)
    val requests = operations.awaitAll()

    assertEquals(1, requests.map { it.uid }.toSet().size)
    assertEquals(listOf(requests.first()), bob.getIncomingRequests())
    assertTrue(alice.getFriends().isEmpty())
  }

  @Test
  fun concurrentCrossedSends_establishOneMutualFriendship() = runTest {
    val start = CompletableDeferred<Unit>()
    val operations =
        listOf(alice to "bob", bob to "alice").map { (repository, target) ->
          async(Dispatchers.Default) {
            start.await()
            repository.sendFriendRequest(target)
          }
        }
    start.complete(Unit)
    val requests = operations.awaitAll()

    assertEquals(1, requests.map { it.uid }.toSet().size)
    assertEquals(1, alice.getFriends().size)
    assertEquals(alice.getFriends(), bob.getFriends())
    assertTrue(alice.getIncomingRequests().isEmpty())
    assertTrue(bob.getIncomingRequests().isEmpty())
  }

  @Test
  fun concurrentAcceptAndDecline_haveOneWinnerWithoutPartialState() = runTest {
    val request = alice.sendFriendRequest("bob")
    val start = CompletableDeferred<Unit>()
    val acceptance =
        async(Dispatchers.Default) {
          start.await()
          runCatching { bob.acceptFriendRequest("alice", request.uid) }
        }
    val decline =
        async(Dispatchers.Default) {
          start.await()
          runCatching { bob.declineFriendRequest("alice", request.uid) }
        }
    start.complete(Unit)
    val accepted = acceptance.await()
    val declined = decline.await()

    assertNotEquals(accepted.isSuccess, declined.isSuccess)
    assertTrue(
        (accepted.exceptionOrNull() ?: declined.exceptionOrNull())
            is FriendRepositoryException.Conflict
    )
    assertEquals(if (accepted.isSuccess) 1 else 0, alice.getFriends().size)
    assertEquals(alice.getFriends(), bob.getFriends())
    assertTrue(alice.getOutgoingRequests().isEmpty())
    assertTrue(bob.getIncomingRequests().isEmpty())
  }

  private suspend fun establishFriendship(): Friendship {
    val request = alice.sendFriendRequest("bob")
    return bob.acceptFriendRequest("alice", request.uid)
  }

  private suspend inline fun <reified T : Throwable> expectFailure(action: suspend () -> Unit): T {
    val failure = runCatching { action() }.exceptionOrNull()
    assertTrue("Expected ${T::class.java.simpleName}, got $failure", failure is T)
    return failure as T
  }
}
